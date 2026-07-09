package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record S2CVNCScreenUpdatePayload(Data data) implements CustomPacketPayload {
    public record Data(int screenId, int resW, int resH) {}
    public static final Type<S2CVNCScreenUpdatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_screen_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CVNCScreenUpdatePayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.composite(
                            ByteBufCodecs.VAR_INT, Data::screenId,
                            ByteBufCodecs.VAR_INT, Data::resW,
                            ByteBufCodecs.VAR_INT, Data::resH,
                            Data::new),
                    S2CVNCScreenUpdatePayload::data, S2CVNCScreenUpdatePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
