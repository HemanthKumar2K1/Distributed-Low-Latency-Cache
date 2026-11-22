package com.hemz.distributedcache.modules.cacheclient.remote;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A test stand-in for Cache System: a real socket on a free port, speaking RESP.
 * The real Cache System is a separate project, so it is not used in these tests.
 * Just enough of the protocol: GET, SET, DEL, INFO, FLUSHALL, and SHUTDOWN the way Redis does it
 * (no reply, the connection just closes).
 */
final class FakeRespServer implements AutoCloseable {
    final ServerSocket socket = new ServerSocket();
    final AtomicInteger accepted = new AtomicInteger();
    final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    final Map<String, byte[]> data = new ConcurrentHashMap<>();
    volatile int delayMs;
    volatile boolean replyWithError;
    /** When set, every command gets exactly these bytes back, however broken they are. */
    volatile String rawReply;

    /** On any free port. */
    FakeRespServer() throws IOException {
        this(0);
    }

    /** On this port: a node that comes back where it was. */
    FakeRespServer(int port) throws IOException {
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress("127.0.0.1", port));
        Thread.ofVirtual().start(() -> {
            while (!socket.isClosed()) {
                try {
                    Socket s = socket.accept();
                    accepted.incrementAndGet();
                    clients.add(s);
                    Thread.ofVirtual().start(() -> serve(s));
                } catch (IOException e) {
                    return;
                }
            }
        });
    }

    int port() {
        return socket.getLocalPort();
    }

    private void serve(Socket s) {
        try (s) {
            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = new BufferedOutputStream(s.getOutputStream());
            while (true) {
                List<String> cmd = read(in);
                if (cmd == null) return;
                if (cmd.getFirst().equals("SHUTDOWN")) return;           // like Redis: no reply, just close
                if (delayMs > 0) Thread.sleep(delayMs);
                out.write(reply(cmd));
                out.flush();
            }
        } catch (IOException | InterruptedException e) {
            // test over or client went away
        }
    }

    private byte[] reply(List<String> cmd) {
        if (rawReply != null) return ascii(rawReply);
        if (replyWithError) return ascii("-ERR fake failure\r\n");
        String key = cmd.size() > 1 ? cmd.get(1) : "";
        return switch (cmd.getFirst()) {
            case "GET" -> bulk(data.get(key));
            case "SET" -> {
                data.put(key, cmd.get(2).getBytes(StandardCharsets.ISO_8859_1));
                yield ascii("+OK\r\n");
            }
            case "DEL" -> ascii(":" + (data.remove(key) == null ? 0 : 1) + "\r\n");
            case "FLUSHALL" -> {
                data.clear();
                yield ascii("+OK\r\n");
            }
            case "INFO" -> bulk(ascii("# Keyspace\r\nkeys:" + data.size() + "\r\nvalue_bytes:"
                    + data.values().stream().mapToInt(v -> v.length).sum() + "\r\n"));
            default -> ascii("-ERR unknown\r\n");
        };
    }

    /** Parts are read as ISO-8859-1 so every byte survives the trip into a String and back. */
    private static List<String> read(InputStream in) throws IOException {
        if (in.read() != '*') return null;
        int n = Integer.parseInt(line(in));
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            in.read();                                         // '$'
            byte[] data = in.readNBytes(Integer.parseInt(line(in)));
            in.readNBytes(2);                                  // \r\n
            parts.add(new String(data, StandardCharsets.ISO_8859_1));
        }
        return parts;
    }

    private static String line(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != '\r') sb.append((char) b);
        in.read();
        return sb.toString();
    }

    private static byte[] bulk(byte[] value) {
        if (value == null) return ascii("$-1\r\n");
        byte[] head = ascii("$" + value.length + "\r\n");
        byte[] out = new byte[head.length + value.length + 2];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(value, 0, out, head.length, value.length);
        out[out.length - 2] = '\r';
        out[out.length - 1] = '\n';
        return out;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    /** Like a node dying: stop listening AND drop every open connection. */
    @Override
    public void close() throws IOException {
        socket.close();
        for (Socket c : clients) {
            c.close();
        }
    }
}
