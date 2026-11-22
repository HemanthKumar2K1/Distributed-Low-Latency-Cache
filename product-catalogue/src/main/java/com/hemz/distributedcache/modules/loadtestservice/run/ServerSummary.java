package com.hemz.distributedcache.modules.loadtestservice.run;

/** Server-side numbers for the measurement window of one step. Null when Product Catalogue metrics were unavailable. */
public record ServerSummary(
        Double poolMax,
        Double poolActiveMax,
        Double poolPendingMax,
        /** Share of seconds in which requests waited for a DB connection. */
        Double pendingSecondsPct,
        Double cpuProcessAvg,
        Double heapMbMax,
        Double hitRatePctAvg,
        /** Per-second p99 of cache hits / database reads, inside the server. */
        Double hitP99MsAvg,
        Double missP99MsAvg
) {
}
