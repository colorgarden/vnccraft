package top.colorgarden.pulseaudiojava.transport;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;

/**
 * Unix Domain Socket transport for PulseAudio.
 * Uses JDK 16+ java.net.UnixDomainSocketAddress — pure Java, no native code.
 *
 * Default PulseAudio socket path: /run/user/$UID/pulse/native
 * or $XDG_RUNTIME_DIR/pulse/native
 */
public class UnixSocketTransport implements Transport {
    private final String socketPath;
    private SocketChannel channel;
    private InputStream inputStream;
    private OutputStream outputStream;

    public UnixSocketTransport(String socketPath) {
        this.socketPath = socketPath;
    }

    public UnixSocketTransport(Path socketPath) {
        this.socketPath = socketPath.toString();
    }

    @Override
    public void connect() throws IOException {
        Path path = Path.of(socketPath);
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(path);
        channel = SocketChannel.open(address);
        channel.configureBlocking(true);

        // Wrap channel streams
        inputStream = Channels.newInputStream(channel);
        outputStream = Channels.newOutputStream(channel);
    }

    @Override
    public InputStream getInputStream() {
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
        return outputStream;
    }

    @Override
    public boolean isConnected() {
        return channel != null && channel.isConnected();
    }

    @Override
    public void close() throws IOException {
        if (channel != null) {
            channel.close();
            channel = null;
        }
    }

    @Override
    public String toString() {
        return "UnixSocket[" + socketPath + "]";
    }
}
