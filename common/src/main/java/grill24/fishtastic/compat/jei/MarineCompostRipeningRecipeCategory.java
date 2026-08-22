package grill24.fishtastic.compat.jei;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.util.FishQualityHelper;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class MarineCompostRipeningRecipeCategory extends AbstractRecipeCategory<MarineCompostRipeningRecipe> {
    public MarineCompostRipeningRecipeCategory(IGuiHelper guiHelper) {
        super(
                FishtasticJeiPlugin.MARINE_COMPOST_RIPENING,
                Component.translatable("jei.fishtastic.marine_compost_ripening"),
                guiHelper.createDrawableItemLike(FishtasticItems.WORMS.value()),
                80,
                38
        );
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, MarineCompostRipeningRecipe recipe, IFocusGroup focuses) {
        ItemStack compost = new ItemStack(FishtasticBlocks.MARINE_COMPOST.value());
        FishQualityHelper.setQuality(compost, recipe.quality());

        builder.addInputSlot(1, 10)
                .setStandardSlotBackground()
                .add(compost)
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.fishtastic.marine_compost_ripening.ready_tooltip")));

        builder.addOutputSlot(60, 10)
                .setOutputSlotBackground()
                .add(new ItemStack(FishtasticItems.WORMS.value(), recipe.worms()))
                .addRichTooltipCallback((view, tooltip) ->
                        tooltip.add(Component.translatable("jei.fishtastic.marine_compost_ripening.aeration_tooltip")));
    }
}
