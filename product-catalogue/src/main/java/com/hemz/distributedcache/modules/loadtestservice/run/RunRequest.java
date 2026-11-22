package com.hemz.distributedcache.modules.loadtestservice.run;

import com.hemz.distributedcache.modules.loadtestservice.engine.KeyDistribution;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * What the dashboard sends to start a run.
 * One rate = a single step. Several rates = a sweep, run in order, to find the knee.
 * Hard limits (max rate, max duration) are also checked in RunService against configuration.
 */
public record RunRequest(
        @Size(max = 80) String label,
        @NotNull @Size(min = 1, max = 12) List<@NotNull @Min(1) @Max(100_000) Integer> rates,
        @Min(1) @Max(3600) int durationSeconds,
        @Min(0) @Max(600) int warmupSeconds,
        @Min(1) @Max(20_000) int connections,
        @NotNull KeyDistribution distribution,
        @DecimalMin("0.5") @DecimalMax("2.0") double zipfExponent,
        @Min(1) @Max(2_000_000) long maxId,
        Long seed
) {
    /** Fixed default seed: same hot keys every run, so runs are comparable. */
    public static final long DEFAULT_SEED = 42L;

    public RunConfig toConfig(String target) {
        return new RunConfig(
                label == null || label.isBlank() ? null : label.strip(),
                target,
                List.copyOf(rates),
                durationSeconds,
                warmupSeconds,
                connections,
                distribution,
                zipfExponent,
                maxId,
                seed == null ? DEFAULT_SEED : seed);
    }
}
