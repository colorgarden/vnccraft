package top.colorgarden.pulseaudiojava.transport;

import top.colorgarden.pulseaudiojava.protocol.ProtocolConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * TCP transport for PulseAudio.
 * Used when connecting to a remote PulseAudio server or on Windows
 * (where Unix sockets are not available).
 *
 * The server must have module-native-protocol-tcp loaded.
 */
public class TcpTransport implements Transport {
    private final String host;
    private final int port;
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    public TcpTransport(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public TcpTransport(String host) {
        this(host, ProtocolConstants.DEFAULT_PORT);
    }

    @Override
    public void connect() throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);  // low latency for audio
        socket.setKeepAlive(true);
        inputStream = socket.getInputStream();
        outputStream = socket.getOutputStream();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return outputStream;
    }

    @Override
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    @Override
    public void close() throws IOException {
        if (socket != null) {
            socket.close();
            socket = null;
        }
    }

    @Override
    public String toString() {
        return "Tcp[" + host + ":" + port + "]";
    }
}
