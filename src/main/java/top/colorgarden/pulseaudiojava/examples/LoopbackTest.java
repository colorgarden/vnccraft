package top.colorgarden.pulseaudiojava.examples;

import top.colorgarden.pulseaudiojava.PulseAudioClient;
import top.colorgarden.pulseaudiojava.audio.ChannelMap;
import top.colorgarden.pulseaudiojava.audio.SampleSpec;
import top.colorgarden.pulseaudiojava.playback.AudioPlayer;
import top.colorgarden.pulseaudiojava.stream.PlaybackStream;
import top.colorgarden.pulseaudiojava.stream.RecordStream;

/**
 * Full loopback test: generates a sine wave, sends it to the PulseAudio server
 * via a playback stream, captures it back via a monitor record stream, and
 * plays it locally on Windows.
 *
 * This tests the complete data path without needing real audio hardware on the server.
 */
public class LoopbackTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== PulseAudioJava Loopback Test ===");

        // 1. Connect
        PulseAudioClient client = PulseAudioClient.create();
        client.handshake();
        System.out.println("Connected! Server protocol: " + client.getServerProtocolVersion());

        // 2. Audio format: CD quality
        SampleSpec spec = SampleSpec.cdQuality();  // S16LE, 44100Hz, 2ch
        ChannelMap map = ChannelMap.stereo();

        // 3. Create playback stream to send audio to server
        PlaybackStream playback = client.createPlaybackStream("ToneGen", spec, map);
        System.out.println("Playback stream: " + playback.getContext());

        // 4. Create record stream to capture the monitor
        RecordStream record = client.createRecordStream(
                "@DEFAULT_MONITOR@", "Capture", spec, map);
        System.out.println("Record stream: " + record.getContext());

        // 5. Local audio player
        final AudioPlayer player = new AudioPlayer(spec);
        player.start();
        record.setReadCallback((data, off, len) -> player.write(data, off, len));

        // 6. Generate a 440Hz sine wave and send it
        byte[] tone = generateTone(spec, 440.0, 2000); // 2 seconds of 440Hz
        System.out.println("Sending " + tone.length + " bytes of tone...");
        playback.write(tone);

        // Uncork both streams
        client.uncorkPlaybackStream(playback);
        client.uncorkRecordStream(record);

        System.out.println("Playing 440Hz tone for 3 seconds...");
        Thread.sleep(3000);

        // Stop
        player.stop();
        client.deletePlaybackStream(playback);
        client.deleteRecordStream(record);
        client.close();
        System.out.println("Done.");
    }

    /** Generate a PCM sine wave */
    private static byte[] generateTone(SampleSpec spec, double freqHz, int durationMs) {
        int sampleRate = spec.rate;
        int channels = spec.channels;
        int bytesPerSample = spec.format.bytesPerSample;
        int numSamples = (int) ((long) sampleRate * durationMs / 1000);
        int totalBytes = numSamples * bytesPerSample * channels;
        byte[] buf = new byte[totalBytes];

        double amplitude = 0.7; // avoid clipping
        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / sampleRate;
            short sample = (short) (amplitude * Short.MAX_VALUE * Math.sin(2.0 * Math.PI * freqHz * t));
            for (int ch = 0; ch < channels; ch++) {
                int offset = (i * channels + ch) * bytesPerSample;
                buf[offset] = (byte) (sample & 0xFF);
                buf[offset + 1] = (byte) ((sample >> 8) & 0xFF);
            }
        }
        return buf;
    }
}
