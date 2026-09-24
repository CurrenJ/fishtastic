package grill24.fishtastic.compat.jei;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.recipe.MarineCompostRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * The dirt + fish -> marine compost slots are hardcoded here rather than read off the
 * recipe instance, because {@link MarineCompostRecipe} (a {@code CustomRecipe}) never
 * stores its ingredients as data — its {@code matches()} checks them procedurally.
 */
public final class MarineCompostRecipeCategory implements IRecipeCategory<MarineCompostRecipe> {
    private static final int WIDTH = 80;
    private static final int HEIGHT = 38;

    private final IGuiHelper guiHelper;
    private final IDrawable icon;

    public MarineCompostRecipeCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
        this.icon = guiHelper.createDrawableItemLike(FishtasticBlocks.MARINE_COMPOST.value());
    }

    @Override
    public RecipeType<MarineCompostRecipe> getRecipeType() {
        return FishtasticJeiPlugin.MARINE_COMPOSTING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.fishtastic.marine_composting");
    }

    @Override
    public IDrawable getBackground() {
        return this.guiHelper.createBlankDrawable(WIDTH, HEIGHT);
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MarineCompostRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1)
                .setStandardSlotBackground()
                .addIngredients(Ingredient.of(Items.DIRT));

        // The fish half is a tag plus the pile-of-fish item, both shown in the one slot. 26.1
        // expresses that as a SlotDisplay.Composite; JEI 19 has no SlotDisplay, so the two
        // ingredient sources are added to the slot directly.
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 19)
                .setStandardSlotBackground()
                .addIngredients(Ingredient.of(ItemTags.FISHES))
                .addItemStack(new ItemStack(FishtasticItems.PILE_OF_FISH.value()));

        builder.addSlot(RecipeIngredientRole.OUTPUT, 60, 10)
                .setOutputSlotBackground()
                .addItemStack(new ItemStack(FishtasticBlocks.MARINE_COMPOST.value()));

        // 1.21.1's JEI has CATALYST where 26.1's JEI 29 has CRAFTING_STATION.
        builder.addInvisibleIngredients(RecipeIngredientRole.CATALYST)
                .addItemLike(Items.CRAFTING_TABLE);
    }
}
