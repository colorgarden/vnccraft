package top.colorgarden.pulseaudiojava.stream;

import top.colorgarden.pulseaudiojava.PulseAudioClient;
import top.colorgarden.pulseaudiojava.audio.SampleSpec;
import top.colorgarden.pulseaudiojava.protocol.Frame;

/**
 * A PulseAudio record stream — receives audio data from the server.
 *
 * When the server sends audio, handleDataFrame() is called by the client's
 * I/O thread, which invokes the registered ReadCallback with PCM data.
 */
public class RecordStream {
    private final PulseAudioClient client;
    private final StreamContext context;
    private volatile ReadCallback readCallback;
    private volatile boolean running;

    public RecordStream(PulseAudioClient client, StreamContext context) {
        this.client = client;
        this.context = context;
        this.running = true;
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
     * Set the callback for incoming audio data.
     * Called from the I/O thread — callbacks should be fast.
     */
    public void setReadCallback(ReadCallback callback) {
        this.readCallback = callback;
    }

    /**
     * Called by PulseAudioClient's I/O thread when audio data arrives
     * on this stream's data channel.
     */
    private long totalBytes;
    private long lastReport;
    private boolean firstNonZero;

    public void handleDataFrame(Frame frame) {
        if (!running || readCallback == null || frame.payload == null) return;
        if (!frame.isMemblock()) return;

        boolean dbg = PulseAudioClient.DEBUG;
        if (dbg) {
            totalBytes += frame.payload.length;
            long now = System.currentTimeMillis();
            if (!firstNonZero && frame.payload.length >= 32) {
                boolean nz = false;
                for (int i = 0; i < Math.min(frame.payload.length, 200); i++)
                    if (frame.payload[i] != 0) { nz = true; break; }
                if (nz) {
                    firstNonZero = true;
                    byte[] p = frame.payload;
                    System.out.printf("[RecordStream] NON-ZERO frame at %dKB: len=%d flags=0x%08X%n",
                            totalBytes / 1024, p.length, frame.flags);
                    int cnt = 0;
                    for (byte b : p) if (b != 0) cnt++;
                    System.out.printf("  nonZero=%d/%d%n", cnt, p.length);
                    System.out.print("  first 32: ");
                    for (int i = 0; i < 32 && i < p.length; i++)
                        System.out.printf("%02X ", p[i] & 0xFF);
                    System.out.println();
                }
            }
            if (now - lastReport > 2000) {
                System.out.printf("[RecordStream] %d KB total%n", totalBytes / 1024);
                lastReport = now;
            }
        }
        readCallback.onAudioData(frame.payload, 0, frame.payload.length);
    }

    /** Called by PulseAudioClient when a REQUEST command arrives for this stream. */
    void handleRequest() {
        // Server wants more audio — for record streams this is informational
    }

    public boolean isRunning() {
        return running;
    }

    public void markClosed() {
        running = false;
    }

    @FunctionalInterface
    public interface ReadCallback {
        void onAudioData(byte[] pcmData, int offset, int length);
    }
}
