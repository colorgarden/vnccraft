package top.colorgarden.vnccraft.client.vnc;

import net.minecraft.core.Direction;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all VNCScreenRenderer and VNCClientInstance instances on the client.
 * Routes incoming packets to the correct screen by screenId.
 */
public class VNCClientRendererManager {

    private static final VNCClientRendererManager INSTANCE = new VNCClientRendererManager();
    public static VNCClientRendererManager getInstance() { return INSTANCE; }

    private final Map<Integer, VNCScreenRenderer> renderers = new ConcurrentHashMap<>();
    private final Map<Integer, VNCClientInstance> vncInstances = new ConcurrentHashMap<>();

    private VNCClientRendererManager() {}

    // ---- Screen lifecycle ----

    public void onScreenPlace(int screenId, double x, double y, double z,
                              int direction, int resW, int resH) {
        VNCScreenRenderer existing = renderers.get(screenId);
        if (existing != null) {
            existing.reposition(x, y, z, Direction.from3DDataValue(direction));
        } else {
            VNCScreenRenderer renderer = new VNCScreenRenderer(
                    screenId, x, y, z, Direction.from3DDataValue(direction), resW, resH);
            renderers.put(screenId, renderer);
        }
    }

    public void onScreenRemove(int screenId) {
        disconnectVNC(screenId);
        VNCScreenRenderer renderer = renderers.remove(screenId);
        if (renderer != null) {
            renderer.cleanup();
        }
    }

    public void onScreenUpdate(int screenId, int resW, int resH) {
        VNCScreenRenderer renderer = renderers.get(screenId);
        if (renderer != null) {
            renderer.updateResolution(resW, resH);
        }
    }

    // ---- VNC connection lifecycle (client-side Vernacular) ----

    /**
     * Called when the client receives a connect confirmation from the server.
     * Starts the client-side Vernacular instance over the tunnel.
     */
    public void connectVNC(int screenId, String password) {
        // Already connected — skip
        VNCClientInstance existing = vncInstances.get(screenId);
        if (existing != null && existing.isRunning()) {
            System.out.println("[VNCCraft-Client] VNC already running for screen " + screenId + " — skipping");
            return;
        }

        VNCScreenRenderer renderer = renderers.get(screenId);
        if (renderer == null) return;

        // Disconnect any previous instance
        disconnectVNC(screenId);

        VNCClientInstance instance = new VNCClientInstance(screenId, renderer, password);
        vncInstances.put(screenId, instance);
        instance.connect();
        System.out.println("[VNCCraft-Client] VNC client started for screen " + screenId);
    }

    /**
     * Called when the client receives a disconnect from the server.
     * Stops the client-side Vernacular instance.
     */
    public void disconnectVNC(int screenId) {
        VNCClientInstance instance = vncInstances.remove(screenId);
        if (instance != null) {
            instance.disconnect();
        }
    }

    // ---- Frame data (deprecated — now handled by VNCClientInstance) ----

    public void onServerInit(int screenId, int width, int height,
                             int bitsPerPixel, int depth, boolean bigEndian, boolean trueColor,
                             int redMax, int greenMax, int blueMax,
                             int redShift, int greenShift, int blueShift) {
        // ServerInit is now handled by client-side Vernacular through the tunnel.
        // This method remains for backward compatibility but does nothing.
    }

    public void onFrame(int screenId, byte[] rgbaPixels, int fw, int fh) {
        // Frame data now flows through the tunnel → Vernacular → VNCClientInstance → VNCScreenRenderer.
        // This method remains for backward compatibility but does nothing.
    }

    public void onDisconnect(int screenId) {
        disconnectVNC(screenId);
    }

    // ---- Tunnel data feed ----

    /**
     * Feed raw bytes from the VNC server (received via S2C tunnel packet) to the
     * client-side Vernacular instance for the given screen.
     */
    public void feedTunnel(int screenId, byte[] data) {
        VNCClientInstance instance = vncInstances.get(screenId);
        if (instance != null) {
            instance.feedFromServer(data);
        }
    }

    // ---- Accessors ----

    public VNCScreenRenderer getRenderer(int screenId) {
        return renderers.get(screenId);
    }

    public VNCClientInstance getVNCInstance(int screenId) {
        return vncInstances.get(screenId);
    }

    public int getScreenCount() {
        return renderers.size();
    }

    public void shutdownAll() {
        for (int id : vncInstances.keySet()) {
            disconnectVNC(id);
        }
        for (VNCScreenRenderer r : renderers.values()) {
            r.cleanup();
        }
        renderers.clear();
    }
}
