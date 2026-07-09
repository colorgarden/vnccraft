package top.colorgarden.vnccraft.client.mixin;

import top.colorgarden.vnccraft.client.vnc.VNCToolHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ScrollWheelHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Vector2i;
import static org.lwjgl.glfw.GLFW.*;

@Mixin(ScrollWheelHandler.class)
public class ExampleClientMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(double scrollX, double scrollY, CallbackInfoReturnable<Vector2i> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        long w = ((WindowAccessor)(Object)mc.getWindow()).getHandle();
        boolean ctrl = glfwGetKey(w, GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS || glfwGetKey(w, GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS;
        boolean alt  = glfwGetKey(w, GLFW_KEY_LEFT_ALT) == GLFW_PRESS     || glfwGetKey(w, GLFW_KEY_RIGHT_ALT) == GLFW_PRESS;
        boolean shift= glfwGetKey(w, GLFW_KEY_LEFT_SHIFT) == GLFW_PRESS   || glfwGetKey(w, GLFW_KEY_RIGHT_SHIFT) == GLFW_PRESS;
        boolean tab  = glfwGetKey(w, GLFW_KEY_TAB) == GLFW_PRESS;

        if (tab && scrollY != 0) {
            VNCToolHandler.getInstance().onTabScroll(scrollY);
            cir.setReturnValue(new Vector2i(0, 0));
            return;
        }
        if (scrollY != 0 && VNCToolHandler.isHoldingTool()) {
            VNCToolHandler.getInstance().onMouseScroll(scrollY, ctrl, alt, shift);
            if (ctrl || alt) cir.setReturnValue(new Vector2i(0, 0));
        }
    }
}
