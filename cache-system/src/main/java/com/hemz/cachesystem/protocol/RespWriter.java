package com.hemz.cachesystem.protocol;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Turns a Reply into RESP bytes. Nothing is sent until flush(). */
public final class RespWriter {

    private static final byte[] CRLF = {'\r', '\n'};
    private static final byte[] NIL_BYTES = "$-1\r\n".getBytes(StandardCharsets.US_ASCII);

    private final OutputStream out;

    /** out should be buffered, so one reply becomes one network write. */
    public RespWriter(OutputStream out) {
        this.out = out;
    }

    public void write(Reply reply) throws IOException {
        switch (reply) {
            case Reply.Simple s -> writeLine('+', oneLine(s.text()));
            case Reply.Err e -> writeLine('-', oneLine(e.message()));
            case Reply.Int i -> writeLine(':', Long.toString(i.value()));
            case Reply.Bulk b when b.value() == null -> out.write(NIL_BYTES);
            case Reply.Bulk b -> {
                writeLine('$', Integer.toString(b.value().length));
                out.write(b.value());
                out.write(CRLF);
            }
        }
    }

    public void flush() throws IOException {
        out.flush();
    }

    private void writeLine(char marker, String text) throws IOException {
        out.write(marker);
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.write(CRLF);
    }

    /** A "\r\n" inside a simple string or error would end the reply early. */
    private static String oneLine(String text) {
        return text.replace('\r', ' ').replace('\n', ' ');
    }
}
