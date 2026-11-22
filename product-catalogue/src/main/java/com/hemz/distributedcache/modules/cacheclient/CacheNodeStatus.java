package com.hemz.distributedcache.modules.cacheclient;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * One cache node as seen right now. keys and valueBytes are null when the node did not answer.
 * breaker: "closed" (normal), "open" (being skipped), "half_open" (one trial call allowed).
 * usedMemory and server: so Cache System and Redis can be compared. valueBytes is null for Redis.
 */
public record CacheNodeStatus(String name, String address, boolean up, Long keys, Long valueBytes, String breaker,
                              Long usedMemory, String server) {

    public static CacheNodeStatus up(String name, String address, CacheServerInfo info, String breaker) {
        return new CacheNodeStatus(name, address, true, info.keys(), info.valueBytes(), breaker,
                info.usedMemory(), info.server());
    }

    public static CacheNodeStatus down(String name, String address, String breaker) {
        return new CacheNodeStatus(name, address, false, null, null, breaker, null, null);
    }

    /** Keys added up over the given nodes. Pass only nodes that answered: a down node has no keys. */
    public static long totalKeys(List<CacheNodeStatus> nodes) {
        return nodes.stream().mapToLong(CacheNodeStatus::keys).sum();
    }

    /** Sum of one field over the nodes, skipping nulls. Empty when every node left the field out. */
    public static Optional<Long> sum(List<CacheNodeStatus> nodes, Function<CacheNodeStatus, Long> field) {
        List<Long> values = nodes.stream().map(field).filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(values.stream().mapToLong(Long::longValue).sum());
    }
}
