package com.hemz.distributedcache.modules.loadtestservice.metrics;

import com.hemz.distributedcache.modules.loadtestservice.engine.LatencySummary;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Server-side numbers for the time between two snapshots.
 * Gauges (pool, CPU, heap) come from the newer snapshot. Hit rate and hit/miss latency
 * come from the difference between the two, so they describe only that interval.
 */
public record ServerInterval(
        Double poolActive,
        Double poolPending,
        Double poolMax,
        Double cpuProcess,
        Double cpuSystem,
        Double heapMb,
        Double hitRatePct,
        /** Slowest 1% of reads answered from the cache / that asked the database, inside the server. */
        Double hitP99Ms,
        Double missP99Ms
) {

    /** Every field null: used when no server numbers are available yet. */
    public static final ServerInterval NONE = new ServerInterval(
            null, null, null, null, null, null, null, null, null);

    private static final double P99 = 0.99;
    private static final double MS_PER_SECOND = 1000;
    private static final double BYTES_PER_MB = 1024.0 * 1024.0;

    public static ServerInterval between(ServerSnapshot prev, ServerSnapshot cur) {
        Double dHits = change(prev, cur, ServerSnapshot::cacheHits);
        Double dMisses = change(prev, cur, ServerSnapshot::cacheMisses);
        Double hitRate = dHits == null || dMisses == null || dHits + dMisses == 0
                ? null : LatencySummary.round(dHits * 100.0 / (dHits + dMisses));

        return new ServerInterval(
                cur.poolActive(),
                cur.poolPending(),
                cur.poolMax(),
                roundOrNull(cur.processCpu()),
                roundOrNull(cur.systemCpu()),
                cur.heapUsedBytes() == null ? null : LatencySummary.round(cur.heapUsedBytes() / BYTES_PER_MB),
                hitRate,
                p99Ms(prev, cur, ServerSnapshot::hitBuckets),
                p99Ms(prev, cur, ServerSnapshot::missBuckets));
    }

    /** How much a counter grew between the two snapshots. Null when there is no earlier snapshot (see delta). */
    private static Double change(ServerSnapshot prev, ServerSnapshot cur, Function<ServerSnapshot, Double> counter) {
        return prev == null ? null : delta(counter.apply(prev), counter.apply(cur));
    }

    /** Slowest 1% in ms, for the requests counted between the two snapshots. Null when there is no earlier one. */
    private static Double p99Ms(ServerSnapshot prev, ServerSnapshot cur,
                                Function<ServerSnapshot, TreeMap<Double, Double>> buckets) {
        return prev == null ? null : quantileMs(buckets.apply(prev), buckets.apply(cur), P99);
    }

    private static Double roundOrNull(Double v) {
        return v == null ? null : LatencySummary.round(v);
    }

    /** Null if either side is missing, or the counter went backwards (the target restarted). */
    static Double delta(Double before, Double after) {
        if (before == null || after == null || after < before) {
            return null;
        }
        return after - before;
    }

    /**
     * The same maths as PromQL's histogram_quantile: find the bucket holding the
     * q-th request, then interpolate linearly inside it. Returns milliseconds.
     */
    static Double quantileMs(TreeMap<Double, Double> prev, TreeMap<Double, Double> cur, double q) {
        if (cur == null || cur.isEmpty()) {
            return null;
        }
        double total = 0;
        TreeMap<Double, Double> deltas = new TreeMap<>();
        for (Map.Entry<Double, Double> e : cur.entrySet()) {
            double before = prev == null ? 0 : prev.getOrDefault(e.getKey(), 0.0);
            double d = Math.max(0, e.getValue() - before);
            deltas.put(e.getKey(), d);
            total = d;                          // the last (largest le, i.e. +Inf) bucket holds the total
        }
        if (total <= 0) {
            return null;
        }
        double rank = q * total;
        double prevLe = 0;
        double prevCount = 0;
        for (Map.Entry<Double, Double> e : deltas.entrySet()) {
            double le = e.getKey();
            double count = e.getValue();
            if (count >= rank) {
                if (Double.isInfinite(le)) {
                    return LatencySummary.round(prevLe * MS_PER_SECOND);
                }
                double width = count - prevCount;
                double fraction = width <= 0 ? 0 : (rank - prevCount) / width;
                return LatencySummary.round((prevLe + (le - prevLe) * fraction) * MS_PER_SECOND);
            }
            prevLe = le;
            prevCount = count;
        }
        return LatencySummary.round(prevLe * MS_PER_SECOND);
    }
}
