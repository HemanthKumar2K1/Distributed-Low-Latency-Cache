package com.hemz.distributedcache.modules.cacheclient.remote;

import com.hemz.distributedcache.modules.cacheclient.CacheAdmin;
import com.hemz.distributedcache.modules.cacheclient.CacheNodeStatus;
import com.hemz.distributedcache.modules.cacheclient.CacheOperations;
import com.hemz.distributedcache.modules.cacheclient.CacheServerInfo;
import com.hemz.distributedcache.modules.cacheclient.ring.HashRing;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * The cache spread over several Cache System nodes.
 * Each key lives on exactly one node, chosen by the hash ring. Each node has its own
 * connection pool (a RemoteCacheOperations).
 *
 * When a node is down, reads and writes for ITS keys fall back to PostgreSQL
 * (counted as cache errors). The other nodes' keys are unaffected.
 * After breaker-threshold failures in a row, that node's CircuitBreaker skips it
 * for breaker-cooldown-ms, so a dead node does not add a timeout to every request.
 */
public final class ShardedCacheOperations implements CacheOperations, CacheAdmin, AutoCloseable {

    /** One node: a display name ("node-1"), and its client. */
    record Node(String name, RemoteCacheOperations client) {
        String address() {
            return client.address();
        }
    }

    private final List<Node> nodes;
    private final HashRing<Node> ring;

    /** settings in node order: the first is "node-1". */
    public ShardedCacheOperations(List<RemoteCacheSettings> settings, int virtualNodes) {
        List<Node> list = new ArrayList<>();
        for (int i = 0; i < settings.size(); i++) {
            list.add(new Node("node-" + (i + 1), new RemoteCacheOperations(settings.get(i))));
        }
        long distinct = list.stream().map(Node::address).distinct().count();
        if (distinct != list.size()) {
            throw new IllegalArgumentException("each cache node must have its own host:port");
        }
        this.nodes = List.copyOf(list);
        // Placed by address, so a key's node does not change when the list is reordered.
        this.ring = new HashRing<>(nodes, virtualNodes, Node::address);
    }

    @Override
    public byte[] get(String key) {
        return ring.nodeFor(key).client().get(key);
    }

    @Override
    public void set(String key, byte[] value) {
        ring.nodeFor(key).client().set(key, value);
    }

    @Override
    public void delete(String key) {
        ring.nodeFor(key).client().delete(key);
    }

    /** Which node owns a key. For tests; requests never need it. */
    String nodeNameFor(String key) {
        return ring.nodeFor(key).name();
    }

    /** Empties every node it can reach, then fails if any node could not be emptied. */
    @Override
    public void flushAll() {
        List<String> failed = new ArrayList<>();
        for (Node n : nodes) {
            try {
                n.client().flushAll();
            } catch (RuntimeException e) {
                failed.add(n.name() + " (" + n.address() + ")");
            }
        }
        if (!failed.isEmpty()) {
            throw new CacheClientException("could not empty " + String.join(", ", failed));
        }
    }

    @Override
    public String address() {
        return nodes.stream().map(Node::address).collect(Collectors.joining(","));
    }

    /** Each node, up or down, with what it holds and its breaker state. */
    @Override
    public List<CacheNodeStatus> nodes() {
        List<CacheNodeStatus> out = new ArrayList<>();
        for (Node n : nodes) {
            try {
                CacheServerInfo info = n.client().info();
                out.add(CacheNodeStatus.up(n.name(), n.address(), info, breaker(n)));
            } catch (RuntimeException e) {
                out.add(CacheNodeStatus.down(n.name(), n.address(), breaker(n)));
            }
        }
        return out;
    }

    private static String breaker(Node n) {
        return n.client().breakerState().name().toLowerCase(Locale.ROOT);
    }

    /** Failure test: stop one node on purpose. Unknown name → IllegalArgumentException. */
    @Override
    public void shutdownNode(String name) {
        Node node = nodes.stream().filter(n -> n.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no cache node named " + name));
        node.client().shutdown();
    }

    @Override
    public void close() {
        nodes.forEach(n -> n.client().close());
    }
}
