package grill24.fishtastic.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.client.renderer.FishtasticRenderTypes;
import grill24.fishtastic.component.FishQuality;
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
/**
 * Mixin to ItemRenderer to replace the default foil effect with our custom quality glow shader
 * for fish items that have quality components.
 */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
    @Unique
    private static final ThreadLocal<ItemStack> fishtastic$currentItemStack = new ThreadLocal<>();
    /**
     * Capture the ItemStack being rendered so we can use it in the foil buffer methods.
     */
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
    /**
     * Clear the captured ItemStack after rendering.
     */
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
    /**
     * Replace the RenderType used for foil rendering with our custom quality glow for fish items.
     */
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
        if (itemStack != null && itemStack.has(FishtasticDataComponents.FISH_QUALITY.value())) {
            FishQuality fishQuality = itemStack.get(FishtasticDataComponents.FISH_QUALITY.value());
            if (fishQuality != null && fishQuality.shouldRenderEffect()) {
                FishQuality.Quality quality = fishQuality.quality();
                // Use VertexMultiConsumer.create() just like vanilla does, with quality-specific render types
                cir.setReturnValue(Minecraft.useShaderTransparency() && renderType == Sheets.translucentItemSheet()
                        ? VertexMultiConsumer.create(bufferSource.getBuffer(FishtasticRenderTypes.qualityGlowTranslucent(quality)), bufferSource.getBuffer(renderType))
                        : VertexMultiConsumer.create(bufferSource.getBuffer(solid ? FishtasticRenderTypes.qualityGlow(quality) : FishtasticRenderTypes.entityQualityGlow(quality)), bufferSource.getBuffer(renderType))
                );
            }
        }
    }
    /**
     * Replace the RenderType used for direct foil rendering with our custom quality glow for fish items.
     */
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
        if (itemStack != null && itemStack.has(FishtasticDataComponents.FISH_QUALITY.value())) {
            FishQuality fishQuality = itemStack.get(FishtasticDataComponents.FISH_QUALITY.value());
            if (fishQuality != null && fishQuality.shouldRenderEffect()) {
                FishQuality.Quality quality = fishQuality.quality();
                // Use our custom quality glow shader instead of the default glint, with quality-specific render types
                RenderType customRenderType = solid ? FishtasticRenderTypes.qualityGlow(quality) : FishtasticRenderTypes.entityQualityGlowDirect(quality);

                // Use VertexMultiConsumer.create() just like vanilla does
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
        if (itemStack != null && itemStack.has(FishtasticDataComponents.FISH_QUALITY.value())) {
            FishQuality fishQuality = itemStack.get(FishtasticDataComponents.FISH_QUALITY.value());
            if (fishQuality != null && fishQuality.shouldRenderEffect()) {
                FishQuality.Quality quality = fishQuality.quality();
                cir.setReturnValue(VertexMultiConsumer.create(
                        new SheetedDecalTextureGenerator(multiBufferSource.getBuffer(FishtasticRenderTypes.qualityGlow(quality)), pose, 0.0078125F), multiBufferSource.getBuffer(renderType)));
            }
        }
    }

}
