package grill24.fishtastic.mixin;

import grill24.fishtastic.util.IGuiGraphicsExtension;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

// TODO MC-26.1: GuiGraphics was replaced by GuiGraphicsExtractor; the old ItemRenderer-based
// render path (getItemRenderer().getModel / render) no longer exists. All fishtastic$renderItem
// methods are no-ops until reimplemented with the new ItemModelResolver / GuiItemRenderState API.
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsMixin implements IGuiGraphicsExtension {

    @Override
    public void fishtastic$renderItem(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack, int i, int j, int k, int l) {
        // TODO MC-26.1: reimplement via ItemModelResolver / GuiItemRenderState
    }

    @Override
    public void fishtastic$renderItem(ItemStack itemStack, int i, int j) {
    }

    @Override
    public void fishtastic$renderItem(ItemStack itemStack, int i, int j, int k) {
    }

    @Override
    public void fishtastic$renderItem(ItemStack itemStack, int i, int j, int k, int l) {
    }

    @Override
    public void fishtastic$renderFakeItem(ItemStack itemStack, int i, int j) {
    }

    @Override
    public void fishtastic$renderFakeItem(ItemStack itemStack, int i, int j, int k) {
    }

    @Override
    public void fishtastic$renderItem(LivingEntity livingEntity, ItemStack itemStack, int i, int j, int k) {
    }

    @Override
    public void fishtastic$renderItem(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack, int i, int j, int k) {
    }
}
