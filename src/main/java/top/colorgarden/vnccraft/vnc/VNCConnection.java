package top.colorgarden.vnccraft.vnc;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VNC RFB protocol client that connects to a VNC server on a background thread.
 * It handles handshake + authentication, then relays raw FramebufferUpdate
 * data to a callback WITHOUT decoding pixel data.
 */
public class VNCConnection implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger("VNCCraft-VNC");

    // RFB constants
    private static final int SECURITY_NONE = 1;
    private static final int SECURITY_VNC_AUTH = 2;

    private static final int MSG_FRAMEBUFFER_UPDATE = 0;
    private static final int MSG_SET_COLOUR_MAP_ENTRIES = 1;
    private static final int MSG_BELL = 2;
    private static final int MSG_SERVER_CUT_TEXT = 3;

    // Encoding types we request
    private static final int ENCODING_RAW = 0;
    private static final int ENCODING_TIGHT = 7;
    private static final int ENCODING_ZRLE = 16;
    private static final int ENCODING_DESKTOP_SIZE = 0xFFFFFF11;

    private final String host;
    private final int port;
    private final String password;
    private final FrameUpdateCallback callback;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private Thread workerThread;

    // Framebuffer info from ServerInit
    private int framebufferWidth;
    private int framebufferHeight;
    private int bitsPerPixel;
    private int depth;
    private boolean bigEndian;
    private boolean trueColor;
    private int redMax, greenMax, blueMax;
    private int redShift, greenShift, blueShift;
    private String desktopName;

    // Connection state
    private volatile boolean connected = false;
    private volatile boolean initComplete = false;
    private volatile String lastError = null;

    public String getLastError() { return lastError; }

    // Configurable
    private int targetFps = 15;
    private int socketTimeoutMs = 3000;

    /**
     * Callback invoked when a FramebufferUpdate is received.
     * The rawBytes contain the full FramebufferUpdate payload (number-of-rectangles
     * followed by rectangle headers and pixel data), exactly as received from the
     * VNC server.
     */
    @FunctionalInterface
    public interface FrameUpdateCallback {
        void onFrameUpdate(byte[] rawRectData, int screenWidth, int screenHeight);
    }

    public VNCConnection(String host, int port, String password, FrameUpdateCallback callback) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.callback = callback;
    }

    // ---- Getters ----

    public boolean isConnected() { return connected; }
    public boolean isInitComplete() { return initComplete; }
    public int getFramebufferWidth() { return framebufferWidth; }
    public int getFramebufferHeight() { return framebufferHeight; }
    public int getBitsPerPixel() { return bitsPerPixel; }
    public int getDepth() { return depth; }
    public boolean isBigEndian() { return bigEndian; }
    public boolean isTrueColor() { return trueColor; }
    public int getRedMax() { return redMax; }
    public int getGreenMax() { return greenMax; }
    public int getBlueMax() { return blueMax; }
    public int getRedShift() { return redShift; }
    public int getGreenShift() { return greenShift; }
    public int getBlueShift() { return blueShift; }
    public String getDesktopName() { return desktopName; }

    public void setTargetFps(int fps) { this.targetFps = Math.max(1, Math.min(30, fps)); }
    public void setSocketTimeoutMs(int ms) { this.socketTimeoutMs = ms; }

    // ---- Lifecycle ----

    public void connect() {
        if (running.get()) return;
        running.set(true);
        workerThread = new Thread(this, "VNC-Connection-" + host + ":" + port);
        workerThread.setDaemon(true);
        workerThread.start();
    }

    public void disconnect() {
        running.set(false);
        connected = false;
        initComplete = false;
        closeSocket();
        if (workerThread != null) {
            workerThread.interrupt();
        }
    }

    @Override
    public void run() {
        try {
            // Phase 0: TCP connect
            socket = new Socket();
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(host, port), socketTimeoutMs);
            socket.setSoTimeout(200); // short timeout for responsive shutdown checks
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());

            // Phase 1: Protocol version exchange
            handshakeProtocolVersion();

            // Phase 2: Security handshake
            handshakeSecurity();

            // Phase 3: ClientInit
            sendClientInit();

            // Phase 4: ServerInit
            receiveServerInit();
            initComplete = true;

            // Signal ServerInit info to caller via callback with null data
            // (handled externally by checking initComplete)

            // Request initial full update
            requestFramebufferUpdate(false);
            out.flush();

            // Phase 5: Main loop
            mainLoop();

        } catch (IOException e) {
            lastError = e.getMessage();
            LOGGER.error("VNC connection error to {}:{} - {}", host, port, e.getMessage());
        } catch (Exception e) {
            lastError = e.getMessage();
            LOGGER.error("VNC protocol error: {}", e.getMessage(), e);
        } finally {
            connected = false;
            initComplete = false;
            closeSocket();
        }
    }

    // ---- RFB Protocol Phases ----

    private void handshakeProtocolVersion() throws IOException {
        // Read server version (12 bytes: "RFB 003.008\n")
        byte[] versionBuf = new byte[12];
        in.readFully(versionBuf);
        String serverVersion = new String(versionBuf, java.nio.charset.StandardCharsets.US_ASCII);
        LOGGER.info("VNC server version: {}", serverVersion.trim());

        // Parse "RFB mmm.nnn\n"
        String[] parts = serverVersion.trim().substring(4).split("\\.");
        int major = Integer.parseInt(parts[0].trim());
        int minor = Integer.parseInt(parts[1].trim());

        // We request 3.8 if supported, else 3.3
        if (major >= 3 && minor >= 8) {
            out.write("RFB 003.008\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.flush();
        } else {
            out.write("RFB 003.003\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            out.flush();
            // For 3.3, security type follows immediately
            // handled in handshakeSecurity
        }
    }

    private void handshakeSecurity() throws IOException {
        // For RFB 3.8+, server sends number of security types
        // then the type list. We read and select VNC Auth (2).
        int numTypes = in.readUnsignedByte();
        if (numTypes == 0) {
            // Connection failed: read reason
            int reasonLen = in.readInt();
            byte[] reasonBytes = new byte[reasonLen];
            in.readFully(reasonBytes);
            throw new IOException("VNC server refused: " + new String(reasonBytes));
        }

        int[] types = new int[numTypes];
        for (int i = 0; i < numTypes; i++) {
            types[i] = in.readUnsignedByte();
        }

        // Select VNC Auth (2) if available, else try None (1)
        int selectedType = -1;
        for (int t : types) {
            if (t == SECURITY_VNC_AUTH) { selectedType = t; break; }
        }
        if (selectedType == -1) {
            for (int t : types) {
                if (t == SECURITY_NONE) { selectedType = t; break; }
            }
        }
        if (selectedType == -1) {
            StringBuilder sb = new StringBuilder();
            for (int t : types) sb.append(t).append(",");
            throw new IOException("No supported security type. Server offers: " + sb + ". Need VNC Auth (2) or None (1).");
        }

        out.writeByte(selectedType);
        out.flush();

        if (selectedType == SECURITY_VNC_AUTH) {
            performVNCAuthentication();
        }
        // For None, nothing more to do

        // Read security result
        int result = in.readInt();
        if (result != 0) {
            if (result == 1) {
                int reasonLen = in.readInt();
                byte[] reasonBytes = new byte[reasonLen];
                in.readFully(reasonBytes);
                throw new IOException("VNC authentication failed: " + new String(reasonBytes));
            }
            throw new IOException("VNC security failed with status: " + result);
        }
    }

    private void performVNCAuthentication() throws IOException {
        // Read 16-byte challenge
        byte[] challenge = new byte[16];
        in.readFully(challenge);

        // Encrypt with password
        byte[] response = VNCDesCipher.encrypt(challenge, password);

        // Send encrypted response
        out.write(response);
        out.flush();
    }

    private void sendClientInit() throws IOException {
        // shared-flag = 1 (shared session)
        out.writeByte(1);
        out.flush();
    }

    private void receiveServerInit() throws IOException {
        framebufferWidth = in.readUnsignedShort();
        framebufferHeight = in.readUnsignedShort();

        // Pixel format (16 bytes)
        bitsPerPixel = in.readUnsignedByte();
        depth = in.readUnsignedByte();
        bigEndian = in.readUnsignedByte() != 0;
        trueColor = in.readUnsignedByte() != 0;
        redMax = in.readUnsignedShort();
        greenMax = in.readUnsignedShort();
        blueMax = in.readUnsignedShort();
        redShift = in.readUnsignedByte();
        greenShift = in.readUnsignedByte();
        blueShift = in.readUnsignedByte();
        in.skipBytes(3); // padding

        // Desktop name
        int nameLen = in.readInt();
        byte[] nameBytes = new byte[nameLen];
        in.readFully(nameBytes);
        desktopName = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8);

        connected = true;
        initComplete = true;
        LOGGER.info("VNC connected: {}x{} bpp={} depth={} name={}",
                framebufferWidth, framebufferHeight, bitsPerPixel, depth, desktopName);

        // Send SetPixelFormat: request 32bpp TrueColor RGB
        sendSetPixelFormat();

        // Send SetEncodings: prefer Tight, ZRLE, then Raw
        sendSetEncodings();
    }

    private void sendSetPixelFormat() throws IOException {
        out.writeByte(0); // message type 0 = SetPixelFormat
        out.writeByte(0); // padding
        out.writeByte(0); // padding
        out.writeByte(0); // padding
        // Request 32bpp true color, little-endian BGR
        out.writeByte(32); // bits-per-pixel
        out.writeByte(24); // depth
        out.writeByte(0);  // big-endian = false (little-endian)
        out.writeByte(1);  // true-color = true
        out.writeShort(255); // red-max
        out.writeShort(255); // green-max
        out.writeShort(255); // blue-max
        out.writeByte(16); // red-shift (R at byte 2 in little-endian BGR)
        out.writeByte(8);  // green-shift
        out.writeByte(0);  // blue-shift (B at byte 0 in little-endian)
        out.writeByte(0);  // padding
        out.writeByte(0);  // padding
        out.writeByte(0);  // padding
        out.flush();
    }

    private void sendSetEncodings() throws IOException {
        int[] encodings = { ENCODING_TIGHT, ENCODING_ZRLE, ENCODING_RAW, ENCODING_DESKTOP_SIZE };
        out.writeByte(2); // message type 2 = SetEncodings
        out.writeByte(0); // padding
        out.writeShort(encodings.length);
        for (int enc : encodings) {
            out.writeInt(enc);
        }
        out.flush();
    }

    // ---- Main Loop ----

    private void mainLoop() throws IOException {
        long frameInterval = 1000L / targetFps;

        while (running.get() && connected) {
            long loopStart = System.currentTimeMillis();

            // Request framebuffer update (incremental after first full update)
            requestFramebufferUpdate(true);

            // Process incoming server messages
            try {
                processServerMessages();
            } catch (java.net.SocketTimeoutException e) {
                // Timeout is expected — just means no data ready
            }

            // Sleep to meet target FPS
            long elapsed = System.currentTimeMillis() - loopStart;
            long sleepMs = frameInterval - elapsed;
            if (sleepMs > 0) {
                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void requestFramebufferUpdate(boolean incremental) throws IOException {
        out.writeByte(3); // FramebufferUpdateRequest
        out.writeByte(incremental ? 1 : 0);
        out.writeShort(0); // x
        out.writeShort(0); // y
        out.writeShort(framebufferWidth);
        out.writeShort(framebufferHeight);
    }

    private void processServerMessages() throws IOException {
        int msgType = in.readUnsignedByte();

        switch (msgType) {
            case MSG_FRAMEBUFFER_UPDATE:
                processFramebufferUpdate();
                break;
            case MSG_SET_COLOUR_MAP_ENTRIES:
                // Skip: 1 byte padding + 2 bytes first-color + 2 bytes count + color data
                in.skipBytes(1);
                int firstColor = in.readUnsignedShort();
                int numColors = in.readUnsignedShort();
                // Each colour entry is 6 bytes (R2 G2 B2)
                in.skipBytes(numColors * 6);
                break;
            case MSG_BELL:
                // Bell — ignore
                break;
            case MSG_SERVER_CUT_TEXT:
                // Skip: 3 bytes padding + 4 bytes length + text
                in.skipBytes(3);
                int textLen = in.readInt();
                in.skipBytes(textLen);
                break;
            default:
                LOGGER.warn("Unknown VNC server message type: {}", msgType);
                break;
        }
    }

    private void processFramebufferUpdate() throws IOException {
        // Skip 1 byte padding
        in.readByte();

        // Number of rectangles
        int numRects = in.readUnsignedShort();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(baos);
        dos.writeShort(numRects);

        for (int i = 0; i < numRects; i++) {
            int rx = in.readUnsignedShort(), ry = in.readUnsignedShort();
            int rw = in.readUnsignedShort(), rh = in.readUnsignedShort();
            int enc = in.readInt();

            dos.writeShort(rx); dos.writeShort(ry);
            dos.writeShort(rw); dos.writeShort(rh);
            dos.writeInt(enc);

            if (enc == ENCODING_DESKTOP_SIZE) {
                framebufferWidth = rw; framebufferHeight = rh;
                continue;
            }
            // TigerVNC: pass encoding to decoder which reads the right byte count
            byte[] data = tightReadRect(enc, rw, rh, bitsPerPixel / 8);
            dos.writeInt(data.length);
            dos.write(data);
        }

        dos.flush();
        if (callback != null)
            callback.onFrameUpdate(baos.toByteArray(), framebufferWidth, framebufferHeight);
    }

    /** TigerVNC TightDecoder::readRect byte-accurate reading for relay */
    private byte[] tightReadRect(int enc, int w, int h, int bpp) throws IOException {
        if (enc != ENCODING_TIGHT && enc != ENCODING_ZRLE) {
            // Raw or unknown: read w*h*bpp bytes
            int len = w * h * bpp;
            return len > 0 ? in.readNBytes(len) : new byte[0];
        }
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        if (enc == ENCODING_ZRLE) {
            int zl = in.readInt();
            bos.write(new byte[]{(byte)(zl>>>24),(byte)(zl>>>16),(byte)(zl>>>8),(byte)zl});
            bos.write(in.readNBytes(zl));
            return bos.toByteArray();
        }
        // Tight encoding
        int ctl = in.readUnsignedByte(); bos.write(ctl);
        int sub = ctl >> 4;
        if (sub == 0) { // Fill
            int n = bpp == 4 ? 3 : bpp; bos.write(in.readNBytes(n));
        } else if (sub == 1) { // JPEG
            int jl = readCompactLen(in); bos.write(encodeInt32(jl)); bos.write(in.readNBytes(jl));
        } else { // Basic
            int filter = 0, pal = 0;
            if ((sub & 4) != 0) { filter = in.readUnsignedByte(); bos.write(filter); }
            if (filter == 1) { pal = in.readUnsignedByte() + 1; bos.write(pal - 1);
                int pb = bpp == 4 ? 3 : bpp; bos.write(in.readNBytes(pal * pb)); }
            int row = (pal != 0) ? (pal <= 2 ? (w+7)/8 : w) : (bpp == 4 ? w*3 : w*bpp);
            int ds = h * row;
            if (ds < 12) { bos.write(in.readNBytes(ds)); }
            else { int zl = readCompactLen(in); bos.write(encodeInt32(zl)); bos.write(in.readNBytes(zl)); }
        }
        return bos.toByteArray();
    }

    private int readCompactLen(DataInputStream in) throws IOException {
        int b = in.readUnsignedByte(), r = b & 0x7F;
        if ((b & 0x80) != 0) { b = in.readUnsignedByte(); r |= (b & 0x7F) << 7;
        if ((b & 0x80) != 0) { b = in.readUnsignedByte(); r |= (b & 0xFF) << 14; } }
        return r;
    }
    private static byte[] encodeInt32(int v) {
        return new byte[]{(byte)(v>>>24),(byte)(v>>>16),(byte)(v>>>8),(byte)v};
    }

    /**
     * Send a VNC PointerEvent to the remote server.
     * Thread-safe — synchronized to prevent concurrent writes with main loop.
     */
    public synchronized void sendPointerEvent(int x, int y, int buttonMask) {
        if (!connected || out == null) return;
        try {
            out.writeByte(5); // PointerEvent
            out.writeByte(buttonMask);
            out.writeShort(x);
            out.writeShort(y);
            out.flush();
        } catch (IOException e) {
            LOGGER.warn("Failed to send VNC pointer event: {}", e.getMessage());
        }
    }

    /**
     * Send a VNC KeyEvent to the remote server.
     */
    public synchronized void sendKeyEvent(int keysym, boolean down) {
        if (!connected || out == null) return;
        try {
            out.writeByte(4); // KeyEvent
            out.writeByte(down ? 1 : 0);
            out.writeByte(0); // padding
            out.writeByte(0); // padding
            out.writeInt(keysym);
            out.flush();
        } catch (IOException e) {
            LOGGER.warn("Failed to send VNC key event: {}", e.getMessage());
        }
    }

    // ---- Internal helpers ----

    private void closeSocket() {
        try { if (in != null) in.close(); } catch (IOException ignored) {}
        try { if (out != null) out.close(); } catch (IOException ignored) {}
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        in = null;
        out = null;
        socket = null;
    }
}
