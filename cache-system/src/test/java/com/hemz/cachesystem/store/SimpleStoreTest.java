package com.hemz.cachesystem.store;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleStoreTest {

    private static final int THREADS = 16;
    private static final int WRITES_PER_THREAD = 5_000;
    private static final int SHARED_KEYS = 100;
    private static final int VALUE_BYTES = 10;

    private final SimpleStore store = new SimpleStore();

    /**
     * Many threads write the same 100 keys at once. Without the lock, the HashMap or the byte count
     * would end up wrong (lost updates). With it, both match exactly what is stored.
     */
    @Test
    void concurrentWritesKeepTheCountsExact() throws Exception {
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            List<Future<?>> done = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                done.add(pool.submit(() -> {
                    for (int i = 0; i < WRITES_PER_THREAD; i++) {
                        String key = "k" + (i % SHARED_KEYS);
                        store.put(key, new byte[VALUE_BYTES]);
                        store.get(key);
                        if (i % 7 == 0) {
                            store.remove(key);
                            store.put(key, new byte[VALUE_BYTES]);
                        }
                    }
                }));
            }
            for (Future<?> f : done) {
                f.get();                          // rethrows anything a thread threw
            }
        }

        assertEquals(SHARED_KEYS, store.size());
        assertEquals((long) SHARED_KEYS * VALUE_BYTES, store.valueBytes());
    }

    @Test
    void putThenGetReturnsTheSameBytes() {
        store.put("a", new byte[]{1, 2, 3});

        assertArrayEquals(new byte[]{1, 2, 3}, store.get("a"));
        assertEquals(1, store.size());
        assertEquals(3, store.valueBytes());
    }

    @Test
    void missingKeyIsNull() {
        assertNull(store.get("nothere"));
    }

    @Test
    void valuesWithZeroBytesComeBackUnchanged() {
        byte[] value = {0, 'a', 0, 0, 'b'};
        store.put("bin", value);

        assertArrayEquals(value, store.get("bin"));
    }

    @Test
    void overwriteCountsOnlyTheNewValue() {
        store.put("a", new byte[10]);
        store.put("a", new byte[4]);

        assertEquals(1, store.size());
        assertEquals(4, store.valueBytes());
    }

    @Test
    void removeFreesTheBytes() {
        store.put("a", new byte[5]);

        assertTrue(store.remove("a"));
        assertFalse(store.remove("a"));
        assertNull(store.get("a"));
        assertEquals(0, store.valueBytes());
    }

    @Test
    void clearEmptiesEverything() {
        store.put("a", new byte[5]);
        store.put("b", new byte[7]);

        store.clear();

        assertEquals(0, store.size());
        assertEquals(0, store.valueBytes());
    }

    @Test
    void nullKeyOrValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> store.put(null, new byte[1]));
        assertThrows(IllegalArgumentException.class, () -> store.put("a", null));
    }
}
