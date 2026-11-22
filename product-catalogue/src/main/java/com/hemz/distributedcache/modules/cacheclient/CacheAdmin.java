package com.hemz.distributedcache.modules.cacheclient;

import java.util.List;

/** Operator commands, separate from the get/set/delete an application uses on every request. */
public interface CacheAdmin {

    /** Empties the whole cache (FLUSHALL on every node). */
    void flushAll();

    /** host:port of the cache server(s), for showing to people. */
    String address();

    /** Each node, up or down, with what it holds. Never throws: a node that fails to answer is "down". */
    List<CacheNodeStatus> nodes();

    /** Asks one node (by name, like "node-2") to stop. For failure tests. */
    void shutdownNode(String name);
}
