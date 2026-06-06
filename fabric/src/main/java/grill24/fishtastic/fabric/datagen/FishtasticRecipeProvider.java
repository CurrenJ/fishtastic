package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;

/**
 * Generates crafting recipes for all Fishtastic items.
 *
 * <ul>
 *   <li>Copper Fishing Rod – shaped, 3 copper ingots + 2 strings</li>
 *   <li>Fish Tank – shaped, 4 iron ingots + 4 glass blocks</li>
 *   <li>Borderless Stained Glass (×16) – shapeless, 1 vanilla stained glass → 1 borderless</li>
 *   <li>Clear Stained Glass (×16) – shapeless, 1 vanilla stained glass + 1 glass → 1 clear</li>
 *   <li>Bait items – shapeless, themed vanilla ingredients</li>
 *   <li>Fried Shrimp – smelting shrimp</li>
 *   <li>Fish items – shapeless, vanilla fish + thematic modifier</li>
 * </ul>
 */
public class FishtasticRecipeProvider extends FabricRecipeProvider {

    public FishtasticRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        return new RecipeProvider(registries, output) {
            @Override
            public void buildRecipes() {
                HolderGetter<Item> items = this.registries.lookupOrThrow(Registries.ITEM);
                buildEquipmentRecipes(items);
                buildGlassRecipes(items);
                buildBaitAndFoodRecipes(items);
                buildFishRecipes(items);
            }

            // -----------------------------------------------------------------
            // Equipment
            // -----------------------------------------------------------------

            private void buildEquipmentRecipes(HolderGetter<Item> items) {
                // Copper Fishing Rod: diagonal of 3 copper ingots + 2 strings
                ShapedRecipeBuilder.shaped(items, RecipeCategory.TOOLS, FishtasticItems.COPPER_FISHING_ROD.value())
                        .pattern("  C")
                        .pattern(" CS")
                        .pattern("C S")
                        .define('C', Items.COPPER_INGOT)
                        .define('S', Items.STRING)
                        .unlockedBy("has_copper_ingot", has(Items.COPPER_INGOT))
                        .save(this.output);

                // Fish Tank: glass walls with iron corners (hollow 3×3)
                ShapedRecipeBuilder.shaped(items, RecipeCategory.DECORATIONS, FishtasticBlocks.FISH_TANK.value())
                        .pattern("IGI")
                        .pattern("G G")
                        .pattern("IGI")
                        .define('I', Items.IRON_INGOT)
                        .define('G', Items.GLASS)
                        .unlockedBy("has_glass", has(Items.GLASS))
                        .save(this.output);
            }

            // -----------------------------------------------------------------
            // Glass variants
            // -----------------------------------------------------------------

            private void buildGlassRecipes(HolderGetter<Item> items) {
                // Undyed glass variants
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, FishtasticBlocks.BORDERLESS_GLASS.value())
                        .requires(Items.GLASS)
                        .unlockedBy("has_glass", has(Items.GLASS))
                        .save(this.output);
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, FishtasticBlocks.CLEAR_GLASS.value())
                        .requires(Items.GLASS)
                        .requires(Items.GLASS)
                        .unlockedBy("has_glass", has(Items.GLASS))
                        .save(this.output);

