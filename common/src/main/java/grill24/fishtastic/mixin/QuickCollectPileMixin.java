package grill24.fishtastic.mixin;

import grill24.fishtastic.item.PileOfFishItem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractContainerMenu.class)
public class QuickCollectPileMixin {

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void fishtastic$quickCollectIntoPile(
            int slotIndex, int buttonNum, ContainerInput containerInput, Player player, CallbackInfo ci) {

        if (containerInput != ContainerInput.PICKUP_ALL || slotIndex < 0) {
            return;
        }

        AbstractContainerMenu self = (AbstractContainerMenu) (Object) this;
        Slot originSlot = self.slots.get(slotIndex);
        ItemStack carried = self.getCarried();
        // Matches vanilla's own guard for this branch: only collect if the origin slot was already
        // emptied by the first click of the double-click (or can't be picked up from).
        if (carried.isEmpty() || (originSlot.hasItem() && originSlot.mayPickup(player))) {
            return;
        }

        if (PileOfFishItem.tryQuickCollectIntoPile(self, player, buttonNum)) {
            ci.cancel();
        }
    }
}
