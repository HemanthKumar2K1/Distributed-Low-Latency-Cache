package com.hemz.distributedcache.modules.cacheclient.remote;

/** The server answered with "-ERR ...". The reply was complete, so the connection is still usable. */
public class CacheServerError extends CacheClientException {

    public CacheServerError(String message) {
        super(message);
    }
}
