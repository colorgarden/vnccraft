package top.colorgarden.vnccraft.client.vnc;

import top.colorgarden.vnccraft.config.VNCConfig;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import sashwind.mc.mod.drawlib.client.WorldDraw;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * Manages a single screen's DrawLib WorldDraw instance.
 * Receives pre-decoded RGBA frames from the server (Vernacular VNC).
 */
public class VNCScreenRenderer {

    private WorldDraw worldDraw;
    private int screenId;
    private double posX, posY, posZ;
    private Direction direction;
    private int resW, resH;
    private int serverInitW, serverInitH;
    private boolean initialized = false;

    public double getPosX() { return posX; }
    public double getPosY() { return posY; }
    public double getPosZ() { return posZ; }

    public VNCScreenRenderer(int screenId, double x, double y, double z,
                             Direction direction, int resW, int resH) {
        this.screenId = screenId;
        this.posX = x; this.posY = y; this.posZ = z;
        this.direction = direction;
        this.resW = resW; this.resH = resH;
        this.serverInitW = resW; this.serverInitH = resH;
        this.worldDraw = new WorldDraw((int) x, (int) y, (int) z, VertexFormat.Mode.QUADS);
        ensureInit();
        setPlaceholderTexture();
    }

    private void ensureInit() {
        if (initialized) return;
        worldDraw.init();
        initialized = true;
    }

    private void setPlaceholderTexture() {
        // Use full resolution placeholder to avoid ensureTexture resize on first frame
        int pw = resW, ph = resH;
        byte[] gray = new byte[pw * ph * 4];
        for (int i = 0; i < gray.length; i += 4) {
            gray[i] = 0x44; gray[i+1] = 0x44; gray[i+2] = 0x44; gray[i+3] = (byte)0xFF;
        }
        worldDraw.setTexture(pw, ph, new Buffer[]{ByteBuffer.wrap(gray)});
        updateVertices();
    }

    public int getScreenId() { return screenId; }

    public void onServerInit(int w, int h, int bpp, int depth, boolean big,
                             boolean tc, int rM, int gM, int bM, int rS, int gS, int bS) {
        serverInitW = w; serverInitH = h;
        resW = w; resH = h;
        updateVertices();
    }

    /** Receive pre-decoded RGBA frame from server */
    private int frameCount = 0;

    public void onFrame(byte[] rgbaPixels, int fw, int fh) {
        if (frameCount < 3) {
            System.out.println("[VNCCraft-Renderer] onFrame called: " + fw + "x" + fh + " init=" + initialized + " rgbaLen=" + rgbaPixels.length);
            frameCount++;
        }
        if (!initialized) { System.out.println("[VNCCraft] onFrame SKIP: !init"); return; }
        // if (fw < serverInitW / 4 || fh < serverInitH / 4) { System.out.println("[VNCCraft] onFrame SKIP: tiny"); return; }
        if (fw != resW || fh != resH) { resW = fw; resH = fh; System.out.println("[VNCCraft] onFrame RESIZE: " + fw + "x" + fh); }
        ByteBuffer buf = ByteBuffer.wrap(rgbaPixels).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        worldDraw.setTexture(fw, fh, new Buffer[]{buf});
        
        System.out.println("[VNCCraft] onFrame: texture uploaded, updating verts");
        updateVertices();
        System.out.println("[VNCCraft] onFrame: DONE, worldDraw=" + (worldDraw != null));
    }

    private void updateVertices() {
        worldDraw.clearVertices();
        float aspect = (float) resW / Math.max(1, resH);
        float wh = Math.max(0.25f, resH / 1000f * VNCConfig.BLOCKS_PER_1000PX);
        float ww = aspect * wh, hw = ww/2f, hh = wh/2f;

        Vec3 n = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        Vec3 r, u;
        if (Math.abs(n.y) > 0.9) { r = new Vec3(1,0,0); u = new Vec3(0,0,1); }
        else { r = n.cross(new Vec3(0,1,0)).normalize(); u = r.cross(n).normalize(); }

        Vec3 c = new Vec3(posX, posY, posZ);
        Vec3 tl = c.add(r.scale(-hw)).add(u.scale(hh));
        Vec3 bl = c.add(r.scale(-hw)).add(u.scale(-hh));
        Vec3 br = c.add(r.scale(hw)).add(u.scale(-hh));
        Vec3 tr = c.add(r.scale(hw)).add(u.scale(hh));

        int light = 15;
        // Flipped UV for correct orientation
        worldDraw.addVertices((float)bl.x,(float)bl.y,(float)bl.z, light, 1,1, 1,1,1,1);
        worldDraw.addVertices((float)tl.x,(float)tl.y,(float)tl.z, light, 1,0, 1,1,1,1);
        worldDraw.addVertices((float)tr.x,(float)tr.y,(float)tr.z, light, 0,0, 1,1,1,1);
        worldDraw.addVertices((float)br.x,(float)br.y,(float)br.z, light, 0,1, 1,1,1,1);
    }

    public void cleanup() {
        if (worldDraw != null) { worldDraw.close(); worldDraw = null; }
        initialized = false;
    }

    public void reposition(double x, double y, double z, Direction dir) {
        this.posX = x; this.posY = y; this.posZ = z; this.direction = dir;
        if (initialized) updateVertices();
    }

    public void updateResolution(int rw, int rh) {
        if (worldDraw != null) { worldDraw.close(); }
        this.resW = rw; this.resH = rh;
        // Recreate WorldDraw and re-initialize
        this.worldDraw = new WorldDraw((int) posX, (int) posY, (int) posZ, VertexFormat.Mode.QUADS);
        worldDraw.init();
        initialized = true;
        setPlaceholderTexture();
    }
}
