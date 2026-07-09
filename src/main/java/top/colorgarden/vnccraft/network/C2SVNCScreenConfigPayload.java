package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SVNCScreenConfigPayload(Data data) implements CustomPacketPayload {
    public record Data(int screenId, int resW, int resH) {}
    public static final Type<C2SVNCScreenConfigPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_screen_config"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SVNCScreenConfigPayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.composite(
                            ByteBufCodecs.VAR_INT, Data::screenId,
                            ByteBufCodecs.VAR_INT, Data::resW,
                            ByteBufCodecs.VAR_INT, Data::resH,
                            Data::new),
                    C2SVNCScreenConfigPayload::data, C2SVNCScreenConfigPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
