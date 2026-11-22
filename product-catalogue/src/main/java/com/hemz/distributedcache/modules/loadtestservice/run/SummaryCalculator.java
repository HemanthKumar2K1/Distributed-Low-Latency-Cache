package com.hemz.distributedcache.modules.loadtestservice.run;

import com.hemz.distributedcache.modules.loadtestservice.engine.LatencySummary;
import com.hemz.distributedcache.modules.loadtestservice.engine.StepOutcome;

import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.DoubleStream;

/**
 * Turns a step's raw numbers into a summary and decides whether the step was saturated.
 * The thresholds are deliberately simple so every verdict can be explained.
 */
public final class SummaryCalculator {

    /** Delivering less than this share of the target counts as saturated. */
    static final double MIN_DELIVERED = 0.95;
    /** Requests queueing for a DB connection in at least this share of seconds counts as saturated. */
    static final double MAX_PENDING_SECONDS_PCT = 20.0;
    /** More than this share of requests failing counts as saturated. */
    static final double MAX_ERROR_PCT = 0.5;

    private SummaryCalculator() {
    }

    public static StepSummary summarize(int targetRps, StepOutcome o, List<Sample> measured) {
        ServerSummary server = server(measured);
        double deliveredPct = targetRps > 0 ? LatencySummary.round(o.achievedRps() * 100.0 / targetRps) : 0;
        double errorPct = o.completed() > 0 ? LatencySummary.round(o.errors().total() * 100.0 / o.completed()) : 0;

        boolean underDelivered = !o.stopped() && deliveredPct < MIN_DELIVERED * 100;
        boolean poolQueueing = server.pendingSecondsPct() != null && server.pendingSecondsPct() >= MAX_PENDING_SECONDS_PCT;
        boolean tooManyErrors = errorPct > MAX_ERROR_PCT;
        boolean saturated = underDelivered || poolQueueing || tooManyErrors;

        return new StepSummary(targetRps, o.achievedRps(), deliveredPct, o.measuredSeconds(),
                errorPct, o.latency(), server, o.stopped(), saturated);
    }

    static ServerSummary server(List<Sample> samples) {
        List<Sample> withPool = samples.stream().filter(s -> s.poolActive() != null).toList();
        Double pendingPct = withPool.isEmpty() ? null
                : LatencySummary.round(withPool.stream().filter(s -> s.poolPending() != null && s.poolPending() > 0).count() * 100.0 / withPool.size());
        return new ServerSummary(
                max(samples, Sample::poolMax),
                max(samples, Sample::poolActive),
                max(samples, Sample::poolPending),
                pendingPct,
                avg(samples, Sample::cpuProcess),
                max(samples, Sample::heapMb),
                avg(samples, Sample::hitRatePct),
                avg(samples, Sample::hitP99Ms),
                avg(samples, Sample::missP99Ms));
    }

    /** Average of one field over the samples that have it; null when none do. */
    private static Double avg(List<Sample> xs, Function<Sample, Double> f) {
        return roundOrNull(present(xs, f).average());
    }

    /** Largest value of one field; null when no sample has it. */
    private static Double max(List<Sample> xs, Function<Sample, Double> f) {
        return roundOrNull(present(xs, f).max());
    }

    /** The values of one field that are really there: nulls and NaN are left out. */
    private static DoubleStream present(List<Sample> xs, Function<Sample, Double> f) {
        return xs.stream().map(f).filter(v -> v != null && !v.isNaN()).mapToDouble(Double::doubleValue);
    }

    private static Double roundOrNull(OptionalDouble v) {
        return v.isPresent() ? LatencySummary.round(v.getAsDouble()) : null;
    }
}
