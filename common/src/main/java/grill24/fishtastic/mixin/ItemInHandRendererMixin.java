package grill24.fishtastic.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.FishtasticGlintState;
import grill24.fishtastic.client.renderer.FishtasticWorldOutlineRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

/**
 * Hooks held items (first-person / third-person hand rendering) into the in-world
 * outline path.
 *
 * <p>{@link ItemInHandRenderer#renderItem} creates a new {@link ItemStackRenderState}
 * each frame, resolves it via {@code updateForTopItem}, and submits. This mixin intercepts
 * between the resolve and submit phases to add a custom outline quad for quality items,
 * using the same {@link FishtasticWorldOutlineRenderer} that serves item entities and
 * item frames.
 *
 * <p>The render state is a method-local and is not pooled/reused, so the
 * {@code WORLD_OUTLINE_MAP} entry is cleaned up immediately after submission rather
 * than relying on {@code ItemStackRenderState.clear()}.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(
            method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            locals = LocalCapture.CAPTURE_FAILHARD,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemModelResolver;updateForTopItem(Lnet/minecraft/client/renderer/item/ItemStackRenderState;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/ItemOwner;I)V",
                    shift = At.Shift.AFTER
            ))
    private void fishtastic$addHandItemOutline(
            LivingEntity mob,
            ItemStack itemStack,
            ItemDisplayContext type,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            int lightCoords,
            CallbackInfo ci,
            ItemStackRenderState renderState) {

        FishtasticWorldOutlineRenderer.capture(renderState, itemStack);
        // mirrorU=false: first-person display transforms differ from FIXED (no 180° Y flip).
        FishtasticWorldOutlineRenderer.submitOutline(poseStack, submitNodeCollector, renderState, false);
        // Clean up immediately — renderState is method-local and not pooled.
        FishtasticGlintState.WORLD_OUTLINE_MAP.remove(renderState);
    }
}
