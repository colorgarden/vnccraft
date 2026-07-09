package top.colorgarden.vnccraft.network;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * C2S: Nudge screen position (Alt+Scroll / Ctrl+Alt+Scroll).
 * axis: 0=X, 1=Y, 2=Z
 * step: direction (+1 or -1)
 * fine: if true, move 0.01 blocks; else 0.1 blocks
 */
public record C2SVNCNudgePayload(Data data) implements CustomPacketPayload {
    public record Data(int screenId, int axis, int step, boolean fine) {}
    public static final Type<C2SVNCNudgePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(VNCCraft.MOD_ID, "vnc_nudge"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SVNCNudgePayload> CODEC =
            StreamCodec.composite(
                    StreamCodec.composite(
                            ByteBufCodecs.VAR_INT, Data::screenId,
                            ByteBufCodecs.VAR_INT, Data::axis,
                            ByteBufCodecs.VAR_INT, Data::step,
                            ByteBufCodecs.BOOL, Data::fine,
                            Data::new),
                    C2SVNCNudgePayload::data, C2SVNCNudgePayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
