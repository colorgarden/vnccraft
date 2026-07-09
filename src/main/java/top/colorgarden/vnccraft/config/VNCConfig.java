package top.colorgarden.vnccraft.config;

/**
 * Mod configuration. In a full implementation, this would load from a JSON config file.
 * Currently uses hardcoded defaults.
 */
public final class VNCConfig {

    private VNCConfig() {}

    /** The item ID players must hold to use VNC tools (default: wooden shovel) */
    public static final String TOOL_ITEM = "minecraft:wooden_shovel";

    /** Maximum distance (blocks) at which a screen is visible and receives frame updates */
    public static final double MAX_VIEW_DISTANCE = 64.0;

    /** Field of view cosine threshold (cos of half horizontal FOV) */
    public static final double FOV_DOT_THRESHOLD = 0.7;

    /** Default screen resolution width */
    public static final int DEFAULT_RES_WIDTH = 1920;

    /** Default screen resolution height */
    public static final int DEFAULT_RES_HEIGHT = 1080;

    /** Blocks per 1000 pixels for screen world-size calculation */
    public static final float BLOCKS_PER_1000PX = 3.0f;
}
