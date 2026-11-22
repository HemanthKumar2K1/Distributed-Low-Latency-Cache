package com.hemz.distributedcache.modules.cacheclient.ring;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashRingTest {

    private static final int KEYS = 100_000;
    private static final List<String> THREE = List.of("127.0.0.1:7001", "127.0.0.1:7002", "127.0.0.1:7003");

    private static HashRing<String> ring(List<String> nodes, int vnodes) {
        return new HashRing<>(nodes, vnodes, Function.identity());
    }

    @Test
    void sameKeyAlwaysGoesToTheSameNode() {
        HashRing<String> a = ring(THREE, 150);
        HashRing<String> b = ring(List.of(THREE.get(2), THREE.get(0), THREE.get(1)), 150);   // order does not matter

        for (int i = 0; i < 1000; i++) {
            assertEquals(a.nodeFor("product:" + i), b.nodeFor("product:" + i));
        }
    }

    /** The checkpoint from the docs: hash 100,000 keys and count per node. */
    @Test
    void keysSpreadEvenlyWith150VirtualNodes() {
        Map<String, Integer> count = countPerNode(ring(THREE, 150));

        for (String node : THREE) {
            double share = count.getOrDefault(node, 0) * 100.0 / KEYS;
            assertTrue(share > 28 && share < 39, node + " got " + share + "% of keys, expected about 33%");
        }
    }

    @Test
    void addingAFourthNodeMovesAboutAQuarterOfTheKeys() {
        HashRing<String> before = ring(THREE, 150);
        HashRing<String> after = ring(List.of(THREE.get(0), THREE.get(1), THREE.get(2), "127.0.0.1:7004"), 150);

        int moved = 0;
        for (int i = 0; i < KEYS; i++) {
            String key = "product:" + i;
            String from = before.nodeFor(key);
            String to = after.nodeFor(key);
            if (!from.equals(to)) {
                moved++;
                assertEquals("127.0.0.1:7004", to, "a key may only move to the new node");
            }
        }
        double movedPct = moved * 100.0 / KEYS;
        assertTrue(movedPct > 18 && movedPct < 32, "moved " + movedPct + "%, expected about 25%");
    }

    @Test
    void oneNodeOwnsEverything() {
        HashRing<String> single = ring(List.of("127.0.0.1:7001"), 150);

        assertEquals("127.0.0.1:7001", single.nodeFor("anything"));
    }

    @Test
    void emptyRingIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ring(List.of(), 150));
    }

    private static Map<String, Integer> countPerNode(HashRing<String> ring) {
        Map<String, Integer> count = new HashMap<>();
        for (int i = 0; i < KEYS; i++) {
            count.merge(ring.nodeFor("product:" + i), 1, Integer::sum);
        }
        return count;
    }
}
