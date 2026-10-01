package grill24.fishtastic.client.renderer;

import grill24.fishtastic.client.FishtasticClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;

/**
 * The minimum light a tank's <em>interior</em> (fish, water fill, sand, glass, cosmetics) is drawn
 * with, independent of the light the tank block emits into the world. Raising it keeps what is
 * inside the glass bright without lighting the room around the tank; the frame always keeps world
 * light so the tank still sits in its surroundings.
 *
 * <p>Applied in three places: {@link FishTankBlockEntityRenderer} raises the light it draws fish and
 * the water fill with, and each loader's tank block model raises its sand, glass and cosmetic quads
 * in the chunk mesh. Fabric raises block light only (a per-vertex minimum lightmap). 26.1.2's NeoForge
 * has no such hook and uses the quads' light emission, which raises sky light too; Forge 1.20.1 does
 * have one — the lightmap baked into a quad's vertices, which its patched {@code putBulkData} takes
 * the maximum of — so on this branch both loaders raise block light only.
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
        return level <= 0 ? lightCoords : max(lightCoords, LightTexture.pack(level, 0));
    }

    /**
     * PORT-ONLY (26.1's {@code LightCoordsUtil.max}, 1.21.1's {@code LightTexture.block/sky}): the
     * brighter of two packed lightmaps, block and sky light taken separately. 1.20.1's
     * {@code LightTexture} only exposes {@link LightTexture#pack}, so the halves are unpacked with
     * vanilla's own layout ({@code block << 4 | sky << 20}).
     */
    public static int max(int a, int b) {
        return LightTexture.pack(Math.max(block(a), block(b)), Math.max(sky(a), sky(b)));
    }

    /** The block-light half of a packed lightmap. */
    public static int block(int lightCoords) {
        return (lightCoords >> 4) & 0xF;
    }

    /** The sky-light half of a packed lightmap. */
    public static int sky(int lightCoords) {
        return (lightCoords >> 20) & 0xF;
    }

    /** The packed minimum lightmap for chunk-meshed interior quads (block light only), or 0 when off. */
    public static int minimumLightmap() {
        return LightTexture.pack(level(), 0);
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
