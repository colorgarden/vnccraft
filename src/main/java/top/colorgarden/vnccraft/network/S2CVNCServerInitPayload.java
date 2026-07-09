package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: Informs client of VNC server framebuffer dimensions and pixel format.
 */
public record S2CVNCServerInitPayload(Data data) implements CustomPacketPayload {

    public record Data(int screenId, int width, int height,
                       int bitsPerPixel, int depth, boolean bigEndian, boolean trueColor,
                       int redMax, int greenMax, int blueMax,
                       int redShift, int greenShift, int blueShift) {}

    public static final Type<S2CVNCServerInitPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_server_init"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CVNCServerInitPayload> CODEC =
            StreamCodec.of(S2CVNCServerInitPayload::encode, S2CVNCServerInitPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buf, S2CVNCServerInitPayload payload) {
        Data d = payload.data();
        ByteBufCodecs.VAR_INT.encode(buf, d.screenId());
        ByteBufCodecs.VAR_INT.encode(buf, d.width());
        ByteBufCodecs.VAR_INT.encode(buf, d.height());
        ByteBufCodecs.VAR_INT.encode(buf, d.bitsPerPixel());
        ByteBufCodecs.VAR_INT.encode(buf, d.depth());
        ByteBufCodecs.BOOL.encode(buf, d.bigEndian());
        ByteBufCodecs.BOOL.encode(buf, d.trueColor());
        ByteBufCodecs.VAR_INT.encode(buf, d.redMax());
        ByteBufCodecs.VAR_INT.encode(buf, d.greenMax());
        ByteBufCodecs.VAR_INT.encode(buf, d.blueMax());
        ByteBufCodecs.VAR_INT.encode(buf, d.redShift());
        ByteBufCodecs.VAR_INT.encode(buf, d.greenShift());
        ByteBufCodecs.VAR_INT.encode(buf, d.blueShift());
    }

    private static S2CVNCServerInitPayload decode(RegistryFriendlyByteBuf buf) {
        return new S2CVNCServerInitPayload(new Data(
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.BOOL.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf)
        ));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
