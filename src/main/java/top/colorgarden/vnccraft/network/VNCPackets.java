package top.colorgarden.vnccraft.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Registers all VNCCraft network packet types.
 * All packet classes are public — usable by external code (e.g., Paper plugin port).
 */
public final class VNCPackets {

    private VNCPackets() {}

    public static void registerAll() {
        // S2C (client-bound)
        PayloadTypeRegistry.clientboundPlay().register(S2CVNCServerInitPayload.TYPE, S2CVNCServerInitPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(S2CVNCFramebufferUpdatePayload.TYPE, S2CVNCFramebufferUpdatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(S2CVNCDisconnectPayload.TYPE, S2CVNCDisconnectPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(S2CVNCScreenPlacePayload.TYPE, S2CVNCScreenPlacePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(S2CVNCScreenRemovePayload.TYPE, S2CVNCScreenRemovePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(S2CVNCScreenUpdatePayload.TYPE, S2CVNCScreenUpdatePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(S2CVNCErrorPayload.TYPE, S2CVNCErrorPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(S2CVNCAudioPayload.TYPE, S2CVNCAudioPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(S2CVNCTunnelPayload.TYPE, S2CVNCTunnelPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(S2CVNCTunnelReadyPayload.TYPE, S2CVNCTunnelReadyPayload.CODEC);

        // C2S (server-bound)
        PayloadTypeRegistry.serverboundPlay().register(C2SVNCConnectRequestPayload.TYPE, C2SVNCConnectRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(C2SVNCDisconnectRequestPayload.TYPE, C2SVNCDisconnectRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(C2SVNCScreenConfigPayload.TYPE, C2SVNCScreenConfigPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(C2SVNCPlaceRequestPayload.TYPE, C2SVNCPlaceRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(C2SVNCRemoveRequestPayload.TYPE, C2SVNCRemoveRequestPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(C2SVNCMouseInputPayload.TYPE, C2SVNCMouseInputPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(C2SVNCNudgePayload.TYPE, C2SVNCNudgePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(C2SVNCTunnelPayload.TYPE, C2SVNCTunnelPayload.CODEC);
    }
}
