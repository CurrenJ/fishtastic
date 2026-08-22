package grill24.fishtastic.compat.jei;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.recipe.MarineCompostRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.types.IRecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;

import java.util.Arrays;
import java.util.List;

/**
 * Only ever loaded by JEI itself (annotation scan on NeoForge, {@code jei_mod_plugin}
 * entrypoint on Fabric) — never referenced from mod code that runs without JEI present,
 * so this class is the entire "optional dependency" boundary; no {@code CompatUtil}
 * reflection needed the way {@code compat.Gelatin*} needs it for gelatinui.
 */
@JeiPlugin
public class FishtasticJeiPlugin implements IModPlugin {
    /**
     * {@link MarineCompostRecipe} is a {@code CustomRecipe} (dynamic matches/assemble, no
     * static ingredient list), so JEI can't derive a display for it from the vanilla
     * crafting category automatically — it needs this dedicated category.
     */
    public static final IRecipeType<MarineCompostRecipe> MARINE_COMPOSTING =
            IRecipeType.create(Fishtastic.MOD_ID, "marine_composting", MarineCompostRecipe.class);

    /**
     * {@code MarineCompostBlockEntity} ripens compost into worms purely by ticking (see its
     * {@code tick}/{@code computeYield}) — there's no recipe object behind that at all, so
     * this type is backed by the synthetic {@link MarineCompostRipeningRecipe} instead of a
     * real {@code Recipe} subclass.
     */
    public static final IRecipeType<MarineCompostRipeningRecipe> MARINE_COMPOST_RIPENING =
            IRecipeType.create(Fishtastic.MOD_ID, "marine_compost_ripening", MarineCompostRipeningRecipe.class);

    @Override
    public Identifier getPluginUid() {
        return Fishtastic.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new MarineCompostRecipeCategory(guiHelper));
        registration.addRecipeCategories(new MarineCompostRipeningRecipeCategory(guiHelper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // Only one shape exists (1 dirt + 1 fish -> marine compost), so a single
        // representative instance is enough; no need to pull from RecipeManager.
        registration.addRecipes(MARINE_COMPOSTING, List.of(new MarineCompostRecipe(CraftingBookCategory.MISC)));

        // One row per fish quality tier the compost can be seeded with.
        registration.addRecipes(MARINE_COMPOST_RIPENING,
                Arrays.stream(FishQuality.Quality.values())
                        .map(MarineCompostRipeningRecipe::of)
                        .toList());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addCraftingStation(MARINE_COMPOSTING, Items.CRAFTING_TABLE);

        // Lets players looking up the marine compost item itself find "recipes it makes" ->
        // this category, in addition to it also surfacing under worms' "how do I make this".
        registration.addCraftingStation(MARINE_COMPOST_RIPENING, FishtasticBlocks.MARINE_COMPOST.value());
    }
}
