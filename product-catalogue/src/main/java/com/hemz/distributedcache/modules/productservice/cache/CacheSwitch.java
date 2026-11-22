package com.hemz.distributedcache.modules.productservice.cache;

import com.hemz.distributedcache.modules.cacheclient.CacheAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Turns the cache on or off while the app runs. Starts from product.cache.enabled.
 * On: reads go through the cache. Off: straight to PostgreSQL, nothing counted.
 * Every change empties the cache (FLUSHALL on Cache System), so each "on" period starts cold.
 */
@Component
public class CacheSwitch {

    private static final Logger log = LoggerFactory.getLogger(CacheSwitch.class);

    private final AtomicBoolean enabled;
    private final CacheAdmin admin;

    public CacheSwitch(ProductCacheProperties props, CacheAdmin admin) {
        this.enabled = new AtomicBoolean(props.enabled());
        this.admin = admin;
        log.info("Product cache starts {}{}", word(props.enabled()), props.enabled() ? "" : " (every read goes to PostgreSQL)");
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Turns the cache on or off; empties it when the state changes. The switch still flips
     * when Cache System is down: then nothing was emptied, and that is logged.
     */
    public void set(boolean on) {
        boolean changed = enabled.getAndSet(on) != on;
        if (changed) {
            try {
                admin.flushAll();
                log.info("Product cache switched {} (cache emptied)", word(on));
            } catch (RuntimeException e) {
                log.warn("Product cache switched {}, but Cache System at {} could not be emptied: {}",
                        word(on), admin.address(), e.getMessage());
            }
        }
    }

    /** "ON" or "OFF", for the log. */
    private static String word(boolean on) {
        return on ? "ON" : "OFF";
    }
}
