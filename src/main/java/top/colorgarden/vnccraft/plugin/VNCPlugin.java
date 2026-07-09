package top.colorgarden.vnccraft.plugin;

import top.colorgarden.vnccraft.plugin.network.VNCProtocolLib;
import top.colorgarden.vnccraft.plugin.vnc.TunnelManager;
import top.colorgarden.vnccraft.plugin.vnc.VNCScreenManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

public class VNCPlugin extends JavaPlugin implements Listener, PluginMessageListener {

    private static VNCPlugin instance;
    public static VNCPlugin getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;

        // Init managers
        VNCScreenManager.init(getDataFolder().toPath());
        TunnelManager.init();

        // Tunnel channels (outgoing: S2C)
        getServer().getMessenger().registerOutgoingPluginChannel(this, "vnccraft:vnc_tunnel_s2c");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "vnccraft:vnc_tunnel_ready");
        // Audio channel
        getServer().getMessenger().registerOutgoingPluginChannel(this, "vnccraft:vnc_audio");
        // Control channels (outgoing: S2C)
        getServer().getMessenger().registerOutgoingPluginChannel(this, "vnccraft:vnc_disconnect");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "vnccraft:vnc_screen_place");
        getServer().getMessenger().registerOutgoingPluginChannel(this, "vnccraft:vnc_screen_remove");

        // Tunnel channel (incoming: C2S)
        getServer().getMessenger().registerIncomingPluginChannel(this, "vnccraft:vnc_tunnel_c2s", this);
        // Control channels (incoming: C2S)
        getServer().getMessenger().registerIncomingPluginChannel(this, "vnccraft:vnc_connect_request", this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "vnccraft:vnc_disconnect_request", this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "vnccraft:vnc_screen_config", this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "vnccraft:vnc_place_request", this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "vnccraft:vnc_remove_request", this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "vnccraft:vnc_mouse_input", this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "vnccraft:vnc_nudge", this);

        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Tick task
        Bukkit.getScheduler().runTaskTimer(this, () -> VNCScreenManager.getInstance().tick(), 1L, 1L);

        getLogger().info("VNCCraft Plugin enabled (tunnel mode)");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        VNCScreenManager.getInstance().shutdown();
        getLogger().info("VNCCraft Plugin disabled");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        VNCScreenManager.getInstance().syncAllTo(e.getPlayer());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        VNCScreenManager mgr = VNCScreenManager.getInstance();
        try {
            switch (channel) {
                case "vnccraft:vnc_tunnel_c2s" -> {
                    // Forward client Vernacular bytes to VNC server via TCP tunnel
                    var req = VNCProtocolLib.decodeTunnelC2S(message);
                    TunnelManager.getInstance().clientToServer(req.screenId(), req.data());
                }
                case "vnccraft:vnc_connect_request" -> {
                    var req = VNCProtocolLib.decodeConnectRequest(message);
                    mgr.connectScreen(req.screenId(), req.host(), req.port(), req.password(),
                            req.resW() > 0 ? req.resW() : 1920, req.resH() > 0 ? req.resH() : 1080,
                            req.audioPort(), req.cookiePath() != null ? req.cookiePath() : "");
                }
                case "vnccraft:vnc_disconnect_request" -> {
                    mgr.disconnectScreen(VNCProtocolLib.decodeDisconnectRequest(message));
                }
                case "vnccraft:vnc_place_request" -> {
                    var req = VNCProtocolLib.decodePlaceRequest(message);
                    int id = mgr.placeScreen(req.x(), req.y(), req.z(), req.direction(), req.resW(), req.resH());
                    mgr.broadcastPlace(id, req.x(), req.y(), req.z(), req.direction(), req.resW(), req.resH());
                }
                case "vnccraft:vnc_remove_request" -> {
                    int id = VNCProtocolLib.decodeDisconnectRequest(message);
                    mgr.removeScreen(id);
                    mgr.broadcastRemove(id);
                }
                case "vnccraft:vnc_mouse_input" -> {
                    // Mouse input is now handled by client-side Vernacular via tunnel_c2s.
                    // This handler remains for backward compatibility with older clients.
                    var mi = VNCProtocolLib.decodeMouseInput(message);
                    var screen = mgr.getScreen(mi.screenId());
                    if (screen != null) { screen.moveMouse(mi.x(), mi.y()); screen.setButtons(mi.buttonMask()); }
                }
                case "vnccraft:vnc_nudge" -> {
                    var n = VNCProtocolLib.decodeNudgeRequest(message);
                    mgr.nudgeScreen(n.screenId(), n.axis(), n.step(), n.fine());
                }
            }
        } catch (Exception ex) {
            getLogger().warning("Packet error on " + channel + ": " + ex.getMessage());
        }
    }
}
