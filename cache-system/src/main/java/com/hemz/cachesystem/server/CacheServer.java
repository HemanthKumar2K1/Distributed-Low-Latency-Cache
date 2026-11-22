package com.hemz.cachesystem.server;

import com.hemz.cachesystem.config.ServerConfig;

import java.io.Closeable;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Accepts TCP connections and gives each one its own virtual thread.
 * The accept loop runs on one normal (platform) thread, which also keeps the JVM alive.
 */
public final class CacheServer implements AutoCloseable {

    private static final System.Logger log = System.getLogger(CacheServer.class.getName());

    /** On shutdown, how long to wait for the accept thread to finish. */
    private static final long ACCEPT_THREAD_JOIN_MILLIS = 2000;
    /**
     * After accept() fails (say, the process ran out of file descriptors), wait this long before trying again.
     * Without the pause the loop would retry at full speed and burn a whole CPU core.
     */
    private static final long ACCEPT_RETRY_PAUSE_MILLIS = 100;
    /** Sent to a client that connects when the server is full, right before closing it. Same text as Redis. */
    private static final byte[] TOO_MANY_CLIENTS = "-ERR max number of clients reached\r\n".getBytes(StandardCharsets.US_ASCII);

    private final ServerConfig config;
    private final CommandHandler commands;
    private final int maxConnections;
    private final Set<Socket> openSockets = ConcurrentHashMap.newKeySet();

    private ServerSocket serverSocket;
    private Thread acceptThread;
    private volatile boolean running;

    public CacheServer(ServerConfig config, CommandHandler commands) {
        this(config, commands, ServerConfig.MAX_CONNECTIONS);
    }

    /** Tests pass a small maxConnections, so the limit can be reached with a few sockets. */
    CacheServer(ServerConfig config, CommandHandler commands, int maxConnections) {
        this.config = config;
        this.commands = commands;
        this.maxConnections = maxConnections;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(ServerConfig.BIND_ADDRESS, config.port()), ServerConfig.ACCEPT_BACKLOG);
        running = true;
        acceptThread = Thread.ofPlatform().name("cache-accept").start(this::acceptLoop);
    }

    /** The real port. Differs from config.port() when that is 0 (tests pick a free port). */
    public int port() {
        return serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (running) {
            Socket socket;
            try {
                socket = serverSocket.accept();
            } catch (IOException e) {
                if (!running || serverSocket.isClosed()) {
                    return;                                  // close() was called: stop quietly
                }
                log.log(Level.WARNING, "accept failed, retrying in {0} ms: {1}", ACCEPT_RETRY_PAUSE_MILLIS, e.getMessage());
                if (!pauseBeforeRetry()) {
                    return;
                }
                continue;
            }
            if (openSockets.size() >= maxConnections) {
                refuse(socket);
                continue;
            }
            try {
                // Send small replies at once instead of waiting to batch them (Nagle's algorithm).
                socket.setTcpNoDelay(true);
            } catch (IOException e) {
                log.log(Level.DEBUG, "could not set TCP_NODELAY: {0}", e.getMessage());
            }
            openSockets.add(socket);
            ConnectionHandler handler = new ConnectionHandler(socket, commands);
            Thread.ofVirtual().name("conn-" + socket.getPort()).start(() -> {
                try {
                    handler.run();
                } finally {
                    openSockets.remove(socket);
                }
            });
        }
    }

    /** false if the thread was interrupted: then stop the loop. */
    private static boolean pauseBeforeRetry() {
        try {
            Thread.sleep(ACCEPT_RETRY_PAUSE_MILLIS);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** Server full: tell the client why, then close. The reply is tiny, so the write does not block. */
    private void refuse(Socket socket) {
        log.log(Level.WARNING, "connection refused: already {0} open", maxConnections);
        try (socket) {
            socket.getOutputStream().write(TOO_MANY_CLIENTS);
        } catch (IOException e) {
            log.log(Level.DEBUG, "could not send the refusal: {0}", e.getMessage());
        }
    }

    /** Stops accepting and closes every open connection. */
    @Override
    public void close() {
        running = false;
        closeQuietly(serverSocket);
        openSockets.forEach(CacheServer::closeQuietly);
        if (acceptThread != null) {
            try {
                acceptThread.join(ACCEPT_THREAD_JOIN_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Closes it, ignoring errors: we are shutting down anyway. */
    private static void closeQuietly(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException ignored) {
            // nothing useful to do while shutting down
        }
    }
}
