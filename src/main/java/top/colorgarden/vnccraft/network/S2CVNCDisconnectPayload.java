package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record S2CVNCDisconnectPayload(int screenId) implements CustomPacketPayload {
    public static final Type<S2CVNCDisconnectPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_disconnect"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CVNCDisconnectPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, S2CVNCDisconnectPayload::screenId, S2CVNCDisconnectPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
