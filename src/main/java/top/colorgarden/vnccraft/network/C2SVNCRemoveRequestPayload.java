package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SVNCRemoveRequestPayload(int screenId) implements CustomPacketPayload {
    public static final Type<C2SVNCRemoveRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_remove_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SVNCRemoveRequestPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, C2SVNCRemoveRequestPayload::screenId, C2SVNCRemoveRequestPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
