package grill24.fishtastic;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.block.ElectricFishOrganizerBlock;
import grill24.fishtastic.block.FishPileBlock;
import grill24.fishtastic.block.FishTankAssemblyBlock;
import grill24.fishtastic.block.FishTankBlock;
import grill24.fishtastic.block.MarineCompostBlock;
import grill24.fishtastic.item.FishTankBlockItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.material.PushReaction;

import java.util.EnumMap;
import java.util.Map;

public class FishtasticBlocks {
    public static Holder<Block> FISH_TANK;
    public static Holder<Block> FISH_TANK_ASSEMBLY;
    public static Holder<Block> MARINE_COMPOST;
    public static Holder<Block> FISH_PILE;
    public static Holder<Block> ELECTRIC_FISH_ORGANIZER;

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
                // Reads as a lit aquarium rather than a plain glass box at the mercy of nearby
                // torches/skylight — without this, capping the tank with one solid block cuts its
                // vertical skylight column and the whole interior (glass, sand, fish) goes dark,
                // since a block's rendered brightness is real Minecraft light, not just its model.
                .lightLevel(state -> 8)
        ), (block, loc) -> new FishTankBlockItem(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, loc))));

        FISH_TANK_ASSEMBLY = RegistrationApiSided.getInstance().registerBlock("fish_tank_assembly",
            loc -> new FishTankAssemblyBlock(Block.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, loc))
                .strength(2.5f)
                .sound(SoundType.WOOD)
        ));

        MARINE_COMPOST = RegistrationApiSided.getInstance().registerBlock("marine_compost",
            loc -> new MarineCompostBlock(Block.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, loc))
                .strength(0.5f)
                .sound(SoundType.GRAVEL)
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY)
        ));

        FISH_PILE = RegistrationApiSided.getInstance().registerBlock("fish_pile",
            loc -> new FishPileBlock(Block.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, loc))
                .strength(0.3f)
                .sound(SoundType.WET_GRASS)
                .noOcclusion()
                .pushReaction(PushReaction.DESTROY)
        ));

        ELECTRIC_FISH_ORGANIZER = RegistrationApiSided.getInstance().registerBlock("electric_fish_organizer",
            loc -> new ElectricFishOrganizerBlock(Block.Properties.of()
                .setId(ResourceKey.create(Registries.BLOCK, loc))
                .strength(3.0f)
                .sound(SoundType.METAL)
                .lightLevel(state -> 3)
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
