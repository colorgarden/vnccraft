package top.colorgarden.vnccraft.client.vnc;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * Client-side local framebuffer with VNC rectangle decoder.
 * Tight decoding based on TigerVNC reference implementation.
 */
public class VNCClientFramebuffer {

    private static final int ENCODING_RAW = 0;
    private static final int ENCODING_TIGHT = 7;
    private static final int ENCODING_ZRLE = 16;
    private static final int ENCODING_DESKTOP_SIZE = 0xFFFFFF11;

    private static final int TIGHT_FILL = 0;
    private static final int TIGHT_JPEG = 1;
    private static final int TIGHT_FILTER_PALETTE = 1;
    private static final int TIGHT_FILTER_GRADIENT = 2;
    private static final int TIGHT_MIN_TO_COMPRESS = 12;

    private int width, height;
    private byte[] pixels; // RGBA

    private int srcBitsPerPixel, srcDepth;
    private boolean srcBigEndian, srcTrueColor;
    private int srcRedMax, srcGreenMax, srcBlueMax;
    private int srcRedShift, srcGreenShift, srcBlueShift;

    // Zlib streams for Tight (4 streams: 0-3)
    private final Inflater[] zlibStreams = new Inflater[4];

    public VNCClientFramebuffer() {
        for (int i = 0; i < 4; i++) zlibStreams[i] = new Inflater();
    }

