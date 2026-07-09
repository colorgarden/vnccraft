package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: Notifies the client that a VNC tunnel is ready for a screen.
 * The client should start its local Vernacular instance to complete the handshake.
 */
public record S2CVNCTunnelReadyPayload(Data data) implements CustomPacketPayload {
    public record Data(int screenId, String password) {}
    public static final Type<S2CVNCTunnelReadyPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_tunnel_ready"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CVNCTunnelReadyPayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.composite(ByteBufCodecs.VAR_INT, Data::screenId,
                            ByteBufCodecs.STRING_UTF8, Data::password, Data::new),
                    S2CVNCTunnelReadyPayload::data, S2CVNCTunnelReadyPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
