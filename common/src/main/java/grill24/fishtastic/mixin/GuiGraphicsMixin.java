package grill24.fishtastic.mixin;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
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

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin implements IGuiGraphicsExtension {
    @Shadow @Final private Minecraft minecraft;

    @Shadow @Final private PoseStack pose;

    @Shadow public abstract void flush();

    @Shadow public abstract MultiBufferSource.BufferSource bufferSource();

    @Override
    public void fishtastic$renderItem(@Nullable LivingEntity livingEntity, @Nullable Level level, ItemStack itemStack, int i, int j, int k, int l) {
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
