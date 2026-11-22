package com.hemz.distributedcache.modules.cacheclient.ring;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Murmur3Test {

    @Test
    void murmur3MatchesKnownValues() {
        assertEquals(0, Murmur3.hash32(""));
        assertEquals(0x248bfa47, Murmur3.hash32("hello"));
        assertEquals(0x2e4ff723, Murmur3.hash32("The quick brown fox jumps over the lazy dog"));
    }
}
