package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.block.FishTankBlock;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;

import java.util.EnumMap;
import java.util.Map;

public class FishtasticBlocks {
    public static Holder<Block> FISH_TANK;

    // Undyed glass variants (no color)
    public static Holder<Block> BORDERLESS_GLASS;
    public static Holder<Block> CLEAR_GLASS;

    // Borderless stained glass blocks (no visible frame/border)
    public static final Map<DyeColor, Holder<Block>> BORDERLESS_STAINED_GLASS = new EnumMap<>(DyeColor.class);

    // Clear stained glass blocks (transparent/clear texture)
    public static final Map<DyeColor, Holder<Block>> CLEAR_STAINED_GLASS = new EnumMap<>(DyeColor.class);

    public static void registerBlocks() {
        // Undyed glass variants
        BORDERLESS_GLASS = RegistrationApiSided.getInstance().registerBlock("borderless_glass",
                loc -> new Block(Block.Properties.ofFullCopy(Blocks.GLASS).setId(ResourceKey.create(Registries.BLOCK, loc))));
        CLEAR_GLASS = RegistrationApiSided.getInstance().registerBlock("clear_glass",
                loc -> new Block(Block.Properties.ofFullCopy(Blocks.GLASS).setId(ResourceKey.create(Registries.BLOCK, loc))));

        // Register borderless and clear stained glass for all colors
        for (DyeColor color : DyeColor.values()) {
            Block vanillaStainedGlass = getVanillaStainedGlass(color);

            // Borderless stained glass
            Holder<Block> borderless = RegistrationApiSided.getInstance().registerBlock(
                    color.getName() + "_borderless_stained_glass",
                    loc -> new StainedGlassBlock(color, Block.Properties.ofFullCopy(vanillaStainedGlass).setId(ResourceKey.create(Registries.BLOCK, loc))));
            BORDERLESS_STAINED_GLASS.put(color, borderless);

            // Clear stained glass
            Holder<Block> clear = RegistrationApiSided.getInstance().registerBlock(
                    color.getName() + "_clear_stained_glass",
                    loc -> new StainedGlassBlock(color, Block.Properties.ofFullCopy(vanillaStainedGlass).setId(ResourceKey.create(Registries.BLOCK, loc))));
            CLEAR_STAINED_GLASS.put(color, clear);
        }

        FISH_TANK = RegistrationApiSided.getInstance().registerBlock("fish_tank",
            loc -> new FishTankBlock(Block.Properties.ofFullCopy(Blocks.GLASS)
                .setId(ResourceKey.create(Registries.BLOCK, loc))
                .noOcclusion()  // Allow transparent rendering
        ));
    }

    /**
     * Get the vanilla stained glass block for a given dye color.
     */
    private static Block getVanillaStainedGlass(DyeColor color) {
        return switch (color) {
            case WHITE -> Blocks.WHITE_STAINED_GLASS;
            case ORANGE -> Blocks.ORANGE_STAINED_GLASS;
            case MAGENTA -> Blocks.MAGENTA_STAINED_GLASS;
            case LIGHT_BLUE -> Blocks.LIGHT_BLUE_STAINED_GLASS;
            case YELLOW -> Blocks.YELLOW_STAINED_GLASS;
            case LIME -> Blocks.LIME_STAINED_GLASS;
            case PINK -> Blocks.PINK_STAINED_GLASS;
            case GRAY -> Blocks.GRAY_STAINED_GLASS;
            case LIGHT_GRAY -> Blocks.LIGHT_GRAY_STAINED_GLASS;
            case CYAN -> Blocks.CYAN_STAINED_GLASS;
            case PURPLE -> Blocks.PURPLE_STAINED_GLASS;
            case BLUE -> Blocks.BLUE_STAINED_GLASS;
            case BROWN -> Blocks.BROWN_STAINED_GLASS;
            case GREEN -> Blocks.GREEN_STAINED_GLASS;
            case RED -> Blocks.RED_STAINED_GLASS;
            case BLACK -> Blocks.BLACK_STAINED_GLASS;
        };
    }
}
