package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record S2CVNCScreenPlacePayload(Data data) implements CustomPacketPayload {
    public record Data(int screenId, double x, double y, double z, int direction, int resW, int resH) {}
    public static final Type<S2CVNCScreenPlacePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_screen_place"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CVNCScreenPlacePayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.composite(
                            ByteBufCodecs.VAR_INT, Data::screenId,
                            ByteBufCodecs.DOUBLE, Data::x,
                            ByteBufCodecs.DOUBLE, Data::y,
                            ByteBufCodecs.DOUBLE, Data::z,
                            ByteBufCodecs.VAR_INT, Data::direction,
                            ByteBufCodecs.VAR_INT, Data::resW,
                            ByteBufCodecs.VAR_INT, Data::resH,
                            Data::new),
                    S2CVNCScreenPlacePayload::data, S2CVNCScreenPlacePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
