package top.colorgarden.vnccraft.vnc;

import com.shinyhut.vernacular.client.VernacularClient;
import com.shinyhut.vernacular.client.VernacularConfig;
import top.colorgarden.vnccraft.entity.VNCScreenEntity;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import java.awt.image.BufferedImage;
import java.awt.Image;

/**
 * VNC screen instance using Vernacular VNC library for protocol handling.
 * Frames arrive as AWT Image via screenUpdateListener.
 */
public class VNCScreenInstance {

    private final int screenId;
    private final ServerLevel level;
    private double x, y, z;
    private Direction direction;
    private int resolutionW, resolutionH;

    private VernacularClient client;
    private boolean connected;
    private boolean serverInitSent;
    private boolean errorReported;
    private long lastReconnectAttempt;
    private VNCScreenEntity entity;

    public VNCScreenInstance(int screenId, ServerLevel level, double x, double y, double z,
                             Direction direction, int resW, int resH, VNCScreenEntity entity) {
        this.screenId = screenId;
        this.level = level;
        this.x = x; this.y = y; this.z = z;
        this.direction = direction;
        this.resolutionW = resW;
        this.resolutionH = resH;
        this.connected = false;
        this.entity = entity;
        this.lastReconnectAttempt = System.currentTimeMillis(); // prevent immediate reconnect on first tick
    }

    public int getScreenId() { return screenId; }
    public ServerLevel getLevel() { return level; }
    public double getX() { return x; } public double getY() { return y; } public double getZ() { return z; }
    public Direction getDirection() { return direction; }
    public int getResolutionW() { return resolutionW; }
    public int getResolutionH() { return resolutionH; }
    public boolean isConnected() { return connected; }
    public VNCScreenEntity getEntity() { return entity; }
    public boolean isServerInitSent() { return serverInitSent; }
    public void setServerInitSent(boolean v) { serverInitSent = v; }
    public boolean isErrorReported() { return errorReported; }
    public void setErrorReported(boolean v) { errorReported = v; }
    public void markReconnectAttempt() { lastReconnectAttempt = System.currentTimeMillis(); }
    public boolean canReconnect() { return System.currentTimeMillis() - lastReconnectAttempt > 5000; }
    public void setResolution(int w, int h) { this.resolutionW = w; this.resolutionH = h; }

    public net.minecraft.world.phys.Vec3 getScreenCenter() {
        return new net.minecraft.world.phys.Vec3(x, y, z);
    }
    public net.minecraft.world.phys.Vec3 getScreenNormal() {
        return new net.minecraft.world.phys.Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
    }

    @FunctionalInterface
    public interface FrameUpdateCallback {
        void onFrameUpdate(byte[] rgbaPixels, int width, int height);
    }

    public void connect(String host, int port, String password, FrameUpdateCallback callback) {
        disconnect();
        VernacularConfig cfg = new VernacularConfig();
        cfg.setColorDepth(com.shinyhut.vernacular.client.rendering.ColorDepth.BPP_24_TRUE);
        cfg.setPasswordSupplier(() -> password);
        cfg.setTargetFramesPerSecond(30);
        cfg.setScreenUpdateListener(image -> {
            int fw = image.getWidth(null), fh = image.getHeight(null);
            System.out.println("[VNCCraft] Vernacular frame: " + fw + "x" + fh + " type=" + image.getClass().getSimpleName());
            try {
            BufferedImage bi = (BufferedImage) image;
            int biType = bi.getType();
            System.out.println("[VNCCraft] BI type=" + biType);
            int sample = bi.getRGB(0, 0);
            System.out.println("[VNCCraft] Pixel[0,0]=" + Integer.toHexString(sample) + " R=" + ((sample>>16)&0xFF) + " G=" + ((sample>>8)&0xFF) + " B=" + (sample&0xFF));
            if (fw != resolutionW || fh != resolutionH) { resolutionW = fw; resolutionH = fh; }
            int[] rgb = bi.getRGB(0, 0, fw, fh, null, 0, fw);
            byte[] rgba = new byte[fw * fh * 4];
            for (int i = 0; i < rgb.length; i++) {
                int c = rgb[i];
                rgba[i*4]   = (byte)((c >> 16) & 0xFF);
                rgba[i*4+1] = (byte)((c >> 8) & 0xFF);
                rgba[i*4+2] = (byte)(c & 0xFF);
                rgba[i*4+3] = (byte)0xFF;
            }
            // Compress here — lets 5MB raw rgba get GC'd before callback captures it
            java.util.zip.Deflater def = new java.util.zip.Deflater(1);
            def.setInput(rgba); def.finish();
            byte[] buf = new byte[rgba.length];
            int clen = def.deflate(buf); def.end();
            byte[] compressed = java.util.Arrays.copyOf(buf, clen);
            callback.onFrameUpdate(compressed, fw, fh);
            } catch (Exception ex) {
                System.out.println("[VNCCraft] Frame callback error: " + ex.getMessage());
                ex.printStackTrace();
            }
        });
        cfg.setErrorListener(e -> { connected = false; System.out.println("[VNCCraft] VNC error: " + e.getMessage()); e.printStackTrace(); });

        client = new VernacularClient(cfg);
        connected = true;
        new Thread(() -> {
            try { client.start(host, port); }
            catch (Exception e) { connected = false; System.err.println("[VNCCraft] VNC start error: " + e.getMessage()); }
        }, "VNC-" + host + ":" + port).start();
    }

