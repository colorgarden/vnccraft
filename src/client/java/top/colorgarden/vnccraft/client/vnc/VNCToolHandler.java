package top.colorgarden.vnccraft.client.vnc;

import top.colorgarden.vnccraft.config.VNCConfig;
import top.colorgarden.vnccraft.entity.VNCScreenEntity;
import top.colorgarden.vnccraft.network.C2SVNCNudgePayload;
import top.colorgarden.vnccraft.network.C2SVNCPlaceRequestPayload;
import top.colorgarden.vnccraft.network.C2SVNCRemoveRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Litematica-style mode switching via Ctrl+Scroll:
 * - Mode PLACE: shovel operations (place/delete/configure)
 * - Mode LASER: laser pointer (view ray = VNC cursor, L/R click = VNC)
 * - Alt+Scroll: nudge (0.1), Shift+Alt+Scroll: fine nudge (0.01) — always available
 */
public class VNCToolHandler {

    public enum Mode { PLACE, LASER }

    private static final VNCToolHandler INSTANCE = new VNCToolHandler();
    public static VNCToolHandler getInstance() { return INSTANCE; }

    private Mode currentMode = Mode.PLACE;

    /** Tab+Scroll → VNC scroll on screen under crosshair */
    public void onTabScroll(double scrollY) {
        VNCScreenEntity screen = getScreenAtCrosshair();
        if (screen == null) return;
        VNCClientInstance vnc = VNCClientRendererManager.getInstance().getVNCInstance(screen.getScreenId());
        if (vnc == null || !vnc.isRunning()) return;
        // Send scroll with proper press+release pair through Vernacular
        if (scrollY > 0) vnc.scrollUp(); else vnc.scrollDown();
    }

    private VNCToolHandler() {}

    public Mode getMode() { return currentMode; }
    public void cycleMode() { currentMode = currentMode == Mode.PLACE ? Mode.LASER : Mode.PLACE; }

    private sashwind.mc.mod.drawlib.client.WorldDraw laserDraw;
    private boolean laserActive = false;

    public void registerEvents() {
        UseEntityCallback.EVENT.register(this::onUseEntity);
        UseBlockCallback.EVENT.register(this::onUseBlock);
        AttackEntityCallback.EVENT.register(this::onAttackEntity);
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(net.minecraft.client.Minecraft mc) {
        if (!isHoldingTool() || currentMode != Mode.LASER) {
            if (laserActive) { if (laserDraw != null) laserDraw.close(); laserDraw = null; laserActive = false; }
            return;
        }
        if (mc.player == null) return;

        VNCScreenEntity screen = getScreenAtCrosshair();
        if (screen == null) {
            if (laserActive) { if (laserDraw != null) laserDraw.close(); laserDraw = null; laserActive = false; }
            return;
        }

        // Compute VNC coordinates from view ray
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
        Vec3 center = screen.getScreenCenter();
        Direction dir = screen.getScreenDirection();
        Vec3 n = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        double denom = look.dot(n);
        if (Math.abs(denom) < 0.001) return;
        double t = (center.subtract(eye)).dot(n) / denom;
        if (t <= 0) return;
        Vec3 hitPoint = eye.add(look.scale(t));
        Vec3 rel = hitPoint.subtract(center);
        Vec3 sr, su;
        if (Math.abs(n.y) > 0.9) { sr = new Vec3(1,0,0); su = new Vec3(0,0,1); }
        else { sr = n.cross(new Vec3(0,1,0)).normalize(); su = sr.cross(n).normalize(); }
        float aspect = (float)screen.getResW()/Math.max(1,screen.getResH());
        float wh = Math.max(0.25f, screen.getResH()/1000f*3.0f), ww = aspect*wh;
        double u = 1.0 - (rel.dot(sr)/(ww/2) + 1.0)/2.0;
        double v = (1.0 - rel.dot(su)/(wh/2))/2.0;
        int vx = Math.clamp((int)(u*screen.getResW()), 0, screen.getResW()-1);
        int vy = Math.clamp((int)(v*screen.getResH()), 0, screen.getResH()-1);

        // Laser beam line
        if (!laserActive) {
            laserDraw = new sashwind.mc.mod.drawlib.client.WorldDraw(0, 0, 0, com.mojang.blaze3d.vertex.VertexFormat.Mode.LINES);
            laserDraw.init(); laserActive = true;
        }
        laserDraw.clearVertices();
        laserDraw.addVertices((float)eye.x, (float)eye.y, (float)eye.z, 15, 0,0, 1,0,0,1);
        laserDraw.addVertices((float)hitPoint.x, (float)hitPoint.y, (float)hitPoint.z, 15, 0,0, 1,0,0,1);

        // Button state — send through Vernacular tunnel
        long w = ((top.colorgarden.vnccraft.client.mixin.WindowAccessor)(Object)mc.getWindow()).getHandle();
        VNCClientInstance vnc = VNCClientRendererManager.getInstance().getVNCInstance(screen.getScreenId());
        if (vnc != null && vnc.isRunning()) {
            vnc.moveMouse(vx, vy);
            vnc.updateMouseButton(1, org.lwjgl.glfw.GLFW.glfwGetMouseButton(w, 0) == 1);
            vnc.updateMouseButton(2, org.lwjgl.glfw.GLFW.glfwGetMouseButton(w, 2) == 1);  // middle
            vnc.updateMouseButton(3, org.lwjgl.glfw.GLFW.glfwGetMouseButton(w, 1) == 1);  // right
        }

        // Tab+Middle-click → VNC middle-click
        if (org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) == 1) {
            if (mc.options.keyPickItem.consumeClick()) {
                if (screen != null) {
                    sendMouseInput(screen, 2); // middle button
                }
            }
        }
    }

