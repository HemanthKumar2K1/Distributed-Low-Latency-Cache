package com.hemz.distributedcache.modules.loadtestservice.engine;

public interface LoadListener {

    /** Called about once per second while a step runs. */
    void onInterval(IntervalStats stats);

    void onLog(String level, String message);
}
