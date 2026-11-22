package com.hemz.distributedcache.modules.cacheclient.aside;

import com.hemz.distributedcache.modules.cacheclient.CacheOperations;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Cache-aside, written once.
 *   hit  → decode and return
 *   miss → run the loader (the database), save a copy, return it
 *
 * Rule: a broken cache must never break a request. Cache failures fall back to
 * the loader and are counted. Only a loader failure (the DB) reaches the caller.
 */
public final class CacheAsideTemplate {

    private final CacheOperations cache;
    private final CacheMetrics metrics;

    public CacheAsideTemplate(CacheOperations cache, CacheMetrics metrics) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** A null from the loader means "does not exist": returned, never cached. */
    public <T> T get(String key, ValueCodec<T> codec, Supplier<T> loader) {
        long start = System.nanoTime();
        byte[] cached = readQuietly(key);
        if (cached != null) {
            try {
                T value = codec.decode(cached);
                metrics.hit(System.nanoTime() - start);
                return value;
            } catch (RuntimeException corrupt) {
                metrics.error();          // unreadable entry: drop it and load fresh
                deleteQuietly(key);
            }
        }

        try {
            T loaded = loader.get();
            if (loaded != null) {
                writeQuietly(key, codec, loaded);
            }
            return loaded;
        } finally {
            metrics.miss(System.nanoTime() - start);    // counted even when the loader fails
        }
    }

    private byte[] readQuietly(String key) {
        try {
            return cache.get(key);
        } catch (RuntimeException e) {
            metrics.error();
            return null;                  // treat as a miss
        }
    }

    private <T> void writeQuietly(String key, ValueCodec<T> codec, T value) {
        try {
            cache.set(key, codec.encode(value));
        } catch (RuntimeException e) {
            metrics.error();              // caller still gets the value
        }
    }

    private void deleteQuietly(String key) {
        try {
            cache.delete(key);
        } catch (RuntimeException e) {
            metrics.error();
        }
    }
}

