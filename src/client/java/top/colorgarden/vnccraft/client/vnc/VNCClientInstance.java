package top.colorgarden.vnccraft.client.vnc;

import com.shinyhut.vernacular.client.VernacularClient;
import com.shinyhut.vernacular.client.VernacularConfig;
import com.shinyhut.vernacular.client.rendering.ColorDepth;
import top.colorgarden.vnccraft.network.C2SVNCTunnelPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Client-side VNC instance that runs Vernacular over the MC network tunnel.
 *
 * Uses a tunnel-backed IO pair:
 * - TunnelInputStream: reads bytes queued by S2CVNCTunnelPayload handler
 * - TunnelOutputStream: writes bytes → C2SVNCTunnelPayload → server → TCP → VNC server
 *
 * Frames from Vernacular's screenUpdateListener are passed to VNCScreenRenderer
 * for DrawLib rendering.
 */
public class VNCClientInstance {

    private final int screenId;
    private final VernacularClient client;
    private final TunnelInputStream tunnelIn;
    private final TunnelOutputStream tunnelOut;
    private final VNCScreenRenderer renderer;

    private volatile boolean running;
    private int framebufferWidth, framebufferHeight;

    public VNCClientInstance(int screenId, VNCScreenRenderer renderer, String password) {
        this.screenId = screenId;
        this.renderer = renderer;
        this.tunnelIn = new TunnelInputStream();
        this.tunnelOut = new TunnelOutputStream(screenId);

        VernacularConfig cfg = new VernacularConfig();
        cfg.setColorDepth(ColorDepth.BPP_24_TRUE);
        cfg.setTargetFramesPerSecond(30);
        if (password != null && !password.isEmpty()) {
            cfg.setPasswordSupplier(() -> password);
        }
        cfg.setScreenUpdateListener(image -> {
            System.out.println("[VNCCraft-Client] Frame received from Vernacular");
            BufferedImage bi = (BufferedImage) image;
            int fw = bi.getWidth(), fh = bi.getHeight();
            System.out.println("[VNCCraft-Client] Frame size: " + fw + "x" + fh);
            if (fw != framebufferWidth || fh != framebufferHeight) {
                framebufferWidth = fw;
                framebufferHeight = fh;
            }
            int[] rgb = bi.getRGB(0, 0, fw, fh, null, 0, fw);
            byte[] rgba = new byte[fw * fh * 4];
            for (int i = 0; i < rgb.length; i++) {
                int c = rgb[i];
                rgba[i * 4]     = (byte) ((c >> 16) & 0xFF);
                rgba[i * 4 + 1] = (byte) ((c >> 8) & 0xFF);
                rgba[i * 4 + 2] = (byte) (c & 0xFF);
                rgba[i * 4 + 3] = (byte) 0xFF;
            }
            // Upload must happen on render thread (DrawLib requirement)
            net.minecraft.client.Minecraft.getInstance().execute(() ->
                    renderer.onFrame(rgba, fw, fh));
        });
        cfg.setErrorListener(e -> {
            String msg = "VNC screen #" + screenId + " error: " + e.getMessage();
            System.err.println("[VNCCraft-Client] " + msg);
            running = false;
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.sendSystemMessage(
                        net.minecraft.network.chat.Component.literal("§c" + msg));
            }
        });

        this.client = new VernacularClient(cfg);
    }

    /**
     * Start the Vernacular client over the tunnel.
     */
    public void connect() {
        if (running) return;
        running = true;
        new Thread(() -> {
            try {
                client.start(tunnelIn, tunnelOut);
            } catch (Exception e) {
                System.err.println("[VNCCraft-Client] Failed to start VNC for screen " + screenId + ": " + e.getMessage());
                running = false;
            }
        }, "VNC-Client-" + screenId).start();
    }

    /**
     * Stop the Vernacular client and clean up.
     */
    public void disconnect() {
        running = false;
        try {
            client.stop();
        } catch (Exception ignored) {}
        tunnelIn.close();
    }

    public boolean isRunning() { return running; }

    // ---- Mouse / Keyboard (delegates through Vernacular → tunnel) ----

    public void moveMouse(int x, int y) {
        if (running) client.moveMouse(x, y);
    }

    public void updateMouseButton(int button, boolean pressed) {
        if (running) client.updateMouseButton(button, pressed);
    }

    public void click(int button) {
        if (running) client.click(button);
    }

    public void scrollUp() {
        if (running) client.scrollUp();
    }

    public void scrollDown() {
        if (running) client.scrollDown();
    }

    public void updateKey(int keySym, boolean pressed) {
        if (running) client.updateKey(keySym, pressed);
    }

    // ---- Tunnel data feed (called from packet handler) ----

    /**
     * Feed bytes received from the VNC server (via S2C tunnel packet) into
     * the input stream that Vernacular reads from.
     */
    private int feedCount = 0;

    public void feedFromServer(byte[] data) {
        if (feedCount < 5) {
            System.out.println("[VNCCraft-Client] Tunnel data received: " + data.length + " bytes (feed #" + (feedCount + 1) + ")");
            feedCount++;
        }
        tunnelIn.feed(data);
    }

    // ---- Tunnel IO streams ----

    /**
     * InputStream that reads from a BlockingQueue fed by S2C tunnel packets.
     */
    private static class TunnelInputStream extends InputStream {
        private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        private byte[] currentChunk;
        private int pos;
        private volatile boolean closed;

        void feed(byte[] data) {
            if (!closed && data != null && data.length > 0) {
                queue.offer(data);
            }
        }

        public void close() {
            closed = true;
            queue.offer(new byte[0]); // wake up any blocking read
        }

        @Override
        public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n == -1 ? -1 : b[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (closed && queue.isEmpty() && currentChunk == null) return -1;

            // If we have a current chunk, drain it first
            if (currentChunk != null && pos < currentChunk.length) {
                int available = currentChunk.length - pos;
                int toCopy = Math.min(len, available);
                System.arraycopy(currentChunk, pos, b, off, toCopy);
                pos += toCopy;
                if (pos >= currentChunk.length) {
                    currentChunk = null;
                    pos = 0;
                }
                return toCopy;
            }

            // Need a new chunk — block until data arrives
            // NOTE: must never return 0 — VNC protocol treats read()==0 as FramebufferUpdate msg type!
            try {
                currentChunk = queue.take(); // block until data or close signal
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }

            if (currentChunk.length == 0) {
                currentChunk = null;
                return -1; // close signal
            }
            pos = 0;
            // Recurse to copy from the new chunk
            return read(b, off, len);
        }

        @Override
        public int available() {
            if (currentChunk != null) return currentChunk.length - pos;
            byte[] next = queue.peek();
            return next != null ? next.length : 0;
        }
    }

    /**
     * OutputStream that sends bytes to the VNC server via C2S tunnel packets.
     */
    private static class TunnelOutputStream extends OutputStream {
        private final int screenId;

        TunnelOutputStream(int screenId) {
            this.screenId = screenId;
        }

        @Override
        public void write(int b) throws IOException {
            write(new byte[]{(byte) b}, 0, 1);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            byte[] data = new byte[len];
            System.arraycopy(b, off, data, 0, len);
            ClientPlayNetworking.send(new C2SVNCTunnelPayload(
                    new C2SVNCTunnelPayload.Data(screenId, data)));
        }
    }
}
