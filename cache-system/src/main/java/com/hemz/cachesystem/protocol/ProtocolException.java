package com.hemz.cachesystem.protocol;

/** The client sent bytes that break the protocol. The server replies with an error and closes the connection. */
public class ProtocolException extends RuntimeException {

    public ProtocolException(String message) {
        super(message);
    }
}
