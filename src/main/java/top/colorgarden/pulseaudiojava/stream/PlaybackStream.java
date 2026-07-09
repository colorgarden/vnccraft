package top.colorgarden.pulseaudiojava.stream;

import top.colorgarden.pulseaudiojava.PulseAudioClient;
import top.colorgarden.pulseaudiojava.audio.SampleSpec;
import top.colorgarden.pulseaudiojava.protocol.ProtocolConstants;

import java.io.IOException;

/**
 * A PulseAudio playback stream — sends audio data to the server for output.
 */
public class PlaybackStream {
    private final PulseAudioClient client;
    private final StreamContext context;
    private volatile boolean running;
    private long writeIndex;  // total bytes written

    public PlaybackStream(PulseAudioClient client, StreamContext context) {
        this.client = client;
        this.context = context;
        this.running = true;
        this.writeIndex = 0;
    }

    public StreamContext getContext() {
        return context;
    }

    public int getStreamIndex() {
        return context.streamIndex;
    }

    public int getChannel() {
        return context.channel;
    }

    public SampleSpec getSampleSpec() {
        return context.sampleSpec;
    }

    /**
     * Write PCM audio data to the playback stream.
     * Sends a memblock frame on this stream's data channel.
     */
    public void write(byte[] pcmData, int offset, int length) throws IOException {
        if (!running) return;
        if (length == 0) return;

        client.getFrameWriter().writeMemblock(
                context.channel,
                writeIndex,
                ProtocolConstants.SEEK_RELATIVE,
                pcmData, offset, length);

        writeIndex += length;
    }

    /**
     * Write PCM data using the entire array.
     */
    public void write(byte[] pcmData) throws IOException {
        write(pcmData, 0, pcmData.length);
    }

    public long getWriteIndex() {
        return writeIndex;
    }

    /** Called by PulseAudioClient when server requests more data */
    public void handleRequest() {
        // For playback, server REQUEST means "send more audio"
    }

    public boolean isRunning() {
        return running;
    }

    public void markClosed() {
        running = false;
    }
}
