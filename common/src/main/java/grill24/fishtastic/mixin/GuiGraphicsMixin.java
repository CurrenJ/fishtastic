package grill24.fishtastic.mixin;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.FishtasticGuiOutlineRenderer;
import grill24.fishtastic.util.IGuiGraphicsExtension;
import net.minecraft.CrashReport;
import net.minecraft.CrashReportCategory;
import net.minecraft.CrashReportDetail;
import net.minecraft.ReportedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin implements IGuiGraphicsExtension {
    @Shadow @Final private Minecraft minecraft;

    @Shadow @Final private PoseStack pose;

    @Shadow public abstract void flush();

    @Shadow public abstract MultiBufferSource.BufferSource bufferSource();

    /**
     * GUI item effects (quality outline, gear black outline, encyclopedia silhouette) behind or in
     * place of every GUI item — the 1.21.1 replacement for 26.1.2's {@code GuiRenderer} hooks. See
     * {@link FishtasticGuiOutlineRenderer}.
     */
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;IIII)V",
            at = @At("HEAD"), cancellable = true)
    private void fishtastic$renderItemEffects(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack,
                                              int x, int y, int seed, int guiOffset, CallbackInfo ci) {
        if (!itemStack.isEmpty() && FishtasticGuiOutlineRenderer.render((GuiGraphics) (Object) this, itemStack, x, y)) {
            ci.cancel();
        }
    }

    @Override
    public void fishtastic$renderItem(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack, int i, int j, int k, int l) {
        // PORT-ONLY: the same GUI item effects for the minigame's unit-space items (centred on the
        // origin, one unit wide) — on 26.1.2 these go through the GUI item atlas like any GUI item.
        if (!itemStack.isEmpty() && FishtasticGuiOutlineRenderer.renderAround((GuiGraphics) (Object) this, itemStack, 0.0F, 0.0F, 1.0F, 0.0F)) {
            return;
        }
        if (!itemStack.isEmpty()) {
            BakedModel bakedModel = this.minecraft.getItemRenderer().getModel(itemStack, level, livingEntity, k);
            this.pose.pushPose();
            // REMOVED FROM ORIGINAL METHOD
//            this.pose.translate((float)(i + 8), (float)(j + 8), (float)(150 + (bakedModel.isGui3d() ? l : 0)));

            try {
                // 16 scale changed to 1 bc we want full control over size in our animation
                this.pose.scale(1.0F, -1.0F, 1.0F);
                boolean bl = !bakedModel.usesBlockLight();
                if (bl) {
                    Lighting.setupForFlatItems();
                }

                this.minecraft
                        .getItemRenderer()
                        .render(itemStack, ItemDisplayContext.GUI, false, this.pose, this.bufferSource(), 15728880, OverlayTexture.NO_OVERLAY, bakedModel);
                this.flush();
                if (bl) {
                    Lighting.setupFor3DItems();
                }
            } catch (Throwable var12) {
                CrashReport crashReport = CrashReport.forThrowable(var12, "Rendering item");
                CrashReportCategory crashReportCategory = crashReport.addCategory("Item being rendered");
                crashReportCategory.setDetail("Item Type", (CrashReportDetail<String>)(() -> String.valueOf(itemStack.getItem())));
                crashReportCategory.setDetail("Item Components", (CrashReportDetail<String>)(() -> String.valueOf(itemStack.getComponents())));
                crashReportCategory.setDetail("Item Foil", (CrashReportDetail<String>)(() -> String.valueOf(itemStack.hasFoil())));
                throw new ReportedException(crashReport);
            }

            this.pose.popPose();
        }
    }

    @Override
    public void fishtastic$renderItem(ItemStack itemStack, int i, int j) {
        this.fishtastic$renderItem(this.minecraft.player, this.minecraft.level, itemStack, i, j, 0);
    }

    @Override
    public void fishtastic$renderItem(ItemStack itemStack, int i, int j, int k) {
        this.fishtastic$renderItem(this.minecraft.player, this.minecraft.level, itemStack, i, j, k);
    }

    @Override
    public void fishtastic$renderItem(ItemStack itemStack, int i, int j, int k, int l) {
        this.fishtastic$renderItem(this.minecraft.player, this.minecraft.level, itemStack, i, j, k, l);
    }

    @Override
    public void fishtastic$renderFakeItem(ItemStack itemStack, int i, int j) {
        this.fishtastic$renderFakeItem(itemStack, i, j, 0);
    }

    @Override
    public void fishtastic$renderFakeItem(ItemStack itemStack, int i, int j, int k) {
        this.fishtastic$renderItem(null, this.minecraft.level, itemStack, i, j, k);
    }

    @Override
    public void fishtastic$renderItem(LivingEntity livingEntity, ItemStack itemStack, int i, int j, int k) {
        this.fishtastic$renderItem(livingEntity, livingEntity.level(), itemStack, i, j, k);
    }

    @Override
    public void fishtastic$renderItem(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack, int i, int j, int k) {
        this.fishtastic$renderItem(livingEntity, level, itemStack, i, j, k, 0);
    }
}
