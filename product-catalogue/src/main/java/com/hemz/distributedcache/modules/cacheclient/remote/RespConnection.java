package com.hemz.distributedcache.modules.cacheclient.remote;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * One TCP connection to the cache server. Sends a command, waits for its reply.
 * Used by one thread at a time (the pool makes sure of that).
 *
 * Any IOException leaves the connection in an unknown state (half a reply may still be
 * on the way), so the caller must close it, never reuse it.
 */
final class RespConnection implements AutoCloseable {

    /** A guard against a broken server sending a huge length. Values are far smaller. */
    private static final int MAX_REPLY_BYTES = 16 * 1024 * 1024;
    /** Longest status, error or length line we accept. Real ones are a few dozen bytes. */
    private static final int MAX_LINE_BYTES = 4096;
    /** Read and write buffer size for the socket streams. */
    private static final int BUFFER_BYTES = 16 * 1024;
    private static final byte[] CRLF = {'\r', '\n'};

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    RespConnection(RemoteCacheSettings settings) throws IOException {
        socket = new Socket();
        try {
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(settings.host(), settings.port()), settings.connectTimeoutMs());
            socket.setSoTimeout(settings.readTimeoutMs());
            in = new BufferedInputStream(socket.getInputStream(), BUFFER_BYTES);
            out = new BufferedOutputStream(socket.getOutputStream(), BUFFER_BYTES);
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }

    /**
     * Sends one command and returns its reply:
     * String (+OK), Long (:1), byte[] ($5 hello), or null ($-1, not found).
     * A "-ERR" reply throws CacheServerError.
     */
    Object call(byte[]... parts) throws IOException {
        writeCommand(parts);
        return readReply();
    }

    private void writeCommand(byte[]... parts) throws IOException {
        out.write(('*' + Integer.toString(parts.length)).getBytes(StandardCharsets.US_ASCII));
        out.write(CRLF);
        for (byte[] part : parts) {
            out.write(('$' + Integer.toString(part.length)).getBytes(StandardCharsets.US_ASCII));
            out.write(CRLF);
            out.write(part);
            out.write(CRLF);
        }
        out.flush();
    }

    private Object readReply() throws IOException {
        int marker = in.read();
        if (marker == -1) {
            throw new EOFException("cache server closed the connection");
        }
        String line = readLine();
        return switch (marker) {
            case '+' -> line;
            case '-' -> throw new CacheServerError(line);
            case ':' -> parseNumber(line);
            case '$' -> readBulk(parseNumber(line));
            default -> throw new IOException("unexpected reply type '" + (char) marker + "'");
        };
    }

    /**
     * A number from the reply. Garbage here means the stream is corrupt, so it is an IOException
     * like any other broken reply: the connection is closed and the node's breaker counts it.
     */
    private static long parseNumber(String line) throws IOException {
        try {
            return Long.parseLong(line);
        } catch (NumberFormatException e) {
            throw new IOException("not a number in reply: '" + line + "'", e);
        }
    }

    private byte[] readBulk(long length) throws IOException {
        if (length == -1) {
            return null;
        }
        if (length < 0 || length > MAX_REPLY_BYTES) {
            throw new IOException("bad reply length " + length);
        }
        byte[] data = in.readNBytes((int) length);              // safe: checked against MAX_REPLY_BYTES above
        if (data.length < length || in.read() != '\r' || in.read() != '\n') {
            throw new EOFException("reply cut short");
        }
        return data;
    }

    private String readLine() throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream(32);
        int b;
        while ((b = in.read()) != '\r') {
            if (b == -1) {
                throw new EOFException("reply cut short");
            }
            if (line.size() >= MAX_LINE_BYTES) {
                throw new IOException("reply line longer than " + MAX_LINE_BYTES + " bytes");
            }
            line.write(b);
        }
        if (in.read() != '\n') {
            throw new IOException("reply line not ended by \\r\\n");
        }
        return line.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
            // nothing useful to do
        }
    }
}
