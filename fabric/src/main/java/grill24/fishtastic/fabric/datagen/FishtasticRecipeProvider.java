package grill24.fishtastic.fabric.datagen;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.recipe.MarineCompostRecipe;
import grill24.fishtastic.util.Ids;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.recipes.*;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
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
 *   <li>Trash catches – old tire smelts to coal, sea glass smelts to glass, plastic litter stonecuts to string</li>
 * </ul>
 */
public class FishtasticRecipeProvider extends FabricRecipeProvider {

    public FishtasticRecipeProvider(FabricDataOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    public void buildRecipes(RecipeOutput output) {
        buildEquipmentRecipes(output);
        buildGlassRecipes(output);
        buildBaitAndFoodRecipes(output);
        buildHookAndCharmRecipes(output);
        buildTrashRecipes(output);
        buildBookRecipes(output);
        buildFishCookingRecipes(output);
    }

    // -----------------------------------------------------------------
    // Equipment
    // -----------------------------------------------------------------

    private void buildEquipmentRecipes(RecipeOutput output) {
        // Copper Fishing Rod: 2 copper ingots + a stick handle + 2 strings
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, FishtasticItems.COPPER_FISHING_ROD.value())
                .pattern("  C")
                .pattern(" CS")
                .pattern("I S")
                .define('C', Items.COPPER_INGOT)
                .define('S', Items.STRING)
                .define('I', Items.STICK)
                .unlockedBy("has_copper_ingot", has(Items.COPPER_INGOT))
                .save(output);

        // Obsidian Fishing Rod: 2 obsidian + a stick handle + 2 strings — the intended lava-fishing tool
        ShapedRecipeBuilder.shaped(RecipeCategory.TOOLS, FishtasticItems.OBSIDIAN_FISHING_ROD.value())
                .pattern("  C")
                .pattern(" CS")
                .pattern("I S")
                .define('C', Items.OBSIDIAN)
                .define('S', Items.STRING)
                .define('I', Items.STICK)
                .unlockedBy("has_obsidian", has(Items.OBSIDIAN))
                .save(output);

        // Fish Tank Assembly: a sized fish shows the crafting table what it's for;
        // materials are chosen afterward in the assembly block's own menu, not here.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.DECORATIONS, FishtasticBlocks.FISH_TANK_ASSEMBLY.value())
                .requires(grill24.fishtastic.FishtasticItemTags.FISH)
                .requires(Items.CRAFTING_TABLE)
                .unlockedBy("has_fish", has(grill24.fishtastic.FishtasticItemTags.FISH))
                .save(output);

        // Electric Fish Organizer: copper-cornered wooden cabinet, an electric eel wired in the middle.
        ShapedRecipeBuilder.shaped(RecipeCategory.DECORATIONS, FishtasticBlocks.ELECTRIC_FISH_ORGANIZER.value())
                .pattern("CPC")
                .pattern("PEP")
                .pattern("CPC")
                .define('C', Items.COPPER_INGOT)
                .define('P', ItemTags.PLANKS)
                .define('E', FishtasticItems.ELECTRIC_EEL.value())
                .unlockedBy("has_electric_eel", has(FishtasticItems.ELECTRIC_EEL.value()))
                .save(output);

        // Marine Compost: 1 dirt + 1 sized fish, shapeless. A custom recipe type since the
        // fish's quality must be copied onto the result (see MarineCompostRecipe). No datagen
        // advancement is emitted here - it's hand-authored alongside the recipe JSON.
        output.accept(
                Ids.of("fishtastic", "marine_compost"),
                new MarineCompostRecipe(CraftingBookCategory.MISC),
                null);
    }

    // -----------------------------------------------------------------
    // Glass variants
    // -----------------------------------------------------------------

