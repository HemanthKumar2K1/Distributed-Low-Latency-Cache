package com.hemz.distributedcache.modules.cacheclient.aside;

/**
 * Callbacks for counting and timing what the cache does. The app plugs in a metrics library.
 * Times are in nanoseconds, from the start of the read to its answer. Hits and misses are timed apart.
 */
public interface CacheMetrics {

    /** Answered from the cache. */
    void hit(long nanos);

    /** Not in the cache (or unreadable): includes the database read and storing the copy. */
    void miss(long nanos);

    /** The cache failed; the request still succeeded through the loader. */
    void error();
}
