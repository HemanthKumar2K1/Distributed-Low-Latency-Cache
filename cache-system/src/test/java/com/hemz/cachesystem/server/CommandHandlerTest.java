package com.hemz.cachesystem.server;

import com.hemz.cachesystem.config.ServerConfig;
import com.hemz.cachesystem.protocol.Reply;
import com.hemz.cachesystem.store.SimpleStore;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandHandlerTest {

    private final SimpleStore store = new SimpleStore();
    private final CommandHandler handler = new CommandHandler(store, ServerConfig.defaults(), () -> { });

    private Reply run(String... words) {
        return run(handler, words);
    }

    private static Reply run(CommandHandler h, String... words) {
        return h.execute(Arrays.stream(words).map(CommandHandlerTest::bytes).toList());
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static String bulkText(Reply reply) {
        return new String(assertInstanceOf(Reply.Bulk.class, reply).value(), StandardCharsets.UTF_8);
    }

    private static String errorText(Reply reply) {
        return assertInstanceOf(Reply.Err.class, reply).message();
    }

    @Test
    void setThenGet() {
        assertEquals(Reply.OK, run("SET", "product:42", "{\"id\":42}"));

        assertEquals("{\"id\":42}", bulkText(run("GET", "product:42")));
    }

    @Test
    void missingKeyIsNil() {
        assertNull(assertInstanceOf(Reply.Bulk.class, run("GET", "nothere")).value());
    }

    @Test
    void commandNamesIgnoreCase() {
        run("set", "k", "v");

        assertEquals("v", bulkText(run("gEt", "k")));
    }

    @Test
    void valuesAreStoredAsExactBytes() {
        byte[] value = {0, 1, '\r', '\n', (byte) 0xFF};
        handler.execute(List.of(bytes("SET"), bytes("k"), value));

        assertArrayEquals(value, store.get("k"));
    }

    @Test
    void delCountsWhatWasRemoved() {
        run("SET", "a", "1");
        run("SET", "b", "2");

        assertEquals(new Reply.Int(2), run("DEL", "a", "b", "c"));
        assertEquals(new Reply.Int(0), run("DEL", "a"));
    }

    @Test
    void existsCountsKeys() {
        run("SET", "a", "1");

        assertEquals(new Reply.Int(1), run("EXISTS", "a", "b"));
    }

    @Test
    void setWithExtraArgumentsIsRejected() {
        assertTrue(errorText(run("SET", "a", "1", "EX", "60")).contains("wrong number of arguments"));
        assertEquals(0, store.size());
    }

    @Test
    void pingAnswersPongOrEchoes() {
        assertEquals(new Reply.Simple("PONG"), run("PING"));
        assertEquals("hi", bulkText(run("PING", "hi")));
    }

    @Test
    void infoReportsKeysAndBytes() {
        run("SET", "a", "12345");

        String info = bulkText(run("INFO"));

        assertTrue(info.contains("keys:1\r\n"), info);
        assertTrue(info.contains("value_bytes:5\r\n"), info);
        assertTrue(info.matches("(?s).*used_memory:\\d+\r\n.*"), info);
    }

    @Test
    void flushAllEmptiesTheStore() {
        run("SET", "a", "1");

        assertEquals(Reply.OK, run("FLUSHALL"));
        assertEquals(0, store.size());
    }

    @Test
    void shutdownIsRefusedByDefault() {
        Reply reply = run("SHUTDOWN");

        assertTrue(errorText(reply).contains("--allow-shutdown=true"), errorText(reply));
    }

    @Test
    void shutdownRepliesOkThenStops() throws InterruptedException {
        ServerConfig allowed = ServerConfig.fromArgs(new String[]{"--allow-shutdown=true"});
        CountDownLatch stopped = new CountDownLatch(1);
        CommandHandler node = new CommandHandler(store, allowed, stopped::countDown);

        assertEquals(Reply.OK, run(node, "SHUTDOWN", "NOSAVE"));
        assertTrue(stopped.await(2, TimeUnit.SECONDS), "onShutdown should run soon after");
    }

    @Test
    void unknownCommandGetsAnError() {
        assertEquals("ERR unknown command 'NOPE'", errorText(run("NOPE")));
    }

    @Test
    void wrongNumberOfArgumentsGetsAnError() {
        assertEquals("ERR wrong number of arguments for 'get' command", errorText(run("GET")));
    }

    @Test
    void badKeysGetAnError() {
        assertEquals("ERR empty key", errorText(run("GET", "")));
        assertTrue(errorText(run("GET", "k".repeat(2000))).contains("key longer than"));
    }
}
