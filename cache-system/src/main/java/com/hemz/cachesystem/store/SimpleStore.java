package com.hemz.cachesystem.store;

import java.util.HashMap;
import java.util.Map;

/**
 * The simplest correct store: a HashMap behind one lock.
 * synchronized because every client connection runs on its own thread.
 *
 * On purpose, it has no size limit and no expiry: values stay until DEL or FLUSHALL.
 */
public final class SimpleStore {

    private final Map<String, byte[]> map = new HashMap<>();
    private long valueBytes;

    /** The value for this key, or null if it is not cached. */
    public synchronized byte[] get(String key) {
        return map.get(key);
    }

    /** True if the key is cached, without reading its value (EXISTS). */
    public synchronized boolean contains(String key) {
        return map.containsKey(key);
    }

    /** Stores the value, replacing any old one. */
    public synchronized void put(String key, byte[] value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("key and value are required");
        }
        byte[] old = map.put(key, value);
        valueBytes += value.length - (old == null ? 0 : old.length);
    }

    /** Removes the key. Returns true if it was there. */
    public synchronized boolean remove(String key) {
        byte[] old = map.remove(key);
        if (old == null) {
            return false;
        }
        valueBytes -= old.length;
        return true;
    }

    /** Removes every key (FLUSHALL). */
    public synchronized void clear() {
        map.clear();
        valueBytes = 0;
    }

    /** How many keys are cached (INFO keys). */
    public synchronized int size() {
        return map.size();
    }

    /** Total bytes of all cached values (INFO value_bytes). */
    public synchronized long valueBytes() {
        return valueBytes;
    }
}
