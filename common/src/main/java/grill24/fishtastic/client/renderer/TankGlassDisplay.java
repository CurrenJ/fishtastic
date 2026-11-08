package grill24.fishtastic.client.renderer;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.component.FishTankMaterials;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Draws a tank built with vanilla glass or stained glass as if it were built with Fishtastic's
 * borderless variant of that glass. Vanilla glass has a frame drawn into its texture, and inside a
 * tank those frames clutter every pane. The tank still <em>stores</em> what the player put in, so
 * tooltips and refit refunds stay honest. Only the look changes, and only on this client.
 *
 * <p>Tinted glass and modded glass have no borderless variant and are drawn as they are.
 *
 * <p>Applied where each loader's tank block model and tank item model resolve the glass texture,
 * and folded into their cache keys, so the lookup runs when a tank is meshed, never per frame.
 * Toggled by {@code /fishtastic tank borderlessglass <true|false>} (see {@link FishtasticClientConfig}).
 */
public final class TankGlassDisplay {

    /** Built on first use: the borderless blocks don't exist until {@code FishtasticBlocks} registers them. */
    private static volatile Map<Block, Block> borderless;

    /** The block a tank's glass is drawn with: its borderless variant when the setting is on and one exists. */
    public static Block displayed(Block glass) {
        if (!FishtasticClientConfig.isTankBorderlessGlass()) return glass;
        return mapping().getOrDefault(glass, glass);
    }

    /** {@code materials} with its glass swapped for the block it is drawn with; the same instance if unchanged. */
    public static FishTankMaterials displayed(FishTankMaterials materials) {
        Block glass = displayed(materials.glass());
        return glass == materials.glass() ? materials : new FishTankMaterials(materials.frame(), materials.sand(), glass);
    }

    /**
     * Changes the setting and, if it changed, rebuilds every chunk mesh: tank bodies bake their glass
     * texture into the mesh, so already-meshed tanks would otherwise keep the old look until re-meshed.
     */
    public static void set(boolean enabled) {
        boolean previous = FishtasticClientConfig.isTankBorderlessGlass();
        FishtasticClientConfig.setTankBorderlessGlass(enabled);
        Minecraft mc = Minecraft.getInstance();
        if (enabled != previous && mc.level != null) {
            mc.levelRenderer.allChanged();
        }
    }

    private static Map<Block, Block> mapping() {
        Map<Block, Block> map = borderless;
        if (map == null) {
            map = new IdentityHashMap<>();
            map.put(Blocks.GLASS, FishtasticBlocks.BORDERLESS_GLASS.value());
            for (DyeColor color : DyeColor.values()) {
                map.put(FishtasticBlocks.getVanillaStainedGlass(color), FishtasticBlocks.BORDERLESS_STAINED_GLASS.get(color).value());
            }
            borderless = map;
        }
        return map;
    }

    private TankGlassDisplay() {}
}
