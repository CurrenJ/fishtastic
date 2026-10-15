package grill24.fishtastic.util;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

public interface IGuiGraphicsExtension {
    void fishtastic$renderItem(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack, int i, int j, int k, int l);
    void fishtastic$renderItem(ItemStack itemStack, int i, int j);

    void fishtastic$renderItem(ItemStack itemStack, int i, int j, int k);

    void fishtastic$renderItem(ItemStack itemStack, int i, int j, int k, int l);

    void fishtastic$renderFakeItem(ItemStack itemStack, int i, int j);

    void fishtastic$renderFakeItem(ItemStack itemStack, int i, int j, int k);

    void fishtastic$renderItem(LivingEntity livingEntity, ItemStack itemStack, int i, int j, int k);

    void fishtastic$renderItem(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack, int i, int j, int k);
}
