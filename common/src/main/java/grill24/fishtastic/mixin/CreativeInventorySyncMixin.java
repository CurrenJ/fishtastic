package grill24.fishtastic.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Creative mode's "Inventory" tab rearranges items the player already owns purely client-side —
 * it calls AbstractContainerMenu.clicked() locally but, unlike survival inventory clicks, never
 * sends a ServerboundContainerClickPacket, since vanilla only needs server authority for actual
 * item creation/destruction in creative. That means custom Item#overrideOtherStackedOnMe /
 * overrideStackedOnOther behavior (e.g. loading bait onto a fishing rod) only ever runs
 * client-side when exercised this way, and anything gated on the resulting server state (like
 * the tutorial's onBaitLoaded hook) never fires. Route this one call through the same
 * gameMode.handleContainerInput(...) survival inventory clicks use, so it predicts locally AND
 * notifies the server, same as it would outside creative.
 */
@Mixin(CreativeModeInventoryScreen.class)
public class CreativeInventorySyncMixin {

    @Redirect(
            method = "slotClicked",
            at = @At(value = "INVOKE",
                    // Player#inventoryMenu is declared as InventoryMenu (not AbstractContainerMenu),
                    // so javac emits the invoke against that static type even though clicked()
                    // itself is inherited from AbstractContainerMenu — the owner here must match.
                    target = "Lnet/minecraft/world/inventory/InventoryMenu;clicked(IILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V",
                    ordinal = 0)
    )
    private void fishtastic$syncInventoryTabRearrangeToServer(
            InventoryMenu menu, int slotIndex, int buttonNum, ContainerInput containerInput, Player player) {
        Minecraft.getInstance().gameMode.handleContainerInput(menu.containerId, slotIndex, buttonNum, containerInput, player);
    }
}
