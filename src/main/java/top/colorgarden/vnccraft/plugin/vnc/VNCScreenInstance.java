package top.colorgarden.vnccraft.plugin.vnc;

/**
 * Holds metadata for a single VNC screen.
 * VNC connectivity is handled by TunnelManager (TCP tunnel).
 * Frames are decoded on the Fabric client by Vernacular.
 */
public class VNCScreenInstance {

    private final int screenId;
    private String host;
    private int port;
    private String password;
    private double x, y, z;
    private int direction; // Direction ordinal
    private int resolutionW, resolutionH;

    private long lastReconnectAttempt;

    public VNCScreenInstance(int screenId, double x, double y, double z,
                             int direction, int resW, int resH) {
        this.screenId = screenId;
        this.x = x; this.y = y; this.z = z;
        this.direction = direction;
        this.resolutionW = resW;
        this.resolutionH = resH;
    }

    public int getScreenId() { return screenId; }
    public double getX() { return x; } public double getY() { return y; } public double getZ() { return z; }
    public void setPos(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
    public int getDirection() { return direction; }
    public void setDirection(int d) { this.direction = d; }
    public int getResolutionW() { return resolutionW; }
    public int getResolutionH() { return resolutionH; }
    public void setResolution(int w, int h) { this.resolutionW = w; this.resolutionH = h; }
    public void markReconnectAttempt() { lastReconnectAttempt = System.currentTimeMillis(); }
    public boolean canReconnect() { return System.currentTimeMillis() - lastReconnectAttempt > 5000; }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getPassword() { return password; }
    public void setConfig(String host, int port, String password) {
        this.host = host; this.port = port; this.password = password;
    }
    public boolean hasConfig() { return host != null && !host.isEmpty(); }

    public void disconnect() {
        disconnectAudio();
    }

    /** No-op in tunnel mode. Mouse input now goes through Vernacular on client. */
    public void moveMouse(int x, int y) {}

    /** No-op in tunnel mode. Mouse input now goes through Vernacular on client. */
    public void setButtons(int buttonState) {}

    // ---- Audio ----

    private top.colorgarden.pulseaudiojava.PulseAudioClient audioClient;
    private top.colorgarden.pulseaudiojava.stream.RecordStream audioStream;

    public void connectAudio(String host, int port, String cookiePath, AudioCallback callback) {
        disconnectAudio();
        System.out.println("[VNCCraft] Audio connecting: " + host + ":" + port);
        new Thread(() -> {
            try {
                audioClient = new top.colorgarden.pulseaudiojava.PulseAudioClient(
                        new top.colorgarden.pulseaudiojava.transport.TcpTransport(host, port));
                if (cookiePath != null && !cookiePath.isEmpty()) {
                    if (cookiePath.length() >= 100 && cookiePath.replaceAll("[0-9a-fA-F]", "").isEmpty()) {
                        byte[] cookie = hexToBytes(cookiePath);
                        audioClient.handshake(cookie);
                    } else {
                        audioClient.handshake(java.nio.file.Paths.get(cookiePath));
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
                for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                    p.sendMessage("§c" + msg);
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
