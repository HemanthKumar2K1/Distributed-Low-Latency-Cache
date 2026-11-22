package com.hemz.distributedcache.modules.loadtestservice.metrics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Hit and miss p99 come from their own histograms, per scrape interval. */
class ServerIntervalTest {

    /** A tiny /actuator/prometheus page with only the cache read histograms (cumulative counts). */
    private static ServerSnapshot snapshot(long atNanos, int hitsFast, int hitsSlow, int missesSlow) {
        String text = String.join("\n",
                "cache_aside_read_seconds_bucket{cache=\"products\",result=\"hit\",le=\"0.0005\"} " + hitsFast,
                "cache_aside_read_seconds_bucket{cache=\"products\",result=\"hit\",le=\"0.001\"} " + (hitsFast + hitsSlow),
                "cache_aside_read_seconds_bucket{cache=\"products\",result=\"hit\",le=\"+Inf\"} " + (hitsFast + hitsSlow),
                "cache_aside_read_seconds_bucket{cache=\"products\",result=\"miss\",le=\"0.001\"} 0",
                "cache_aside_read_seconds_bucket{cache=\"products\",result=\"miss\",le=\"0.02\"} " + missesSlow,
                "cache_aside_read_seconds_bucket{cache=\"products\",result=\"miss\",le=\"+Inf\"} " + missesSlow,
                "");
        return ServerSnapshot.from(PrometheusText.parse(text), "product-pool", atNanos);
    }

    @Test
    void hitAndMissP99ComeFromTheirOwnBuckets() {
        ServerSnapshot before = snapshot(0, 0, 0, 0);
        ServerSnapshot after = snapshot(1_000_000_000L, 1000, 0, 100);

        ServerInterval i = ServerInterval.between(before, after);

        assertTrue(i.hitP99Ms() <= 0.5, "every hit was under 0.5 ms, got " + i.hitP99Ms());
        assertTrue(i.missP99Ms() > 1 && i.missP99Ms() <= 20, "misses were 1-20 ms, got " + i.missP99Ms());
    }

    @Test
    void onlyTheNewRequestsInTheIntervalCount() {
        ServerSnapshot before = snapshot(0, 1000, 0, 0);
        ServerSnapshot after = snapshot(1_000_000_000L, 1000, 100, 0);   // 100 new hits, all slow

        ServerInterval i = ServerInterval.between(before, after);

        assertTrue(i.hitP99Ms() > 0.5, "the old fast hits must not count, got " + i.hitP99Ms());
        assertNull(i.missP99Ms(), "no new misses means no miss p99");
    }

    @Test
    void firstSnapshotHasNoInterval() {
        ServerInterval i = ServerInterval.between(null, snapshot(0, 10, 0, 1));

        assertNull(i.hitP99Ms());
        assertNull(i.missP99Ms());
    }

    @Test
    void missingMetricMeansNull() {
        ServerSnapshot empty = ServerSnapshot.from(PrometheusText.parse(""), "product-pool", 0);

        assertEquals(0, empty.hitBuckets().size());
        assertNull(ServerInterval.between(empty, empty).hitP99Ms());
    }
}
