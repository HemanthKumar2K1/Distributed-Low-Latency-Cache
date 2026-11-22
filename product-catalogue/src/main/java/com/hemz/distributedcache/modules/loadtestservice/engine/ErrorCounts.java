package com.hemz.distributedcache.modules.loadtestservice.engine;

public record ErrorCounts(long timeouts, long connect, long non2xx, long other) {

    public long total() {
        return timeouts + connect + non2xx + other;
    }
}
