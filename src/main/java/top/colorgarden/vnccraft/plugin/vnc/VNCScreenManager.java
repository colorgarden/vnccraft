package top.colorgarden.vnccraft.plugin.vnc;

import top.colorgarden.vnccraft.plugin.VNCPlugin;
import top.colorgarden.vnccraft.plugin.network.VNCProtocolLib;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages VNC screens: CRUD, JSON persistence, tunnel lifecycle.
 * VNC frames are NOT decoded here — raw bytes are forwarded by TunnelManager.
 */
public class VNCScreenManager {

    private static VNCScreenManager instance;
    private final Map<Integer, VNCScreenInstance> screens = new ConcurrentHashMap<>();
    private int nextId = 0;
    private Path dataDir;

    public static VNCScreenManager getInstance() { return instance; }

    public static void init(Path pluginDataFolder) {
        instance = new VNCScreenManager();
        instance.dataDir = pluginDataFolder.resolve("vnccraft_screens");
        try { Files.createDirectories(instance.dataDir); } catch (IOException ignored) {}
        instance.loadAll();
    }

    public void shutdown() {
        saveAll();
        for (VNCScreenInstance s : screens.values()) {
            TunnelManager.getInstance().closeTunnel(s.getScreenId());
            s.disconnect();
        }
        screens.clear();
    }

    // ---- Screen CRUD ----

    public int placeScreen(double x, double y, double z, int direction, int resW, int resH) {
        // Check for existing screen at this position (within 0.5 block tolerance)
        for (VNCScreenInstance existing : screens.values()) {
            if (Math.abs(existing.getX() - x) < 0.5 &&
                Math.abs(existing.getY() - y) < 0.5 &&
                Math.abs(existing.getZ() - z) < 0.5) {
                return existing.getScreenId();
            }
        }
        int id = nextId++;
        VNCScreenInstance screen = new VNCScreenInstance(id, x, y, z, direction, resW, resH);
        screens.put(id, screen);
        saveScreen(screen);
        return id;
    }

    public void removeScreen(int id) {
        TunnelManager.getInstance().closeTunnel(id);
        VNCScreenInstance s = screens.remove(id);
        if (s != null) { s.disconnect(); deleteScreenFile(id); }
    }

    public VNCScreenInstance getScreen(int id) { return screens.get(id); }

    // ---- VNC Connection (tunnel mode) ----

    public void connectScreen(int id, String host, int port, String password, int resW, int resH,
                              int audioPort, String cookiePath) {
        VNCScreenInstance screen = screens.get(id);
        if (screen == null) return;
        screen.setConfig(host, port, password);
        screen.setResolution(resW, resH);
        screen.markReconnectAttempt(); // prevent immediate tick re-reconnect
        saveScreen(screen);

        // Audio forwarding (PulseAudio)
        if (audioPort > 0) {
            screen.connectAudio(host, audioPort,
                    cookiePath != null && !cookiePath.isEmpty() ? cookiePath : null,
                    (pcm, len) -> {
                        byte[] chunk = new byte[len];
                        System.arraycopy(pcm, 0, chunk, 0, len);
                        byte[] pkt = VNCProtocolLib.encodeAudio(id, chunk);
                        for (Player p : Bukkit.getOnlinePlayers())
                            p.sendPluginMessage(VNCPlugin.getInstance(), "vnccraft:vnc_audio", pkt);
                    });
        }

        // Open TCP tunnel — server is a pure byte pipe
        TunnelManager.getInstance().openTunnel(id, host, port);

        // Notify clients to auto-start Vernacular
        TunnelManager.getInstance().broadcastTunnelReady(id, password);

        System.out.println("[VNCCraft] connectScreen: id=" + id + " host=" + host + ":" + port + " (tunnel mode)");
    }

    public void disconnectScreen(int id) {
        TunnelManager.getInstance().closeTunnel(id);
        VNCScreenInstance s = screens.get(id);
        if (s != null) {
            s.disconnect();
            s.setConfig("", 5900, "");
            saveScreen(s);
        }
        // Notify clients
        for (Player p : Bukkit.getOnlinePlayers())
            p.sendPluginMessage(VNCPlugin.getInstance(), "vnccraft:vnc_disconnect", VNCProtocolLib.encodeInt(id));
    }

    public void broadcastPlace(int id, double x, double y, double z, int dir, int rw, int rh) {
        byte[] pkt = VNCProtocolLib.encodeScreenPlace(id, x, y, z, dir, rw, rh);
        for (Player p : Bukkit.getOnlinePlayers())
            p.sendPluginMessage(VNCPlugin.getInstance(), "vnccraft:vnc_screen_place", pkt);
    }

    public void broadcastRemove(int id) {
        byte[] pkt = VNCProtocolLib.encodeInt(id);
        for (Player p : Bukkit.getOnlinePlayers())
            p.sendPluginMessage(VNCPlugin.getInstance(), "vnccraft:vnc_screen_remove", pkt);
    }

