package top.colorgarden.pulseaudiojava.protocol;

import top.colorgarden.pulseaudiojava.audio.ChannelMap;
import top.colorgarden.pulseaudiojava.audio.SampleFormat;
import top.colorgarden.pulseaudiojava.audio.SampleSpec;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads/parses PulseAudio TagStruct binary payloads.
 *
 * Each field begins with a 1-byte tag marker. This reader expects the caller
 * to know what fields to expect (the protocol is strongly-typed per command).
 * Based on the verified format from tagstruct.h / tagstruct.c.
 */
public class TagStructReader implements AutoCloseable {
    private final ByteBuffer buf;

    public TagStructReader(byte[] payload) {
        this.buf = ByteBuffer.wrap(payload);
    }

    /** @return the next tag byte without consuming it */
    public byte peekTag() {
        if (!buf.hasRemaining()) return ProtocolConstants.TAG_INVALID;
        return buf.get(buf.position());
    }

    /** @return true if there's more data to read */
    public boolean hasMore() {
        return buf.hasRemaining();
    }

    /**
     * Read a tag byte, throwing if it doesn't match expectedTag.
     */
    private byte expectTag(byte expectedTag) {
        byte actual = buf.get();
        if (actual != expectedTag) {
            throw new ProtocolException(
                    String.format("Expected tag 0x%02X ('%c'), got 0x%02X ('%c')",
                            expectedTag, (char) expectedTag, actual, (char) actual));
        }
        return actual;
    }

    // ---- Primitive types ----

    @SuppressWarnings("unchecked")
    public <T> T read(byte tag, Class<T> type) {
        if (tag == ProtocolConstants.TAG_U32) return (T) Long.valueOf(readU32());
        if (tag == ProtocolConstants.TAG_U8) return (T) Integer.valueOf(readU8());
        if (tag == ProtocolConstants.TAG_U64) return (T) Long.valueOf(readU64());
        if (tag == ProtocolConstants.TAG_S64) return (T) Long.valueOf(readS64());
        if (tag == ProtocolConstants.TAG_STRING) return (T) readString();
        if (tag == ProtocolConstants.TAG_BOOLEAN_TRUE || tag == ProtocolConstants.TAG_BOOLEAN_FALSE)
            return (T) Boolean.valueOf(readBoolean());
        if (tag == ProtocolConstants.TAG_SAMPLE_SPEC) return (T) readSampleSpec();
        if (tag == ProtocolConstants.TAG_CHANNEL_MAP) return (T) readChannelMap();
        if (tag == ProtocolConstants.TAG_ARBITRARY) return (T) readArbitrary();
        throw new ProtocolException("Unsupported tag type: 0x" + Integer.toHexString(tag));
    }

    public int readU8() {
        expectTag(ProtocolConstants.TAG_U8);
        return buf.get() & 0xFF;
    }

    public long readU32() {
        expectTag(ProtocolConstants.TAG_U32);
        return readU32BE();
    }

    public long readU64() {
        expectTag(ProtocolConstants.TAG_U64);
        return readU64BE();
    }

    public long readS64() {
        expectTag(ProtocolConstants.TAG_S64);
        return readU64BE();  // same wire representation
    }

    public boolean readBoolean() {
        byte tag = buf.get();
        if (tag == ProtocolConstants.TAG_BOOLEAN_TRUE) return true;
        if (tag == ProtocolConstants.TAG_BOOLEAN_FALSE) return false;
        throw new ProtocolException("Expected boolean tag, got 0x" + Integer.toHexString(tag));
    }

    // ---- String types ----

