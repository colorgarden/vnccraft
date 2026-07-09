package top.colorgarden.pulseaudiojava.transport;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Abstraction over the transport layer (Unix socket or TCP).
 */
public interface Transport extends Closeable {
    void connect() throws IOException;
    InputStream getInputStream() throws IOException;
    OutputStream getOutputStream() throws IOException;
    boolean isConnected();
    void close() throws IOException;
}
