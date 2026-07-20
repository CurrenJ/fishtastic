package grill24.fishtastic.mixin;

import grill24.fishtastic.client.renderer.FishtasticGlintState;
import grill24.fishtastic.client.renderer.FishtasticItemStackRenderState;
import grill24.fishtastic.itemeffect.ItemEffect;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After the full item-model hierarchy has been resolved into {@link ItemStackRenderState}
 * layers, check whether the item has a custom fishtastic glint effect and, if so,
 * record it in {@link grill24.fishtastic.client.renderer.FishtasticGlintState#SUBMIT_EFFECT_MAP}
 * keyed by each layer's quads-list identity.
 *
 * <p>We hook {@code updateForTopItem} (rather than {@code appendItemLayers}) so that
 * recursive calls from composite models do not overwrite the top-level item's effect.
 */
@Mixin(ItemModelResolver.class)
public class ItemModelResolverMixin {

    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void fishtastic$captureGlintEffect(
            ItemStackRenderState output,
            ItemStack item,
            ItemDisplayContext displayContext,
            @Nullable Level level,
            @Nullable ItemOwner owner,
            int seed,
            CallbackInfo ci) {

        ItemEffect effect = ItemEffectManager.getEffectForItem(item);
        FishtasticItemStackRenderState fishtasticOutput = (FishtasticItemStackRenderState) output;
        fishtasticOutput.fishtastic$captureGlintEffects(effect);
        if (effect != null) {
            FishtasticGlintState.GUI_EFFECT_MAP.put(output, effect);
        } else {
            FishtasticGlintState.GUI_EFFECT_MAP.remove(output);
        }

        if (Boolean.TRUE.equals(FishtasticGlintState.SILHOUETTE_REQUESTED.get())) {
            FishtasticGlintState.GUI_SILHOUETTE_MAP.put(output, Boolean.TRUE);
        } else {
            FishtasticGlintState.GUI_SILHOUETTE_MAP.remove(output);
        }

        if (Boolean.TRUE.equals(FishtasticGlintState.BLACK_OUTLINE_REQUESTED.get())) {
            FishtasticGlintState.GUI_BLACK_OUTLINE_MAP.put(output, Boolean.TRUE);
        } else {
            FishtasticGlintState.GUI_BLACK_OUTLINE_MAP.remove(output);
        }
    }
}


