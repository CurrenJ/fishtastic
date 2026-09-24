package grill24.fishtastic.compat.jei;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.client.FishEncyclopediaScreen;
import grill24.fishtastic.client.FishTankAssemblyScreen;
import grill24.fishtastic.client.FishTankBrowserScreen;
import grill24.fishtastic.client.LeaderboardScreen;
import grill24.fishtastic.client.QuestLogScreen;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.recipe.MarineCompostRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import org.jetbrains.annotations.Nullable;

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
    public static final RecipeType<MarineCompostRecipe> MARINE_COMPOSTING =
            RecipeType.create(Fishtastic.MOD_ID, "marine_composting", MarineCompostRecipe.class);

    /**
     * {@code MarineCompostBlockEntity} ripens compost into worms purely by ticking (see its
     * {@code tick}/{@code computeYield}) — there's no recipe object behind that at all, so
     * this type is backed by the synthetic {@link MarineCompostRipeningRecipe} instead of a
     * real {@code Recipe} subclass.
     */
    public static final RecipeType<MarineCompostRipeningRecipe> MARINE_COMPOST_RIPENING =
            RecipeType.create(Fishtastic.MOD_ID, "marine_compost_ripening", MarineCompostRipeningRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
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

    /**
     * The mod's full-screen gelatin-ui screens are {@code AbstractContainerScreen}s only because
     * gelatin-ui builds on a menu — they have no player inventory grid and no free margin, so
     * JEI's default {@code AbstractContainerScreen} handler drops its ingredient list straight on
     * top of the artwork. These handlers report the GUI as occupying the entire screen, which
     * leaves JEI no space to lay an overlay out in and so hides the ingredient list, its search
     * box, and the bookmark overlay on these screens only.
     *
     * <p><b>Why not simply return {@code null}.</b> Through JEI 29.2 a null handler meant "this
     * screen has no GUI properties", which suppressed the overlay. In 29.29+ the lookup became
     * {@code handlers.map(h -> h.apply(screen)).filter(Objects::nonNull).findFirst()} — sorted by
     * class distance, so ours is consulted first, but a null return now means "skip me, try the
     * next handler" and JEI falls straight through to its own {@code AbstractContainerScreen}
     * handler. Null would silently stop hiding anything.
     *
     * <p>Reporting a full-screen GUI doesn't depend on that contract at all:
     * {@code IngredientListOverlayLayout#createDisplayArea} is
     * {@code rect(0, 0, screenWidth, screenHeight).cropLeft(guiRight())}, so a GUI spanning the
     * whole screen leaves a zero-width area no matter how the surrounding logic is rearranged.
     * It's also honest for the quest log and encyclopedia — those really are full-bleed.
     * {@code FishTankBrowserScreen}, {@code FishTankAssemblyScreen}, and {@code LeaderboardScreen}
     * aren't (they're a conventional centered panel), but the overlay is hidden there too on
     * request — the shape gallery / grid / podium content sits close enough to the panel edges
     * that JEI's sliver of leftover space wasn't usable anyway.
     */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiScreenHandler(QuestLogScreen.class, FishtasticJeiPlugin::fullScreenGui);
        registration.addGuiScreenHandler(FishEncyclopediaScreen.class, FishtasticJeiPlugin::fullScreenGui);
        registration.addGuiScreenHandler(FishTankBrowserScreen.class, FishtasticJeiPlugin::fullScreenGui);
        registration.addGuiScreenHandler(FishTankAssemblyScreen.class, FishtasticJeiPlugin::fullScreenGui);
        registration.addGuiScreenHandler(LeaderboardScreen.class, FishtasticJeiPlugin::fullScreenGui);
    }

    /**
     * {@link IGuiProperties} claiming the whole screen, leaving JEI nowhere to draw. Null while
     * the screen hasn't been sized yet: JEI can ask before {@code Screen.init}, validates what it
     * gets immediately and logs an error for zero sizes, and re-asks every frame anyway.
     */
    private static @Nullable IGuiProperties fullScreenGui(Screen screen) {
        int width = screen.width;
        int height = screen.height;
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new IGuiProperties() {
            @Override
            public Class<? extends Screen> screenClass() {
                return screen.getClass();
            }

            @Override
            public int guiLeft() {
                return 0;
            }

            @Override
            public int guiTop() {
                return 0;
            }

            @Override
            public int guiXSize() {
                return width;
            }

            @Override
            public int guiYSize() {
                return height;
            }

            @Override
            public int screenWidth() {
                return width;
            }

            @Override
            public int screenHeight() {
                return height;
            }
        };
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(Items.CRAFTING_TABLE, MARINE_COMPOSTING);

        // Lets players looking up the marine compost item itself find "recipes it makes" ->
        // this category, in addition to it also surfacing under worms' "how do I make this".
        registration.addRecipeCatalyst(FishtasticBlocks.MARINE_COMPOST.value(), MARINE_COMPOST_RIPENING);
    }
}
