package com.hemz.distributedcache.modules.loadtestservice.metrics;

import java.util.List;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * One reading of Product Catalogue's metrics. Counters here are cumulative;
 * ServerInterval turns two snapshots into per-second numbers.
 * Fields are null when the metric was not found.
 */
public record ServerSnapshot(
        long takenAtNanos,
        Double poolActive,
        Double poolPending,
        Double poolMax,
        Double processCpu,
        Double systemCpu,
        Double heapUsedBytes,
        Double cacheHits,
        Double cacheMisses,
        /** How long cached reads took, hits and misses apart (cumulative buckets by le). */
        TreeMap<Double, Double> hitBuckets,
        TreeMap<Double, Double> missBuckets
) {

    public static ServerSnapshot from(List<PrometheusText.Metric> metrics, String poolName, long takenAtNanos) {
        return new ServerSnapshot(
                takenAtNanos,
                pool(metrics, "hikaricp_connections_active", poolName),
                pool(metrics, "hikaricp_connections_pending", poolName),
                pool(metrics, "hikaricp_connections_max", poolName),
                sum(metrics, "process_cpu_usage", null, null),
                sum(metrics, "system_cpu_usage", null, null),
                sum(metrics, "jvm_memory_used_bytes", "area", "heap"),
                sum(metrics, "cache_gets_total", "result", "hit"),
                sum(metrics, "cache_gets_total", "result", "miss"),
                buckets(metrics, "cache_aside_read_seconds_bucket", m -> "hit".equals(m.label("result"))),
                buckets(metrics, "cache_aside_read_seconds_bucket", m -> "miss".equals(m.label("result"))));
    }

    private static Double pool(List<PrometheusText.Metric> ms, String name, String poolName) {
        return sum(ms, name, poolName == null || poolName.isBlank() ? null : "pool", poolName);
    }

    private static Double sum(List<PrometheusText.Metric> ms, String name, String labelKey, String labelValue) {
        double total = 0;
        boolean found = false;
        for (PrometheusText.Metric m : ms) {
            if (m.name().equals(name) && (labelKey == null || labelValue.equals(m.label(labelKey)))
                    && !Double.isNaN(m.value())) {
                total += m.value();
                found = true;
            }
        }
        return found ? total : null;
    }

    /**
     * Cumulative bucket counts of one histogram by upper bound (le), for the lines that match.
     * Lines with the same le are added up. Empty when the metric is missing.
     */
    private static TreeMap<Double, Double> buckets(List<PrometheusText.Metric> ms, String name,
                                                   Predicate<PrometheusText.Metric> matches) {
        TreeMap<Double, Double> out = new TreeMap<>();
        for (PrometheusText.Metric m : ms) {
            if (m.name().equals(name) && matches.test(m)) {
                String le = m.label("le");
                if (!le.isEmpty()) {
                    out.merge(PrometheusText.parseValue(le), m.value(), Double::sum);
                }
            }
        }
        return out;
    }
}
