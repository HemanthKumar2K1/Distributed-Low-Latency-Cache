package com.hemz.cachesystem.protocol;

/**
 * What the server sends back. One type per RESP marker:
 *   Simple  +OK\r\n
 *   Err     -ERR unknown command\r\n
 *   Int     :1\r\n
 *   Bulk    $5\r\nhello\r\n      (null value = $-1\r\n, "not found")
 */
public sealed interface Reply {

    record Simple(String text) implements Reply {
    }

    /** message starts with an error code, like "ERR ...". */
    record Err(String message) implements Reply {
    }

    record Int(long value) implements Reply {
    }

    record Bulk(byte[] value) implements Reply {
    }

    Reply OK = new Simple("OK");
    Reply NIL = new Bulk(null);

    static Reply error(String message) {
        return new Err("ERR " + message);
    }
}
