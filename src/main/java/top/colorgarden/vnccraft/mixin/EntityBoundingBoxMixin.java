package top.colorgarden.vnccraft.mixin;

import top.colorgarden.vnccraft.entity.VNCScreenEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Override makeBoundingBox for VNCScreenEntity to use dynamic resolution-based sizing.
 * makeBoundingBox is final in MC 26.1.2, so we inject instead of override.
 */
@Mixin(Entity.class)
public class EntityBoundingBoxMixin {

    @Inject(method = "makeBoundingBox", at = @At("HEAD"), cancellable = true)
    private void onMakeBoundingBox(CallbackInfoReturnable<AABB> cir) {
        Entity self = (Entity) (Object) this;
        if (self instanceof VNCScreenEntity screen) {
            screen.updateHitbox();
            cir.setReturnValue(screen.getBoundingBox());
        }
    }
}