    private void buildGlassRecipes(RecipeOutput output) {
        // Undyed glass variants
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, FishtasticBlocks.BORDERLESS_GLASS.value())
                .requires(Items.GLASS)
                .unlockedBy("has_glass", has(Items.GLASS))
                .save(output);
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, FishtasticBlocks.CLEAR_GLASS.value())
                .requires(Items.GLASS)
                .requires(Items.GLASS)
                .unlockedBy("has_glass", has(Items.GLASS))
                .save(output);

        // Also allow crafting clear glass from borderless glass + plain glass
        ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, FishtasticBlocks.CLEAR_GLASS.value())
                .requires(FishtasticBlocks.BORDERLESS_GLASS.value())
                .requires(Items.GLASS)
                .unlockedBy("has_borderless_glass", has(FishtasticBlocks.BORDERLESS_GLASS.value()))
                .save(output, "borderless_glass_plus_glass_to_clear_glass");

        for (DyeColor color : DyeColor.values()) {
            Block vanilla = getVanillaStainedGlass(color);
            Holder<Block> borderless = FishtasticBlocks.BORDERLESS_STAINED_GLASS.get(color);
            Holder<Block> clear = FishtasticBlocks.CLEAR_STAINED_GLASS.get(color);

            // Borderless: polish one stained glass block to remove the border
            ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, borderless.value())
                    .requires(vanilla)
                    .unlockedBy("has_stained_glass", has(vanilla))
                    .save(output);

            // Clear: combine stained glass with plain glass to clarify the tint
            ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, clear.value())
                    .requires(vanilla)
                    .requires(Items.GLASS)
                    .unlockedBy("has_stained_glass", has(vanilla))
                    .save(output);

            // Also allow crafting clear from borderless + plain glass
            ShapelessRecipeBuilder.shapeless(RecipeCategory.BUILDING_BLOCKS, clear.value())
                    .requires(borderless.value())
                    .requires(Items.GLASS)
                    .unlockedBy("has_borderless_stained_glass", has(borderless.value()))
                    .save(output, borderless.getRegisteredName() + "_plus_glass_to_clear");
        }
    }

    // -----------------------------------------------------------------
    // Bait and food
    // -----------------------------------------------------------------

    private void buildBaitAndFoodRecipes(RecipeOutput output) {
        // Worms are no longer directly craftable from dirt - obtain them from the
        // Worm Bin (fish -> worms) or the token shop so both faucets stay meaningful.

        // Gummy Worms
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, FishtasticItems.GUMMY_WORMS.value())
                .requires(FishtasticItems.WORMS.value())
                .requires(Items.GOLDEN_CARROT)
                .requires(Items.SUGAR)
                .requires(Items.DIAMOND)
                .unlockedBy("has_worms", has(FishtasticItems.WORMS.value()))
                .save(output);

        // Blazed Grub: worms infused with blaze powder
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, FishtasticItems.BLAZED_GRUB.value())
                .requires(FishtasticItems.WORMS.value())
                .requires(Items.BLAZE_POWDER)
                .unlockedBy("has_worms", has(FishtasticItems.WORMS.value()))
                .save(output);

        // Calm Bait: worms + kelp (item id kept as ocean_bait — see FishtasticItems)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, FishtasticItems.CALM_BAIT.value())
                .requires(FishtasticItems.WORMS.value())
                .requires(Items.KELP)
                .requires(Items.SEAGRASS)
                .unlockedBy("has_worms", has(FishtasticItems.WORMS.value()))
                .save(output);

        // Small Fish Bait: worms + mushrooms (item id kept as freshwater_bait)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, FishtasticItems.SMALL_FISH_BAIT.value())
                .requires(FishtasticItems.WORMS.value())
                .requires(Items.BROWN_MUSHROOM)
                .requires(Items.RED_MUSHROOM)
                .unlockedBy("has_worms", has(FishtasticItems.WORMS.value()))
                .save(output);

        // Frenzy Bait: worms + raw meat (item id kept as predator_bait)
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, FishtasticItems.FRENZY_BAIT.value())
                .requires(FishtasticItems.WORMS.value())
                .requires(ItemTags.MEAT)
                .requires(ItemTags.MEAT)
                .unlockedBy("has_worms", has(FishtasticItems.WORMS.value()))
                .save(output);
    }

    // -----------------------------------------------------------------
    // Hooks and charms
    // -----------------------------------------------------------------

    private void buildHookAndCharmRecipes(RecipeOutput output) {
        // Hook: iron ingot sharpened with string, better quality bias / lower trash chance
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, FishtasticItems.HOOK.value())
                .requires(Items.IRON_INGOT)
                .requires(Items.STRING)
                .unlockedBy("has_iron_ingot", has(Items.IRON_INGOT))
                .save(output);

        // Old Copper Hook: cheaper copper substitute, worse quality bias / higher trash chance
        ShapelessRecipeBuilder.shapeless(RecipeCategory.TOOLS, FishtasticItems.OLD_COPPER_HOOK.value())
                .requires(Items.COPPER_INGOT)
                .requires(Items.STRING)
                .unlockedBy("has_copper_ingot", has(Items.COPPER_INGOT))
                .save(output);
    }

    // -----------------------------------------------------------------
    // Trash catches
    // -----------------------------------------------------------------

    private void buildTrashRecipes(RecipeOutput output) {
        // Old Tire: smelts down to coal
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(FishtasticItems.OLD_TIRE.value()), RecipeCategory.MISC,
                        Items.COAL, 0.1F, 200)
                .unlockedBy("has_old_tire", has(FishtasticItems.OLD_TIRE.value()))
                .save(output);

        // Sea Glass: smelts down to plain glass
        SimpleCookingRecipeBuilder.smelting(Ingredient.of(FishtasticItems.SEA_GLASS.value()), RecipeCategory.BUILDING_BLOCKS,
                        Items.GLASS, 0.1F, 200)
                .unlockedBy("has_sea_glass", has(FishtasticItems.SEA_GLASS.value()))
                .save(output);

        // Plastic Litter: stonecut apart into string
        SingleItemRecipeBuilder.stonecutting(Ingredient.of(FishtasticItems.PLASTIC_LITTER.value()), RecipeCategory.MISC,
                        Items.STRING, 1)
                .unlockedBy("has_plastic_litter", has(FishtasticItems.PLASTIC_LITTER.value()))
                .save(output);
    }

    // -----------------------------------------------------------------
    // Fish cooking
    // -----------------------------------------------------------------

    private void buildFishCookingRecipes(RecipeOutput output) {
        // Any Fishtastic fish (fishtastic:fish tag) can be furnaced, smoked, or campfired
        // into vanilla cooked cod - mirrors vanilla's own raw fish -> cooked fish recipes.
        Ingredient fish = Ingredient.of(grill24.fishtastic.FishtasticItemTags.FISH);

        SimpleCookingRecipeBuilder.smelting(fish, RecipeCategory.FOOD,
                        Items.COOKED_COD, 0.35F, 200)
                .unlockedBy("has_fish", has(grill24.fishtastic.FishtasticItemTags.FISH))
                .save(output, "cooked_cod_from_fishtastic_fish_smelting");

        SimpleCookingRecipeBuilder.smoking(fish, RecipeCategory.FOOD,
                        Items.COOKED_COD, 0.35F, 100)
                .unlockedBy("has_fish", has(grill24.fishtastic.FishtasticItemTags.FISH))
                .save(output, "cooked_cod_from_fishtastic_fish_smoking");

        SimpleCookingRecipeBuilder.campfireCooking(fish, RecipeCategory.FOOD,
                        Items.COOKED_COD, 0.35F, 600)
                .unlockedBy("has_fish", has(grill24.fishtastic.FishtasticItemTags.FISH))
                .save(output, "cooked_cod_from_fishtastic_fish_campfire");
    }

    // -----------------------------------------------------------------
    // Books
    // -----------------------------------------------------------------

    private void buildBookRecipes(RecipeOutput output) {
        // Quest Book: a book pressed with any fish to bind the quest log
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, FishtasticItems.QUEST_BOOK.value())
                .requires(Items.BOOK)
                .requires(grill24.fishtastic.FishtasticItemTags.FISH)
                .unlockedBy("has_fish", has(grill24.fishtastic.FishtasticItemTags.FISH))
                .save(output);

        // Once a Quest Book exists, the three books cycle into each other one at a time:
        // Quest Book -> Fishopedia -> Leaderboards Book -> Quest Book.
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, FishtasticItems.FISHOPEDIA.value())
                .requires(FishtasticItems.QUEST_BOOK.value())
                .unlockedBy("has_quest_book", has(FishtasticItems.QUEST_BOOK.value()))
                .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, FishtasticItems.LEADERBOARDS_BOOK.value())
                .requires(FishtasticItems.FISHOPEDIA.value())
                .unlockedBy("has_fishopedia", has(FishtasticItems.FISHOPEDIA.value()))
                .save(output);

        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, FishtasticItems.QUEST_BOOK.value())
                .requires(FishtasticItems.LEADERBOARDS_BOOK.value())
                .unlockedBy("has_leaderboards_book", has(FishtasticItems.LEADERBOARDS_BOOK.value()))
                .save(output, "quest_book_from_leaderboards_book");
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
