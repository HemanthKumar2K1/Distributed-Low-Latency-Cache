package com.hemz.distributedcache.modules.cacheclient.remote;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client against FakeRespServer, a tiny server speaking the same RESP bytes.
 * The real Cache System is a separate project, so it is not used in these tests.
 */
class RemoteCacheOperationsTest {

    /** The usual circuit breaker: 3 failures in a row, then the node is skipped for 5 s. */
    private static final int BREAKER_THRESHOLD = 3;
    private static final int BREAKER_COOLDOWN_MS = 5000;

    private FakeRespServer server;
    private RemoteCacheOperations client;

    @BeforeEach
    void start() throws IOException {
        server = new FakeRespServer();
        client = new RemoteCacheOperations(settings(server.port(), 200, 4, 20));
    }

    @AfterEach
    void stop() throws IOException {
        client.close();
        server.close();
    }

    private static RemoteCacheSettings settings(int port, int readTimeoutMs, int poolSize, int borrowTimeoutMs) {
        return settings(port, readTimeoutMs, poolSize, borrowTimeoutMs, BREAKER_THRESHOLD, BREAKER_COOLDOWN_MS);
    }

    /** Same, with the circuit breaker set by the test. */
    private static RemoteCacheSettings settings(int port, int readTimeoutMs, int poolSize, int borrowTimeoutMs,
                                                int breakerThreshold, int breakerCooldownMs) {
        return new RemoteCacheSettings("127.0.0.1", port, 200, readTimeoutMs, poolSize, borrowTimeoutMs,
                breakerThreshold, breakerCooldownMs);
    }

    @Test
    void setGetDelete() {
        byte[] value = {0, 1, '\r', '\n', 2};
        client.set("product:42", value);

        assertArrayEquals(value, client.get("product:42"));
        client.delete("product:42");
        assertNull(client.get("product:42"));
    }

    @Test
    void oneConnectionIsReusedForManyCalls() {
        for (int i = 0; i < 50; i++) {
            client.get("k" + i);
        }

        assertEquals(1, server.accepted.get());
    }

    @Test
    void infoAndFlushAll() {
        client.set("a", "12345".getBytes(StandardCharsets.UTF_8));

        assertEquals(1, client.info().keys());
        assertEquals(5, client.info().valueBytes());
        client.flushAll();
        assertEquals(0, client.info().keys());
    }

    @Test
    void serverDownFailsFast() throws IOException {
        int deadPort;
        try (ServerSocket s = new ServerSocket(0)) {
            deadPort = s.getLocalPort();
        }
        try (RemoteCacheOperations dead = new RemoteCacheOperations(settings(deadPort, 200, 4, 20))) {
            long start = System.nanoTime();

            assertThrows(CacheClientException.class, () -> dead.get("k"));

            assertTrue(System.nanoTime() - start < 1_000_000_000L, "should fail well under a second");
        }
    }

    @Test
    void slowReplyTimesOutAndTheConnectionIsReplaced() {
        try (RemoteCacheOperations impatient = new RemoteCacheOperations(settings(server.port(), 20, 4, 20))) {
            server.delayMs = 300;
            assertThrows(CacheClientException.class, () -> impatient.get("k"));

            server.delayMs = 0;
            impatient.set("k", new byte[]{7});
            assertArrayEquals(new byte[]{7}, impatient.get("k"));

            assertEquals(2, server.accepted.get(), "the timed-out connection must not be reused");
        }
    }

    @Test
    void afterThreeTimeoutsTheNodeIsSkippedWithoutWaiting() {
        int readTimeoutMs = 30;
        int breakerThreshold = 3;
        int breakerCooldownMs = 5000;
        try (RemoteCacheOperations breakerClient = new RemoteCacheOperations(
                settings(server.port(), readTimeoutMs, 4, 20, breakerThreshold, breakerCooldownMs))) {
            server.delayMs = 500;                         // a node that hangs: accepts, never answers in time
            for (int i = 0; i < breakerThreshold; i++) {
                assertThrows(CacheClientException.class, () -> breakerClient.get("k"));
            }
            assertEquals(CircuitBreaker.State.OPEN, breakerClient.breakerState());

            long start = System.nanoTime();
            CacheClientException e = assertThrows(CacheClientException.class, () -> breakerClient.get("k"));

            assertTrue(e.getMessage().contains("circuit breaker open"), e.getMessage());
            assertTrue(System.nanoTime() - start < 10_000_000L, "a skipped call must not wait for a timeout");
            assertEquals(3, server.accepted.get(), "the 4th call must not reach the node");
        }
    }

    @Test
    void aGarbledReplyCountsAgainstTheBreakerAndDropsTheConnection() {
        server.rawReply = ":not-a-number\r\n";
        for (int i = 0; i < BREAKER_THRESHOLD; i++) {
            assertThrows(CacheClientException.class, () -> client.delete("k"));
        }

        assertEquals(CircuitBreaker.State.OPEN, client.breakerState());
        assertEquals(BREAKER_THRESHOLD, server.accepted.get(), "a garbled connection is never reused");
    }

    @Test
    void aNodeThatComesBackIsUsedAgainAfterTheCooldown() throws Exception {
        int cooldownMs = 100;
        int port = server.port();
        try (RemoteCacheOperations recovering = new RemoteCacheOperations(
                settings(port, 200, 4, 20, BREAKER_THRESHOLD, cooldownMs))) {
            recovering.set("k", new byte[]{1});
            server.close();                                    // the node dies
            for (int i = 0; i < BREAKER_THRESHOLD; i++) {
                assertThrows(CacheClientException.class, () -> recovering.get("k"));
            }
            assertEquals(CircuitBreaker.State.OPEN, recovering.breakerState());

            server = new FakeRespServer(port);                 // the node comes back, empty, on the same port
            Thread.sleep(cooldownMs);

            assertNull(recovering.get("k"), "the trial call reaches the new node (empty after a restart)");
            assertEquals(CircuitBreaker.State.CLOSED, recovering.breakerState());
        }
    }

    @Test
    void redisStyleShutdownWithNoReplyCountsAsStopped() {
        client.shutdown();                                     // the fake closes the connection, like Redis
    }

    @Test
    void errorReplyDoesNotCountAgainstTheBreaker() {
        server.replyWithError = true;
        for (int i = 0; i < 5; i++) {
            assertThrows(CacheServerError.class, () -> client.get("k"));
        }

        assertEquals(CircuitBreaker.State.CLOSED, client.breakerState());
    }

    @Test
    void errorReplyKeepsTheConnection() {
        server.replyWithError = true;
        CacheServerError e = assertThrows(CacheServerError.class, () -> client.get("k"));
        assertEquals("ERR fake failure", e.getMessage());

        server.replyWithError = false;
        client.get("k");

        assertEquals(1, server.accepted.get());
    }

    @Test
    void allConnectionsBusyFailsAfterTheBorrowTimeout() throws Exception {
        try (RemoteCacheOperations onlyOne = new RemoteCacheOperations(settings(server.port(), 2000, 1, 20))) {
            server.delayMs = 500;
            Thread holder = Thread.ofVirtual().start(() -> onlyOne.get("slow"));
            while (server.accepted.get() == 0) {
                Thread.onSpinWait();
            }

            CacheClientException e = assertThrows(CacheClientException.class, () -> onlyOne.get("k"));

            assertTrue(e.getMessage().contains("busy"), e.getMessage());
            holder.join();
        }
    }
}
