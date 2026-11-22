package com.hemz.cachesystem.protocol;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads one command at a time from a client.
 *
 * Normal form (what the client library sends): an array of bulk strings.
 *   *2\r\n$3\r\nGET\r\n$10\r\nproduct:42\r\n
 * Inline form (what you type in nc): words separated by spaces, ended by a newline.
 *   GET product:42
 *
 * Safety rule: every length the client declares is checked against a limit BEFORE
 * any buffer is allocated. "$2000000000" is rejected, never turned into a 2 GB array.
 */
public final class RespReader {

    /** "No byte read yet", for readLineStartingWith. */
    private static final int NO_BYTE = -1;

    private final InputStream in;
    private final int maxArgs;
    private final int maxValueBytes;
    private final int maxLineBytes;
    private final int maxCommandBytes;

    /** in should be buffered: this reads one byte at a time. */
    public RespReader(InputStream in, int maxArgs, int maxValueBytes, int maxLineBytes, int maxCommandBytes) {
        this.in = in;
        this.maxArgs = maxArgs;
        this.maxValueBytes = maxValueBytes;
        this.maxLineBytes = maxLineBytes;
        this.maxCommandBytes = maxCommandBytes;
    }

    /** The next command as its parts (name first), or null if the client closed the connection. */
    public List<byte[]> readCommand() throws IOException {
        while (true) {
            int first = in.read();
            if (first == -1) {
                return null;
            }
            if (first == '*') {
                return readArray();
            }
            List<byte[]> inline = readInline(first);
            if (!inline.isEmpty()) {
                return inline;
            }
            // blank line: ignore it, like Redis does
        }
    }

    private List<byte[]> readArray() throws IOException {
        int count = parseLength(readLine());
        if (count < 1 || count > maxArgs) {
            throw tooManyParts(count);
        }
        List<byte[]> parts = new ArrayList<>(count);
        long commandBytes = 0;
        for (int i = 0; i < count; i++) {
            int marker = in.read();
            if (marker == -1) {
                throw new EOFException("connection closed inside a command");
            }
            if (marker != '$') {
                throw new ProtocolException("expected '$', got '" + (char) marker + "'");
            }
            int length = parseLength(readLine());
            if (length < 0 || length > maxValueBytes) {
                throw new ProtocolException("bulk length must be 0 to " + maxValueBytes + ", got " + length);
            }
            commandBytes += length;
            if (commandBytes > maxCommandBytes) {
                throw new ProtocolException("command longer than " + maxCommandBytes + " bytes");
            }
            byte[] data = in.readNBytes(length);     // safe: length was checked above
            if (data.length < length) {
                throw closedInsideValue();
            }
            expectCrlf();
            parts.add(data);
        }
        return parts;
    }

    private List<byte[]> readInline(int first) throws IOException {
        String line = first == '\n' ? "" : readLineStartingWith(first);
        List<byte[]> parts = new ArrayList<>();
        for (String word : line.trim().split("\\s+")) {
            if (!word.isEmpty()) {
                parts.add(word.getBytes(StandardCharsets.UTF_8));
            }
        }
        if (parts.size() > maxArgs) {
            throw tooManyParts(parts.size());
        }
        return parts;
    }

    private String readLine() throws IOException {
        return readLineStartingWith(NO_BYTE);
    }

    /**
     * Reads up to "\n" (a "\r" before it is dropped). nc sends plain "\n", so both are accepted.
     * first is a byte already taken from the stream, or NO_BYTE.
     */
    private String readLineStartingWith(int first) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        if (first != NO_BYTE) {
            line.write(first);
        }
        while (true) {
            int b = in.read();
            if (b == -1) {
                throw new EOFException("connection closed inside a line");
            }
            if (b == '\n') {
                byte[] bytes = line.toByteArray();
                int end = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, end, StandardCharsets.UTF_8);
            }
            if (line.size() >= maxLineBytes) {
                throw new ProtocolException("line longer than " + maxLineBytes + " bytes");
            }
            line.write(b);
        }
    }

    private void expectCrlf() throws IOException {
        int cr = in.read();
        int lf = in.read();
        if (cr == -1 || lf == -1) {
            throw closedInsideValue();
        }
        if (cr != '\r' || lf != '\n') {
            throw new ProtocolException("value is longer than its declared length");
        }
    }

    private static int parseLength(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new ProtocolException("not a valid length: '" + s + "'");
        }
    }

    private ProtocolException tooManyParts(int count) {
        return new ProtocolException("a command must have 1 to " + maxArgs + " parts, got " + count);
    }

    private static EOFException closedInsideValue() {
        return new EOFException("connection closed inside a value");
    }
}
