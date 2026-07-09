package top.colorgarden.pulseaudiojava.examples;

import top.colorgarden.pulseaudiojava.PulseAudioClient;
import top.colorgarden.pulseaudiojava.audio.ChannelMap;
import top.colorgarden.pulseaudiojava.audio.SampleSpec;
import top.colorgarden.pulseaudiojava.playback.AudioPlayer;
import top.colorgarden.pulseaudiojava.stream.RecordStream;

/**
 * Example: record system audio output (monitor source) and play it locally.
 *
 * This captures whatever is playing through PulseAudio and outputs it
 * through the local speakers — effectively a "listen to this device" feature.
 *
 * Usage: java -cp build com.pulseaudio.examples.RecordAndPlayExample [source_name]
 *
 * If no source name is given, uses "@DEFAULT_MONITOR@" (the default
 * monitor source, which captures all output audio).
 */
public class RecordAndPlayExample {

    public static void main(String[] args) throws Exception {
        String sourceName = "@DEFAULT_MONITOR@";
        byte[] cookieBytes = null;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--debug":
                    System.setProperty("pulseaudio.debug", "true");
                    break;
                case "--cookie-hex":
                    String hex = args[++i];
                    cookieBytes = new byte[hex.length() / 2];
                    for (int j = 0; j < cookieBytes.length; j++)
                        cookieBytes[j] = (byte) Integer.parseInt(hex.substring(j * 2, j * 2 + 2), 16);
                    break;
                default:
                    sourceName = args[i];
            }
        }

        System.out.println("=== PulseAudio Java Client ===");
        System.out.println("Connecting to PulseAudio server...");

        PulseAudioClient client = PulseAudioClient.create();
        if (cookieBytes != null) {
            client.handshake(cookieBytes);
        } else {
            client.handshake();
        }

        System.out.println("Connected! Server protocol version: "
                + client.getServerProtocolVersion());

        // 2. Set up audio format: CD quality stereo
        SampleSpec spec = SampleSpec.cdQuality();  // S16LE, 2ch, 44100Hz
        ChannelMap map = ChannelMap.stereo();

        System.out.println("Audio format: " + spec);

        // 3. Create record stream from the monitor source
        RecordStream recordStream = client.createRecordStream(
                sourceName, "PulseAudioJava-Capture", spec, map);

        System.out.println("Record stream created: " + recordStream.getContext());
        System.out.println("  Actual format: " + recordStream.getSampleSpec());

        // 4. Set up local audio player
        final AudioPlayer player = new AudioPlayer(spec);
        player.start();

        // Wire the record stream to the player
        recordStream.setReadCallback((pcmData, offset, length) -> {
            player.write(pcmData, offset, length);
        });

        // 5. Uncork the stream to start receiving audio
        client.uncorkRecordStream(recordStream);

        System.out.println("Playing... Press Ctrl+C to stop.");

        // 6. Run until interrupted
        try {
            while (player.isRunning()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            System.out.println("Stopped.");
        } finally {
            player.close();
            client.close();
        }
    }
}
