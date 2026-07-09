package top.colorgarden.pulseaudiojava.protocol;

import top.colorgarden.pulseaudiojava.audio.ChannelMap;
import top.colorgarden.pulseaudiojava.audio.SampleSpec;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Writes PulseAudio TagStruct binary payloads.
 *
 * Each field is written as a 1-byte tag marker followed by the marshalled data.
 * Based on the verified format from tagstruct.h / tagstruct.c.
 *
 * WARNING from source: "proplists may only be at the END of a packet
 * or not before a STRING!" — always write proplists last.
 */
public class TagStructWriter implements AutoCloseable {
    private final ByteArrayOutputStream buf = new ByteArrayOutputStream();

    public TagStructWriter() {}

    /**
     * Write the standard command preamble: command(U32) + tag(U32).
     * Order verified from PulseAudio source: pa_tagstruct_command() in context.c
     * writes pa_tagstruct_putu32(t, command) THEN pa_tagstruct_putu32(t, tag).
     */
    public TagStructWriter writeCommandPreamble(int command, int tag) {
        writeU32(command);
        writeU32(tag);
        return this;
    }

    // ---- Primitive types ----

    public TagStructWriter writeU8(int value) {
        buf.write(ProtocolConstants.TAG_U8);
        buf.write(value & 0xFF);
        return this;
    }

    public TagStructWriter writeU32(long value) {
        buf.write(ProtocolConstants.TAG_U32);
        writeU32BE(value);
        return this;
    }

    public TagStructWriter writeU64(long value) {
        buf.write(ProtocolConstants.TAG_U64);
        writeU64BE(value);
        return this;
    }

    public TagStructWriter writeS64(long value) {
        buf.write(ProtocolConstants.TAG_S64);
        writeU64BE(value);  // same wire representation
        return this;
    }

    public TagStructWriter writeBoolean(boolean value) {
        if (value) {
            buf.write(ProtocolConstants.TAG_BOOLEAN_TRUE);
            // no data for true
        } else {
            buf.write(ProtocolConstants.TAG_BOOLEAN_FALSE);
            // no data for false
        }
        return this;
    }

    // ---- String types ----

