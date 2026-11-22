package com.hemz.distributedcache.modules.loadtestservice.engine;

import org.HdrHistogram.Histogram;

/** Latency percentiles for a whole step, in milliseconds. */
public record LatencySummary(
        double p50Ms,
        double p99Ms
) {
    public static final LatencySummary EMPTY = new LatencySummary(0, 0);

    /** The histogram must hold values in microseconds. */
    public static LatencySummary fromMicros(Histogram h) {
        if (h.getTotalCount() == 0) {
            return EMPTY;
        }
        return new LatencySummary(ms(h, 50), ms(h, 99));
    }

    static double ms(Histogram h, double percentile) {
        return round(h.getValueAtPercentile(percentile) / 1000.0);
    }

    public static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
