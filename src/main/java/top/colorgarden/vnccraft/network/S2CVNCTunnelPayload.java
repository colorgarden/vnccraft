package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: Tunnel raw bytes from the VNC server to the client via the MC server.
 * The MC server reads bytes from the VNC TCP socket and forwards them verbatim
 * to the client.
 */
public record S2CVNCTunnelPayload(Data data) implements CustomPacketPayload {
    public record Data(int screenId, byte[] rawBytes) {}
    public static final Type<S2CVNCTunnelPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_tunnel_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, S2CVNCTunnelPayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.composite(ByteBufCodecs.VAR_INT, Data::screenId,
                            ByteBufCodecs.BYTE_ARRAY, Data::rawBytes, Data::new),
                    S2CVNCTunnelPayload::data, S2CVNCTunnelPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
