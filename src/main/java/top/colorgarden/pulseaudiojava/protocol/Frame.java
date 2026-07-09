package top.colorgarden.pulseaudiojava.protocol;

/**
 * A PulseAudio pstream frame.
 *
 * Frame header is 20 bytes (5 × uint32 big-endian):
 *   [0..3]  length     — payload size in bytes
 *   [4..7]  channel    — 0xFFFFFFFF (-1) for control, else stream channel
 *   [8..11] offset_hi  — seek/write offset high 32 bits
 *   [12..15] offset_lo — seek/write offset low 32 bits
 *   [16..19] flags     — lower 8 bits = seek mode, upper = SHM flags
 */
public class Frame {
    public static final int HEADER_SIZE = 20;

    public final int length;     // payload length
    public final int channel;    // channel field (0xFFFFFFFF = control)
    public final long offset;    // seek/write offset (combined from hi+lo)
    public final int flags;      // seek + SHM flags
    public final byte[] payload; // raw payload bytes

    public Frame(int length, int channel, long offset, int flags, byte[] payload) {
        this.length = length;
        this.channel = channel;
        this.offset = offset;
        this.flags = flags;
        this.payload = payload;
    }

    /**
     * Creates a control frame (channel = -1 / 0xFFFFFFFF) with the given payload.
     */
    public static Frame control(int length, byte[] payload) {
        return new Frame(length, -1, 0, 0, payload);
    }

    /**
     * Creates a memblock data frame with the given channel, offset, and PCM payload.
     */
    public static Frame memblock(int channel, long writeOffset, int seekMode, byte[] payload) {
        return new Frame(payload.length, channel, writeOffset,
                seekMode & ProtocolConstants.FLAG_SEEKMASK, payload);
    }

    /** @return true if this is a control packet (channel == 0xFFFFFFFF) */
    public boolean isControl() {
        return channel == -1;  // 0xFFFFFFFF
    }

    /** @return true if this is a memblock (audio data) frame */
    public boolean isMemblock() {
        return channel != -1 && (flags & ProtocolConstants.FLAG_SHMMASK) == 0;
    }

    /** @return true if this is an SHM release frame */
    public boolean isShmRelease() {
        return flags == ProtocolConstants.FLAG_SHMRELEASE;
    }

    /** @return true if this is an SHM revoke frame */
    public boolean isShmRevoke() {
        return flags == ProtocolConstants.FLAG_SHMREVOKE;
    }

    @Override
    public String toString() {
        return String.format("Frame[len=%d, chan=0x%08X, off=%d, flags=0x%08X, payload=%d bytes]",
                length, channel, offset, flags, payload != null ? payload.length : 0);
    }
}
