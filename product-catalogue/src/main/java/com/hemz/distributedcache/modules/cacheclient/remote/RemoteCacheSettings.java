package com.hemz.distributedcache.modules.cacheclient.remote;

/**
 * Where the cache server is and how long to wait for it.
 * Short timeouts on purpose: a cache that is slow is worse than no cache,
 * because the request still has to read the database afterwards.
 */
public record RemoteCacheSettings(
        String host,
        int port,
        /** Time allowed to open a new connection. */
        int connectTimeoutMs,
        /** Time allowed for one reply. */
        int readTimeoutMs,
        /** Most connections open at once. Opened only when needed. */
        int poolSize,
        /** Time a request waits for a free connection when all are in use. */
        int borrowTimeoutMs,
        /** Circuit breaker: failures in a row before the node is skipped. */
        int breakerThreshold,
        /** Circuit breaker: how long a node is skipped before one trial call. */
        int breakerCooldownMs
) {
    public RemoteCacheSettings {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("host is required");
        if (port < 1 || port > 65_535) throw new IllegalArgumentException("port must be 1-65535");
        if (connectTimeoutMs < 1 || readTimeoutMs < 1 || poolSize < 1 || borrowTimeoutMs < 0) {
            throw new IllegalArgumentException("timeouts and pool size must be positive");
        }
        if (breakerThreshold < 1 || breakerCooldownMs < 1) {
            throw new IllegalArgumentException("breaker threshold and cooldown must be positive");
        }
    }

    public String address() {
        return host + ":" + port;
    }
}
