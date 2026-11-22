package com.hemz.distributedcache.modules.cacheclient;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** One parser for both servers' INFO replies. */
class CacheServerInfoTest {

    @Test
    void readsCacheSystemInfo() {
        CacheServerInfo info = CacheServerInfo.parse(
                "# Server\r\n# Memory\r\nused_memory:9000\r\n# Keyspace\r\nkeys:12\r\nvalue_bytes:6000\r\n");

        assertEquals(12, info.keys());
        assertEquals(6000L, info.valueBytes());
        assertEquals(9000L, info.usedMemory());
        assertEquals("cache-system", info.server());
        assertFalse(info.isRedis());
    }

    @Test
    void readsRedisInfo() {
        CacheServerInfo info = CacheServerInfo.parse(String.join("\r\n",
                "# Server", "redis_version:8.10.3", "# Memory", "used_memory:958448",
                "# Keyspace", "db0:keys=12,expires=0,avg_ttl=0,subexpiry=0", "db0_distrib_strings_sizes:4=12", ""));

        assertEquals(12, info.keys());
        assertNull(info.valueBytes(), "Redis does not report value bytes");
        assertEquals(958448L, info.usedMemory());
        assertEquals("redis 8.10.3", info.server());
    }

    @Test
    void emptyRedisHasNoKeyspaceLineAndZeroKeys() {
        CacheServerInfo info = CacheServerInfo.parse("redis_version:8.10.3\r\nused_memory:1000\r\n# Keyspace\r\n");

        assertEquals(0, info.keys());
    }

    @Test
    void redisKeysAreAddedUpAcrossDatabases() {
        CacheServerInfo info = CacheServerInfo.parse("redis_version:8\r\ndb0:keys=3,expires=0\r\ndb1:keys=4,expires=0\r\n");

        assertEquals(7, info.keys());
    }

    @Test
    void unknownInfoHasNoKeys() {
        CacheServerInfo info = CacheServerInfo.parse("hello:world\r\n");

        assertThrows(IllegalStateException.class, info::keys);
        assertNull(info.usedMemory());
    }
}
