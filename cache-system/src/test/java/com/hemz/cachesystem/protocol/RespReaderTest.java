package com.hemz.cachesystem.protocol;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RespReaderTest {

    // Small limits, so the "too big" tests need only short inputs.
    private static final int MAX_ARGS = 8;
    private static final int MAX_VALUE_BYTES = 100;
    private static final int MAX_LINE_BYTES = 64;
    /** Two full values fit in one command, three do not. */
    private static final int MAX_COMMAND_BYTES = 250;

    private static RespReader reader(String wire) {
        return reader(wire.getBytes(StandardCharsets.UTF_8));
    }

    private static RespReader reader(byte[] wire) {
        return new RespReader(new ByteArrayInputStream(wire), MAX_ARGS, MAX_VALUE_BYTES, MAX_LINE_BYTES, MAX_COMMAND_BYTES);
    }

    private static List<String> words(List<byte[]> parts) {
        return parts.stream().map(p -> new String(p, StandardCharsets.UTF_8)).toList();
    }

    @Test
    void readsAnArrayCommand() throws IOException {
        RespReader r = reader("*2\r\n$3\r\nGET\r\n$10\r\nproduct:42\r\n");

        assertEquals(List.of("GET", "product:42"), words(r.readCommand()));
        assertNull(r.readCommand());
    }

    @Test
    void readsSeveralCommandsInARow() throws IOException {
        RespReader r = reader("*1\r\n$4\r\nPING\r\n*2\r\n$3\r\nGET\r\n$1\r\na\r\n");

        assertEquals(List.of("PING"), words(r.readCommand()));
        assertEquals(List.of("GET", "a"), words(r.readCommand()));
    }

    @Test
    void valuesMayContainNewlinesAndZeroBytes() throws IOException {
        byte[] value = {'a', '\r', '\n', 0, 'b'};
        byte[] head = "*3\r\n$3\r\nSET\r\n$1\r\nk\r\n$5\r\n".getBytes(StandardCharsets.US_ASCII);
        byte[] wire = new byte[head.length + value.length + 2];
        System.arraycopy(head, 0, wire, 0, head.length);
        System.arraycopy(value, 0, wire, head.length, value.length);
        wire[wire.length - 2] = '\r';
        wire[wire.length - 1] = '\n';

        assertArrayEquals(value, reader(wire).readCommand().get(2));
    }

    @Test
    void readsInlineCommandsTypedInNc() throws IOException {
        RespReader r = reader("\nset  k   v\nGET k\r\n");

        assertEquals(List.of("set", "k", "v"), words(r.readCommand()));
        assertEquals(List.of("GET", "k"), words(r.readCommand()));
    }

    @Test
    void hugeDeclaredLengthIsRejectedBeforeAllocating() {
        ProtocolException e = assertThrows(ProtocolException.class,
                () -> reader("*2\r\n$3\r\nGET\r\n$2000000000\r\n").readCommand());

        assertEquals("bulk length must be 0 to " + MAX_VALUE_BYTES + ", got 2000000000", e.getMessage());
    }

    @Test
    void commandWhosePartsAddUpPastTheLimitIsRejected() {
        String part = "$100\r\n" + "x".repeat(100) + "\r\n";

        ProtocolException e = assertThrows(ProtocolException.class,
                () -> reader("*3\r\n" + part + part + part).readCommand());

        assertEquals("command longer than " + MAX_COMMAND_BYTES + " bytes", e.getMessage());
    }

    @Test
    void partCountOutsideLimitsIsRejected() {
        assertThrows(ProtocolException.class, () -> reader("*" + (MAX_ARGS + 1) + "\r\n").readCommand());
        assertThrows(ProtocolException.class, () -> reader("*0\r\n").readCommand());
    }

    @Test
    void lengthThatIsNotANumberIsRejected() {
        assertThrows(ProtocolException.class, () -> reader("*x\r\n").readCommand());
        assertThrows(ProtocolException.class, () -> reader("*1\r\n$99999999999\r\n").readCommand());
    }

    @Test
    void valueLongerThanDeclaredIsRejected() {
        assertThrows(ProtocolException.class, () -> reader("*1\r\n$2\r\nPING\r\n").readCommand());
    }

    @Test
    void missingDollarIsRejected() {
        assertThrows(ProtocolException.class, () -> reader("*1\r\n:4\r\n").readCommand());
    }

    @Test
    void lineLongerThanLimitIsRejected() {
        assertThrows(ProtocolException.class, () -> reader("*" + "1".repeat(MAX_LINE_BYTES + 1)).readCommand());
    }

    @Test
    void connectionClosedMidCommandIsEof() {
        assertThrows(EOFException.class, () -> reader("*2\r\n$3\r\nGET\r\n$5\r\nab").readCommand());
    }
}
