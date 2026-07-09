package top.colorgarden.vnccraft.client;

import top.colorgarden.vnccraft.client.vnc.VNCClientPacketHandler;
import top.colorgarden.vnccraft.client.vnc.VNCScreenEntityRenderer;
import top.colorgarden.vnccraft.client.vnc.VNCToolHandler;
import top.colorgarden.vnccraft.entity.VNCEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class VNCCraftClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Register entity renderer (no-op, actual rendering via DrawLib)
        EntityRendererRegistry.register(VNCEntities.VNC_SCREEN, VNCScreenEntityRenderer::new);

        // Register S2C packet handlers
        VNCClientPacketHandler.getInstance().registerAll();

        // Register tool interaction events
        VNCToolHandler.getInstance().registerEvents();
    }
}
