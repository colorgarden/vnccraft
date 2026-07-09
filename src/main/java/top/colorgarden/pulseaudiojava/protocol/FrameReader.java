package top.colorgarden.pulseaudiojava.protocol;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Reads PulseAudio pstream frames from an InputStream.
 *
 * Blocking reads of the 20-byte header, then reads the payload.
 * Based on pstream.c do_read().
 */
public class FrameReader {
    private final DataInputStream in;

    public FrameReader(InputStream inputStream) {
        this.in = new DataInputStream(inputStream);
    }

    /**
     * Blocking read of a complete frame (header + payload).
     * Returns null on EOF (clean disconnect).
     *
     * @throws IOException on I/O error
     * @throws ProtocolException on invalid frame data
     */
    public Frame readFrame() throws IOException {
        // Read 20-byte header
        int length;
        try {
            length = in.readInt();  // big-endian
        } catch (java.io.EOFException e) {
            return null; // clean disconnect
        }

        int channel = in.readInt();
        int offsetHi = in.readInt();
        int offsetLo = in.readInt();
        int flags = in.readInt();

        long offset = ((long) offsetHi << 32) | (offsetLo & 0xFFFFFFFFL);

        // Validate
        if (length < 0 || length > ProtocolConstants.FRAME_SIZE_MAX_ALLOW) {
            throw new TagStructReader.ProtocolException(
                    "Invalid frame length: " + (length & 0xFFFFFFFFL));
        }

        // Handle frames with no payload (SHM release/revoke, length=0)
        byte[] payload;
        if (length == 0) {
            payload = new byte[0];
        } else {
            payload = new byte[length];
            in.readFully(payload);
        }

        return new Frame(length, channel, offset, flags, payload);
    }

    public void close() throws IOException {
        in.close();
    }
}