    public String readString() {
        expectTag(ProtocolConstants.TAG_STRING);
        StringBuilder sb = new StringBuilder();
        byte b;
        while (buf.hasRemaining() && (b = buf.get()) != 0) {
            sb.append((char) (b & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Read a string or string_null tag. Returns null if TAG_STRING_NULL.
     */
    public String readStringOrNull() {
        byte tag = peekTag();
        if (tag == ProtocolConstants.TAG_STRING_NULL) {
            buf.get(); // consume tag
            return null;
        }
        return readString();
    }

    // ---- Binary data ----

    public byte[] readArbitrary() {
        expectTag(ProtocolConstants.TAG_ARBITRARY);
        int len = (int) readU32BE();
        byte[] data = new byte[len];
        buf.get(data);
        return data;
    }

    /**
     * Read arbitrary data with an expected length. Throws if length mismatch.
     */
    public byte[] readArbitrary(int expectedLen) {
        expectTag(ProtocolConstants.TAG_ARBITRARY);
        int len = (int) readU32BE();
        if (len != expectedLen) {
            throw new ProtocolException(
                    "Expected arbitrary data of " + expectedLen + " bytes, got " + len);
        }
        byte[] data = new byte[len];
        buf.get(data);
        return data;
    }

    // ---- Audio types ----

    public SampleSpec readSampleSpec() {
        expectTag(ProtocolConstants.TAG_SAMPLE_SPEC);
        int formatCode = buf.get() & 0xFF;
        int channels = buf.get() & 0xFF;
        int rate = (int) readU32BE();
        return new SampleSpec(SampleFormat.fromPaCode(formatCode), channels, rate);
    }

    public ChannelMap readChannelMap() {
        expectTag(ProtocolConstants.TAG_CHANNEL_MAP);
        int count = buf.get() & 0xFF;
        int[] positions = new int[count];
        for (int i = 0; i < count; i++) {
            positions[i] = buf.get() & 0xFF;
        }
        return new ChannelMap(positions);
    }

    /**
     * Read a cvolume. Returns array of raw PA volume values.
     * PA_VOLUME_NORM = 0x10000 (65536).
     */
    public long[] readCVolume() {
        expectTag(ProtocolConstants.TAG_CVOLUME);
        int channels = buf.get() & 0xFF;
        long[] volumes = new long[channels];
        for (int i = 0; i < channels; i++) {
            volumes[i] = readU32BE();
        }
        return volumes;
    }

    public long readVolume() {
        expectTag(ProtocolConstants.TAG_VOLUME);
        return readU32BE();
    }

    // ---- Time types ----

    public long readUsec() {
        expectTag(ProtocolConstants.TAG_USEC);
        return readU64BE();
    }

    public long[] readTimeval() {
        expectTag(ProtocolConstants.TAG_TIMEVAL);
        return new long[]{readU64BE(), readU64BE()}; // [sec, usec]
    }

    // ---- Property list ----

    /**
     * Read a proplist. Returns a map of String key -> byte[] value.
     */
    public Map<String, byte[]> readPropList() {
        expectTag(ProtocolConstants.TAG_PROPLIST);
        Map<String, byte[]> props = new HashMap<>();
        while (buf.hasRemaining()) {
            // Read null-terminated key
            StringBuilder keyBuilder = new StringBuilder();
            byte b;
            boolean hasKey = false;
            while (buf.hasRemaining() && (b = buf.get()) != 0) {
                keyBuilder.append((char) (b & 0xFF));
                hasKey = true;
            }
            if (!hasKey || keyBuilder.length() == 0) {
                break; // end of proplist
            }
            String key = keyBuilder.toString();

            // Read value tag
            if (!buf.hasRemaining()) break;
            byte valueTag = buf.get();
            if (valueTag == ProtocolConstants.TAG_STRING_NULL) {
                props.put(key, new byte[0]);
            } else if (valueTag == ProtocolConstants.TAG_ARBITRARY) {
                int len = (int) readU32BE();
                byte[] value = new byte[len];
                buf.get(value);
                props.put(key, value);
            } else if (valueTag == ProtocolConstants.TAG_STRING) {
                // value is actually a string
                StringBuilder valBuilder = new StringBuilder();
                while (buf.hasRemaining() && (b = buf.get()) != 0) {
                    valBuilder.append((char) (b & 0xFF));
                }
                props.put(key, valBuilder.toString().getBytes(StandardCharsets.UTF_8));
            } else {
                throw new ProtocolException(
                        "Unexpected proplist value tag: 0x" + Integer.toHexString(valueTag));
            }
        }
        return props;
    }

    // ---- Format info ----

    public int readFormatInfo() {
        expectTag(ProtocolConstants.TAG_FORMAT_INFO);
        return buf.get() & 0xFF; // encoding
        // remaining arbitrary data is ignored by caller
    }

    // ---- Low-level big-endian readers ----

    private long readU32BE() {
        return (buf.get() & 0xFFL) << 24
                | (buf.get() & 0xFFL) << 16
                | (buf.get() & 0xFFL) << 8
                | (buf.get() & 0xFFL);
    }

    private long readU64BE() {
        return (buf.get() & 0xFFL) << 56
                | (buf.get() & 0xFFL) << 48
                | (buf.get() & 0xFFL) << 40
                | (buf.get() & 0xFFL) << 32
                | (buf.get() & 0xFFL) << 24
                | (buf.get() & 0xFFL) << 16
                | (buf.get() & 0xFFL) << 8
                | (buf.get() & 0xFFL);
    }

    @Override
    public void close() {
        // no-op
    }

    /**
     * Exception for protocol parsing errors.
     */
    public static class ProtocolException extends RuntimeException {
        public ProtocolException(String message) {
            super(message);
        }
        public ProtocolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
