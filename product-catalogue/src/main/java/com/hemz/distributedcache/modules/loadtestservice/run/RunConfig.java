package com.hemz.distributedcache.modules.loadtestservice.run;

import com.hemz.distributedcache.modules.loadtestservice.engine.KeyDistribution;

import java.util.List;

/** A validated, normalised run configuration. Stored with every run so results can be reproduced. */
public record RunConfig(
        String label,
        String target,
        List<Integer> rates,
        int durationSeconds,
        int warmupSeconds,
        int connections,
        KeyDistribution distribution,
        double zipfExponent,
        long maxId,
        long seed
) {
    /** Pause between two steps, so the target settles before the next rate. */
    public static final int COOLDOWN_SECONDS = 3;
}
