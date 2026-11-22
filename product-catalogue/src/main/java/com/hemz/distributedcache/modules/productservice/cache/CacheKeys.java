package com.hemz.distributedcache.modules.productservice.cache;

/** Every cache key is built here, as "type:id". */
public final class CacheKeys {

    private CacheKeys() {
    }

    public static String product(long id) {
        return "product:" + id;
    }
}

