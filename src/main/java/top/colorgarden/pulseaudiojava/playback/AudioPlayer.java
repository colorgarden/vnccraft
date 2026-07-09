package top.colorgarden.pulseaudiojava.playback;

import top.colorgarden.pulseaudiojava.PulseAudioClient;
import top.colorgarden.pulseaudiojava.PulseAudioException;
import top.colorgarden.pulseaudiojava.audio.SampleSpec;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

/**
 * Plays PCM audio data through the local sound system using Java Sound API.
 *
 * Uses a SourceDataLine and a RingBuffer. The I/O thread writes incoming
 * PCM data to the RingBuffer; a playback daemon thread reads from the
 * RingBuffer and writes to the sound card.
 */
public class AudioPlayer implements AutoCloseable {
    private final AudioFormat audioFormat;
    private final int bufferSize;
    private SourceDataLine line;
    private RingBuffer ringBuffer;
    private volatile boolean running;
    private Thread playbackThread;

    public AudioPlayer(SampleSpec spec) {
        this.audioFormat = spec.format.toJavaAudioFormat(spec.rate, spec.channels);
        // Buffer for ~500ms of audio
        this.bufferSize = spec.bytesPerSecond() / 2;
    }

    public AudioPlayer(AudioFormat format, int bufferSize) {
        this.audioFormat = format;
        this.bufferSize = bufferSize;
    }

    /**
     * Open the audio line and start the playback thread.
     */
    public void start() throws PulseAudioException {
        try {
            line = AudioSystem.getSourceDataLine(audioFormat);
            line.open(audioFormat, bufferSize);
        } catch (LineUnavailableException e) {
            throw new PulseAudioException("Failed to open audio line: " + audioFormat, e);
        }

        ringBuffer = new RingBuffer(bufferSize * 4); // 4x for safety margin
        running = true;
        line.start();

        playbackThread = new Thread(this::playbackLoop, "AudioPlayer");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    /**
     * Push PCM data to be played. Non-blocking, may drop data if buffer full.
     */
    public void write(byte[] pcmData, int offset, int length) {
        if (!running || ringBuffer == null) return;
        int wrote = ringBuffer.write(pcmData, offset, length);
        if (wrote < length) {
            // Buffer full — audio glitch, increase buffer size
        }
    }

    /**
     * Push PCM data from the entire array.
     */
    public void write(byte[] pcmData) {
        write(pcmData, 0, pcmData.length);
    }

    /**
     * Playback loop: pulls from ring buffer and writes to SourceDataLine.
     */
    private void playbackLoop() {
        int frameSize = audioFormat.getFrameSize();
        byte[] buf = new byte[Math.max(bufferSize / 4, frameSize * 1024)];
        long lastSyncCheck = 0;

        while (running) {
            try {
                int read = ringBuffer.read(buf, 0, buf.length);
                int aligned = (read / frameSize) * frameSize;
                if (aligned > 0 && line != null) {
                    line.write(buf, 0, aligned);
                }

                // Adaptive sync: if buffer > 75% full, skip 200ms to re-sync
                long now = System.currentTimeMillis();
                if (now - lastSyncCheck > 1000) {
                    lastSyncCheck = now;
                    if (ringBuffer.fillRatio() > 0.75) {
                        int skipBytes = (int) (audioFormat.getFrameRate() * frameSize * 0.2); // 200ms
                        skipBytes = (skipBytes / frameSize) * frameSize;
                        int skipped = ringBuffer.skip(skipBytes);
                        if (PulseAudioClient.DEBUG && skipped > 0) {
                            System.out.printf("[AudioPlayer] Sync: skipped %d bytes (buffer %.0f%% full)%n",
                                    skipped, ringBuffer.fillRatio() * 100);
                        }
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Stop playback and close the audio line.
     */
    public void stop() {
        running = false;
        if (playbackThread != null) {
            try {
                playbackThread.join(1000);
            } catch (InterruptedException ignored) {}
        }
        if (line != null) {
            line.drain();
            line.stop();
            line.close();
            line = null;
        }
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        return running;
    }

    public AudioFormat getAudioFormat() {
        return audioFormat;
    }
}
