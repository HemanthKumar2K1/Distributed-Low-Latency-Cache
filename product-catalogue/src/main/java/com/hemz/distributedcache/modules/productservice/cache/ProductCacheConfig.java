package com.hemz.distributedcache.modules.productservice.cache;

import com.hemz.distributedcache.modules.cacheclient.CacheOperations;
import com.hemz.distributedcache.modules.cacheclient.aside.CacheAsideTemplate;
import com.hemz.distributedcache.modules.cacheclient.remote.ShardedCacheOperations;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Creates the plain-Java cache client objects as Spring beans. */
@Configuration
public class ProductCacheConfig {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheConfig.class);

    /**
     * One client object is both the per-request CacheOperations and the CacheAdmin.
     * Always sharded, even with one node: one code path.
     */
    @Bean(destroyMethod = "close")
    public ShardedCacheOperations productCacheClient(ProductCacheProperties props) {
        ShardedCacheOperations client = new ShardedCacheOperations(props.remote().toSettings(), props.remote().virtualNodes());
        log.info("Product cache spreads keys over {} Cache System node(s): {}",
                props.remote().nodes().size(), client.address());
        return client;
    }

    @Bean
    public MicrometerCacheMetrics productCacheMetrics(MeterRegistry registry) {
        return new MicrometerCacheMetrics(registry);
    }

    @Bean
    public CacheAsideTemplate productCacheTemplate(CacheOperations operations, MicrometerCacheMetrics metrics) {
        return new CacheAsideTemplate(operations, metrics);   // on/off is decided per request by CacheSwitch
    }
}
