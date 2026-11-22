package com.hemz.distributedcache.modules.loadtestservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings for the load tester (prefix "loadtest"). Defaults fit the local setup. */
@ConfigurationProperties(prefix = "loadtest")
public record LoadTestProperties(
        /** Where run JSON files are kept. */
        @DefaultValue("./runs") String runsDir,
        /** Product Catalogue's base URL: the one service this load tester measures. */
        @DefaultValue("http://localhost:8080") String target,
        /** HikariCP pool name in Product Catalogue (spring.datasource.hikari.pool-name). */
        @DefaultValue("product-pool") String poolName,
        @DefaultValue("50000") int maxRate,
        @DefaultValue("900") int maxDurationSeconds,
        @DefaultValue("5000") int maxConnections,
        @DefaultValue("12") int maxSteps,
        @DefaultValue("200000") long defaultMaxId
) {
}
