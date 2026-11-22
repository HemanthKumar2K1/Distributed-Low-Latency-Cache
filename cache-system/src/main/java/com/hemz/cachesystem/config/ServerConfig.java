package com.hemz.cachesystem.config;

import java.util.HashMap;
import java.util.Map;

/**
 * Settings for one cache node. Only two can be changed, with --name=value:
 *   java -jar cache-system.jar --port=7002 --allow-shutdown=true
 * The protocol limits below are fixed constants: they keep one bad client from using up the heap.
 */
public record ServerConfig(
        /** TCP port. 0 = any free port (tests use this). */
        int port,
        /** SHUTDOWN stops this node. Off by default; start-nodes.sh turns it on for the "Stop node" test. */
        boolean allowShutdown
) {

    /** Address to listen on. Local only: the cache has no password. */
    public static final String BIND_ADDRESS = "127.0.0.1";
    public static final int DEFAULT_PORT = 7001;
    /** How many new connections may wait before accept() takes them. */
    public static final int ACCEPT_BACKLOG = 128;
    /** Largest value a client may send (1 MiB). Checked before any buffer is allocated. */
    public static final int MAX_VALUE_BYTES = 1024 * 1024;
    /** Largest key. */
    public static final int MAX_KEY_BYTES = 1024;
    /** Most parts in one command (SET key value = 3; DEL and EXISTS take several keys). */
    public static final int MAX_ARGS = 64;
    /** Longest header line, like "*3" or "$548", or a typed command from nc. */
    public static final int MAX_LINE_BYTES = 4096;
    /**
     * Most bytes in one whole command: one full value plus a key-sized part for every other slot.
     * Without it, 64 parts of 1 MiB each would let one command hold 64 MiB.
     */
    public static final int MAX_COMMAND_BYTES = MAX_VALUE_BYTES + MAX_ARGS * MAX_KEY_BYTES;
    /** Most open client connections. Beyond this a new client gets "-ERR max number of clients reached" (like Redis). */
    public static final int MAX_CONNECTIONS = 1000;

    private static final int MAX_PORT = 65_535;

    public static ServerConfig defaults() {
        return new ServerConfig(DEFAULT_PORT, false);
    }

    /** Reads --name=value flags. A flag that is not listed below is an error, so a typo never goes unnoticed. */
    public static ServerConfig fromArgs(String[] args) {
        Map<String, String> opts = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--") || !arg.contains("=")) {
                throw new IllegalArgumentException("Expected --name=value, got: " + arg);
            }
            int eq = arg.indexOf('=');
            opts.put(arg.substring(2, eq), arg.substring(eq + 1));
        }

        // Each read REMOVES its flag, so whatever is left at the end is unknown.
        ServerConfig d = defaults();
        ServerConfig config = new ServerConfig(
                intOpt(opts, "port", d.port()),
                boolOpt(opts, "allow-shutdown", d.allowShutdown()));

        if (!opts.isEmpty()) {
            throw new IllegalArgumentException("Unknown options: " + opts.keySet());
        }
        if (config.port() < 0 || config.port() > MAX_PORT) {
            throw new IllegalArgumentException("port must be 0-" + MAX_PORT);
        }
        return config;
    }

    private static int intOpt(Map<String, String> opts, String name, int fallback) {
        String v = opts.remove(name);
        return v == null ? fallback : Integer.parseInt(v);
    }

    private static boolean boolOpt(Map<String, String> opts, String name, boolean fallback) {
        String v = opts.remove(name);
        return v == null ? fallback : Boolean.parseBoolean(v);
    }
}
