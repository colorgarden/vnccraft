package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SVNCPlaceRequestPayload(Data data) implements CustomPacketPayload {
    public record Data(double x, double y, double z, int direction, int resW, int resH) {}
    public static final Type<C2SVNCPlaceRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_place_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SVNCPlaceRequestPayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.composite(
                            ByteBufCodecs.DOUBLE, Data::x,
                            ByteBufCodecs.DOUBLE, Data::y,
                            ByteBufCodecs.DOUBLE, Data::z,
                            ByteBufCodecs.VAR_INT, Data::direction,
                            ByteBufCodecs.VAR_INT, Data::resW,
                            ByteBufCodecs.VAR_INT, Data::resH,
                            Data::new),
                    C2SVNCPlaceRequestPayload::data, C2SVNCPlaceRequestPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
