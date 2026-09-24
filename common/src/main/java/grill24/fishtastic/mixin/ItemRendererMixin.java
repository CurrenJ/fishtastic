package grill24.fishtastic.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.SheetedDecalTextureGenerator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import grill24.fishtastic.client.renderer.FishtasticRenderTypes;
import grill24.fishtastic.client.renderer.FishtasticWorldOutlineRenderer;
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

import java.util.ArrayDeque;

/**
 * Item quality effects in {@code ItemRenderer.render}, which every item draw goes through on 1.21.1
 * (GUI, item entities, frames, hands, the tank's fish, the pile icons' fish).
 * <ul>
 *   <li><b>Glint:</b> the stack is captured for the duration of {@code render}, and the foil
 *       buffers vanilla asks for are swapped for the matching {@link ItemEffect}'s glint render
 *       types. This is the 1.21.1 ancestor's mixin, unchanged apart from where the render types
 *       live. It replaces 26.1.2's {@code ItemModelResolverMixin} + {@code ItemStackRenderStateMixin}
 *       + {@code ItemFeatureRendererMixin#getFoilRenderType} chain, which only exists to carry the
 *       effect across the extract/submit split.</li>
 *   <li><b>World outline:</b> {@link FishtasticWorldOutlineRenderer}, just after the display
 *       transform and the model-space translate. It replaces 26.1.2's
 *       {@code ItemEntityRendererMixin}, {@code ItemFrameRendererMixin}, the tank renderer's outline
 *       calls and {@code ItemInHandRendererMixin}.</li>
 * </ul>
 */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {
    /** A stack, not a single slot: item renderers draw nested items (the fish in a pile icon). */
    @Unique
    private static final ThreadLocal<ArrayDeque<ItemStack>> fishtastic$currentItemStack = ThreadLocal.withInitial(ArrayDeque::new);

    @Inject(method = "render", at = @At("HEAD"))
    private void fishtastic$captureItemStack(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand,
                                             PoseStack poseStack, MultiBufferSource buffer, int combinedLight,
                                             int combinedOverlay, BakedModel model, CallbackInfo ci) {
        fishtastic$currentItemStack.get().push(itemStack);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void fishtastic$clearItemStack(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand,
                                           PoseStack poseStack, MultiBufferSource buffer, int combinedLight,
                                           int combinedOverlay, BakedModel model, CallbackInfo ci) {
        fishtastic$currentItemStack.get().poll();
    }

    /**
     * World quality outline. Injected just after the display transform and the
     * {@code (-0.5, -0.5, -0.5)} translate, so the pose is in model space.
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V", shift = At.Shift.AFTER, ordinal = 0))
    private void fishtastic$renderWorldQualityOutline(ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand,
                                                      PoseStack poseStack, MultiBufferSource buffer, int combinedLight,
                                                      int combinedOverlay, BakedModel model, CallbackInfo ci) {
        FishtasticWorldOutlineRenderer.render(itemStack, displayContext, model, poseStack, buffer);
    }

    @Inject(method = "getFoilBuffer", at = @At("HEAD"), cancellable = true)
    private static void fishtastic$replaceGlintWithQualityGlow(MultiBufferSource bufferSource, RenderType renderType,
                                                               boolean solid, boolean hasFoil,
                                                               CallbackInfoReturnable<VertexConsumer> cir) {
        ItemEffect effect = fishtastic$currentEffect();
        if (effect != null) {
            RenderType glint = Minecraft.useShaderTransparency() && renderType == Sheets.translucentItemSheet()
                    ? FishtasticRenderTypes.qualityGlowTranslucent(effect.texture())
                    : solid ? FishtasticRenderTypes.qualityGlow(effect.texture())
                    : FishtasticRenderTypes.entityQualityGlow(effect.texture());
            cir.setReturnValue(VertexMultiConsumer.create(bufferSource.getBuffer(glint), bufferSource.getBuffer(renderType)));
        }
    }

    @Inject(method = "getFoilBufferDirect", at = @At("HEAD"), cancellable = true)
    private static void fishtastic$replaceGlintWithQualityGlowDirect(MultiBufferSource bufferSource, RenderType renderType,
                                                                     boolean solid, boolean hasFoil,
                                                                     CallbackInfoReturnable<VertexConsumer> cir) {
        ItemEffect effect = fishtastic$currentEffect();
        if (effect != null) {
            RenderType glint = solid ? FishtasticRenderTypes.qualityGlow(effect.texture())
                    : FishtasticRenderTypes.entityQualityGlowDirect(effect.texture());
            cir.setReturnValue(VertexMultiConsumer.create(bufferSource.getBuffer(glint), bufferSource.getBuffer(renderType)));
        }
    }

    @Inject(method = "getCompassFoilBuffer", at = @At("HEAD"), cancellable = true)
    private static void fishtastic$replaceCompassFoilBuffer(MultiBufferSource bufferSource, RenderType renderType,
                                                            PoseStack.Pose pose, CallbackInfoReturnable<VertexConsumer> cir) {
        ItemEffect effect = fishtastic$currentEffect();
        if (effect != null) {
            cir.setReturnValue(VertexMultiConsumer.create(
                    new SheetedDecalTextureGenerator(bufferSource.getBuffer(FishtasticRenderTypes.qualityGlow(effect.texture())), pose, 0.0078125F),
                    bufferSource.getBuffer(renderType)));
        }
    }

    @Unique
    private static ItemEffect fishtastic$currentEffect() {
        ItemStack itemStack = fishtastic$currentItemStack.get().peek();
        return itemStack == null ? null : ItemEffectManager.getEffectForItem(itemStack);
    }
}