    public boolean isInitComplete() { return client != null && connected; }
    public VernacularClient getClient() { return client; }

    public void disconnect() {
        connected = false; serverInitSent = false; errorReported = false;
        disconnectAudio();
        if (client != null) { try { client.stop(); } catch (Exception ignored) {} client = null; }
    }

    public void tick() {}

    public void moveMouse(int x, int y) {
        if (client == null || !connected) return;
        try { client.moveMouse(x, y); } catch (Exception e) {}
    }

    public void setButtons(int buttonState) {
        if (client == null || !connected) return;
        try {
            client.updateMouseButton(1, (buttonState & 1) != 0);
            client.updateMouseButton(2, (buttonState & 2) != 0);
            client.updateMouseButton(3, (buttonState & 4) != 0);
            client.updateMouseButton(4, (buttonState & 8) != 0);  // scroll up
            client.updateMouseButton(5, (buttonState & 16) != 0); // scroll down
        } catch (Exception e) {}
    }

    // ---- Audio ----

    private top.colorgarden.pulseaudiojava.PulseAudioClient audioClient;
    private top.colorgarden.pulseaudiojava.stream.RecordStream audioStream;

    public void connectAudio(String host, int port, String cookiePath, AudioCallback callback) {
        disconnectAudio();
        System.out.println("[VNCCraft] Audio connecting: " + host + ":" + port + " cookie=" + (cookiePath != null && !cookiePath.isEmpty() ? "yes" : "no"));
        new Thread(() -> {
            try {
                audioClient = new top.colorgarden.pulseaudiojava.PulseAudioClient(
                        new top.colorgarden.pulseaudiojava.transport.TcpTransport(host, port));
                if (cookiePath != null && !cookiePath.isEmpty()) {
                    if (cookiePath.length() >= 100 && cookiePath.replaceAll("[0-9a-fA-F]", "").isEmpty()) {
                        byte[] cookie = hexToBytes(cookiePath);
                        System.out.println("[VNCCraft] Audio cookie hex parsed: " + cookie.length + " bytes, first=" + Integer.toHexString(cookie[0] & 0xFF));
                        audioClient.handshake(cookie);
                    } else {
                        audioClient.handshake(java.nio.file.Paths.get(cookiePath)); // file path
                    }
                } else {
                    audioClient.handshake((byte[]) null);
                }
                var spec = new top.colorgarden.pulseaudiojava.audio.SampleSpec(
                        top.colorgarden.pulseaudiojava.audio.SampleFormat.S16LE, 2, 44100);
                var map = top.colorgarden.pulseaudiojava.audio.ChannelMap.stereo();
                audioStream = audioClient.createRecordStream("@DEFAULT_MONITOR@", "VNCCraft", spec, map);
                audioStream.setReadCallback((pcm, off, len) -> callback.onPcmData(pcm, len));
                audioClient.uncorkRecordStream(audioStream);
                System.out.println("[VNCCraft] Audio stream started successfully");
            } catch (Exception e) {
                String msg = "VNC screen #" + screenId + " audio error: " + e.getMessage();
                System.err.println("[VNCCraft] " + msg);
                e.printStackTrace();
                var srv = level.getServer();
                if (srv != null) {
                    for (var p : srv.getPlayerList().getPlayers()) {
                        p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§c" + msg));
                    }
                }
            }
        }, "VNC-Audio-" + host).start();
    }

    public void disconnectAudio() {
        if (audioStream != null) { try { audioClient.deleteRecordStream(audioStream); } catch (Exception ignored) {} audioStream = null; }
        if (audioClient != null) { try { audioClient.close(); } catch (Exception ignored) {} audioClient = null; }
    }

    public interface AudioCallback {
        void onPcmData(byte[] pcm, int length);
    }

    private static byte[] hexToBytes(String hex) {
        hex = hex.replaceAll("[^0-9a-fA-F]", "");
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i/2] = (byte)((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}
