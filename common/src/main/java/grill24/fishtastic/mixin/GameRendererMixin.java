package grill24.fishtastic.mixin;

import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.ItemActivationAnimation;
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

    @Inject(method = "renderItemActivationAnimation", at = @At("HEAD"), cancellable = true)
    private void renderItemActivation(GuiGraphics guiGraphics, float partialTick, CallbackInfo ci) {
        if (this.activeAnimation != null) {
            if(this.activeAnimation.isActive()) {
                // If we have an active animation, render it
                this.activeAnimation.render(this.minecraft, guiGraphics, partialTick);
                ci.cancel();
            } else {
                // Animation is no longer active, clear it
                this.activeAnimation = null;
            }
        } // Else pass to the original method
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
