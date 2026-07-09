package top.colorgarden.vnccraft.vnc;

import top.colorgarden.vnccraft.config.VNCConfig;
import top.colorgarden.vnccraft.entity.VNCScreenEntity;
import top.colorgarden.vnccraft.entity.VNCEntities;
import top.colorgarden.vnccraft.network.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Singleton that manages all VNC screen instances on the server.
 * Handles screen placement, VNC connections, frustum culling,
 * and broadcasting frame data to visible players.
 */
public class VNCManager {

    private static VNCManager instance;

    private MinecraftServer server;
    private final Map<Integer, VNCScreenInstance> screens = new ConcurrentHashMap<>();
    private final AtomicInteger nextScreenId = new AtomicInteger(0);
    private VNCManager() {}

    public static VNCManager getInstance() { return instance; }

    private static final java.nio.file.Path ID_FILE =
            java.nio.file.Path.of("vnccraft_next_id.txt");

    public static void init(MinecraftServer server) {
        instance = new VNCManager();
        instance.server = server;
        instance.loadNextId();
        instance.registerExistingEntities();
    }

    private void loadNextId() {
        try {
            java.nio.file.Path p = server.getServerDirectory().resolve(ID_FILE);
            if (java.nio.file.Files.exists(p)) {
                int saved = Integer.parseInt(java.nio.file.Files.readString(p).trim());
                nextScreenId.set(Math.max(nextScreenId.get(), saved));
            }
        } catch (Exception ignored) {}
    }

    private void saveNextId() {
        try {
            java.nio.file.Path p = server.getServerDirectory().resolve(ID_FILE);
            java.nio.file.Files.writeString(p, String.valueOf(nextScreenId.get()));
        } catch (Exception ignored) {}
    }

    public MinecraftServer getServer() { return server; }
    public VNCScreenInstance getScreen(int screenId) { return screens.get(screenId); }

    /**
     * Scan all worlds for existing VNCScreenEntity instances and register them.
     * Needed when the world is loaded from save (entities persist, but VNCManager is fresh).
     */
    private void registerExistingEntities() {
        int maxId = 0;
        for (ServerLevel level : server.getAllLevels()) {
            for (net.minecraft.world.entity.Entity entity : level.getAllEntities()) {
                if (entity instanceof VNCScreenEntity screen && !screens.containsKey(screen.getScreenId())) {
                    registerFromEntity(screen);
                    if (screen.getScreenId() > maxId) maxId = screen.getScreenId();
                }
            }
        }
        nextScreenId.set(maxId + 1);
    }

    public boolean hasScreen(int screenId) { return screens.containsKey(screenId); }

    public void registerFromEntity(VNCScreenEntity entity) {
        int screenId = entity.getScreenId();
        if (screens.containsKey(screenId)) return;
        if (screenId >= nextScreenId.get()) nextScreenId.set(screenId + 1);
        VNCScreenInstance instance = new VNCScreenInstance(
                screenId, (ServerLevel) entity.level(),
                entity.getX(), entity.getY(), entity.getZ(),
                entity.getScreenDirection(),
                entity.getResW(), entity.getResH(), entity);
        instance.disconnect();
        screens.put(screenId, instance);

        // VNC auto-reconnect deferred to tick handler (world still loading)
    }

    /**
     * Nudge screen position by step along axis.
     * Called when player Alt+Scrolls while looking at a screen.
     */
    public void nudgeScreen(int screenId, int axis, int step, boolean fine) {
        VNCScreenInstance instance = screens.get(screenId);
        if (instance == null) return;
        VNCScreenEntity entity = instance.getEntity();
        if (entity == null) return;

        double delta = step * (fine ? 0.01 : 0.1); // 0.01 for Ctrl+Alt, 0.1 for Alt alone
        double nx = entity.getX();
        double ny = entity.getY();
        double nz = entity.getZ();

        switch (axis) {
            case 0 -> nx += delta; // X
            case 1 -> ny += delta; // Y
            case 2 -> nz += delta; // Z
        }

        entity.setPos(nx, ny, nz);

        // Broadcast updated position
        broadcastScreenPlace(instance);
    }

    // ---- Screen Management ----

    /**
     * Place a new screen at the given position and direction.
     * Creates both the server-side instance and the entity.
     * @return the new screen's ID
     */
    public int placeScreen(ServerLevel level, BlockPos pos, Direction face, int resW, int resH) {
        // Check for existing screen at this position
        for (VNCScreenInstance existing : screens.values()) {
            if (existing.getLevel() == level &&
                Math.abs(existing.getX() - pos.getX() - 0.5) < 0.5 &&
                Math.abs(existing.getY() - pos.getY() - 0.5) < 0.5 &&
                Math.abs(existing.getZ() - pos.getZ() - 0.5) < 0.5) {
                return existing.getScreenId();
            }
        }

        int screenId = nextScreenId.getAndIncrement();

        // Create the screen entity
        VNCScreenEntity entity = new VNCScreenEntity(VNCEntities.VNC_SCREEN, level);
        entity.setPlacement(pos, face);
        entity.setScreenId(screenId);
        entity.setResolution(resW, resH);
        level.addFreshEntity(entity);

        // Create the instance using entity's precise position
        VNCScreenInstance instance = new VNCScreenInstance(screenId, level,
                entity.getX(), entity.getY(), entity.getZ(), face, resW, resH, entity);
        screens.put(screenId, instance);

        // Notify all players
        broadcastScreenPlace(instance);
        saveNextId();

        return screenId;
    }

