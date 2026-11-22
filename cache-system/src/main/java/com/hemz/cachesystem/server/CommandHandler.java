package com.hemz.cachesystem.server;

import com.hemz.cachesystem.config.ServerConfig;
import com.hemz.cachesystem.protocol.Reply;
import com.hemz.cachesystem.store.SimpleStore;

import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * Runs one command against the store. No sockets here, so it is easy to test.
 *
 * Command names match Redis, so the same client can also talk to real Redis (the Redis comparison).
 *   PING [message]       +PONG
 *   GET key              value, or nil
 *   SET key value        +OK
 *   DEL key [key ...]    how many were removed
 *   EXISTS key [key ...] how many exist
 *   INFO                 used_memory, keys, value_bytes as "name:value" lines (node health and memory)
 *   FLUSHALL             empties the cache (cold start before a benchmark, cache on/off switch)
 *   SHUTDOWN [NOSAVE]    replies +OK, then stops this node, only if --allow-shutdown=true (failure test)
 * QUIT is not here: it closes the socket, so ConnectionHandler handles it.
 */
public final class CommandHandler {

    private static final System.Logger log = System.getLogger(CommandHandler.class.getName());

    /** Time for the SHUTDOWN reply to reach the client before the process goes away. */
    private static final long SHUTDOWN_DELAY_MILLIS = 100;

    private final SimpleStore store;
    private final ServerConfig config;
    private final Runnable onShutdown;

    /** onShutdown runs shortly after a SHUTDOWN reply is sent (the process exits in CacheSystemMain). */
    public CommandHandler(SimpleStore store, ServerConfig config, Runnable onShutdown) {
        this.store = store;
        this.config = config;
        this.onShutdown = onShutdown;
    }

    /**
     * parts[0] is the command name. Never throws for bad input: it returns an error reply.
     * Two kinds of error: wrong input (bad arguments, bad key) throws BadCommand inside;
     * a command refused on purpose (SHUTDOWN without its flag) returns Reply.error directly.
     */
    public Reply execute(List<byte[]> parts) {
        String name = text(parts.getFirst()).toUpperCase(Locale.ROOT);
        try {
            return switch (name) {
                case "PING" -> ping(parts);
                case "GET" -> get(parts);
                case "SET" -> set(parts);
                case "DEL" -> del(parts);
                case "EXISTS" -> exists(parts);
                case "INFO" -> info();
                case "FLUSHALL" -> flushAll(parts);
                case "SHUTDOWN" -> shutdown(parts);
                default -> Reply.error("unknown command '" + name + "'");
            };
        } catch (BadCommand e) {
            return Reply.error(e.getMessage());
        }
    }

    private Reply ping(List<byte[]> parts) {
        if (parts.size() > 2) throw wrongArgs("ping");
        return parts.size() == 2 ? new Reply.Bulk(parts.get(1)) : new Reply.Simple("PONG");
    }

    private Reply get(List<byte[]> parts) {
        if (parts.size() != 2) throw wrongArgs("get");
        byte[] value = store.get(key(parts.get(1)));
        return value == null ? Reply.NIL : new Reply.Bulk(value);
    }

    private Reply set(List<byte[]> parts) {
        if (parts.size() != 3) throw wrongArgs("set");
        store.put(key(parts.get(1)), parts.get(2));
        return Reply.OK;
    }

    private Reply del(List<byte[]> parts) {
        if (parts.size() < 2) throw wrongArgs("del");
        return new Reply.Int(countKeys(parts, store::remove));
    }

    private Reply exists(List<byte[]> parts) {
        if (parts.size() < 2) throw wrongArgs("exists");
        return new Reply.Int(countKeys(parts, store::contains));
    }

    /** Runs `action` on every key after the command name; counts how many returned true. */
    private long countKeys(List<byte[]> parts, Predicate<String> action) {
        long count = 0;
        for (byte[] k : parts.subList(1, parts.size())) {
            if (action.test(key(k))) count++;
        }
        return count;
    }

    private Reply info() {
        // Exactly the fields Product Catalogue reads (CacheServerInfo): memory, key count, value bytes.
        String text = "used_memory:" + heapInUse() + "\r\n"
                + "keys:" + store.size() + "\r\n"
                + "value_bytes:" + store.valueBytes() + "\r\n";
        return new Reply.Bulk(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * JVM heap in use: values, keys, map entries, AND garbage not collected yet. Redis's used_memory is
     * its allocator's view, so the two are close in meaning but not identical.
     */
    private static long heapInUse() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private Reply flushAll(List<byte[]> parts) {
        if (parts.size() != 1) throw wrongArgs("flushall");
        store.clear();
        return Reply.OK;
    }

    private Reply shutdown(List<byte[]> parts) {
        if (parts.size() > 2 || (parts.size() == 2 && !text(parts.get(1)).equalsIgnoreCase("NOSAVE"))) {
            throw wrongArgs("shutdown");
        }
        if (!config.allowShutdown()) {
            return Reply.error("SHUTDOWN is disabled (start with --allow-shutdown=true)");
        }
        log.log(Level.INFO, "SHUTDOWN received: stopping this node");
        Thread.ofPlatform().name("cache-shutdown-request").start(() -> {
            try {
                Thread.sleep(SHUTDOWN_DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            onShutdown.run();
        });
        return Reply.OK;
    }

    private String key(byte[] raw) {
        if (raw.length == 0) throw new BadCommand("empty key");
        if (raw.length > ServerConfig.MAX_KEY_BYTES) {
            throw new BadCommand("key longer than " + ServerConfig.MAX_KEY_BYTES + " bytes");
        }
        return text(raw);
    }

    private static String text(byte[] raw) {
        return new String(raw, StandardCharsets.UTF_8);
    }

    private static BadCommand wrongArgs(String command) {
        return new BadCommand("wrong number of arguments for '" + command + "' command");
    }

    /** A bad command is the client's mistake: it gets an error reply, the connection stays open. */
    private static final class BadCommand extends RuntimeException {
        BadCommand(String message) {
            super(message, null, false, false);
        }
    }
}
