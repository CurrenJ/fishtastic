package grill24.fishtastic.mixin;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.datafixers.util.Pair;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.renderer.FishtasticShaders;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.ItemActivationAnimation;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.CrashReportDetail;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.server.packs.resources.ResourceProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import static grill24.fishtastic.util.Utility.ft;

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
