package top.colorgarden.vnccraft.client.vnc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class VNCToolHUD {

    private static final VNCToolHUD INSTANCE = new VNCToolHUD();
    public static VNCToolHUD getInstance() { return INSTANCE; }

    private VNCToolHUD() {}

    public void render(GuiGraphicsExtractor graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (!VNCToolHandler.isHoldingTool()) return;

        String modeKey = VNCToolHandler.getInstance().getMode() == VNCToolHandler.Mode.PLACE ? "vnccraft.hud.mode_place" : "vnccraft.hud.mode_laser";
        int cnt = VNCClientRendererManager.getInstance().getScreenCount();
        Font font = mc.font;
        int x = 6;
        int y = mc.getWindow().getGuiScaledHeight() - 70;

        graphics.text(font, Component.translatable("vnccraft.hud.title"), x, y, 0xFFFFFFAA);
        graphics.text(font, Component.translatable(modeKey), x, y + 10, 0xFFFFFF55);
        graphics.text(font, Component.translatable("vnccraft.hud.place"), x, y + 20, 0xFF55FF55);
        graphics.text(font, Component.translatable("vnccraft.hud.delete"), x, y + 30, 0xFF55FF55);
        graphics.text(font, Component.translatable("vnccraft.hud.nudge"), x, y + 40, 0xFF55FF55);
        graphics.text(font, Component.translatable("vnccraft.hud.nudge_fine"), x, y + 50, 0xFF55FF55);
        graphics.text(font, Component.translatable("vnccraft.hud.count", cnt), x, y + 60, 0xCCCCCCCC);
    }
}