    public TagStructWriter writeString(String value) {
        buf.write(ProtocolConstants.TAG_STRING);
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        try {
            buf.write(bytes);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        buf.write(0); // null terminator
        return this;
    }

    public TagStructWriter writeStringNull() {
        buf.write(ProtocolConstants.TAG_STRING_NULL);
        // No data
        return this;
    }

    // ---- Binary data ----

    public TagStructWriter writeArbitrary(byte[] data) {
        buf.write(ProtocolConstants.TAG_ARBITRARY);
        writeU32BE(data.length);
        try {
            buf.write(data);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    // ---- Audio types ----

    public TagStructWriter writeSampleSpec(SampleSpec spec) {
        buf.write(ProtocolConstants.TAG_SAMPLE_SPEC);
        buf.write(spec.format.paCode);   // 1 byte format
        buf.write(spec.channels);        // 1 byte channels
        writeU32BE(spec.rate);           // 4 bytes big-endian
        return this;
    }

    public TagStructWriter writeChannelMap(ChannelMap map) {
        buf.write(ProtocolConstants.TAG_CHANNEL_MAP);
        buf.write(map.channelCount());   // 1 byte count
        for (int pos : map.positions) {
            buf.write(pos);              // 1 byte per position
        }
        return this;
    }

    /**
     * Write per-channel volume. Format: channels(1B) + volume(4B BE per channel).
     * Volume values are PA volume (0 to PA_VOLUME_NORM = 0x10000).
     */
    public TagStructWriter writeCVolume(int channels, long[] volumes) {
        buf.write(ProtocolConstants.TAG_CVOLUME);
        buf.write(channels);
        for (int i = 0; i < channels; i++) {
            writeU32BE(i < volumes.length ? volumes[i] : 0x10000L);
        }
        return this;
    }

    /** Write default volume (100% per channel) */
    public TagStructWriter writeCVolumeDefault(int channels) {
        buf.write(ProtocolConstants.TAG_CVOLUME);
        buf.write(channels);
        for (int i = 0; i < channels; i++) {
            writeU32BE(0x10000L);  // PA_VOLUME_NORM
        }
        return this;
    }

    public TagStructWriter writeVolume(long volume) {
        buf.write(ProtocolConstants.TAG_VOLUME);
        writeU32BE(volume);
        return this;
    }

    // ---- Time types ----

    public TagStructWriter writeUsec(long usec) {
        buf.write(ProtocolConstants.TAG_USEC);
        writeU64BE(usec);
        return this;
    }

    public TagStructWriter writeTimeval(long sec, long usec) {
        buf.write(ProtocolConstants.TAG_TIMEVAL);
        writeU64BE(sec);
        writeU64BE(usec);
        return this;
    }

    // ---- Property list (must be LAST in packet!) ----

    /**
     * Write a property list. MUST be the last field in the packet!
     *
     * Wire format verified against pa_tagstruct_put/get_proplist in tagstruct.c:
     *   'P'  TAG_PROPLIST
     *   For each entry:
     *     't' key\0       — TAG_STRING key
     *     'L' value_len   — TAG_U32 value byte length
     *     'x' len data    — TAG_ARBITRARY value
     *   'N'               — TAG_STRING_NULL marks end
     */
    public TagStructWriter writePropList(Map<String, byte[]> props) {
        buf.write(ProtocolConstants.TAG_PROPLIST);
        for (Map.Entry<String, byte[]> e : props.entrySet()) {
            // Write key as tagged string: 't' + key + \0
            buf.write(ProtocolConstants.TAG_STRING);
            byte[] keyBytes = e.getKey().getBytes(StandardCharsets.UTF_8);
            try {
                buf.write(keyBytes);
                buf.write(0); // null terminator
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            // Write value length as tagged U32: 'L' + 4 bytes BE
            byte[] value = e.getValue();
            int len = (value != null) ? value.length : 0;
            buf.write(ProtocolConstants.TAG_U32);
            writeU32BE(len);

            // Write value as tagged arbitrary: 'x' + 4B len(BE) + data
            buf.write(ProtocolConstants.TAG_ARBITRARY);
            writeU32BE(len);
            if (len > 0) {
                try {
                    buf.write(value);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        // End of proplist: TAG_STRING_NULL
        buf.write(ProtocolConstants.TAG_STRING_NULL);
        return this;
    }

    // ---- Format info ----

    public TagStructWriter writeFormatInfo(int encoding, byte[] plistPayload) {
        buf.write(ProtocolConstants.TAG_FORMAT_INFO);
        buf.write(encoding);
        writeArbitrary(plistPayload);
        return this;
    }

    // ---- Utility ----

    /**
     * Write raw bytes directly into the output (no tag prefix).
     * Useful for embedding pre-serialized data (e.g., proplist sub-payloads).
     */
    public TagStructWriter writeRawBytes(byte[] data) {
        try {
            buf.write(data);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return this;
    }

    /** @return the current buffer size */
    public int size() {
        return buf.size();
    }

    /** @return the complete serialized payload as bytes */
    public byte[] toByteArray() {
        return buf.toByteArray();
    }

    @Override
    public void close() {
        // no-op; satisfies AutoCloseable
    }

    // ---- Internal big-endian writers ----

    private void writeU32BE(long v) {
        buf.write((int) (v >>> 24) & 0xFF);
        buf.write((int) (v >>> 16) & 0xFF);
        buf.write((int) (v >>> 8) & 0xFF);
        buf.write((int) (v) & 0xFF);
    }

    private void writeU64BE(long v) {
        buf.write((int) (v >>> 56) & 0xFF);
        buf.write((int) (v >>> 48) & 0xFF);
        buf.write((int) (v >>> 40) & 0xFF);
        buf.write((int) (v >>> 32) & 0xFF);
        buf.write((int) (v >>> 24) & 0xFF);
        buf.write((int) (v >>> 16) & 0xFF);
        buf.write((int) (v >>> 8) & 0xFF);
        buf.write((int) (v) & 0xFF);
    }
}
