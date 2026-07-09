package top.colorgarden.vnccraft.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import top.colorgarden.vnccraft.vnc.VNCManager;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Invisible entity for VNC screen hit detection and right-click interaction.
 * Rendering is done by DrawLib's WorldDraw.
 */
public class VNCScreenEntity extends Entity {

    private static final EntityDataAccessor<Integer> DATA_SCREEN_ID =
            SynchedEntityData.defineId(VNCScreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DIRECTION =
            SynchedEntityData.defineId(VNCScreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RES_W =
            SynchedEntityData.defineId(VNCScreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RES_H =
            SynchedEntityData.defineId(VNCScreenEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> DATA_VNC_HOST =
            SynchedEntityData.defineId(VNCScreenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_VNC_PORT =
            SynchedEntityData.defineId(VNCScreenEntity.class, EntityDataSerializers.INT);

    // VNC config — plain fields for disk save/load, EntityData for client sync
    private String vncHost = "";
    private int vncPort = 5900;
    private String vncPassword = "";
    private int audioPort = 0;
    private String audioCookie = "";

    public VNCScreenEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public void setVncConfig(String host, int port, String password) {
        this.vncHost = host; this.vncPort = port; this.vncPassword = password;
        entityData.set(DATA_VNC_HOST, host);
        entityData.set(DATA_VNC_PORT, port);
    }
    public String getVncHost() { return entityData.get(DATA_VNC_HOST); }
    public int getVncPort() { return entityData.get(DATA_VNC_PORT); }
    public String getVncPassword() { return vncPassword; }
    public int getAudioPort() { return audioPort; }
    public String getAudioCookie() { return audioCookie; }
    public boolean hasVncConfig() { return !getVncHost().isEmpty(); }

    public void setAudioConfig(int port, String cookie) {
        this.audioPort = port; this.audioCookie = cookie;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_SCREEN_ID, 0);
        builder.define(DATA_DIRECTION, 0);
        builder.define(DATA_RES_W, 1920);
        builder.define(DATA_RES_H, 1080);
        builder.define(DATA_VNC_HOST, "");
        builder.define(DATA_VNC_PORT, 5900);
    }

    public int getScreenId() { return entityData.get(DATA_SCREEN_ID); }
    public void setScreenId(int id) { entityData.set(DATA_SCREEN_ID, id); }

    public Direction getScreenDirection() {
        return Direction.from3DDataValue(entityData.get(DATA_DIRECTION));
    }
    public void setScreenDirection(Direction dir) {
        entityData.set(DATA_DIRECTION, dir.get3DDataValue());
    }

    public int getResW() { return entityData.get(DATA_RES_W); }
    public int getResH() { return entityData.get(DATA_RES_H); }
    public void setResolution(int w, int h) {
        entityData.set(DATA_RES_W, w);
        entityData.set(DATA_RES_H, h);
        updateHitbox();
    }

    public Vec3 getScreenCenter() {
        return new Vec3(getX(), getY(), getZ());
    }

    public void setPlacement(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;
        // Push entity outward past the block surface, screen faces outward
        double offset = 0.55;
        setPos(x + face.getStepX() * offset, y + face.getStepY() * offset, z + face.getStepZ() * offset);
        setScreenDirection(face);
        updateHitbox();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 clickPos) {
        return InteractionResult.SUCCESS;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * Update hitbox to match screen resolution.
     */
    public void updateHitbox() {
        float aspect = (float) getResW() / (float) Math.max(1, getResH());
        float worldH = Math.max(0.25f, getResH() / 1000f * top.colorgarden.vnccraft.config.VNCConfig.BLOCKS_PER_1000PX);
        float worldW = aspect * worldH;
        float hw = worldW / 2f;
        float hh = worldH / 2f;

        Direction dir = getScreenDirection();
        Vec3 normal = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        Vec3 screenRight, screenUp;
        if (Math.abs(normal.y) > 0.9) {
            screenRight = new Vec3(1, 0, 0);
            screenUp = new Vec3(0, 0, 1);
        } else {
            screenRight = normal.cross(new Vec3(0, 1, 0)).normalize();
            screenUp = screenRight.cross(normal).normalize();
        }

        Vec3 center = new Vec3(getX(), getY(), getZ());
        Vec3 tl = center.add(screenRight.scale(-hw)).add(screenUp.scale(hh));
        Vec3 br = center.add(screenRight.scale(hw)).add(screenUp.scale(-hh));

        // Add small thickness along the normal direction
        double thickness = 0.0025;
        Vec3 nudge = normal.scale(thickness);
        Vec3 min = new Vec3(
                Math.min(tl.x, br.x) - Math.abs(nudge.x),
                Math.min(tl.y, br.y) - Math.abs(nudge.y),
                Math.min(tl.z, br.z) - Math.abs(nudge.z));
        Vec3 max = new Vec3(
                Math.max(tl.x, br.x) + Math.abs(nudge.x),
                Math.max(tl.y, br.y) + Math.abs(nudge.y),
                Math.max(tl.z, br.z) + Math.abs(nudge.z));

        setBoundingBox(new AABB(min.x, min.y, min.z, max.x, max.y, max.z));
    }

    @Override
    public void onSyncedDataUpdated(net.minecraft.network.syncher.EntityDataAccessor<?> accessor) {
        super.onSyncedDataUpdated(accessor);
        if (DATA_RES_W.equals(accessor) || DATA_RES_H.equals(accessor) || DATA_DIRECTION.equals(accessor)) {
            updateHitbox();
        }
    }

    // ---- MC 26.1.2 required abstract methods ----

    @Override
    public boolean hurtServer(net.minecraft.server.level.ServerLevel level,
                              net.minecraft.world.damagesource.DamageSource source, float amount) {
        // Screens are only removed via the shovel tool, not by direct damage
        return false;
    }

    // ---- MC 26.1.2 required methods ----

    @Override
    public void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput output) {
        output.putInt("ScreenId", getScreenId());
        output.putInt("Direction", getScreenDirection().get3DDataValue());
        output.putInt("ResW", getResW());
        output.putInt("ResH", getResH());
        output.putString("VncHost", getVncHost());
        output.putInt("VncPort", getVncPort());
        output.putString("VncPassword", vncPassword);
        output.putInt("AudioPort", audioPort);
        output.putString("AudioCookie", audioCookie);
        System.out.println("[VNCCraft] Entity saved: id=" + getScreenId() + " host=" + vncHost + " port=" + vncPort + " audio=" + audioPort);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput input) {
        setScreenId(input.getIntOr("ScreenId", 0));
        setScreenDirection(Direction.from3DDataValue(input.getIntOr("Direction", 0)));
        entityData.set(DATA_RES_W, input.getIntOr("ResW", 1920));
        entityData.set(DATA_RES_H, input.getIntOr("ResH", 1080));
        vncHost = input.getStringOr("VncHost", "");
        vncPort = input.getIntOr("VncPort", 5900);
        vncPassword = input.getStringOr("VncPassword", "");
        audioPort = input.getIntOr("AudioPort", 0);
        audioCookie = input.getStringOr("AudioCookie", "");
        System.out.println("[VNCCraft] Entity loaded from save: id=" + getScreenId() + " host=" + vncHost + " port=" + vncPort + " audio=" + audioPort);
        entityData.set(DATA_VNC_HOST, vncHost);
        entityData.set(DATA_VNC_PORT, vncPort);
        updateHitbox();
        if (level() instanceof net.minecraft.server.level.ServerLevel) {
            VNCManager mgr = VNCManager.getInstance();
            if (mgr != null && !mgr.hasScreen(getScreenId())) {
                mgr.registerFromEntity(this);
            }
        }
    }
}