    /**
     * Remove a screen, disconnect VNC, and remove the entity from the world.
     * Idempotent — safe to call multiple times.
     */
    public void removeScreen(int screenId) {
        TunnelManager.getInstance().closeTunnel(screenId);
        VNCScreenInstance instance = screens.remove(screenId);
        if (instance == null) return; // already removed
        instance.disconnect();
        VNCScreenEntity entity = instance.getEntity();
        if (entity != null && entity.isAlive()) {
            entity.discard();
        }
        // Notify clients (vanilla entity removal also syncs, but we send explicit)
        broadcastScreenRemove(screenId);
    }

    /**
     * Connect a screen to a VNC server.
     */
    public void connectScreen(int screenId, String host, int port, String password, int resW, int resH,
                              int audioPort, String cookiePath) {
        VNCScreenInstance instance = screens.get(screenId);
        if (instance == null) return;
        instance.setResolution(resW, resH);
        instance.markReconnectAttempt(); // reset reconnect cooldown
        VNCScreenEntity entity = instance.getEntity();
        if (entity != null) entity.setVncConfig(host, port, password);

        final int sid = screenId;
        if (audioPort > 0) {
            instance.connectAudio(host, audioPort, cookiePath.isEmpty() ? null : cookiePath, (pcm, len) -> {
                byte[] chunk = new byte[len];
                System.arraycopy(pcm, 0, chunk, 0, len);
                server.execute(() -> {
                    var pkt = new S2CVNCAudioPayload(new S2CVNCAudioPayload.Data(sid, chunk));
                    for (ServerPlayer p : server.getPlayerList().getPlayers())
                        ServerPlayNetworking.send(p, pkt);
                });
            });
        }

        System.out.println("[VNCCraft] connectScreen: id=" + sid + " host=" + host + ":" + port + " (tunnel mode)");

        // Open TCP tunnel — server becomes a pure byte pipe, client runs Vernacular
        TunnelManager.getInstance().openTunnel(sid, host, port);

        // Notify all clients that the tunnel is ready — they should auto-start Vernacular
        if (server != null) {
            var readyPayload = new S2CVNCTunnelReadyPayload(
                    new S2CVNCTunnelReadyPayload.Data(sid, password != null ? password : ""));
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(p, readyPayload);
            }
        }
    }

    /**
     * Disconnect a screen from VNC.
     */
    public void disconnectAudio(int screenId) {
        VNCScreenInstance instance = screens.get(screenId);
        if (instance != null) instance.disconnectAudio();
    }

    public void disconnectScreen(int screenId) {
        TunnelManager.getInstance().closeTunnel(screenId);
        VNCScreenInstance instance = screens.get(screenId);
        if (instance != null) {
            instance.disconnect();
            VNCScreenEntity entity = instance.getEntity();
            if (entity != null) entity.setVncConfig("", 5900, "");
            broadcastDisconnect(screenId);
        }
    }

    /**
     * Update screen resolution and notify clients.
     */
    public void updateResolution(int screenId, int resW, int resH) {
        VNCScreenInstance instance = screens.get(screenId);
        if (instance != null) {
            instance.setResolution(resW, resH);
            var payload = new S2CVNCScreenUpdatePayload(
                    new S2CVNCScreenUpdatePayload.Data(screenId, resW, resH));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    // ---- Tick ----

    /**
     * Called every server tick. Checks if newly connected screens have completed
     * their VNC handshake, and sends ServerInit to clients.
     */
    public void onServerTick() {
        for (VNCScreenInstance instance : screens.values()) {
            instance.tick();
            // Auto-reconnect if tunnel is dead but has saved config (5s cooldown)
            // Only reconnect if there are players online to receive the tunnel data
            VNCScreenEntity ent = instance.getEntity();
            if (server.getPlayerCount() > 0
                    && !TunnelManager.getInstance().isTunnelActive(instance.getScreenId())
                    && ent != null && ent.hasVncConfig()
                    && instance.canReconnect()) {
                instance.markReconnectAttempt();
                connectScreen(instance.getScreenId(), ent.getVncHost(), ent.getVncPort(),
                        ent.getVncPassword(), instance.getResolutionW(), instance.getResolutionH(), 0, "");
            }
        }
    }

    // ---- Player Sync ----

    /**
     * Sync all existing screens to a newly joined player.
     */
    public void syncToPlayer(ServerPlayer player) {
        for (VNCScreenInstance instance : screens.values()) {
            var placePayload = new S2CVNCScreenPlacePayload(
                    new S2CVNCScreenPlacePayload.Data(
                            instance.getScreenId(),
                            instance.getX(), instance.getY(), instance.getZ(),
                            instance.getDirection().get3DDataValue(),
                            instance.getResolutionW(),
                            instance.getResolutionH()));
            ServerPlayNetworking.send(player, placePayload);

            // If tunnel is active, tell the new player's client to auto-start Vernacular
            if (TunnelManager.getInstance().isTunnelActive(instance.getScreenId())) {
                VNCScreenEntity ent = instance.getEntity();
                String pwd = ent != null ? ent.getVncPassword() : "";
                var readyPayload = new S2CVNCTunnelReadyPayload(
                        new S2CVNCTunnelReadyPayload.Data(instance.getScreenId(), pwd));
                ServerPlayNetworking.send(player, readyPayload);
            }
        }
    }

    // ---- Broadcasting ----

    private void broadcastScreenPlace(VNCScreenInstance instance) {
        VNCScreenEntity entity = instance.getEntity();
        double x = entity != null ? entity.getX() : instance.getX();
        double y = entity != null ? entity.getY() : instance.getY();
        double z = entity != null ? entity.getZ() : instance.getZ();

        var payload = new S2CVNCScreenPlacePayload(
                new S2CVNCScreenPlacePayload.Data(
                        instance.getScreenId(),
                        x, y, z,
                        instance.getDirection().get3DDataValue(),
                        instance.getResolutionW(),
                        instance.getResolutionH()));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private void broadcastScreenRemove(int screenId) {
        var payload = new S2CVNCScreenRemovePayload(screenId);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private void broadcastDisconnect(int screenId) {
        var payload = new S2CVNCDisconnectPayload(screenId);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    // ---- Frustum Culling ----

    /**
     * Check if any part of the screen is visible to the player's camera.
     * Uses camera position (getEyePosition), not model position, for
     * compatibility with freecam/soul-out mods.
     */
    public static boolean isScreenVisibleToPlayer(VNCScreenInstance screen, ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 screenCenter = screen.getScreenCenter();
        Vec3 screenNormal = screen.getScreenNormal();

        // ① Distance check
        if (eye.distanceTo(screenCenter) > VNCConfig.MAX_VIEW_DISTANCE) {
            return false;
        }

        // ② Back-face culling: skip if camera is behind the screen
        Vec3 toCenter = screenCenter.subtract(eye).normalize();
        double facingDot = screenNormal.dot(toCenter);
        if (facingDot > 0) {
            // Camera is on the back side of the screen (screen normal points away)
            return false;
        }

        // ③ Check if any of the 4 corners fall within the view frustum
        Vec3 right = new Vec3(look.z, 0, -look.x).normalize(); // perpendicular to look in XZ
        Vec3 up = right.cross(look).normalize();
        double hFovRad = Math.toRadians(70);
        double vFovRad = Math.toRadians(60);

        // Calculate the 4 screen corners in world space
        // Screen is oriented perpendicular to its normal
        Vec3 screenUp = new Vec3(0, 1, 0);
        // If the normal is vertical, use a different "up"
        if (Math.abs(screenNormal.y) > 0.9) {
            screenUp = new Vec3(0, 0, 1);
        }
        Vec3 screenRight = screenNormal.cross(screenUp).normalize();
        screenUp = screenRight.cross(screenNormal).normalize();

        float hw = screen.getResolutionW() / 1000f * VNCConfig.BLOCKS_PER_1000PX / 2f;
        float hh = screen.getResolutionH() / 1000f * VNCConfig.BLOCKS_PER_1000PX / 2f;
        hw = Math.max(hw, 0.25f);
        hh = Math.max(hh, 0.25f);

        Vec3[] corners = new Vec3[4];
        corners[0] = screenCenter.add(screenRight.scale(-hw)).add(screenUp.scale(hh));   // top-left
        corners[1] = screenCenter.add(screenRight.scale(-hw)).add(screenUp.scale(-hh));  // bottom-left
        corners[2] = screenCenter.add(screenRight.scale(hw)).add(screenUp.scale(-hh));   // bottom-right
        corners[3] = screenCenter.add(screenRight.scale(hw)).add(screenUp.scale(hh));    // top-right

        for (Vec3 corner : corners) {
            Vec3 toCorner = corner.subtract(eye);
            if (toCorner.dot(look) <= 0) continue; // behind camera

            Vec3 dir = toCorner.normalize();
            double hAngle = Math.atan2(dir.dot(right), dir.dot(look));
            double vAngle = Math.atan2(dir.dot(up), dir.dot(look));

            if (Math.abs(hAngle) < hFovRad / 2 && Math.abs(vAngle) < vFovRad / 2) {
                return true; // at least one corner is visible
            }
        }

        // ④ Cross-frustum scenario: screen may span across the view even if all
        // corners are outside. Simple approximation: check if the screen AABB
        // intersects with the view near plane.
        // For simplicity, return true if the screen is very close and in front.
        double dist = eye.distanceTo(screenCenter);
        double screenDiagonal = Math.sqrt(hw * hw + hh * hh);
        if (dist < screenDiagonal * 2 && toCenter.dot(look) > 0.3) {
            return true;
        }

        return false;
    }
}
