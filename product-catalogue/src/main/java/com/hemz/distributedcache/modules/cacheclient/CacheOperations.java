package com.hemz.distributedcache.modules.cacheclient;

/** The three things an application asks of a cache. */
public interface CacheOperations {

    /** Cached bytes for the key, or null on a miss. */
    byte[] get(String key);

    void set(String key, byte[] value);

    void delete(String key);
}

