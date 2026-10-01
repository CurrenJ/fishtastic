package grill24.fishtastic.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface IGuiGraphicsExtension {
    void fishtastic$renderItem(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack, int i, int j, int k, int l);
    void fishtastic$renderItem(ItemStack itemStack, int i, int j);

    /**
     * PORT-ONLY: {@code itemStack}'s GUI effects (the rarity ring), then {@code sprite} blitted in
     * place of the item, in the same unit-space box {@link #fishtastic$renderItem(ItemStack, int, int)}
     * would draw the item in. The 1.21.1 counterpart of 26.1.2's item-model swap (see
     * {@code FishingBarLayout#fishTargetSprite}).
     */
    void fishtastic$renderSprite(ItemStack itemStack, FishingMinigameAnimation.GuiTextureItem sprite, int i, int j);

    void fishtastic$renderItem(ItemStack itemStack, int i, int j, int k);

    void fishtastic$renderItem(ItemStack itemStack, int i, int j, int k, int l);

    void fishtastic$renderFakeItem(ItemStack itemStack, int i, int j);

    void fishtastic$renderFakeItem(ItemStack itemStack, int i, int j, int k);

    void fishtastic$renderItem(LivingEntity livingEntity, ItemStack itemStack, int i, int j, int k);

    void fishtastic$renderItem(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack, int i, int j, int k);
}
