package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record C2SVNCConnectRequestPayload(Data data) implements CustomPacketPayload {
    public record Data(int screenId, String host, int port, String password, int resW, int resH,
                       int audioPort, String cookiePath) {}
    public static final Type<C2SVNCConnectRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_connect_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SVNCConnectRequestPayload> CODEC =
            StreamCodec.of(C2SVNCConnectRequestPayload::encode, C2SVNCConnectRequestPayload::decode);
    private static void encode(RegistryFriendlyByteBuf b, C2SVNCConnectRequestPayload p) {
        var d = p.data();
        ByteBufCodecs.VAR_INT.encode(b, d.screenId());
        ByteBufCodecs.STRING_UTF8.encode(b, d.host());
        ByteBufCodecs.VAR_INT.encode(b, d.port());
        ByteBufCodecs.STRING_UTF8.encode(b, d.password());
        ByteBufCodecs.VAR_INT.encode(b, d.resW());
        ByteBufCodecs.VAR_INT.encode(b, d.resH());
        ByteBufCodecs.VAR_INT.encode(b, d.audioPort());
        ByteBufCodecs.STRING_UTF8.encode(b, d.cookiePath());
    }
    private static C2SVNCConnectRequestPayload decode(RegistryFriendlyByteBuf b) {
        return new C2SVNCConnectRequestPayload(new Data(
                ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b),
                ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b),
                ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.VAR_INT.decode(b),
                ByteBufCodecs.VAR_INT.decode(b), ByteBufCodecs.STRING_UTF8.decode(b)));
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
