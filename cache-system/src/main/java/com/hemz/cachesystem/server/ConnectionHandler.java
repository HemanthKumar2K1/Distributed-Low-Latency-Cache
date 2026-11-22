package com.hemz.cachesystem.server;

import com.hemz.cachesystem.config.ServerConfig;
import com.hemz.cachesystem.protocol.ProtocolException;
import com.hemz.cachesystem.protocol.Reply;
import com.hemz.cachesystem.protocol.RespReader;
import com.hemz.cachesystem.protocol.RespWriter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * One client connection, on its own virtual thread: read a command, run it, reply, repeat.
 * Blocking reads are fine here: a virtual thread waiting on a socket costs almost nothing.
 */
final class ConnectionHandler implements Runnable {

    private static final System.Logger log = System.getLogger(ConnectionHandler.class.getName());

    /** Read and write buffer per connection: one network read or write moves up to this much. */
    private static final int BUFFER_BYTES = 16 * 1024;

    private final Socket socket;
    private final CommandHandler commands;

    ConnectionHandler(Socket socket, CommandHandler commands) {
        this.socket = socket;
        this.commands = commands;
    }

    @Override
    public void run() {
        try (socket) {
            InputStream in = new BufferedInputStream(socket.getInputStream(), BUFFER_BYTES);
            RespReader reader = new RespReader(in, ServerConfig.MAX_ARGS, ServerConfig.MAX_VALUE_BYTES,
                    ServerConfig.MAX_LINE_BYTES, ServerConfig.MAX_COMMAND_BYTES);
            RespWriter writer = new RespWriter(new BufferedOutputStream(socket.getOutputStream(), BUFFER_BYTES));

            while (true) {
                List<byte[]> command;
                try {
                    command = reader.readCommand();
                } catch (ProtocolException e) {
                    // We no longer know where the next command starts, so the connection cannot continue.
                    writer.write(Reply.error("Protocol error: " + e.getMessage()));
                    writer.flush();
                    return;
                }
                // null: the client closed the connection.
                if (command == null) {
                    return;
                }
                if (isQuit(command)) {
                    writer.write(Reply.OK);
                    writer.flush();
                    return;
                }
                writer.write(commands.execute(command));
                // Pipelining: if more commands are already waiting, reply to them all in one write.
                if (in.available() == 0) {
                    writer.flush();
                }
            }
        } catch (IOException e) {
            log.log(Level.DEBUG, "Connection ended: {0}", e.getMessage());
        }
    }

    /** QUIT is handled here, not in CommandHandler, because it closes this connection. */
    private static boolean isQuit(List<byte[]> command) {
        return command.size() == 1
                && new String(command.getFirst(), StandardCharsets.UTF_8).equalsIgnoreCase("QUIT");
    }
}
