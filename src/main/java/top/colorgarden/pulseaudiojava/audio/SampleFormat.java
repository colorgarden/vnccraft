package top.colorgarden.pulseaudiojava.audio;

import javax.sound.sampled.AudioFormat;

/**
 * PulseAudio sample formats verified against pulse/sample.h.
 */
public enum SampleFormat {
    U8(0, 1, false),
    ALAW(1, 1, false),
    ULAW(2, 1, false),
    S16LE(3, 2, true),
    S16BE(4, 2, true),
    FLOAT32LE(5, 4, false),
    FLOAT32BE(6, 4, false),
    S32LE(7, 4, true),
    S32BE(8, 4, true),
    S24LE(9, 3, true),
    S24BE(10, 3, true),
    S24_32LE(11, 4, true),
    S24_32BE(12, 4, true);

    public final int paCode;
    public final int bytesPerSample;
    public final boolean signed;

    SampleFormat(int paCode, int bytesPerSample, boolean signed) {
        this.paCode = paCode;
        this.bytesPerSample = bytesPerSample;
        this.signed = signed;
    }

    public static SampleFormat fromPaCode(int code) {
        for (SampleFormat f : values()) {
            if (f.paCode == code) return f;
        }
        throw new IllegalArgumentException("Unknown PA sample format code: " + code);
    }

    public boolean isLittleEndian() {
        switch (this) {
            case S16LE: case FLOAT32LE: case S32LE: case S24LE: case S24_32LE:
                return true;
            case S16BE: case FLOAT32BE: case S32BE: case S24BE: case S24_32BE:
                return false;
            default:
                return true; // U8, ALAW, ULAW don't have endianness
        }
    }

    public boolean isBigEndian() {
        switch (this) {
            case S16BE: case FLOAT32BE: case S32BE: case S24BE: case S24_32BE:
                return true;
            case S16LE: case FLOAT32LE: case S32LE: case S24LE: case S24_32LE:
                return false;
            default:
                return true;
        }
    }

    /**
     * Convert to Java Sound AudioFormat for the given sample rate and channel count.
     */
    public AudioFormat toJavaAudioFormat(float sampleRate, int channels) {
        int sampleSizeBits = bytesPerSample * 8;
        int frameSize = bytesPerSample * channels;

        switch (this) {
            case U8:
                return new AudioFormat(AudioFormat.Encoding.PCM_UNSIGNED,
                        sampleRate, sampleSizeBits, channels, frameSize,
                        sampleRate, false);
            case ALAW:
                return new AudioFormat(AudioFormat.Encoding.ALAW,
                        sampleRate, sampleSizeBits, channels, frameSize,
                        sampleRate, false);
            case ULAW:
                return new AudioFormat(AudioFormat.Encoding.ULAW,
                        sampleRate, sampleSizeBits, channels, frameSize,
                        sampleRate, false);
            case FLOAT32LE:
                return new AudioFormat(AudioFormat.Encoding.PCM_FLOAT,
                        sampleRate, sampleSizeBits, channels, frameSize,
                        sampleRate, false);
            case FLOAT32BE:
                return new AudioFormat(AudioFormat.Encoding.PCM_FLOAT,
                        sampleRate, sampleSizeBits, channels, frameSize,
                        sampleRate, true);
            case S16LE:
                return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        sampleRate, sampleSizeBits, channels, frameSize,
                        sampleRate, false);
            case S16BE:
                return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        sampleRate, sampleSizeBits, channels, frameSize,
                        sampleRate, true);
            case S32LE:
            case S24_32LE:
                return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        sampleRate, 32, channels, 4 * channels,
                        sampleRate, false);
            case S32BE:
            case S24_32BE:
                return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        sampleRate, 32, channels, 4 * channels,
                        sampleRate, true);
            default:
                // S24LE/S24BE packed 24-bit — convert to S16LE as safest fallback
                return new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                        sampleRate, 16, channels, 2 * channels,
                        sampleRate, false);
        }
    }
}
