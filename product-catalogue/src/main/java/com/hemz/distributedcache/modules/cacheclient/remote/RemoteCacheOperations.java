package com.hemz.distributedcache.modules.cacheclient.remote;

import com.hemz.distributedcache.modules.cacheclient.CacheOperations;
import com.hemz.distributedcache.modules.cacheclient.CacheServerInfo;

import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * One Cache System node: the cache runs in its own process, Cache System, reached over TCP.
 * Every failure becomes a CacheClientException, which CacheAsideTemplate turns into
 * "count an error, read the database". So a dead cache slows requests down, never breaks them.
 */
public final class RemoteCacheOperations implements CacheOperations, AutoCloseable {

    private static final byte[] GET = bytes("GET");
    private static final byte[] SET = bytes("SET");
    private static final byte[] DEL = bytes("DEL");
    private static final byte[] INFO = bytes("INFO");
    private static final byte[] FLUSHALL = bytes("FLUSHALL");
    private static final byte[] SHUTDOWN = bytes("SHUTDOWN");

    private final RemoteCacheSettings settings;
    private final ConnectionPool pool;
    private final CircuitBreaker breaker;

    public RemoteCacheOperations(RemoteCacheSettings settings) {
        this.settings = settings;
        this.pool = new ConnectionPool(settings);
        this.breaker = new CircuitBreaker(settings.breakerThreshold(), settings.breakerCooldownMs());
    }

    @Override
    public byte[] get(String key) {
        return (byte[]) call(GET, bytes(key));
    }

    @Override
    public void set(String key, byte[] value) {
        call(SET, bytes(key), value);
    }

    @Override
    public void delete(String key) {
        call(DEL, bytes(key));
    }

    public void flushAll() {
        call(FLUSHALL);
    }

    /** What this node reports about itself (INFO). */
    public CacheServerInfo info() {
        return CacheServerInfo.parse(new String((byte[]) call(INFO), StandardCharsets.UTF_8));
    }

    public String address() {
        return settings.address();
    }

    /**
     * Asks the node to stop (SHUTDOWN). Cache System only obeys when started with --allow-shutdown=true.
     * Cache System replies +OK first; Redis replies nothing and just closes the connection.
     * So "connection closed before a reply" is success here, not a failure.
     */
    public void shutdown() {
        try {
            call(SHUTDOWN);
        } catch (CacheClientException e) {
            if (!(e.getCause() instanceof EOFException)) {
                throw e;
            }
        }
    }

    public CircuitBreaker.State breakerState() {
        return breaker.state();
    }

    /**
     * Network failures (cannot connect, timeout, connection dropped) count against the breaker.
     * Not counted: a "-ERR" reply (the node answered) and "all connections busy" (our side is saturated, not the node).
     */
    private Object call(byte[]... parts) {
        if (!breaker.allow()) {
            throw new CacheClientException("cache at " + settings.address() + " skipped: circuit breaker open");
        }
        RespConnection connection;
        try {
            connection = pool.borrow();
        } catch (IOException e) {
            nodeFailed();
            throw new CacheClientException("cannot connect to cache at " + settings.address() + ": " + e.getMessage(), e);
        }
        boolean broken = true;
        try {
            Object reply = connection.call(parts);
            broken = false;
            breaker.recordSuccess();
            return reply;
        } catch (CacheServerError e) {
            broken = false;                       // a full "-ERR" reply was read: the connection is fine
            breaker.recordSuccess();
            throw e;
        } catch (IOException e) {
            nodeFailed();
            throw new CacheClientException("cache at " + settings.address() + " failed: " + e.getMessage(), e);
        } finally {
            pool.release(connection, broken);
        }
    }

    /** The other idle connections point at the same dead or restarted node: drop them, so recovery needs one good call. */
    private void nodeFailed() {
        breaker.recordFailure();
        pool.discardIdle();
    }

    @Override
    public void close() {
        pool.close();
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
