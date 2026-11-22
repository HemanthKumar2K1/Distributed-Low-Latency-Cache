package com.hemz.distributedcache.modules.productservice.cache;

import com.hemz.distributedcache.modules.cacheclient.aside.CacheMetrics;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.TimeUnit;

/**
 * Shows up in /actuator/prometheus as:
 *   cache_gets_total{cache="products",result="hit"|"miss"}
 *   cache_aside_read_seconds_bucket{cache="products",result="hit"|"miss",le=...}
 *   cache_errors_total
 * The load tester reads cache_gets_total for the hit rate, and the two histograms for
 * "slowest 1% found in cache" vs "slowest 1% asked the database".
 *
 * Hits and misses are counted here, in the app, because only the app knows a miss
 * led to a database read.
 */
public class MicrometerCacheMetrics implements CacheMetrics {

    public static final String CACHE_NAME = "products";

    private final Counter hits;
    private final Counter misses;
    private final Counter errors;
    private final Timer hitTime;
    private final Timer missTime;

    public MicrometerCacheMetrics(MeterRegistry registry) {
        hits = getsCounter(registry, "hit");
        misses = getsCounter(registry, "miss");
        errors = Counter.builder("cache.errors").tag("cache", CACHE_NAME).register(registry);
        hitTime = readTimer(registry, "hit");
        missTime = readTimer(registry, "miss");
    }

    /** cache_gets_total{cache="products",result=...}: one counter for hits, one for misses. */
    private static Counter getsCounter(MeterRegistry registry, String result) {
        return Counter.builder("cache.gets").tag("cache", CACHE_NAME).tag("result", result).register(registry);
    }

    /** Buckets from 10 µs: a hit takes well under 1 ms, and the default 1 ms floor would put every hit in one bucket. */
    private static Timer readTimer(MeterRegistry registry, String result) {
        return Timer.builder("cache.aside.read")
                .description("One product read through the cache, start to answer")
                .tag("cache", CACHE_NAME)
                .tag("result", result)
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.of(10, ChronoUnit.MICROS))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(registry);
    }

    @Override
    public void hit(long nanos) {
        hits.increment();
        hitTime.record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void miss(long nanos) {
        misses.increment();
        missTime.record(nanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void error() {
        errors.increment();
    }

    public long hits() {
        return (long) hits.count();
    }

    public long misses() {
        return (long) misses.count();
    }

    public long errors() {
        return (long) errors.count();
    }
}
