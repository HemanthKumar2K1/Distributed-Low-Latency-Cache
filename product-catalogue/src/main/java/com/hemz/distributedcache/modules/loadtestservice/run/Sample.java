package com.hemz.distributedcache.modules.loadtestservice.run;

import com.hemz.distributedcache.modules.loadtestservice.engine.IntervalStats;
import com.hemz.distributedcache.modules.loadtestservice.metrics.ServerInterval;

/**
 * One second of one step: what the load generator saw (client side) next to what
 * Product Catalogue reported about itself (server side). Null means "not available".
 */
public record Sample(
        int step,
        String phase,
        double t,
        // client side: measured from each request's intended send time
        Double rps,
        long errors,
        Double p50Ms,
        Double p99Ms,
        // server side: from Product Catalogue /actuator/prometheus
        Double poolActive,
        Double poolPending,
        Double poolMax,
        Double cpuProcess,
        Double cpuSystem,
        Double heapMb,
        Double hitRatePct,
        // Inside the server, cache hits and database reads timed apart
        Double hitP99Ms,
        Double missP99Ms
) {
    /** s is null when the server's numbers are not available yet: then every server field is null. */
    public static Sample of(int step, IntervalStats c, ServerInterval s) {
        ServerInterval server = s == null ? ServerInterval.NONE : s;
        return new Sample(
                step, c.phase(), c.elapsedSeconds(),
                c.rps(), c.errors(), c.p50Ms(), c.p99Ms(),
                server.poolActive(),
                server.poolPending(),
                server.poolMax(),
                server.cpuProcess(),
                server.cpuSystem(),
                server.heapMb(),
                server.hitRatePct(),
                server.hitP99Ms(),
                server.missP99Ms());
    }
}
