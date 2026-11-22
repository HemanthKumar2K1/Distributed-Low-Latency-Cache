package com.hemz.distributedcache.modules.cacheclient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The INFO reply: "name:value" lines, "#" lines are section titles.
 * Understands both servers, so the same client can talk to either (the Redis comparison):
 *   Cache System  keys:12   value_bytes:6000   used_memory:...   module:4
 *   Redis         db0:keys=12,expires=0,...    used_memory:...   redis_version:8.x
 */
public record CacheServerInfo(Map<String, String> fields) {

    public static CacheServerInfo parse(String text) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (String line : text.split("\r?\n")) {
            int colon = line.indexOf(':');
            if (line.isBlank() || line.startsWith("#") || colon < 0) {
                continue;
            }
            fields.put(line.substring(0, colon), line.substring(colon + 1).trim());
        }
        return new CacheServerInfo(Map.copyOf(fields));
    }

    /** Keys stored. Redis lists them per database ("db0:keys=12,...") and leaves the line out when empty. */
    public long keys() {
        if (fields.containsKey("keys")) {
            return number("keys");
        }
        long total = 0;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (e.getKey().matches("db\\d+")) {
                for (String part : e.getValue().split(",")) {
                    if (part.startsWith("keys=")) total += Long.parseLong(part.substring("keys=".length()));
                }
            }
        }
        if (total == 0 && !isRedis()) {
            throw new IllegalStateException("INFO has no 'keys' field");
        }
        return total;
    }

    /** Bytes of the stored values alone. Only Cache System reports this; null for Redis. */
    public Long valueBytes() {
        return fields.containsKey("value_bytes") ? number("value_bytes") : null;
    }

    /** Memory the server process uses for its data: Redis used_memory, Cache System JVM heap in use. Null if not reported. */
    public Long usedMemory() {
        return fields.containsKey("used_memory") ? number("used_memory") : null;
    }

    /** "redis 8.10.3" or "cache-system". */
    public String server() {
        return isRedis() ? "redis " + fields.get("redis_version") : "cache-system";
    }

    public boolean isRedis() {
        return fields.containsKey("redis_version");
    }

    private long number(String name) {
        String v = fields.get(name);
        if (v == null) {
            throw new IllegalStateException("INFO has no '" + name + "' field");
        }
        return Long.parseLong(v);
    }
}
