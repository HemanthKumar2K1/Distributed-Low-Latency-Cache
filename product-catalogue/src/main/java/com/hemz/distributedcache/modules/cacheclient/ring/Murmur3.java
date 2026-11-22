package com.hemz.distributedcache.modules.cacheclient.ring;

import java.nio.charset.StandardCharsets;

/**
 * MurmurHash3, 32-bit (x86_32), seed 0. Fast, and spreads similar keys ("product:41", "product:42")
 * far apart on the ring. String.hashCode() does not: neighbouring keys get neighbouring hashes.
 */
public final class Murmur3 {

    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;

    private Murmur3() {
    }

    public static int hash32(String key) {
        return hash32(key.getBytes(StandardCharsets.UTF_8), 0);
    }

    public static int hash32(byte[] data, int seed) {
        int h = seed;
        int blocks = data.length / 4;
        for (int i = 0; i < blocks; i++) {
            int k = (data[i * 4] & 0xff)
                    | (data[i * 4 + 1] & 0xff) << 8
                    | (data[i * 4 + 2] & 0xff) << 16
                    | (data[i * 4 + 3] & 0xff) << 24;
            h ^= mixK(k);
            h = Integer.rotateLeft(h, 13);
            h = h * 5 + 0xe6546b64;
        }

        int tail = blocks * 4;
        int k = 0;
        switch (data.length & 3) {
            case 3:
                k ^= (data[tail + 2] & 0xff) << 16;
                // falls through on purpose
            case 2:
                k ^= (data[tail + 1] & 0xff) << 8;
                // falls through on purpose
            case 1:
                k ^= data[tail] & 0xff;
                h ^= mixK(k);
                // falls through on purpose
            default:
                break;
        }

        h ^= data.length;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        return h;
    }

    private static int mixK(int k) {
        k *= C1;
        k = Integer.rotateLeft(k, 15);
        return k * C2;
    }
}
