package com.hemz.distributedcache.modules.loadtestservice.engine;

import java.time.Duration;

/** Everything one load step needs. One run is one or more steps (a "sweep"). */
public record StepSpec(
        String targetBaseUrl,
        int rate,
        int durationSeconds,
        int warmupSeconds,
        int connections,
        KeyDistribution distribution,
        double zipfExponent,
        long maxId,
        long seed,
        Duration requestTimeout
) {
    public StepSpec {
        if (targetBaseUrl == null || targetBaseUrl.isBlank()) throw new IllegalArgumentException("target is required");
        if (rate < 1) throw new IllegalArgumentException("rate must be at least 1");
        if (durationSeconds < 1) throw new IllegalArgumentException("duration must be at least 1 second");
        if (warmupSeconds < 0) throw new IllegalArgumentException("warmup cannot be negative");
        if (connections < 1) throw new IllegalArgumentException("connections must be at least 1");
        if (maxId < 1) throw new IllegalArgumentException("maxId must be at least 1");
        targetBaseUrl = targetBaseUrl.endsWith("/") ? targetBaseUrl.substring(0, targetBaseUrl.length() - 1) : targetBaseUrl;
    }
}
