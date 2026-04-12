package grill24.fishtastic.mixin;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class SizedItemClickMixin {

    @Inject(method = "overrideOtherStackedOnMe", at = @At("HEAD"), cancellable = true)
    private void fishtastic$autoPileOnDoubleSize(
            ItemStack self, ItemStack other, Slot slot,
            ClickAction clickAction, Player player, SlotAccess carriedItem,
            CallbackInfoReturnable<Boolean> cir) {

        if (clickAction != ClickAction.PRIMARY || other.isEmpty()) return;
        if (!canBePiled(self) || !canBePiled(other)) return;

        // Create pile, insert both items
        ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
        BundleContents.Mutable contents = new BundleContents.Mutable(BundleContents.EMPTY);

        int addedOther = contents.tryInsert(other);       // shrinks `other` in-place
        int addedSelf  = contents.tryInsert(self.copy()); // copies self for insertion count

        // A pile must contain at least 2 items; abort if not enough fit
        if (addedOther + addedSelf < 2) {
            other.grow(addedOther); // undo the shrink on `other`
            return;
        }

        pile.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
        self.shrink(addedSelf);
        if (self.isEmpty()) slot.setByPlayer(pile);
        if (other.isEmpty()) carriedItem.set(ItemStack.EMPTY);

        // Play insert sound
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F,
                0.8F + player.level().getRandom().nextFloat() * 0.4F);
        cir.setReturnValue(true);
    }

    @Unique
    private static boolean canBePiled(ItemStack s) {
        return !s.isEmpty()
                && s.getItem().canFitInsideContainerItems()
                && (s.is(FishtasticItemTags.FISH)
                    || s.has(FishtasticDataComponents.ITEM_SIZE.value()));
    }
}



