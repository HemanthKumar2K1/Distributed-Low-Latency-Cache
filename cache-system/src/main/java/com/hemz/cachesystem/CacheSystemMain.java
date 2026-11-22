package com.hemz.cachesystem;

import com.hemz.cachesystem.config.ServerConfig;
import com.hemz.cachesystem.server.CacheServer;
import com.hemz.cachesystem.server.CommandHandler;
import com.hemz.cachesystem.store.SimpleStore;

import java.io.IOException;
import java.lang.System.Logger.Level;

/**
 * Starts one cache node.
 *   java -jar target/cache-system.jar                  port 7001
 *   java -jar target/cache-system.jar --port=7002
 * Try it: nc localhost 7001, then type PING
 */
public final class CacheSystemMain {

    private static final System.Logger log = System.getLogger(CacheSystemMain.class.getName());

    private CacheSystemMain() {
    }

    public static void main(String[] args) throws IOException {
        ServerConfig config = ServerConfig.fromArgs(args);
        SimpleStore store = new SimpleStore();
        // SHUTDOWN (if allowed) exits the process; the shutdown hook below then closes the server.
        CommandHandler commands = new CommandHandler(store, config, () -> System.exit(0));
        CacheServer server = new CacheServer(config, commands);

        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(server::close, "cache-shutdown"));
        // String.valueOf: stops the logger printing numbers with commas, like "7,001".
        log.log(Level.INFO, "Cache System listening on {0}:{1} (SHUTDOWN {2})",
                ServerConfig.BIND_ADDRESS, String.valueOf(server.port()), config.allowShutdown() ? "allowed" : "disabled");
    }
}
