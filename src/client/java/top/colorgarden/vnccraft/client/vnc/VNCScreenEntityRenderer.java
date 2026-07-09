package top.colorgarden.vnccraft.client.vnc;

import top.colorgarden.vnccraft.entity.VNCScreenEntity;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;

/**
 * No-op renderer for VNCScreenEntity.
 * Actual rendering via DrawLib. This exists to prevent NPE.
 */
public class VNCScreenEntityRenderer extends EntityRenderer<VNCScreenEntity, EntityRenderState> {

    public VNCScreenEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public EntityRenderState createRenderState() {
        return new EntityRenderState();
    }
}
