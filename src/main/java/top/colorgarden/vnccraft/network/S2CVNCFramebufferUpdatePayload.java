package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record S2CVNCFramebufferUpdatePayload(Data data) implements CustomPacketPayload {

    public record Data(int screenId, int width, int height, byte[] rgbaPixels) {}

    public static final Type<S2CVNCFramebufferUpdatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_fb_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CVNCFramebufferUpdatePayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.composite(
                            ByteBufCodecs.VAR_INT, Data::screenId,
                            ByteBufCodecs.VAR_INT, Data::width,
                            ByteBufCodecs.VAR_INT, Data::height,
                            ByteBufCodecs.BYTE_ARRAY, Data::rgbaPixels,
                            Data::new),
                    S2CVNCFramebufferUpdatePayload::data,
                    S2CVNCFramebufferUpdatePayload::new
            );

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
