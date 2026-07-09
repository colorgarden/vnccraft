package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record S2CVNCAudioPayload(Data data) implements CustomPacketPayload {
    public record Data(int screenId, byte[] pcmData) {}
    public static final Type<S2CVNCAudioPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_audio"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CVNCAudioPayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.composite(ByteBufCodecs.VAR_INT, Data::screenId,
                            ByteBufCodecs.BYTE_ARRAY, Data::pcmData, Data::new),
                    S2CVNCAudioPayload::data, S2CVNCAudioPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
