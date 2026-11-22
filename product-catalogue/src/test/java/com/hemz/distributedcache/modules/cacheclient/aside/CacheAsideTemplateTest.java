package com.hemz.distributedcache.modules.cacheclient.aside;

import com.hemz.distributedcache.modules.cacheclient.CacheOperations;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Plain Java: a map stands in for the cache, so no server or Spring is needed. */
class CacheAsideTemplateTest {

    private static final ValueCodec<String> STRING = new ValueCodec<>() {
        @Override public byte[] encode(String v) { return v.getBytes(StandardCharsets.UTF_8); }
        @Override public String decode(byte[] b) { return new String(b, StandardCharsets.UTF_8); }
    };

    /** A loader whose database is down. */
    private static final Supplier<String> FAILING_LOADER = () -> {
        throw new IllegalStateException("db down");
    };

    private final FakeCache cache = new FakeCache();
    private final CountingMetrics metrics = new CountingMetrics();
    private final CacheAsideTemplate template = new CacheAsideTemplate(cache, metrics);
    private final AtomicInteger loads = new AtomicInteger();

    private Supplier<String> loader(String value) {
        return () -> {
            loads.incrementAndGet();
            return value;
        };
    }

    @Test
    void missLoadsOnceThenHits() {
        assertEquals("v", template.get("k", STRING, loader("v")));
        assertEquals("v", template.get("k", STRING, loader("v")));

        assertEquals(1, loads.get());
        assertEquals(1, metrics.misses);
        assertEquals(1, metrics.hits);
        assertEquals(0, metrics.errors);
    }

    @Test
    void nullFromLoaderIsNotCached() {
        assertNull(template.get("k", STRING, loader(null)));
        assertNull(template.get("k", STRING, loader(null)));

        assertEquals(2, loads.get());
        assertFalse(cache.map.containsKey("k"));
    }

    @Test
    void brokenReadFallsBackToLoader() {
        cache.failReads = true;

        assertEquals("v", template.get("k", STRING, loader("v")));

        assertEquals(1, loads.get());
        assertEquals(1, metrics.errors);
    }

    @Test
    void brokenWriteStillReturnsTheValue() {
        cache.failWrites = true;

        assertEquals("v", template.get("k", STRING, loader("v")));

        assertEquals(1, metrics.errors);
        assertFalse(cache.map.containsKey("k"));
    }

    @Test
    void unreadableEntryIsDroppedAndReloaded() {
        cache.map.put("k", "corrupt".getBytes(StandardCharsets.UTF_8));
        ValueCodec<String> strict = new ValueCodec<>() {
            @Override public byte[] encode(String v) { return STRING.encode(v); }
            @Override public String decode(byte[] b) {
                String s = STRING.decode(b);
                if (s.equals("corrupt")) throw new IllegalStateException("bad bytes");
                return s;
            }
        };

        assertEquals("fresh", template.get("k", strict, loader("fresh")));

        assertEquals(1, metrics.errors);
        assertEquals(1, metrics.misses);
        assertEquals("fresh", STRING.decode(cache.map.get("k")));
    }

    @Test
    void loaderFailureReachesTheCaller() {
        assertThrows(IllegalStateException.class, () -> template.get("k", STRING, FAILING_LOADER));
    }

    @Test
    void missTimeIncludesTheLoaderAndHitTimeDoesNot() {
        Supplier<String> slowLoader = () -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "v";
        };

        template.get("k", STRING, slowLoader);
        template.get("k", STRING, slowLoader);

        assertTrue(metrics.missNanos >= 30_000_000L, "miss should include the 30 ms load: " + metrics.missNanos);
        assertTrue(metrics.hitNanos >= 0 && metrics.hitNanos < 30_000_000L, "hit should skip the loader: " + metrics.hitNanos);
    }

    @Test
    void missIsCountedEvenWhenTheLoaderFails() {
        assertThrows(IllegalStateException.class, () -> template.get("k", STRING, FAILING_LOADER));

        assertEquals(1, metrics.misses);
    }

    private static final class FakeCache implements CacheOperations {
        final Map<String, byte[]> map = new HashMap<>();
        boolean failReads;
        boolean failWrites;

        @Override
        public byte[] get(String key) {
            if (failReads) throw new IllegalStateException("cache read failed");
            return map.get(key);
        }

        @Override
        public void set(String key, byte[] value) {
            if (failWrites) throw new IllegalStateException("cache write failed");
            map.put(key, value);
        }

        @Override
        public void delete(String key) {
            map.remove(key);
        }
    }

    private static final class CountingMetrics implements CacheMetrics {
        int hits;
        int misses;
        int errors;
        long hitNanos = -1;
        long missNanos = -1;

        @Override public void hit(long nanos) { hits++; hitNanos = nanos; }
        @Override public void miss(long nanos) { misses++; missNanos = nanos; }
        @Override public void error() { errors++; }
    }
}