    public void init(int w, int h, int bpp, int depth, boolean bigEndian, boolean trueColor,
                     int redMax, int greenMax, int blueMax, int redShift, int greenShift, int blueShift) {
        this.width = w; this.height = h;
        this.pixels = new byte[w * h * 4];
        this.srcBitsPerPixel = bpp; this.srcDepth = depth;
        this.srcBigEndian = bigEndian; this.srcTrueColor = trueColor;
        this.srcRedMax = redMax; this.srcGreenMax = greenMax; this.srcBlueMax = blueMax;
        this.srcRedShift = redShift; this.srcGreenShift = greenShift; this.srcBlueShift = blueShift;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public byte[] getPixels() { return pixels; }

    public void decodeAndApply(byte[] rawRectData) {
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(rawRectData));
            int numRects = dis.readUnsignedShort();
            System.out.printf("[VNCCraft] decodeAndApply: %d rects, rawData=%d bytes, fb=%dx%d\n",
                    numRects, rawRectData.length, width, height);
            for (int i = 0; i < numRects; i++) {
                int rx = dis.readUnsignedShort(), ry = dis.readUnsignedShort();
                int rw = dis.readUnsignedShort(), rh = dis.readUnsignedShort();
                int encoding = dis.readInt();
                if (encoding == ENCODING_DESKTOP_SIZE) {
                    this.width = rw; this.height = rh;
                    this.pixels = new byte[rw * rh * 4];
                    continue;
                }
                int dataLen = dis.readInt();
                byte[] rectData = new byte[dataLen];
                dis.readFully(rectData);
                if (encoding == ENCODING_RAW)
                    applyRaw(rx, ry, rw, rh, rectData);
                else if (encoding == ENCODING_TIGHT)
                    decodeTight(rx, ry, rw, rh, rectData);
                else if (encoding == ENCODING_ZRLE)
                    decodeZRLE(rx, ry, rw, rh, rectData);
            }
        } catch (IOException e) {
            System.err.println("[VNCCraft] Framebuffer decode error: " + e.getMessage());
        }
    }

    // ---- Raw ----
    private void applyRaw(int x, int y, int w, int h, byte[] data) {
        int bpp = srcBitsPerPixel / 8;
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int px = x + col, py = y + row;
                if (px < 0 || px >= width || py < 0 || py >= height) continue;
                int si = (row * w + col) * bpp;
                int pixel = 0;
                if (srcBigEndian)
                    for (int b = 0; b < bpp; b++) pixel = (pixel << 8) | (data[si + b] & 0xFF);
                else
                    for (int b = bpp - 1; b >= 0; b--) pixel = (pixel << 8) | (data[si + b] & 0xFF);

                int r, g, b;
                if (srcTrueColor) {
                    r = ((pixel >> srcRedShift) & srcRedMax) * 255 / srcRedMax;
                    g = ((pixel >> srcGreenShift) & srcGreenMax) * 255 / srcGreenMax;
                    b = ((pixel >> srcBlueShift) & srcBlueMax) * 255 / srcBlueMax;
                } else {
                    r = g = b = pixel * 255 / ((1 << srcDepth) - 1);
                }
                int di = (py * width + px) * 4;
                pixels[di] = (byte) r;
                pixels[di + 1] = (byte) g;
                pixels[di + 2] = (byte) b;
                pixels[di + 3] = (byte) 0xFF;
            }
        }
    }

    // ---- Tight (TigerVNC decodeRect) ----
    private void decodeTight(int x, int y, int w, int h, byte[] data) {
        try {
            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));
            int compCtl = dis.readUnsignedByte();
            int subencoding = compCtl >> 4;
            int bpp = srcBitsPerPixel / 8;

            // Reset zlib streams as indicated by lower 4 bits
            for (int i = 0; i < 4; i++) {
                if ((compCtl & (1 << i)) != 0) { zlibStreams[i].reset(); }
            }

            if (subencoding == 0) { // Fill
                byte[] fill = new byte[bpp == 4 ? 3 : bpp];
                dis.readFully(fill);
                int pixel = bpp == 4 ? buildFillPixel24(fill) : buildPixel(fill, 0);
                for (int row = 0; row < h; row++)
                    for (int col = 0; col < w; col++)
                        setPixel(x + col, y + row, pixel);
                return;
            }

            if (subencoding == 1) { // JPEG — not supported client-side, skip
                int jpegLen = dis.readInt();
                dis.skipBytes(jpegLen);
                return;
            }

            // Basic compression
            boolean explicitFilter = (subencoding & 4) != 0;
            int filterId = 0, palSize = 0;
            int[][] palette = null;

            if (explicitFilter) {
                filterId = dis.readUnsignedByte();
                if (filterId == 1) { // palette
                    palSize = dis.readUnsignedByte() + 1;
                    palette = new int[palSize][];
                    int palBpp = bpp == 4 ? 3 : bpp;
                    for (int p = 0; p < palSize; p++) {
                        byte[] pb = new byte[palBpp];
                        dis.readFully(pb);
                        palette[p] = new int[]{bpp == 4 ? buildFillPixel24(pb) : buildPixel(pb, 0)};
                    }
                }
            }

            int rowSize;
            if (palSize != 0) {
                rowSize = palSize <= 2 ? (w + 7) / 8 : w;
            } else if (bpp == 4) {
                rowSize = w * 3;
            } else {
                rowSize = w * bpp;
            }
            int dataSize = h * rowSize;

            byte[] decoded;
            if (dataSize < 12) {
                decoded = new byte[dataSize];
                dis.readFully(decoded);
            } else {
                int zipLen = dis.readInt();
                byte[] compressed = new byte[zipLen];
                dis.readFully(compressed);
                int streamId = compCtl & 0x03;
                resetZlibStream(streamId);
                decoded = inflateStream(streamId, compressed, dataSize);
                if (decoded == null) return;
            }

            // Decode pixel data
            if (palSize == 0) {
                if (filterId == 2 && bpp == 4) // gradient 24-bit
                    applyGradient24(x, y, w, h, decoded);
                else if (filterId == 2) // gradient
                    applyRaw(x, y, w, h, decoded);
                else
                    applyRaw(x, y, w, h, decoded);
            } else {
                applyPalette(x, y, w, h, decoded, palette, palSize);
            }
        } catch (IOException e) {
            System.err.println("[VNCCraft] Tight decode error: " + e.getMessage());
        }
    }

    private int buildFillPixel24(byte[] rgb) {
        return (rgb[0] & 0xFF) | ((rgb[1] & 0xFF) << 8) | ((rgb[2] & 0xFF) << 16);
    }

    private void applyPalette(int x, int y, int w, int h, byte[] data, int[][] palette, int palSize) {
        int srcPos = 0;
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int idx;
                if (palSize <= 2) {
                    int byteIdx = row * ((w + 7) / 8) + col / 8;
                    int bitIdx = 7 - (col % 8);
                    if (byteIdx >= data.length) continue;
                    idx = (data[byteIdx] >> bitIdx) & 1;
                } else {
                    if (srcPos >= data.length) continue;
                    idx = data[srcPos++] & 0xFF;
                }
                if (idx < palSize) setPixel(x + col, y + row, palette[idx][0]);
            }
        }
    }

    private void applyGradient24(int x, int y, int w, int h, byte[] data) {
        byte[] prevRow = new byte[w * 3], thisRow = new byte[w * 3];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                int si = (row * w + col) * 3;
                int r, g, b;
                if (col == 0) {
                    r = (data[si] & 0xFF) + (prevRow[0] & 0xFF);
                    g = (data[si + 1] & 0xFF) + (prevRow[1] & 0xFF);
                    b = (data[si + 2] & 0xFF) + (prevRow[2] & 0xFF);
                } else {
                    int pr = prevRow[col * 3] & 0xFF, pg = prevRow[col * 3 + 1] & 0xFF, pb = prevRow[col * 3 + 2] & 0xFF;
                    r = clamp((data[si] & 0xFF) + pr + (thisRow[(col - 1) * 3] & 0xFF) - (prevRow[(col - 1) * 3] & 0xFF));
                    g = clamp((data[si + 1] & 0xFF) + pg + (thisRow[(col - 1) * 3 + 1] & 0xFF) - (prevRow[(col - 1) * 3 + 1] & 0xFF));
                    b = clamp((data[si + 2] & 0xFF) + pb + (thisRow[(col - 1) * 3 + 2] & 0xFF) - (prevRow[(col - 1) * 3 + 2] & 0xFF));
                }
                thisRow[col * 3] = (byte) r; thisRow[col * 3 + 1] = (byte) g; thisRow[col * 3 + 2] = (byte) b;
                setPixelRaw(x + col, y + row, r, g, b);
            }
            System.arraycopy(thisRow, 0, prevRow, 0, w * 3);
        }
    }

    private void setPixelRaw(int px, int py, int r, int g, int b) {
        if (px < 0 || px >= width || py < 0 || py >= height) return;
        int di = (py * width + px) * 4;
        pixels[di] = (byte) r; pixels[di + 1] = (byte) g;
        pixels[di + 2] = (byte) b; pixels[di + 3] = (byte) 0xFF;
    }

    private int clamp(int v) { return v < 0 ? 0 : Math.min(v, 255); }

    private void resetZlibStream(int id) { zlibStreams[id].reset(); }

    private byte[] inflateStream(int id, byte[] compressed, int expectedSize) {
        try {
            zlibStreams[id].setInput(compressed);
            byte[] buf = new byte[expectedSize];
            int total = 0;
            while (!zlibStreams[id].finished() && total < expectedSize) {
                int n = zlibStreams[id].inflate(buf, total, expectedSize - total);
                if (n == 0) break;
                total += n;
            }
            if (total == expectedSize) return buf;
            if (total > 0) { byte[] result = new byte[total]; System.arraycopy(buf, 0, result, 0, total); return result; }
            return null;
        } catch (Exception e) { return null; }
    }

    // ---- ZRLE ----
    private void decodeZRLE(int x, int y, int w, int h, byte[] relayData) {
        byte[] decompressed = inflate(relayData);
        if (decompressed == null) return;
        int bpp = srcBitsPerPixel / 8;
        int srcPos = 0;
        for (int ty = 0; ty < h; ty += 64) {
            for (int tx = 0; tx < w; tx += 64) {
                int tw = Math.min(64, w - tx), th = Math.min(64, h - ty);
                if (srcPos >= decompressed.length) return;
                int se = decompressed[srcPos++] & 0xFF;
                if (se == 0) {
                    for (int row = 0; row < th; row++) {
                        for (int col = 0; col < tw; col++) {
                            int px = tx + col, py = ty + row;
                            if (px >= width || py >= height) continue;
                            int si = srcPos + (row * tw + col) * bpp;
                            if (si + bpp > decompressed.length) break;
                            int pixel = buildPixel(decompressed, si);
                            setPixel(px, py, pixel);
                        }
                    }
                    srcPos += tw * th * bpp;
                } else if (se == 1) {
                    if (srcPos + bpp > decompressed.length) return;
                    int pixel = buildPixel(decompressed, srcPos);
                    for (int row = 0; row < th; row++)
                        for (int col = 0; col < tw; col++)
                            setPixel(tx + col, ty + row, pixel);
                    srcPos += bpp;
                } else {
                    int palSize = se;
                    int[][] palette = new int[palSize][];
                    for (int p = 0; p < palSize; p++) {
                        if (srcPos + bpp > decompressed.length) return;
                        palette[p] = new int[]{buildPixel(decompressed, srcPos)};
                        srcPos += bpp;
                    }
                    int remaining = tw * th;
                    while (remaining > 0 && srcPos < decompressed.length) {
                        int runInfo = decompressed[srcPos++] & 0xFF;
                        int runLen, palIdx;
                        if ((runInfo & 0x80) != 0) {
                            runLen = (runInfo & 0x7F) + 1;
                            if (srcPos >= decompressed.length) break;
                            palIdx = decompressed[srcPos++] & 0xFF;
                        } else {
                            runLen = runInfo + 1; palIdx = 0;
                        }
                        for (int r = 0; r < runLen && remaining > 0; r++, remaining--) {
                            int idx = tw * th - remaining;
                            int px = tx + idx % tw, py = ty + idx / tw;
                            if (palIdx < palSize) setPixel(px, py, palette[palIdx][0]);
                        }
                    }
                }
            }
        }
    }

    private int buildPixel(byte[] data, int offset) {
        int bpp = srcBitsPerPixel / 8;
        int pixel = 0;
        if (srcBigEndian)
            for (int b = 0; b < bpp; b++) pixel = (pixel << 8) | (data[offset + b] & 0xFF);
        else
            for (int b = bpp - 1; b >= 0; b--) pixel = (pixel << 8) | (data[offset + b] & 0xFF);
        return pixel;
    }

    private void setPixel(int px, int py, int pixel) {
        if (px < 0 || px >= width || py < 0 || py >= height) return;
        int r, g, b;
        if (srcTrueColor) {
            r = ((pixel >> srcRedShift) & srcRedMax) * 255 / srcRedMax;
            g = ((pixel >> srcGreenShift) & srcGreenMax) * 255 / srcGreenMax;
            b = ((pixel >> srcBlueShift) & srcBlueMax) * 255 / srcBlueMax;
        } else {
            r = g = b = pixel * 255 / ((1 << srcDepth) - 1);
        }
        int di = (py * width + px) * 4;
        pixels[di] = (byte) r; pixels[di + 1] = (byte) g;
        pixels[di + 2] = (byte) b; pixels[di + 3] = (byte) 0xFF;
    }

    private static byte[] inflate(byte[] data) {
        try {
            Inflater inf = new Inflater();
            inf.setInput(data);
            byte[] buf = new byte[Math.max(data.length * 4, 4096)];
            int len = inf.inflate(buf); inf.end();
            byte[] result = new byte[len];
            System.arraycopy(buf, 0, result, 0, len);
            return result;
        } catch (Exception e) { return null; }
    }
}
