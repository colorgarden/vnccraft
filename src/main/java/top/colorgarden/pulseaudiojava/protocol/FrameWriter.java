package top.colorgarden.pulseaudiojava.protocol;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Writes PulseAudio pstream frames to an OutputStream.
 *
 * Frame header is 20 bytes (5 × uint32 big-endian):
 *   [0..3]  length     — payload size
 *   [4..7]  channel    — 0xFFFFFFFF for control, else stream channel
 *   [8..11] offset_hi  — offset high 32 bits
 *   [12..15] offset_lo — offset low 32 bits
 *   [16..19] flags     — seek mode and SHM flags
 *
 * All writes are synchronized to prevent interleaving of frames
 * from different threads. Based on pstream.c do_write().
 */
public class FrameWriter {
    private final DataOutputStream out;
    private final ReentrantLock lock = new ReentrantLock();

    public FrameWriter(OutputStream outputStream) {
        this.out = new DataOutputStream(outputStream);
    }

    /**
     * Write a complete frame (header + payload) with the lock held.
     */
    public void writeFrame(Frame frame) throws IOException {
        lock.lock();
        try {
            // Write 20-byte header, all big-endian
            out.writeInt(frame.length);                     // [0..3] length
            out.writeInt(frame.channel);                    // [4..7] channel
            out.writeInt((int) (frame.offset >>> 32));      // [8..11] offset_hi
            out.writeInt((int) (frame.offset & 0xFFFFFFFFL)); // [12..15] offset_lo
            out.writeInt(frame.flags);                      // [16..19] flags

            // Write payload if present
            if (frame.payload != null && frame.length > 0) {
                out.write(frame.payload, 0, frame.length);
            }
            out.flush();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Write a control packet frame (channel = 0xFFFFFFFF, flags = 0).
     */
    public void writeControlPacket(byte[] payload) throws IOException {
        writeFrame(Frame.control(payload.length, payload));
    }

    /**
     * Write a memblock data frame for audio data.
     * Channel is the stream channel, offset is the write index, seekMode is PA_SEEK_*.
     */
    public void writeMemblock(int channel, long writeOffset, int seekMode, byte[] pcmData, int off, int len) throws IOException {
        byte[] payload;
        if (off == 0 && len == pcmData.length) {
            payload = pcmData;
        } else {
            payload = new byte[len];
            System.arraycopy(pcmData, off, payload, 0, len);
        }
        Frame frame = Frame.memblock(channel, writeOffset, seekMode, payload);
        writeFrame(frame);
    }

    public void flush() throws IOException {
        lock.lock();
        try {
            out.flush();
        } finally {
            lock.unlock();
        }
    }

    public void close() throws IOException {
        lock.lock();
        try {
            out.close();
        } finally {
            lock.unlock();
        }
    }
}
