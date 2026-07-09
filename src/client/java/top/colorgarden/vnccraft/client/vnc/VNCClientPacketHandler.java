package top.colorgarden.vnccraft.client.vnc;

import top.colorgarden.vnccraft.client.audio.VNCAudioPlayer;
import top.colorgarden.vnccraft.network.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;

public class VNCClientPacketHandler {

    private static final VNCClientPacketHandler INSTANCE = new VNCClientPacketHandler();
    public static VNCClientPacketHandler getInstance() { return INSTANCE; }

    private VNCClientPacketHandler() {}

    private static byte[] decompress(byte[] data, int expectedSize) {
        try {
            java.util.zip.Inflater inf = new java.util.zip.Inflater();
            inf.setInput(data);
            byte[] out = new byte[expectedSize];
            int total = 0;
            while (!inf.finished() && total < expectedSize) {
                int n = inf.inflate(out, total, expectedSize - total);
                if (n == 0) break;
                total += n;
            }
            inf.end();
            if (total > 0) return java.util.Arrays.copyOf(out, total);
        } catch (Exception ignored) {}
        return data;
    }

    public void registerAll() {
        System.out.println("[VNCCraft-Client] Registering S2C handlers...");

        ClientPlayNetworking.registerGlobalReceiver(S2CVNCServerInitPayload.TYPE,
                (payload, context) -> {
                    System.out.println("[VNCCraft-Client] << RAW HANDLER FIRED >> ServerInit");
                    Minecraft.getInstance().execute(() -> {
                        var d = payload.data();
                        VNCClientRendererManager.getInstance().onServerInit(
                                d.screenId(), d.width(), d.height(),
                                d.bitsPerPixel(), d.depth(), d.bigEndian(), d.trueColor(),
                                d.redMax(), d.greenMax(), d.blueMax(),
                                d.redShift(), d.greenShift(), d.blueShift());
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(S2CVNCFramebufferUpdatePayload.TYPE,
                (payload, context) -> {
                    System.out.println("[VNCCraft-Client] << RAW HANDLER FIRED >> FrameUpdate screen=" + payload.data().screenId() + " data=" + payload.data().rgbaPixels().length);
                    Minecraft.getInstance().execute(() -> {
                        try {
                        var d = payload.data();
                        System.out.println("[VNCCraft-Client] FrameUpdate execute: decompressing...");
                        byte[] pixels = decompress(d.rgbaPixels(), d.width() * d.height() * 4);
                        System.out.println("[VNCCraft-Client] FrameUpdate execute: decompressed " + pixels.length + " bytes, calling onFrame");
                        VNCClientRendererManager.getInstance().onFrame(d.screenId(), pixels, d.width(), d.height());
                        System.out.println("[VNCCraft-Client] FrameUpdate execute: onFrame returned");
                        } catch (Exception e) {
                            System.out.println("[VNCCraft-Client] FrameUpdate execute ERROR: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(S2CVNCDisconnectPayload.TYPE,
                (payload, context) -> {
                    Minecraft.getInstance().execute(() -> {
                        VNCClientRendererManager.getInstance().onDisconnect(payload.screenId());
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(S2CVNCScreenPlacePayload.TYPE,
                (payload, context) -> {
                    Minecraft.getInstance().execute(() -> {
                        var d = payload.data();
                        VNCClientRendererManager.getInstance().onScreenPlace(
                                d.screenId(), d.x(), d.y(), d.z(),
                                d.direction(), d.resW(), d.resH());
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(S2CVNCScreenRemovePayload.TYPE,
                (payload, context) -> {
                    Minecraft.getInstance().execute(() -> {
                        VNCClientRendererManager.getInstance().onScreenRemove(payload.screenId());
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(S2CVNCScreenUpdatePayload.TYPE,
                (payload, context) -> {
                    Minecraft.getInstance().execute(() -> {
                        var d = payload.data();
                        VNCClientRendererManager.getInstance().onScreenUpdate(
                                d.screenId(), d.resW(), d.resH());
                    });
                });

        ClientPlayNetworking.registerGlobalReceiver(S2CVNCErrorPayload.TYPE,
                (payload, context) -> {
                    Minecraft.getInstance().execute(() -> {
                        var d = payload.data();
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.sendSystemMessage(
                                    net.minecraft.network.chat.Component.literal("VNC screen #" + d.screenId() + " error: " + d.error()));
                        }
                    });
                });

        // Audio with stereo panning + distance volume
        ClientPlayNetworking.registerGlobalReceiver(S2CVNCAudioPayload.TYPE,
                (payload, context) -> {
                    byte[] pcm = payload.data().pcmData();
                    int sid = payload.data().screenId();
                    VNCScreenRenderer r = VNCClientRendererManager.getInstance().getRenderer(sid);
                    var p = Minecraft.getInstance().player;
                    if (r != null && p != null) {
                        double dx = r.getPosX() - p.getX();
                        double dy = r.getPosY() - p.getEyeY();
                        double dz = r.getPosZ() - p.getZ();
                        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
                        float d = (float) Math.clamp(1.0 / Math.max(1, dist / 4), 0, 1);
                        var look = p.getLookAngle();
                        var right = new net.minecraft.world.phys.Vec3(-look.z, 0, look.x).normalize();
                        var toScr = new net.minecraft.world.phys.Vec3(dx, 0, dz).normalize();
                        float pan = (float) Math.clamp(right.dot(toScr), -1, 1);
                        float u = VNCAudioPlayer.getInstance().getUserVolume();
                        VNCAudioPlayer.getInstance().writeStereo(pcm, 0, pcm.length, d*u*(1-pan)/2, d*u*(1+pan)/2);
                    }
                });

        // Tunnel ready (Fabric): server opened a tunnel — auto-start client-side Vernacular
        ClientPlayNetworking.registerGlobalReceiver(S2CVNCTunnelReadyPayload.TYPE,
                (payload, context) -> {
                    var d = payload.data();
                    System.out.println("[VNCCraft-Client] Tunnel ready (Fabric) for screen " + d.screenId());
                    VNCClientRendererManager.getInstance().connectVNC(d.screenId(), d.password());
                });

        // Tunnel: feed raw bytes from VNC server to the client-side Vernacular
        ClientPlayNetworking.registerGlobalReceiver(S2CVNCTunnelPayload.TYPE,
                (payload, context) -> {
                    var d = payload.data();
                    VNCClientRendererManager.getInstance().feedTunnel(d.screenId(), d.rawBytes());
                });
    }
}
