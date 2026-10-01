package grill24.fishtastic.client.renderer;

import grill24.fishtastic.client.FishtasticClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.LightCoordsUtil;

/**
 * The minimum light a tank's <em>interior</em> (fish, water fill, sand, glass, cosmetics) is drawn
 * with, independent of the light the tank block emits into the world. Raising it keeps what is
 * inside the glass bright without lighting the room around the tank; the frame always keeps world
 * light so the tank still sits in its surroundings.
 *
 * <p>Applied in three places: {@link FishTankBlockEntityRenderer} raises the light it draws fish and
 * the water fill with, and each loader's tank block model raises its sand, glass and cosmetic quads
 * in the chunk mesh. Fabric raises block light only (a per-vertex minimum lightmap); NeoForge has no
 * such hook, so it uses the quads' own light emission, which vanilla applies to block and sky light
 * alike — under shaders that reads slightly cooler and hazier than block light alone.
 *
 * <p>Set by {@code /fishtastic tank interiorlight <0-15>} (see {@link FishtasticClientConfig}).
 */
public final class TankInteriorLight {

    /** The configured level, 0-15; 0 is off. */
    public static int level() {
        return FishtasticClientConfig.getTankInteriorLight();
    }

    /** {@code lightCoords} with its block light raised to at least {@link #level()}. */
    public static int apply(int lightCoords) {
        int level = level();
        return level <= 0 ? lightCoords : LightCoordsUtil.max(lightCoords, LightCoordsUtil.pack(level, 0));
    }

    /** The packed minimum lightmap for chunk-meshed interior quads (block light only), or 0 when off. */
    public static int minimumLightmap() {
        return LightCoordsUtil.pack(level(), 0);
    }

    /**
     * Changes the level and, if it changed, rebuilds every chunk mesh: tank interiors bake the level
     * into their quads, so already-meshed tanks would otherwise keep the old one until next re-mesh.
     */
    public static void set(int level) {
        int previous = level();
        FishtasticClientConfig.setTankInteriorLight(level);
        Minecraft mc = Minecraft.getInstance();
        if (level() != previous && mc.level != null) {
            mc.levelRenderer.allChanged();
        }
    }

    private TankInteriorLight() {}
}
