package top.colorgarden.vnccraft.entity;

import top.colorgarden.vnccraft.VNCCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public final class VNCEntities {

    private static final ResourceKey<EntityType<?>> VNC_SCREEN_KEY =
            ResourceKey.create(BuiltInRegistries.ENTITY_TYPE.key(), VNCCraft.id("vnc_screen"));

    public static final EntityType<VNCScreenEntity> VNC_SCREEN =
            EntityType.Builder.of(VNCScreenEntity::new, MobCategory.MISC)
                    .sized(2.0f, 2.0f)
                    .clientTrackingRange(128)
                    .updateInterval(3)
                    .build(VNC_SCREEN_KEY);

    private VNCEntities() {}

    public static void register() {
        Registry.register(BuiltInRegistries.ENTITY_TYPE, VNCCraft.id("vnc_screen"), VNC_SCREEN);
    }
}
