package com.hemz.cachesystem.server;

import com.hemz.cachesystem.config.ServerConfig;
import com.hemz.cachesystem.store.SimpleStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A real server on a free port, spoken to over a real socket with raw RESP bytes. */
class CacheServerTest {

    /** A test fails instead of hanging if the server never answers. */
    private static final int READ_TIMEOUT_MILLIS = 2000;
    private static final int CLIENTS = 200;
    private static final int SLOT_FREE_ATTEMPTS = 20;
    private static final long SLOT_FREE_PAUSE_MILLIS = 50;

    private CacheServer server;

    @BeforeEach
    void start() throws IOException {
        // Port 0 = any free port.
        ServerConfig config = ServerConfig.fromArgs(new String[]{"--port=0"});
        server = new CacheServer(config, new CommandHandler(new SimpleStore(), config, () -> { }));
        server.start();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private Socket connect() throws IOException {
        return connect(server);
    }

    private static Socket connect(CacheServer target) throws IOException {
        Socket s = new Socket("127.0.0.1", target.port());
        s.setSoTimeout(READ_TIMEOUT_MILLIS);
        return s;
    }

    private static void send(Socket s, String wire) throws IOException {
        OutputStream out = s.getOutputStream();
        out.write(wire.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /** Reads exactly as many bytes as expected, so the test does not depend on how TCP splits them. */
    private static String receive(Socket s, int bytes) throws IOException {
        return new String(s.getInputStream().readNBytes(bytes), StandardCharsets.UTF_8);
    }

    private static void assertReceives(Socket s, String expected) throws IOException {
        assertEquals(expected, receive(s, expected.length()));
    }

    /** Everything until the server closes the connection. */
    private static String receiveUntilClosed(Socket s) throws IOException {
        return new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    @Test
    void setThenGetOverTheWire() throws IOException {
        try (Socket s = connect()) {
            send(s, "*3\r\n$3\r\nSET\r\n$10\r\nproduct:42\r\n$7\r\n{\"a\":1}\r\n");
            assertReceives(s, "+OK\r\n");

            send(s, "*2\r\n$3\r\nGET\r\n$10\r\nproduct:42\r\n");
            assertReceives(s, "$7\r\n{\"a\":1}\r\n");

            send(s, "*2\r\n$3\r\nGET\r\n$4\r\nnope\r\n");
            assertReceives(s, "$-1\r\n");
        }
    }

    @Test
    void typedCommandsWorkLikeNc() throws IOException {
        try (Socket s = connect()) {
            send(s, "PING\n");
            assertReceives(s, "+PONG\r\n");
        }
    }

    @Test
    void pipelinedCommandsAllGetReplies() throws IOException {
        try (Socket s = connect()) {
            send(s, "*1\r\n$4\r\nPING\r\n".repeat(3));
            assertReceives(s, "+PONG\r\n".repeat(3));
        }
    }

    @Test
    void oversizedValueClosesOnlyThatConnection() throws IOException {
        try (Socket bad = connect()) {
            send(bad, "*3\r\n$3\r\nSET\r\n$1\r\nk\r\n$2000000000\r\n");
            String reply = receiveUntilClosed(bad);
            assertTrue(reply.startsWith("-ERR Protocol error: bulk length"), reply);
        }
        try (Socket good = connect()) {
            send(good, "PING\n");
            assertReceives(good, "+PONG\r\n");
        }
    }

    @Test
    void quitClosesTheConnection() throws IOException {
        try (Socket s = connect()) {
            send(s, "QUIT\n");
            assertEquals("+OK\r\n", receiveUntilClosed(s));
        }
    }

    @Test
    void manyClientsAtOnceEachGetTheirOwnValue() throws Exception {
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> results = new ArrayList<>();
            for (int i = 0; i < CLIENTS; i++) {
                String key = "k" + i;
                String expected = expectedSetThenGet(key);
                Callable<String> client = () -> {
                    try (Socket s = connect()) {
                        send(s, "SET " + key + " " + key + "\nGET " + key + "\n");
                        return receive(s, expected.length());
                    }
                };
                results.add(pool.submit(client));
            }
            for (int i = 0; i < CLIENTS; i++) {
                assertEquals(expectedSetThenGet("k" + i), results.get(i).get());
            }
        }
    }

    @Test
    void closingTheServerClosesOpenConnections() throws IOException {
        try (Socket s = connect()) {
            send(s, "PING\n");
            assertReceives(s, "+PONG\r\n");

            server.close();

            assertEquals("", receiveUntilClosed(s), "the client sees the connection end, not a hang");
        }
    }

    @Test
    void clientsBeyondTheLimitAreRefusedAndAFreedSlotIsReused() throws Exception {
        ServerConfig config = ServerConfig.fromArgs(new String[]{"--port=0"});
        try (CacheServer small = new CacheServer(config, new CommandHandler(new SimpleStore(), config, () -> { }), 2)) {
            small.start();
            Socket first = connect(small);
            try (Socket second = connect(small)) {
                for (Socket s : List.of(first, second)) {         // a reply proves the server took the connection
                    send(s, "PING\n");
                    assertReceives(s, "+PONG\r\n");
                }
                try (Socket third = connect(small)) {
                    assertEquals("-ERR max number of clients reached\r\n", receiveUntilClosed(third));
                }

                first.close();

                assertEquals("+PONG\r\n", pingOnNewConnection(small), "the closed client's slot is free again");
            }
        }
    }

    /** The slot frees when the closed connection's thread notices, so allow a few tries. */
    private static String pingOnNewConnection(CacheServer target) throws Exception {
        String reply = "";
        for (int attempt = 0; attempt < SLOT_FREE_ATTEMPTS && !reply.equals("+PONG\r\n"); attempt++) {
            try (Socket s = connect(target)) {
                send(s, "PING\nQUIT\n");
                reply = receiveUntilClosed(s).replace("+OK\r\n", "");
            }
            if (!reply.equals("+PONG\r\n")) {
                Thread.sleep(SLOT_FREE_PAUSE_MILLIS);
            }
        }
        return reply;
    }

    /** The two replies to "SET key key" then "GET key": +OK, then the value back. */
    private static String expectedSetThenGet(String key) {
        return "+OK\r\n$" + key.length() + "\r\n" + key + "\r\n";
    }
}
