package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S: Tunnel raw bytes from client to the VNC server via the MC server.
 * The MC server forwards these bytes verbatim to the VNC TCP socket.
 */
public record C2SVNCTunnelPayload(Data data) implements CustomPacketPayload {
    public record Data(int screenId, byte[] rawBytes) {}
    public static final Type<C2SVNCTunnelPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_tunnel_c2s"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SVNCTunnelPayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.composite(ByteBufCodecs.VAR_INT, Data::screenId,
                            ByteBufCodecs.BYTE_ARRAY, Data::rawBytes, Data::new),
                    C2SVNCTunnelPayload::data, C2SVNCTunnelPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
