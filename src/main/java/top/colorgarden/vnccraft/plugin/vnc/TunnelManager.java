package top.colorgarden.vnccraft.plugin.vnc;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages TCP tunnels between the Paper server and VNC servers.
 * Each screen gets one TCP connection. Raw bytes are forwarded
 * bidirectionally between the VNC server and Fabric clients via plugin messages.
 *
 * The server knows NOTHING about VNC protocol — it's a pure byte pipe.
 */
public class TunnelManager {

    private static TunnelManager instance;
    private static final String CHANNEL_TUNNEL_S2C = "vnccraft:vnc_tunnel_s2c";
    private static final String CHANNEL_TUNNEL_READY = "vnccraft:vnc_tunnel_ready";

    private final Map<Integer, Tunnel> tunnels = new ConcurrentHashMap<>();
    private org.bukkit.plugin.Plugin plugin;

    private TunnelManager() {}

    public static TunnelManager getInstance() { return instance; }

    public static void init() {
        instance = new TunnelManager();
        instance.plugin = org.bukkit.Bukkit.getPluginManager().getPlugin("vnccraft-plugin");
    }

    // ---- Public API ----

    /**
     * Open a TCP tunnel to a VNC server for the given screen.
     */
    public void openTunnel(int screenId, String host, int port) {
        Tunnel existing = tunnels.get(screenId);
        if (existing != null && existing.connected
                && host.equals(existing.host) && port == existing.port) {
            return;
        }
        closeTunnel(screenId);
        Tunnel tunnel = new Tunnel(screenId, host, port);
        tunnels.put(screenId, tunnel);
        tunnel.start();
    }

    public void closeTunnel(int screenId) {
        Tunnel tunnel = tunnels.remove(screenId);
        if (tunnel != null) {
            tunnel.stop();
        }
    }

    private int c2sCount = 0;

    /**
     * Forward bytes from a client to the VNC server.
     */
    public void clientToServer(int screenId, byte[] data) {
        Tunnel tunnel = tunnels.get(screenId);
        if (tunnel == null || !tunnel.connected) {
            System.err.println("[VNCCraft-Tunnel] clientToServer: tunnel not active for screen " + screenId);
            return;
        }
        try {
            if (c2sCount < 5) {
                System.out.println("[VNCCraft-Tunnel] Client→Server: " + data.length + " bytes (msg #" + (c2sCount + 1) + ")");
                c2sCount++;
            }
            tunnel.out.write(data);
            tunnel.out.flush();
        } catch (IOException e) {
            System.err.println("[VNCCraft-Tunnel] Write error for screen " + screenId + ": " + e.getMessage());
        }
    }

    public boolean isTunnelActive(int screenId) {
        Tunnel tunnel = tunnels.get(screenId);
        return tunnel != null && tunnel.connected;
    }

    /**
     * Broadcast a "tunnel ready" signal to all online players.
     * The Fabric client will auto-start Vernacular upon receiving this.
     */
    public void broadcastTunnelReady(int screenId, String password) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(screenId);
        buf.writeUtf(password != null ? password : "");
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendPluginMessage(plugin, CHANNEL_TUNNEL_READY, data);
        }
    }

    // ---- Tunnel internal class ----

    private class Tunnel {
        final int screenId;
        final String host;
        final int port;

        Socket socket;
        DataInputStream in;
        DataOutputStream out;
        volatile boolean connected;
        Thread readThread;
        volatile boolean running;

        Tunnel(int screenId, String host, int port) {
            this.screenId = screenId;
            this.host = host;
            this.port = port;
        }

        void start() {
            running = true;
            readThread = new Thread(() -> {
                try {
                    socket = new Socket();
                    socket.setTcpNoDelay(true);
                    socket.connect(new InetSocketAddress(host, port), 5000);
                    socket.setSoTimeout(100);
                    in = new DataInputStream(socket.getInputStream());
                    out = new DataOutputStream(socket.getOutputStream());
                    connected = true;
                    System.out.println("[VNCCraft-Tunnel] Connected to " + host + ":" + port + " for screen " + screenId);

                    byte[] buf = new byte[65536];
                    int chunkCount = 0;
                    while (running && connected) {
                        try {
                            int avail = Math.min(buf.length, Math.max(1, socket.getInputStream().available()));
                            int bytesRead = in.read(buf, 0, Math.max(1, avail));
                            if (bytesRead == -1) break;
                            if (bytesRead > 0) {
                                byte[] chunk = new byte[bytesRead];
                                System.arraycopy(buf, 0, chunk, 0, bytesRead);
                                if (chunkCount < 5) {
                                    System.out.println("[VNCCraft-Tunnel] Forwarding " + bytesRead + " bytes to client (chunk #" + (chunkCount + 1) + ")");
                                }
                                broadcastToClients(screenId, chunk);
                                chunkCount++;
                            }
                        } catch (java.net.SocketTimeoutException e) {
                            // Expected — no data ready
                        }
                    }
                } catch (IOException e) {
                    String msg = "VNC screen #" + screenId + " tunnel error: " + e.getMessage();
                    System.err.println("[VNCCraft-Tunnel] " + msg);
                    broadcastChat("§c" + msg);
                } finally {
                    connected = false;
                    closeSocket();
                }
            }, "VNC-Tunnel-" + screenId);
            readThread.setDaemon(true);
            readThread.start();
        }

        void stop() {
            running = false;
            connected = false;
            closeSocket();
            if (readThread != null) {
                readThread.interrupt();
            }
        }

        void closeSocket() {
            try { if (in != null) in.close(); } catch (IOException ignored) {}
            try { if (out != null) out.close(); } catch (IOException ignored) {}
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
            in = null;
            out = null;
            socket = null;
        }
    }

    private void broadcastChat(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(msg);
        }
    }

    private void broadcastToClients(int screenId, byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(screenId);
        buf.writeByteArray(data);
        byte[] payload = new byte[buf.readableBytes()];
        buf.readBytes(payload);

        if (plugin == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendPluginMessage(plugin, CHANNEL_TUNNEL_S2C, payload);
        }
    }
}
