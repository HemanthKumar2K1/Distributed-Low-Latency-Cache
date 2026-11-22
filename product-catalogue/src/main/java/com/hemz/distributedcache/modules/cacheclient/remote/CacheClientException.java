package com.hemz.distributedcache.modules.cacheclient.remote;

/**
 * The cache could not answer: not reachable, too slow, or no free connection.
 * CacheAsideTemplate catches it, counts an error and reads the database instead.
 */
public class CacheClientException extends RuntimeException {

    public CacheClientException(String message) {
        super(message);
    }

    public CacheClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