                // Also allow crafting clear glass from borderless glass + plain glass
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, FishtasticBlocks.CLEAR_GLASS.value())
                        .requires(FishtasticBlocks.BORDERLESS_GLASS.value())
                        .requires(Items.GLASS)
                        .unlockedBy("has_borderless_glass", has(FishtasticBlocks.BORDERLESS_GLASS.value()))
                        .save(this.output, "borderless_glass_plus_glass_to_clear_glass");

                for (DyeColor color : DyeColor.values()) {
                    Block vanilla = getVanillaStainedGlass(color);
                    Holder<Block> borderless = FishtasticBlocks.BORDERLESS_STAINED_GLASS.get(color);
                    Holder<Block> clear = FishtasticBlocks.CLEAR_STAINED_GLASS.get(color);

                    // Borderless: polish one stained glass block to remove the border
                    ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, borderless.value())
                            .requires(vanilla)
                            .unlockedBy("has_stained_glass", has(vanilla))
                            .save(this.output);

                    // Clear: combine stained glass with plain glass to clarify the tint
                    ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, clear.value())
                            .requires(vanilla)
                            .requires(Items.GLASS)
                            .unlockedBy("has_stained_glass", has(vanilla))
                            .save(this.output);

                    // Also allow crafting clear from borderless + plain glass
                    ShapelessRecipeBuilder.shapeless(items, RecipeCategory.BUILDING_BLOCKS, clear.value())
                            .requires(borderless.value())
                            .requires(Items.GLASS)
                            .unlockedBy("has_borderless_stained_glass", has(borderless.value()))
                            .save(this.output, borderless.getRegisteredName() + "_plus_glass_to_clear");
                }
            }

            // -----------------------------------------------------------------
            // Bait and food
            // -----------------------------------------------------------------

            private void buildBaitAndFoodRecipes(HolderGetter<Item> items) {
                // Worms: dig through 3 dirt blocks
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.WORMS.value())
                        .requires(Items.DIRT)
                        .requires(Items.DIRT)
                        .requires(Items.DIRT)
                        .unlockedBy("has_dirt", has(Items.DIRT))
                        .save(this.output);

                // Shrimp: seagrass + gravel (found on sandy ocean floors)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.FOOD, FishtasticItems.SHRIMP.value())
                        .requires(Items.SEAGRASS)
                        .requires(Items.SEAGRASS)
                        .requires(Items.GRAVEL)
                        .unlockedBy("has_seagrass", has(Items.SEAGRASS))
                        .save(this.output);

                // Fried Shrimp: cook shrimp in a furnace
                SimpleCookingRecipeBuilder.smelting(
                                Ingredient.of(FishtasticItems.SHRIMP.value()),
                                RecipeCategory.FOOD,
                                CookingBookCategory.FOOD,
                                FishtasticItems.FRIED_SHRIMP.value(),
                                0.35f, 200)
                        .unlockedBy("has_shrimp", has(FishtasticItems.SHRIMP.value()))
                        .save(this.output);

                // Gummy Worms: worms + slime ball
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.GUMMY_WORMS.value())
                        .requires(FishtasticItems.WORMS.value())
                        .requires(Items.GOLDEN_CARROT)
                        .requires(Items.SUGAR)
                        .requires(Items.DIAMOND)
                        .unlockedBy("has_worms", has(FishtasticItems.WORMS.value()))
                        .save(this.output);

                // Blazed Grub: worms infused with blaze powder
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.BLAZED_GRUB.value())
                        .requires(FishtasticItems.WORMS.value())
                        .requires(Items.BLAZE_POWDER)
                        .unlockedBy("has_worms", has(FishtasticItems.WORMS.value()))
                        .save(this.output);
            }

            // -----------------------------------------------------------------
            // Fish
            // -----------------------------------------------------------------

            private void buildFishRecipes(HolderGetter<Item> items) {
                // Bluegill: cod + lapis lazuli (blue coloring)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.BLUEGILL.value())
                        .requires(Items.COD)
                        .requires(Items.LAPIS_LAZULI)
                        .unlockedBy("has_cod", has(Items.COD))
                        .save(this.output);

                // Garden Eel: tropical fish + vine (hides in seagrass gardens)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.GARDEN_EEL.value())
                        .requires(Items.TROPICAL_FISH)
                        .requires(Items.VINE)
                        .unlockedBy("has_tropical_fish", has(Items.TROPICAL_FISH))
                        .save(this.output);

                // Royal Garden Eel: garden eel + gold ingot (gilded)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.ROYAL_GARDEN_EEL.value())
                        .requires(FishtasticItems.GARDEN_EEL.value())
                        .requires(Items.GOLD_INGOT)
                        .unlockedBy("has_garden_eel", has(FishtasticItems.GARDEN_EEL.value()))
                        .save(this.output);

                // Giant Manta Ray: cod + phantom membrane (large, wing-like)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.GIANT_MANTA_RAY.value())
                        .requires(Items.COD)
                        .requires(Items.PHANTOM_MEMBRANE)
                        .unlockedBy("has_phantom_membrane", has(Items.PHANTOM_MEMBRANE))
                        .save(this.output);

                // Frozen Giant Manta Ray: giant manta ray + ice
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.FROZEN_GIANT_MANTA_RAY.value())
                        .requires(FishtasticItems.GIANT_MANTA_RAY.value())
                        .requires(Items.ICE)
                        .unlockedBy("has_giant_manta_ray", has(FishtasticItems.GIANT_MANTA_RAY.value()))
                        .save(this.output);

                // Lizardfish: tropical fish + cactus (spiky appearance)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.LIZARDFISH.value())
                        .requires(Items.TROPICAL_FISH)
                        .requires(Items.CACTUS)
                        .unlockedBy("has_tropical_fish", has(Items.TROPICAL_FISH))
                        .save(this.output);

                // Longnose Gar: salmon + arrow (long pointed snout)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.LONGNOSE_GAR.value())
                        .requires(Items.SALMON)
                        .requires(Items.ARROW)
                        .unlockedBy("has_salmon", has(Items.SALMON))
                        .save(this.output);

                // Moorish Idol: tropical fish + gold ingot (regal black-and-gold stripes)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.MOORISH_IDOL.value())
                        .requires(Items.TROPICAL_FISH)
                        .requires(Items.GOLD_INGOT)
                        .unlockedBy("has_tropical_fish", has(Items.TROPICAL_FISH))
                        .save(this.output);

                // Molten Moorish Idol: moorish idol + blaze powder (fiery variant)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.MOLTEN_MOORISH_IDOL.value())
                        .requires(FishtasticItems.MOORISH_IDOL.value())
                        .requires(Items.BLAZE_POWDER)
                        .unlockedBy("has_moorish_idol", has(FishtasticItems.MOORISH_IDOL.value()))
                        .save(this.output);

                // Neon Tetra: tropical fish + glowstone dust (bioluminescent)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.NEON_TETRA.value())
                        .requires(Items.TROPICAL_FISH)
                        .requires(Items.GLOWSTONE_DUST)
                        .unlockedBy("has_tropical_fish", has(Items.TROPICAL_FISH))
                        .save(this.output);

                // Northern Pike: salmon + iron ingot (tough northern predator)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.NORTHERN_PIKE.value())
                        .requires(Items.SALMON)
                        .requires(Items.IRON_INGOT)
                        .unlockedBy("has_salmon", has(Items.SALMON))
                        .save(this.output);

                // Ocean Sunfish: cod + sunflower (round sun-shaped body)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.OCEAN_SUNFISH.value())
                        .requires(Items.COD)
                        .requires(Items.SUNFLOWER)
                        .unlockedBy("has_cod", has(Items.COD))
                        .save(this.output);

                // Parrotfish: tropical fish + feather (vibrant parrot-like colors)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.PARROTFISH.value())
                        .requires(Items.TROPICAL_FISH)
                        .requires(Items.FEATHER)
                        .unlockedBy("has_tropical_fish", has(Items.TROPICAL_FISH))
                        .save(this.output);

                // Portuguese Man O' War: tropical fish + string (long trailing tentacles)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.PORTUGUESE_MAN_O_WAR.value())
                        .requires(Items.TROPICAL_FISH)
                        .requires(Items.STRING)
                        .unlockedBy("has_tropical_fish", has(Items.TROPICAL_FISH))
                        .save(this.output);

                // Rainfordia: tropical fish + emerald (rare and exotic)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.RAINFORDIA.value())
                        .requires(Items.TROPICAL_FISH)
                        .requires(Items.EMERALD)
                        .unlockedBy("has_tropical_fish", has(Items.TROPICAL_FISH))
                        .save(this.output);

                // Starfish: tropical fish + redstone (five-armed star shape)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.STARFISH.value())
                        .requires(Items.TROPICAL_FISH)
                        .requires(Items.REDSTONE)
                        .unlockedBy("has_tropical_fish", has(Items.TROPICAL_FISH))
                        .save(this.output);

                // Acute Iaspis: tropical fish + amethyst shard (iaspis = precious gemstone)
                ShapelessRecipeBuilder.shapeless(items, RecipeCategory.MISC, FishtasticItems.ACUTE_IASPIS.value())
                        .requires(Items.TROPICAL_FISH)
                        .requires(Items.AMETHYST_SHARD)
                        .unlockedBy("has_amethyst_shard", has(Items.AMETHYST_SHARD))
                        .save(this.output);
            }
        };
    }

    @Override
    public String getName() {
        return "Fishtastic Recipes";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

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
