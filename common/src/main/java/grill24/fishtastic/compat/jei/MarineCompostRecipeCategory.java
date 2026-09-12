package grill24.fishtastic.compat.jei;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.recipe.MarineCompostRecipe;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * The dirt + fish -> marine compost slots are hardcoded here rather than read off the
 * recipe instance, because {@link MarineCompostRecipe} (a {@code CustomRecipe}) never
 * stores its ingredients as data — its {@code matches()} checks them procedurally.
 */
public final class MarineCompostRecipeCategory extends AbstractRecipeCategory<MarineCompostRecipe> {
    public MarineCompostRecipeCategory(IGuiHelper guiHelper) {
        super(
                FishtasticJeiPlugin.MARINE_COMPOSTING,
                Component.translatable("jei.fishtastic.marine_composting"),
                guiHelper.createDrawableItemLike(FishtasticBlocks.MARINE_COMPOST.value()),
                80,
                38
        );
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MarineCompostRecipe recipe, IFocusGroup focuses) {
        builder.addInputSlot(1, 1)
                .setStandardSlotBackground()
                .add(Ingredient.of(Items.DIRT));

        builder.addInputSlot(1, 19)
                .setStandardSlotBackground()
                .add(new SlotDisplay.TagSlotDisplay(ItemTags.FISHES));

        builder.addOutputSlot(60, 10)
                .setOutputSlotBackground()
                .add(new ItemStack(FishtasticBlocks.MARINE_COMPOST.value()));

        builder.addInvisibleIngredients(RecipeIngredientRole.CRAFTING_STATION)
                .add(Items.CRAFTING_TABLE);
    }
}
