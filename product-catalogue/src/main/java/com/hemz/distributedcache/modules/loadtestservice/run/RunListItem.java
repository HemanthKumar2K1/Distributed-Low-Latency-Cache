package com.hemz.distributedcache.modules.loadtestservice.run;

import java.util.Objects;

/** The history-table view of a run: no samples. cache is "on" / "off", cacheBackend says which cache. */
public record RunListItem(
        long id,
        String label,
        RunStatus status,
        RunConfig config,
        String cache,
        String cacheBackend
) {
    public static RunListItem from(Run run) {
        return new RunListItem(run.getId(), run.getLabel(), run.getStatus(), run.getConfig(),
                Objects.toString(run.getEnvironment().get("cache"), null),
                Objects.toString(run.getEnvironment().get("cacheBackend"), null));
    }
}
