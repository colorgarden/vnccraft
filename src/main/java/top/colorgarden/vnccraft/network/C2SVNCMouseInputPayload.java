package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S: Forward a mouse click to VNC server as PointerEvent.
 * screenId: target screen
 * x, y: pixel coordinates within the VNC framebuffer
 * buttonMask: VNC button mask (bit0=left, bit2=right)
 */
public record C2SVNCMouseInputPayload(Data data) implements CustomPacketPayload {
    public record Data(int screenId, int x, int y, int buttonMask) {}
    public static final Type<C2SVNCMouseInputPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_mouse_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SVNCMouseInputPayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.composite(
                            ByteBufCodecs.VAR_INT, Data::screenId,
                            ByteBufCodecs.VAR_INT, Data::x,
                            ByteBufCodecs.VAR_INT, Data::y,
                            ByteBufCodecs.VAR_INT, Data::buttonMask,
                            Data::new),
                    C2SVNCMouseInputPayload::data, C2SVNCMouseInputPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
