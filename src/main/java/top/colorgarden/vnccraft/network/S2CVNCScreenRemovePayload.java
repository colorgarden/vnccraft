package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record S2CVNCScreenRemovePayload(int screenId) implements CustomPacketPayload {
    public static final Type<S2CVNCScreenRemovePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_screen_remove"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CVNCScreenRemovePayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, S2CVNCScreenRemovePayload::screenId, S2CVNCScreenRemovePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