    public void syncAllTo(Player player) {
        for (VNCScreenInstance s : screens.values()) {
            byte[] place = VNCProtocolLib.encodeScreenPlace(s.getScreenId(), s.getX(), s.getY(), s.getZ(),
                    s.getDirection(), s.getResolutionW(), s.getResolutionH());
            player.sendPluginMessage(VNCPlugin.getInstance(), "vnccraft:vnc_screen_place", place);
            // If tunnel is active, tell the new player to auto-start Vernacular
            if (TunnelManager.getInstance().isTunnelActive(s.getScreenId())) {
                byte[] ready = VNCProtocolLib.encodeTunnelReady(s.getScreenId(),
                        s.getPassword() != null ? s.getPassword() : "");
                player.sendPluginMessage(VNCPlugin.getInstance(), "vnccraft:vnc_tunnel_ready", ready);
            }
        }
    }

    // ---- Tick (auto-reconnect) ----

    public void tick() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        for (VNCScreenInstance s : screens.values()) {
            int id = s.getScreenId();
            if (!TunnelManager.getInstance().isTunnelActive(id) && s.hasConfig() && s.canReconnect()) {
                s.markReconnectAttempt();
                connectScreen(id, s.getHost(), s.getPort(), s.getPassword(), s.getResolutionW(), s.getResolutionH(), 0, "");
            }
        }
    }

    public void nudgeScreen(int id, int axis, int step, boolean fine) {
        VNCScreenInstance s = screens.get(id);
        if (s == null) return;
        double delta = step * (fine ? 0.01 : 0.1);
        switch (axis) {
            case 0 -> s.setPos(s.getX() + delta, s.getY(), s.getZ());
            case 1 -> s.setPos(s.getX(), s.getY() + delta, s.getZ());
            case 2 -> s.setPos(s.getX(), s.getY(), s.getZ() + delta);
        }
        saveScreen(s);
        broadcastPlace(id, s.getX(), s.getY(), s.getZ(), s.getDirection(), s.getResolutionW(), s.getResolutionH());
    }

    // ---- Persistence ----

    private void loadAll() {
        try {
            Files.list(dataDir).filter(f -> f.toString().endsWith(".json")).forEach(f -> {
                try {
                    String name = f.getFileName().toString().replace(".json", "");
                    int id = Integer.parseInt(name);
                    String json = Files.readString(f);
                    double x = Double.parseDouble(extractJson(json, "x"));
                    double y = Double.parseDouble(extractJson(json, "y"));
                    double z = Double.parseDouble(extractJson(json, "z"));
                    int dir = Integer.parseInt(extractJson(json, "direction"));
                    int rw = Integer.parseInt(extractJson(json, "resW"));
                    int rh = Integer.parseInt(extractJson(json, "resH"));
                    String host = extractJson(json, "host");
                    int port = json.contains("\"port\"") ? Integer.parseInt(extractJson(json, "port")) : 5900;
                    String password = extractJson(json, "password");
                    VNCScreenInstance s = new VNCScreenInstance(id, x, y, z, dir, rw, rh);
                    if (!host.isEmpty()) s.setConfig(host, port, password);
                    screens.put(id, s);
                    if (id >= nextId) nextId = id + 1;
                } catch (Exception ignored) {}
            });
        } catch (IOException ignored) {}
        // Auto-connect on load only if players are online
        if (!Bukkit.getOnlinePlayers().isEmpty()) {
            for (VNCScreenInstance s : screens.values()) {
                if (s.hasConfig()) {
                    connectScreen(s.getScreenId(), s.getHost(), s.getPort(), s.getPassword(),
                            s.getResolutionW(), s.getResolutionH(), 0, "");
                }
            }
        }
    }

    private void saveScreen(VNCScreenInstance s) {
        try {
            String json = String.format(
                    "{\"x\":%s,\"y\":%s,\"z\":%s,\"direction\":%d,\"resW\":%d,\"resH\":%d,\"host\":\"%s\",\"port\":%d,\"password\":\"%s\"}",
                    s.getX(), s.getY(), s.getZ(), s.getDirection(), s.getResolutionW(), s.getResolutionH(),
                    s.getHost() != null ? s.getHost() : "", s.getPort(), s.getPassword() != null ? s.getPassword() : "");
            Files.writeString(dataDir.resolve(s.getScreenId() + ".json"), json);
        } catch (IOException ignored) {}
    }

    private void saveAll() {
        for (VNCScreenInstance s : screens.values()) saveScreen(s);
    }

    private void deleteScreenFile(int id) {
        try { Files.deleteIfExists(dataDir.resolve(id + ".json")); } catch (IOException ignored) {}
    }

    private static String extractJson(String json, String key) {
        int i = json.indexOf("\"" + key + "\":");
        if (i < 0) return "";
        i = json.indexOf(":", i) + 1;
        while (i < json.length() && json.charAt(i) == ' ') i++;
        if (i >= json.length()) return "";
        if (json.charAt(i) == '"') { int end = json.indexOf('"', i + 1); return end < 0 ? "" : json.substring(i + 1, end); }
        int end = i;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(i, end);
    }
}
