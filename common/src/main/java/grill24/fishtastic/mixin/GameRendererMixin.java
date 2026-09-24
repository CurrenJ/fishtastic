package grill24.fishtastic.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import grill24.fishtastic.client.FishtasticHudLayers;
import grill24.fishtastic.client.renderer.FishtasticItemOutlineAtlas;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.ItemActivationAnimation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin implements IGameRendererExtension {
    @Shadow
    @Final
    private Minecraft minecraft;


    // ---- Active Animation ----- //

    private ItemActivationAnimation activeAnimation = null;

    @Inject(method = "tick", at = @At("TAIL"))
    private void tick(CallbackInfo ci) {
        if (this.activeAnimation != null) {
            this.activeAnimation.tick();
        }
    }

    // The render call is now hooked via HUD events in FishtasticFabricClient / FishtasticNeoForgeClient,
    // and, while no screen is open, after vanilla's toasts below (FishtasticHudLayers).

    /**
     * PORT-ONLY (owner decision 2026-09-24): Fishtastic's HUD layers after vanilla's toasts, so a
     * toast can't cover a quest notification. Only while no screen is open; with one open they're
     * drawn in the HUD pass, under it (see {@link FishtasticHudLayers}).
     */
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/toasts/ToastComponent;render(Lnet/minecraft/client/gui/GuiGraphics;)V",
            shift = At.Shift.AFTER))
    private void fishtastic$renderHudLayersAboveToasts(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci,
                                                        @Local GuiGraphics guiGraphics) {
        if (FishtasticHudLayers.drawnInHudPass() || this.minecraft.options.hideGui || this.minecraft.level == null) {
            return;
        }
        FishtasticHudLayers.render(guiGraphics, deltaTracker.getGameTimeDeltaPartialTick(false));
    }

    /**
     * Bakes queued items into the world-outline atlas at the head of the frame, before any
     * level or GUI submission — the shared SubmitNodeStorage / FeatureRenderDispatcher /
     * BufferSource borrowed by the bake are idle only at this point.
     */
    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void fishtastic$processOutlineAtlasBakes(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        FishtasticItemOutlineAtlas.getInstance().processBakeQueue(this.minecraft);
    }

    @Inject(method = "displayItemActivation", at = @At("HEAD"))
    private void displayItemActivation(ItemStack stack, CallbackInfo ci) {
        this.activeAnimation = null;
    }
    // ----- Extension methods ----- //

    @Override
    public ItemActivationAnimation fishtastic$getActiveAnimation() {
        return this.activeAnimation;
    }


    @Override
    public void fishtastic$displayItemActivation(ItemActivationAnimation animation) {
        this.activeAnimation = animation;
    }

    @Override
    public void fishtastic$displayItemActivation(java.util.function.Supplier<? extends ItemActivationAnimation> animationSupplier) {
        this.activeAnimation = animationSupplier.get();
    }

    @Override
    public void fishtastic$cancelCurrentAnimation() {
        this.activeAnimation = null;
    }
}
