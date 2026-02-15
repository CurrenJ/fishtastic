package grill24.fishtastic.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import grill24.fishtastic.itemeffect.ItemEffect;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
    @Unique
    private static final ThreadLocal<ItemStack> fishtastic$currentItemStack = new ThreadLocal<>();

    @Inject(
            method = "render",
            at = @At("HEAD")
    )
    private void captureItemStack(
            ItemStack itemStack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int combinedLight,
            int combinedOverlay,
            BakedModel model,
            CallbackInfo ci
    ) {
        fishtastic$currentItemStack.set(itemStack);
    }

    @Inject(
            method = "render",
            at = @At("RETURN")
    )
    private void clearItemStack(
            ItemStack itemStack,
            ItemDisplayContext displayContext,
            boolean leftHand,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int combinedLight,
            int combinedOverlay,
            BakedModel model,
            CallbackInfo ci
    ) {
        fishtastic$currentItemStack.remove();
    }

    @Inject(
            method = "getFoilBuffer",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void replaceGlintWithQualityGlow(
            MultiBufferSource bufferSource,
            RenderType renderType,
            boolean solid,
            boolean hasFoil,
            CallbackInfoReturnable<VertexConsumer> cir
    ) {
        ItemStack itemStack = fishtastic$currentItemStack.get();
        if (itemStack != null) {
            ItemEffect effect = ItemEffectManager.getEffectForItem(itemStack);
            if (effect != null) {
                cir.setReturnValue(Minecraft.useShaderTransparency() && renderType == Sheets.translucentItemSheet()
                        ? VertexMultiConsumer.create(bufferSource.getBuffer(effect.qualityGlowTranslucent()), bufferSource.getBuffer(renderType))
                        : VertexMultiConsumer.create(bufferSource.getBuffer(solid ? effect.qualityGlow() : effect.entityQualityGlow()), bufferSource.getBuffer(renderType))
                );
            }
        }
    }

    @Inject(
            method = "getFoilBufferDirect",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void replaceGlintWithQualityGlowDirect(
            MultiBufferSource bufferSource,
            RenderType renderType,
            boolean solid,
            boolean hasFoil,
            CallbackInfoReturnable<VertexConsumer> cir
    ) {
        ItemStack itemStack = fishtastic$currentItemStack.get();
        if (itemStack != null) {
            ItemEffect effect = ItemEffectManager.getEffectForItem(itemStack);
            if (effect != null) {
                RenderType customRenderType = solid ? effect.qualityGlow() : effect.entityQualityGlowDirect();
                cir.setReturnValue(VertexMultiConsumer.create(
                        bufferSource.getBuffer(customRenderType),
                        bufferSource.getBuffer(renderType)
                ));
            }
        }
    }

    @Inject(method = "getCompassFoilBuffer", at = @At("HEAD"), cancellable = true)
    private static void replaceCompassFoilBuffer(MultiBufferSource multiBufferSource, RenderType renderType, PoseStack.Pose pose, CallbackInfoReturnable<VertexConsumer> cir) {
        ItemStack itemStack = fishtastic$currentItemStack.get();
        if (itemStack != null) {
            ItemEffect effect = ItemEffectManager.getEffectForItem(itemStack);
            if (effect != null) {
                cir.setReturnValue(VertexMultiConsumer.create(
                        new SheetedDecalTextureGenerator(multiBufferSource.getBuffer(effect.qualityGlow()), pose, 0.0078125F), multiBufferSource.getBuffer(renderType)));
            }
        }
    }
}
