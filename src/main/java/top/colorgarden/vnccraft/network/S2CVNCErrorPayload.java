package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record S2CVNCErrorPayload(Data data) implements CustomPacketPayload {
    public record Data(int screenId, String error) {}
    public static final Type<S2CVNCErrorPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_error"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CVNCErrorPayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.composite(ByteBufCodecs.VAR_INT, Data::screenId,
                            ByteBufCodecs.STRING_UTF8, Data::error, Data::new),
                    S2CVNCErrorPayload::data, S2CVNCErrorPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
