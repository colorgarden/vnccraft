package top.colorgarden.vnccraft.vnc;

import top.colorgarden.vnccraft.network.S2CVNCTunnelPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages TCP tunnels between the MC server and VNC servers.
 * Each screen gets one TCP connection. Raw bytes are forwarded
 * bidirectionally between the VNC server and MC clients.
 *
 * The server knows NOTHING about VNC protocol — it's a pure byte pipe.
 */
public class TunnelManager {

    private static TunnelManager instance;
    private MinecraftServer server;

    private final Map<Integer, Tunnel> tunnels = new ConcurrentHashMap<>();

    private TunnelManager() {}

    public static TunnelManager getInstance() { return instance; }

    public static void init(MinecraftServer server) {
        instance = new TunnelManager();
        instance.server = server;
    }

    public MinecraftServer getServer() { return server; }

    // ---- Public API ----

    /**
     * Open a TCP tunnel to a VNC server for the given screen.
     * If a tunnel already exists for this screen, it is closed first.
     */
    public void openTunnel(int screenId, String host, int port) {
        // If already connected to same host:port, do nothing
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

    /**
     * Close the tunnel for the given screen.
     */
    public void closeTunnel(int screenId) {
        Tunnel tunnel = tunnels.remove(screenId);
        if (tunnel != null) {
            tunnel.stop();
        }
    }

    /**
     * Forward bytes from a client to the VNC server.
     */
    private int c2sCount = 0;

    public void clientToServer(int screenId, byte[] data) {
        Tunnel tunnel = tunnels.get(screenId);
        if (tunnel == null) {
            System.err.println("[VNCCraft-Tunnel] clientToServer: NO tunnel in map for screen " + screenId + " (keys: " + tunnels.keySet() + ")");
            return;
        }
        if (!tunnel.connected) {
            System.err.println("[VNCCraft-Tunnel] clientToServer: tunnel found but not connected for screen " + screenId + " (running=" + tunnel.running + ")");
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

    /**
     * Check if a tunnel is active for the given screen.
     */
    public boolean isTunnelActive(int screenId) {
        Tunnel tunnel = tunnels.get(screenId);
        return tunnel != null && tunnel.connected;
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
                    socket.setSoTimeout(100); // short timeout for responsive shutdown
                    in = new DataInputStream(socket.getInputStream());
                    out = new DataOutputStream(socket.getOutputStream());
                    connected = true;
                    System.out.println("[VNCCraft-Tunnel] Connected to " + host + ":" + port + " for screen " + screenId);

                    // Read loop: forward bytes from VNC server to all MC clients
                    byte[] buf = new byte[65536];
                    int chunkCount = 0;
                    while (running && connected) {
                        try {
                            // Read whatever is available (at least 1 byte, up to buffer size)
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
                            // Expected — just means no data ready, continue
                        }
                    }
                } catch (IOException e) {
                    System.err.println("[VNCCraft-Tunnel] Tunnel error for screen " + screenId + ": " + e.getMessage());
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

    private void broadcastToClients(int screenId, byte[] data) {
        MinecraftServer srv = server;
        if (srv == null) return;
        var payload = new S2CVNCTunnelPayload(new S2CVNCTunnelPayload.Data(screenId, data));
        for (ServerPlayer player : srv.getPlayerList().getPlayers()) {
            try {
                ServerPlayNetworking.send(player, payload);
            } catch (Exception e) {
                System.err.println("[VNCCraft-Tunnel] Failed to send to " + player.getName() + ": " + e.getMessage());
            }
        }
    }
}
