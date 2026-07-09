package top.colorgarden.vnccraft.client.mixin;

import top.colorgarden.vnccraft.client.vnc.VNCToolHUD;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects VNC tool HUD after the main GUI render state extraction.
 */
@Mixin(Gui.class)
public class GuiRenderMixin {

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
        VNCToolHUD.getInstance().render(graphics, tracker.getGameTimeDeltaTicks());
    }
}
