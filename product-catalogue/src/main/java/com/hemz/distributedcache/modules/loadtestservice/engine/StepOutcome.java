package com.hemz.distributedcache.modules.loadtestservice.engine;

/**
 * The result of one step, measured on the client side.
 *
 * Only requests scheduled after the warm-up count. completed and errors cover those requests.
 * achievedRps counts SUCCESSFUL (2xx) responses that completed inside the measurement window.
 * Counting by send time instead would hide saturation: queued requests finish
 * late, during the drain, and would make throughput look close to the target.
 */
public record StepOutcome(
        long completed,
        double achievedRps,
        double measuredSeconds,
        LatencySummary latency,
        ErrorCounts errors,
        boolean stopped
) {
}
