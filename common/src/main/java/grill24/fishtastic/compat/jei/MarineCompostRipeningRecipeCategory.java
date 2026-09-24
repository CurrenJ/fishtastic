package grill24.fishtastic.compat.jei;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.util.FishQualityHelper;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class MarineCompostRipeningRecipeCategory implements IRecipeCategory<MarineCompostRipeningRecipe> {
    private static final int WIDTH = 80;
    private static final int HEIGHT = 38;

    private final IGuiHelper guiHelper;
    private final IDrawable icon;

    public MarineCompostRipeningRecipeCategory(IGuiHelper guiHelper) {
        this.guiHelper = guiHelper;
        this.icon = guiHelper.createDrawableItemLike(FishtasticItems.WORMS.value());
    }

    @Override
    public RecipeType<MarineCompostRipeningRecipe> getRecipeType() {
        return FishtasticJeiPlugin.MARINE_COMPOST_RIPENING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.fishtastic.marine_compost_ripening");
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
    public void setRecipe(IRecipeLayoutBuilder builder, MarineCompostRipeningRecipe recipe, IFocusGroup focuses) {
        ItemStack compost = new ItemStack(FishtasticBlocks.MARINE_COMPOST.value());
        FishQualityHelper.setQuality(compost, recipe.quality());

        builder.addSlot(RecipeIngredientRole.INPUT, 1, 10)
                .setStandardSlotBackground()
                .addItemStack(compost)
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.fishtastic.marine_compost_ripening.ready_tooltip")));

        builder.addSlot(RecipeIngredientRole.OUTPUT, 60, 10)
                .setOutputSlotBackground()
                .addItemStack(new ItemStack(FishtasticItems.WORMS.value(), recipe.worms()))
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.fishtastic.marine_compost_ripening.aeration_tooltip")));
    }
}
