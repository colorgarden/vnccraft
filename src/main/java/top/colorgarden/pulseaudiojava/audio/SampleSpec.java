package top.colorgarden.pulseaudiojava.audio;

/**
 * PulseAudio sample specification: format + rate + channels.
 */
public class SampleSpec {
    public final SampleFormat format;
    public final int rate;
    public final int channels;

    public SampleSpec(SampleFormat format, int channels, int rate) {
        this.format = format;
        this.channels = channels;
        this.rate = rate;
    }

    /** CD-quality stereo: S16LE, 44100Hz, 2 channels */
    public static SampleSpec cdQuality() {
        return new SampleSpec(SampleFormat.S16LE, 2, 44100);
    }

    /** Common PCM: S16LE, variable rate */
    public static SampleSpec s16le(int channels, int rate) {
        return new SampleSpec(SampleFormat.S16LE, channels, rate);
    }

    public int frameSize() {
        return format.bytesPerSample * channels;
    }

    public int bytesPerSecond() {
        return frameSize() * rate;
    }

    @Override
    public String toString() {
        return String.format("%s %dHz %dch", format.name(), rate, channels);
    }
}
