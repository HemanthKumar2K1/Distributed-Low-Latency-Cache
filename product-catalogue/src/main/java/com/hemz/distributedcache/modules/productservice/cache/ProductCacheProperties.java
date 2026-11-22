package com.hemz.distributedcache.modules.productservice.cache;

import com.hemz.distributedcache.modules.cacheclient.remote.RemoteCacheSettings;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;

/**
 * product.cache.enabled       false = baseline, true = cache on
 * product.cache.remote.*      which Cache System nodes, and how long to wait for them
 */
@ConfigurationProperties(prefix = "product.cache")
public record ProductCacheProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue Remote remote
) {

    public record Remote(
            /** host:port of each Cache System node. The first is node-1. */
            @DefaultValue("127.0.0.1:7001") List<String> nodes,
            /** Points per node on the hash ring. Fewer gives a visibly uneven spread. */
            @DefaultValue("150") int virtualNodes,
            /** Time allowed to open a new connection to a node. */
            @DefaultValue("10") int connectTimeoutMs,
            /** Time allowed for one reply. A slow cache is worse than none. */
            @DefaultValue("20") int readTimeoutMs,
            /** Per node. Tomcat runs up to 200 request threads, so 200 means a request never waits for a connection. */
            @DefaultValue("200") int poolSize,
            /** Time a request waits for a free connection when all are in use. */
            @DefaultValue("20") int borrowTimeoutMs,
            /** Circuit breaker: failures in a row before a node is skipped. */
            @DefaultValue("3") int breakerThreshold,
            /** Circuit breaker: how long a node is skipped before one trial call. */
            @DefaultValue("5000") int breakerCooldownMs
    ) {
        public List<RemoteCacheSettings> toSettings() {
            return nodes.stream().map(this::settingsFor).toList();
        }

        private RemoteCacheSettings settingsFor(String hostPort) {
            int colon = hostPort.lastIndexOf(':');
            if (colon < 1) {
                throw new IllegalArgumentException("product.cache.remote.nodes: expected host:port, got '" + hostPort + "'");
            }
            return new RemoteCacheSettings(hostPort.substring(0, colon).trim(),
                    Integer.parseInt(hostPort.substring(colon + 1).trim()),
                    connectTimeoutMs, readTimeoutMs, poolSize, borrowTimeoutMs, breakerThreshold, breakerCooldownMs);
        }
    }
}
