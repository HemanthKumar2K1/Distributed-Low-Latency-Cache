package com.hemz.distributedcache.modules.cacheclient.remote;

import com.hemz.distributedcache.modules.cacheclient.CacheNodeStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Three fake nodes on three free ports, one ring in front of them. */
class ShardedCacheOperationsTest {

    /** Circuit breaker: 3 failures in a row, then the node is skipped for 5 s. */
    private static final int BREAKER_THRESHOLD = 3;
    private static final int BREAKER_COOLDOWN_MS = 5000;

    private final List<FakeRespServer> servers = new ArrayList<>();
    private ShardedCacheOperations cache;

    @BeforeEach
    void start() throws IOException {
        List<RemoteCacheSettings> settings = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            FakeRespServer s = new FakeRespServer();
            servers.add(s);
            settings.add(new RemoteCacheSettings("127.0.0.1", s.port(), 200, 200, 4, 20,
                    BREAKER_THRESHOLD, BREAKER_COOLDOWN_MS));
        }
        cache = new ShardedCacheOperations(settings, 150);
    }

    @AfterEach
    void stop() throws IOException {
        cache.close();
        for (FakeRespServer s : servers) s.close();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private FakeRespServer serverOf(String key) {
        return servers.get(Integer.parseInt(cache.nodeNameFor(key).substring("node-".length())) - 1);
    }

    /** The first "product:i" key that lives on this node, like "node-1". */
    private String keyOn(String nodeName) {
        for (int i = 0; i < 1000; i++) {
            if (cache.nodeNameFor("product:" + i).equals(nodeName)) return "product:" + i;
        }
        throw new AssertionError("no key found on " + nodeName);
    }

    /** The first "product:i" key that lives on any other node. */
    private String keyNotOn(String nodeName) {
        for (int i = 0; i < 1000; i++) {
            if (!cache.nodeNameFor("product:" + i).equals(nodeName)) return "product:" + i;
        }
        throw new AssertionError("no key found off " + nodeName);
    }

    @Test
    void eachKeyIsStoredOnExactlyOneNode() {
        for (int i = 0; i < 300; i++) {
            cache.set("product:" + i, bytes("v" + i));
        }

        int total = 0;
        for (FakeRespServer s : servers) {
            assertTrue(s.data.size() > 50, "every node should get a share, got " + s.data.size());
            total += s.data.size();
        }
        assertEquals(300, total, "no key may be stored twice");
        for (int i = 0; i < 300; i++) {
            assertTrue(serverOf("product:" + i).data.containsKey("product:" + i));
        }
    }

    @Test
    void getReadsFromTheNodeThatSetWrote() {
        cache.set("product:42", bytes("lamp"));

        assertArrayEquals(bytes("lamp"), cache.get("product:42"));
        cache.delete("product:42");
        assertNull(cache.get("product:42"));
    }

    @Test
    void aDeadNodeOnlyBreaksItsOwnKeys() throws IOException {
        serverOf("product:1").close();
        String otherKey = keyNotOn(cache.nodeNameFor("product:1"));

        assertThrows(CacheClientException.class, () -> cache.get("product:1"));
        cache.set(otherKey, bytes("ok"));
        assertArrayEquals(bytes("ok"), cache.get(otherKey));
    }

    @Test
    void nodesReportsEachNodeUpOrDown() throws IOException {
        cache.set("product:7", bytes("12345"));
        servers.get(2).close();

        List<CacheNodeStatus> nodes = cache.nodes();

        assertEquals(List.of("node-1", "node-2", "node-3"), nodes.stream().map(CacheNodeStatus::name).toList());
        assertTrue(nodes.get(0).up());
        assertTrue(nodes.get(1).up());
        assertFalse(nodes.get(2).up());
        assertNull(nodes.get(2).keys());
    }

    @Test
    void flushAllEmptiesEveryNodeAndReportsOnesItCouldNot() throws IOException {
        for (int i = 0; i < 30; i++) {
            cache.set("product:" + i, bytes("v"));
        }
        servers.get(1).close();

        CacheClientException e = assertThrows(CacheClientException.class, cache::flushAll);

        assertTrue(e.getMessage().contains("node-2"), e.getMessage());
        assertEquals(0, servers.get(0).data.size());
        assertEquals(0, servers.get(2).data.size());
    }

    @Test
    void nodesReportTheirBreakerState() throws IOException {
        servers.get(0).close();
        String keyOnNode1 = keyOn("node-1");
        for (int i = 0; i < BREAKER_THRESHOLD; i++) {
            assertThrows(CacheClientException.class, () -> cache.get(keyOnNode1));
        }

        List<CacheNodeStatus> nodes = cache.nodes();

        assertEquals("open", nodes.get(0).breaker());
        assertEquals("closed", nodes.get(1).breaker());
    }

    @Test
    void unknownNodeCannotBeShutDown() {
        assertThrows(IllegalArgumentException.class, () -> cache.shutdownNode("node-9"));
    }

    @Test
    void theSameAddressTwiceIsRejected() {
        RemoteCacheSettings one = new RemoteCacheSettings("127.0.0.1", 7001, 10, 20, 4, 20,
                BREAKER_THRESHOLD, BREAKER_COOLDOWN_MS);

        assertThrows(IllegalArgumentException.class, () -> new ShardedCacheOperations(List.of(one, one), 150));
    }
}
