package com.hemz.distributedcache.modules.cacheclient.ring;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Consistent hashing. Each node is placed on a ring at many points
 * ("virtual nodes"); a key belongs to the first node point at or after the key's own hash,
 * wrapping around at the end.
 *
 * Why not hash(key) % nodeCount: adding a 4th node would move about 3 of every 4 keys.
 * On a ring only the keys that land on the new node's points move: about 1 in 4.
 * Why many points per node: with one point each, one node can own half the ring by chance.
 *
 * Immutable: build a new ring to add or remove a node.
 */
public final class HashRing<N> {

    private final TreeMap<Long, N> ring = new TreeMap<>();

    /** name(node) must be unique and stable: it decides where the node sits on the ring. */
    public HashRing(List<N> nodes, int virtualNodes, Function<N, String> name) {
        if (nodes.isEmpty()) throw new IllegalArgumentException("at least one node is needed");
        if (virtualNodes < 1) throw new IllegalArgumentException("virtualNodes must be at least 1");
        for (N node : nodes) {
            String id = Objects.requireNonNull(name.apply(node), "node name");
            for (int i = 0; i < virtualNodes; i++) {
                // A collision between two points is possible but very rare; the first one keeps it.
                ring.putIfAbsent(position(id + "#" + i), node);
            }
        }
    }

    /** The node that owns this key. */
    public N nodeFor(String key) {
        Map.Entry<Long, N> e = ring.ceilingEntry(position(key));
        return e != null ? e.getValue() : ring.firstEntry().getValue();
    }

    /** 0 .. 2^32-1, so the ring is ordered the same way the hash values are. */
    private static long position(String s) {
        return Integer.toUnsignedLong(Murmur3.hash32(s));
    }
}
