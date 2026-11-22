package com.hemz.distributedcache.modules.loadtestservice.run;

import com.hemz.distributedcache.modules.loadtestservice.engine.LatencySummary;

/** Results for one step. "saturated" means this rate was beyond what the service could sustain. */
public record StepSummary(
        int targetRps,
        double achievedRps,
        double deliveredPct,
        double measuredSeconds,
        double errorPct,
        LatencySummary latency,
        ServerSummary server,
        boolean stopped,
        boolean saturated
) {
}
