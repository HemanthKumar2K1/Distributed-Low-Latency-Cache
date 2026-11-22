package com.hemz.distributedcache.modules.cacheclient.aside;

/** Turns a value into bytes for the cache, and back. */
public interface ValueCodec<T> {

    byte[] encode(T value);

    T decode(byte[] bytes);
}

