package com.hemz.distributedcache.modules.loadtestservice.engine;

/** How product ids are chosen for each request. */
public enum KeyDistribution {
    /** Every id equally likely. Hardest case for a cache. */
    UNIFORM,
    /** A few ids are very popular, most are rare. Closest to real traffic. */
    ZIPFIAN,
    /** 90% of requests go to 1% of ids. Stresses a small hot set. */
    HOTSPOT
}
