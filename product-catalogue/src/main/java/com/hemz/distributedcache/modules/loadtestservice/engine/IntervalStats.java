package com.hemz.distributedcache.modules.loadtestservice.engine;

/**
 * Client-side numbers for one second of a step. "rps" counts successful (2xx) responses only.
 * Latency fields are null when no request finished in that second.
 */
public record IntervalStats(
        /** "warmup", "measure", or "drain" (queued requests finishing after the window). */
        String phase,
        double elapsedSeconds,
        long errors,
        Double rps,
        Double p50Ms,
        Double p99Ms
) {
}
