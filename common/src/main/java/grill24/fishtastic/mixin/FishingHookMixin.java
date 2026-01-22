package grill24.fishtastic.mixin;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.server.FishingMinigameManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
public class FishingHookMixin {
    @Inject(method = "retrieve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getServer()Lnet/minecraft/server/MinecraftServer;"), cancellable = true)
    private void retrieve(ItemStack itemStack, CallbackInfoReturnable<Integer> cir) {
        FishingHook fishingHook = (FishingHook)(Object)this;
        Player player = fishingHook.getPlayerOwner();

        if (player == null) return;

        if(itemStack.is(FishtasticItems.COPPER_FISHING_ROD)) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                grill24.fishtastic.server.FishingMinigameManager manager =
                        grill24.fishtastic.server.FishingMinigameManager.get(serverPlayer.serverLevel());
                manager.startSession(serverPlayer, 1.0f, false);
                cir.setReturnValue(0); // Prevent normal loot retrieval
            }
        }
    }

    @Inject(method = "retrieve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/FishingHook;discard()V"), cancellable = true)
    private void retrieveDiscardHook(ItemStack itemStack, CallbackInfoReturnable<Integer> cir) {
        FishingHook fishingHook = (FishingHook)(Object)this;
        Player player = fishingHook.getPlayerOwner();

        if(player instanceof ServerPlayer serverPlayer) {
            FishingMinigameManager manager = FishingMinigameManager.get(serverPlayer.serverLevel());
            boolean isFishingMinigameActive = manager.isPlayerInActiveSession(serverPlayer.getUUID());

            if (itemStack.is(FishtasticItems.COPPER_FISHING_ROD) && isFishingMinigameActive) {
                cir.setReturnValue(0); // Prevent hook discard when minigame is active
            }
        }
    }
}
