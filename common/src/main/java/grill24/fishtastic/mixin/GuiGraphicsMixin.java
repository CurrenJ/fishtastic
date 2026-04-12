package grill24.fishtastic.mixin;

import grill24.fishtastic.util.IGuiGraphicsExtension;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Implements fishtastic item-render helpers on top of the 26.1 GuiGraphicsExtractor API.
 *
 * <h3>Coordinate-space change from 1.21.1 → 26.1</h3>
 * In 1.21.1, renderItem() rendered the item's BakedModel in unit space (−0.5 to 0.5).
 * The pose scale directly determined the item's size in pixels (scale 480 → 480 px wide).
 *
 * In 26.1, item() renders a 16×16 pixel slot and then the full pose matrix is applied to
 * that rectangle (see GuiItemRenderState: bounds = ScreenRectangle(x, y, 16, 16) then
 * .transformMaxBounds(pose)). This means scale 480 → item is 480×16 = 7680 px wide.
 *
 * Fix: before calling item(), push an extra scale(1/16) so the 16×16 slot shrinks to
 * 1×1 unit, matching the old model space. Also shift by (i−8, j−8) to center the slot
 * at the pose origin (half of 16 = 8), matching the −0.5..0.5 model bounds from 1.21.1.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsMixin implements IGuiGraphicsExtension {

    @Shadow public abstract Matrix3x2fStack pose();
    @Shadow public abstract void item(ItemStack itemStack, int x, int y);
    @Shadow public abstract void item(ItemStack itemStack, int x, int y, int seed);
    @Shadow public abstract void item(LivingEntity owner, ItemStack itemStack, int x, int y, int seed);
    @Shadow public abstract void fakeItem(ItemStack itemStack, int x, int y);
    @Shadow public abstract void fakeItem(ItemStack itemStack, int x, int y, int seed);

    /** Scale factor to convert from 26.1's 16-pixel item slot to the 1-unit model space used in 1.21.1. */
    private static final float ITEM_UNIT_SCALE = 1f / 16f;

    @Override
    public void fishtastic$renderItem(ItemStack itemStack, int i, int j) {
        this.pose().pushMatrix();
        this.pose().scale(ITEM_UNIT_SCALE, ITEM_UNIT_SCALE);
        this.item(itemStack, i - 8, j - 8);
        this.pose().popMatrix();
    }

    @Override
    public void fishtastic$renderItem(ItemStack itemStack, int i, int j, int k) {
        this.pose().pushMatrix();
        this.pose().scale(ITEM_UNIT_SCALE, ITEM_UNIT_SCALE);
        this.item(itemStack, i - 8, j - 8, k);
        this.pose().popMatrix();
    }

    @Override
    public void fishtastic$renderItem(ItemStack itemStack, int i, int j, int k, int l) {
        // 'l' was a z-level offset in the old API; not used in 26.1
        this.pose().pushMatrix();
        this.pose().scale(ITEM_UNIT_SCALE, ITEM_UNIT_SCALE);
        this.item(itemStack, i - 8, j - 8, k);
        this.pose().popMatrix();
    }

    @Override
    public void fishtastic$renderFakeItem(ItemStack itemStack, int i, int j) {
        this.pose().pushMatrix();
        this.pose().scale(ITEM_UNIT_SCALE, ITEM_UNIT_SCALE);
        this.fakeItem(itemStack, i - 8, j - 8);
        this.pose().popMatrix();
    }

    @Override
    public void fishtastic$renderFakeItem(ItemStack itemStack, int i, int j, int k) {
        this.pose().pushMatrix();
        this.pose().scale(ITEM_UNIT_SCALE, ITEM_UNIT_SCALE);
        this.fakeItem(itemStack, i - 8, j - 8, k);
        this.pose().popMatrix();
    }

    @Override
    public void fishtastic$renderItem(LivingEntity livingEntity, ItemStack itemStack, int i, int j, int k) {
        this.pose().pushMatrix();
        this.pose().scale(ITEM_UNIT_SCALE, ITEM_UNIT_SCALE);
        this.item(livingEntity, itemStack, i - 8, j - 8, k);
        this.pose().popMatrix();
    }

    @Override
    public void fishtastic$renderItem(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack, int i, int j, int k) {
        // Level is captured internally by GuiGraphicsExtractor; entity is used as owner/seed source
        this.pose().pushMatrix();
        this.pose().scale(ITEM_UNIT_SCALE, ITEM_UNIT_SCALE);
        if (livingEntity != null) {
            this.item(livingEntity, itemStack, i - 8, j - 8, k);
        } else {
            this.fakeItem(itemStack, i - 8, j - 8);
        }
        this.pose().popMatrix();
    }

    @Override
    public void fishtastic$renderItem(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack, int i, int j, int k, int l) {
        // 'l' was z-level in old API; use seed=k, drop l
        this.pose().pushMatrix();
        this.pose().scale(ITEM_UNIT_SCALE, ITEM_UNIT_SCALE);
        if (livingEntity != null) {
            this.item(livingEntity, itemStack, i - 8, j - 8, k);
        } else {
            this.fakeItem(itemStack, i - 8, j - 8, k);
        }
        this.pose().popMatrix();
    }
}
