package com.hemz.distributedcache.modules.cacheclient.remote;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Reuses connections, so a request does not pay for a new TCP handshake.
 * At most poolSize are in use at once; they are opened only when needed.
 * A broken connection is closed and simply replaced by a new one later.
 */
final class ConnectionPool implements AutoCloseable {

    private final RemoteCacheSettings settings;
    private final Semaphore permits;
    private final BlockingQueue<RespConnection> idle = new LinkedBlockingQueue<>();
    private volatile boolean closed;

    ConnectionPool(RemoteCacheSettings settings) {
        this.settings = settings;
        this.permits = new Semaphore(settings.poolSize());
    }

    RespConnection borrow() throws IOException {
        if (closed) {
            throw new CacheClientException("cache client is closed");
        }
        boolean got;
        try {
            got = permits.tryAcquire(settings.borrowTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CacheClientException("interrupted while waiting for a cache connection");
        }
        if (!got) {
            throw new CacheClientException("all " + settings.poolSize() + " cache connections busy for "
                    + settings.borrowTimeoutMs() + " ms");
        }
        RespConnection connection = idle.poll();
        if (connection != null) {
            return connection;
        }
        try {
            return new RespConnection(settings);
        } catch (IOException | RuntimeException e) {
            permits.release();
            throw e;
        }
    }

    /** broken = something went wrong mid-call; the connection is closed instead of reused. */
    void release(RespConnection connection, boolean broken) {
        if (broken || closed) {
            connection.close();
        } else {
            idle.offer(connection);
        }
        permits.release();
    }

    /** Closes every idle connection. Used when the node failed: they all point at a dead socket. */
    void discardIdle() {
        RespConnection c;
        while ((c = idle.poll()) != null) {
            c.close();
        }
    }

    @Override
    public void close() {
        closed = true;
        discardIdle();
    }
}
