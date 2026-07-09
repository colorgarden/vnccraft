package top.colorgarden.vnccraft.plugin.network;

import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Encodes/decodes VNCCraft packets for Plugin Messaging Channels.
 * Byte format matches Fabric's ByteBufCodecs for cross-compatibility.
 */
public final class VNCProtocolLib {

    private VNCProtocolLib() {}

    // ---- Encode methods ----

    public static byte[] encodeInt(int v) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(v);
        return bufBytes(buf);
    }

    public static byte[] encodeScreenPlace(int id, double x, double y, double z, int dir, int rw, int rh) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(id);
        buf.writeDouble(x); buf.writeDouble(y); buf.writeDouble(z);
        buf.writeVarInt(dir);
        buf.writeVarInt(rw); buf.writeVarInt(rh);
        return bufBytes(buf);
    }

    public static byte[] encodeServerInit(int id, int w, int h, int bpp, int depth, boolean big, boolean tc,
                                           int rM, int gM, int bM, int rS, int gS, int bS) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(id);
        buf.writeVarInt(w); buf.writeVarInt(h);
        buf.writeVarInt(bpp); buf.writeVarInt(depth);
        buf.writeBoolean(big); buf.writeBoolean(tc);
        buf.writeVarInt(rM); buf.writeVarInt(gM); buf.writeVarInt(bM);
        buf.writeVarInt(rS); buf.writeVarInt(gS); buf.writeVarInt(bS);
        return bufBytes(buf);
    }

    public static byte[] encodeFrameUpdate(int id, int w, int h, byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(id);
        buf.writeVarInt(w); buf.writeVarInt(h);
        buf.writeByteArray(data);
        return bufBytes(buf);
    }

    // ---- Decode methods (for incoming C2S packets) ----

    public record ConnectRequest(int screenId, String host, int port, String password, int resW, int resH,
                                  int audioPort, String cookiePath) {}

    public static ConnectRequest decodeConnectRequest(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        return new ConnectRequest(
                buf.readVarInt(), buf.readUtf(), buf.readVarInt(),
                buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                buf.readVarInt(), buf.readUtf());
    }

    public static int decodeDisconnectRequest(byte[] data) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(data)).readVarInt();
    }

    public record NudgeRequest(int screenId, int axis, int step, boolean fine) {}

    public static NudgeRequest decodeNudgeRequest(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        return new NudgeRequest(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public record MouseInput(int screenId, int x, int y, int buttonMask) {}

    public static MouseInput decodeMouseInput(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        return new MouseInput(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public record PlaceRequest(double x, double y, double z, int direction, int resW, int resH) {}

    public static PlaceRequest decodePlaceRequest(byte[] data) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(data));
        return new PlaceRequest(buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    // ---- Tunnel encoding ----

    public static byte[] encodeTunnelS2C(int screenId, byte[] rawData) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(screenId);
        buf.writeByteArray(rawData);
        return bufBytes(buf);
    }

    public static byte[] encodeTunnelReady(int screenId, String password) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(screenId);
        buf.writeUtf(password != null ? password : "");
        return bufBytes(buf);
    }

    public static byte[] encodeAudio(int screenId, byte[] pcmData) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(screenId);
        buf.writeByteArray(pcmData);
        return bufBytes(buf);
    }

    public record TunnelC2S(int screenId, byte[] data) {}

    public static TunnelC2S decodeTunnelC2S(byte[] rawData) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.wrappedBuffer(rawData));
        return new TunnelC2S(buf.readVarInt(), buf.readByteArray());
    }

    // ---- Helpers ----

    private static byte[] bufBytes(FriendlyByteBuf buf) {
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);
        return bytes;
    }
}