    /** Ctrl+Scroll: cycle mode. Alt+Scroll/Shift+Alt: nudge. */
    public void onMouseScroll(double scrollY, boolean ctrl, boolean alt, boolean shift) {
        if (!isHoldingTool()) return;
        if (ctrl) { cycleMode(); return; }
        if (alt) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.hitResult == null) return;
            Entity t = mc.hitResult instanceof EntityHitResult ehr ? ehr.getEntity() : null;
            if (!(t instanceof VNCScreenEntity screen)) return;
            int axis = dominantAxis(mc.player.getLookAngle());
            int step = scrollY > 0 ? 1 : -1;
            ClientPlayNetworking.send(new C2SVNCNudgePayload(
                    new C2SVNCNudgePayload.Data(screen.getScreenId(), axis, step, shift)));
        }
    }

    private InteractionResult onUseEntity(Player p, Level w, InteractionHand h, Entity e, @Nullable EntityHitResult hit) {
        if (!(w instanceof net.minecraft.client.multiplayer.ClientLevel)) return InteractionResult.PASS;
        if (h != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(e instanceof VNCScreenEntity screen)) return InteractionResult.PASS;

        if (isHoldingTool()) {
            if (currentMode == Mode.LASER) {
                return InteractionResult.FAIL; // laser mode: tick handler manages buttons
            }
            // PLACE mode: right-click screen — do nothing (use block click for place)
            return InteractionResult.PASS;
        } else {
            if (p.isShiftKeyDown()) {
                Minecraft.getInstance().setScreen(
                        new top.colorgarden.vnccraft.client.screen.VNCConnectScreen(screen.getScreenId(), screen.getVncHost(), screen.getVncPort()));
                return InteractionResult.SUCCESS;
            }
            sendMouseInput(screen, 3);  // VNC right-click
            return InteractionResult.FAIL;
        }
    }

    private InteractionResult onUseBlock(Player p, Level w, InteractionHand h, BlockHitResult hit) {
        if (!(w instanceof net.minecraft.client.multiplayer.ClientLevel)) return InteractionResult.PASS;
        if (h != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!isHoldingTool() || currentMode != Mode.PLACE) return InteractionResult.PASS;

        BlockPos pos = hit.getBlockPos().relative(hit.getDirection());
        Direction face = hit.getDirection();
        ClientPlayNetworking.send(new C2SVNCPlaceRequestPayload(
                new C2SVNCPlaceRequestPayload.Data(pos.getX(), pos.getY(), pos.getZ(),
                        face.get3DDataValue(), VNCConfig.DEFAULT_RES_WIDTH, VNCConfig.DEFAULT_RES_HEIGHT)));
        return InteractionResult.FAIL;
    }

    private InteractionResult onAttackEntity(Player p, Level w, InteractionHand h, Entity e, @Nullable EntityHitResult hit) {
        if (!(w instanceof net.minecraft.client.multiplayer.ClientLevel)) return InteractionResult.PASS;
        if (h != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(e instanceof VNCScreenEntity screen)) return InteractionResult.PASS;

        if (isHoldingTool()) {
            if (currentMode == Mode.LASER) {
                return InteractionResult.FAIL; // laser mode: tick handler manages buttons
            }
            // PLACE mode: left-click screen → delete
            ClientPlayNetworking.send(new C2SVNCRemoveRequestPayload(screen.getScreenId()));
            return InteractionResult.FAIL;
        } else {
            sendMouseInput(screen, 1);
            return InteractionResult.FAIL;
        }
    }

    private static void sendMouseInput(VNCScreenEntity screen, int vncButton) {
        VNCClientInstance vnc = VNCClientRendererManager.getInstance().getVNCInstance(screen.getScreenId());
        if (vnc == null || !vnc.isRunning()) return;
        // Compute screen coordinates from crosshair
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition(), look = mc.player.getLookAngle();
        Vec3 center = screen.getScreenCenter();
        Direction dir = screen.getScreenDirection();
        Vec3 n = new Vec3(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        double denom = look.dot(n);
        if (Math.abs(denom) < 0.001) return;
        double t = (center.subtract(eye)).dot(n) / denom;
        if (t <= 0) return;
        Vec3 rel = eye.add(look.scale(t)).subtract(center);
        Vec3 sr, su;
        if (Math.abs(n.y) > 0.9) { sr = new Vec3(1,0,0); su = new Vec3(0,0,1); }
        else { sr = n.cross(new Vec3(0,1,0)).normalize(); su = sr.cross(n).normalize(); }
        float aspect = (float)screen.getResW()/Math.max(1,screen.getResH());
        float wh = Math.max(0.25f, screen.getResH()/1000f*3.0f), ww = aspect*wh;
        double u = 1.0 - (rel.dot(sr)/(ww/2) + 1.0)/2.0;
        double v = (1.0 - rel.dot(su)/(wh/2))/2.0;
        int vx = (int)(u*screen.getResW()), vy = (int)(v*screen.getResH());
        vx = Math.clamp(vx, 0, screen.getResW()-1);
        vy = Math.clamp(vy, 0, screen.getResH()-1);
        vnc.moveMouse(vx, vy);
        vnc.click(vncButton);
    }

    public static boolean isHoldingTool() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;
        Identifier heldId = BuiltInRegistries.ITEM.getKey(player.getMainHandItem().getItem());
        return heldId.toString().equals(VNCConfig.TOOL_ITEM);
    }

    private static VNCScreenEntity getScreenAtCrosshair() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.hitResult instanceof EntityHitResult ehr && ehr.getEntity() instanceof VNCScreenEntity se) return se;
        return null;
    }

    private static int dominantAxis(Vec3 dir) {
        double ax = Math.abs(dir.x), ay = Math.abs(dir.y), az = Math.abs(dir.z);
        if (ax >= ay && ax >= az) return 0;
        if (ay >= ax && ay >= az) return 1;
        return 2;
    }
}
