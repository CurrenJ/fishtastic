package grill24.fishtastic.mixin;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.server.FishingMinigameManager;
import grill24.fishtastic.util.IFishingHookExtension;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingHook.class)
public class FishingHookMixin implements IFishingHookExtension {
    @Shadow
    @Final
    private int luck;

    // On vanilla/Fabric, shouldStopFishing checks is(Items.FISHING_ROD) which excludes modded rods.
    // NeoForge patches this to canPerformAction(), so it already works there.
    @Inject(method = "shouldStopFishing", at = @At("HEAD"), cancellable = true)
    private void fishtastic$keepCopperRodAlive(Player owner, CallbackInfoReturnable<Boolean> cir) {
        if (!owner.canInteractWithLevel()) return;
        ItemStack mainHand = owner.getMainHandItem();
        ItemStack offHand = owner.getOffhandItem();
        if ((mainHand.is(FishtasticItems.COPPER_FISHING_ROD) || offHand.is(FishtasticItems.COPPER_FISHING_ROD))
                && ((FishingHook)(Object)this).distanceToSqr(owner) <= 1024.0) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "retrieve", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;getServer()Lnet/minecraft/server/MinecraftServer;"), cancellable = true)
    private void retrieve(ItemStack itemStack, CallbackInfoReturnable<Integer> cir) {
        FishingHook fishingHook = (FishingHook)(Object)this;
        Player player = fishingHook.getPlayerOwner();

        if (player == null) return;

        if(itemStack.is(FishtasticItems.COPPER_FISHING_ROD)) {
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                grill24.fishtastic.server.FishingMinigameManager manager =
                        grill24.fishtastic.server.FishingMinigameManager.get(serverPlayer.level());
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
            FishingMinigameManager manager = FishingMinigameManager.get(serverPlayer.level());
            boolean isFishingMinigameActive = manager.isPlayerInActiveSession(serverPlayer.getUUID());

            if (itemStack.is(FishtasticItems.COPPER_FISHING_ROD) && isFishingMinigameActive) {
                cir.setReturnValue(0); // Prevent hook discard when minigame is active
            }
        }
    }

    @Override
    public int getLuck() {
        return this.luck;
    }
}
