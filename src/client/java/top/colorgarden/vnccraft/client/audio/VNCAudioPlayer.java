package top.colorgarden.vnccraft.client.audio;

import javax.sound.sampled.*;

/**
 * Singleton PCM audio player with volume control.
 * Format: 44100Hz, 16bit, stereo, little-endian.
 */
public class VNCAudioPlayer {

    private static final VNCAudioPlayer INSTANCE = new VNCAudioPlayer();
    public static VNCAudioPlayer getInstance() { return INSTANCE; }

    private SourceDataLine line;
    private FloatControl volCtrl;
    private float currentVol = 1.0f;
    private float userVolume = 1.0f;

    public float getUserVolume() { return userVolume; }
    public void setUserVolume(float v) { this.userVolume = v / 100f; }

    private VNCAudioPlayer() {
        start();
    }

    private void start() {
        try {
            AudioFormat format = new AudioFormat(44100, 16, 2, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(format, 44100 * 4);
            if (line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                volCtrl = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
            }
            line.start();
        } catch (Exception e) {}
    }

    /** Stereo PCM: apply left/right volume independently */
    public void writeStereo(byte[] pcm, int offset, int len, float lVol, float rVol) {
        if (line == null) return;
        // Clone and scale left/right channels independently (16bit LE stereo: [L0 L1][R0 R1]...)
        byte[] out = new byte[len];
        for (int i = 0; i + 3 < len; i += 4) {
            short ls = (short)((pcm[offset+i+1] << 8) | (pcm[offset+i] & 0xFF));
            short rs = (short)((pcm[offset+i+3] << 8) | (pcm[offset+i+2] & 0xFF));
            short lo = (short)Math.clamp(ls * lVol, -32768, 32767);
            short ro = (short)Math.clamp(rs * rVol, -32768, 32767);
            out[i] = (byte)lo; out[i+1] = (byte)(lo>>8);
            out[i+2] = (byte)ro; out[i+3] = (byte)(ro>>8);
        }
        line.write(out, 0, len);
    }

    public void write(byte[] pcm, int offset, int len, float volume) {
        if (line == null) return;
        if (volume != currentVol && volCtrl != null) {
            float gain = Math.max(volCtrl.getMinimum(), Math.min(volCtrl.getMaximum(),
                    20f * (float)Math.log10(Math.max(volume, 0.001))));
            volCtrl.setValue(gain);
            currentVol = volume;
        }
        line.write(pcm, offset, len);
    }
}
