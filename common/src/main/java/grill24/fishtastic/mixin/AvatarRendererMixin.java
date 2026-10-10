package grill24.fishtastic.mixin;

import grill24.fishtastic.client.companion.CompanionPerchLayer;
import grill24.fishtastic.mixin.accessor.LivingEntityRendererInvoker;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the fish companion perch layer to every player renderer (docs/fish-companions.md §11).
 *
 * <p>Both player model variants — and the mannequin renderers, which are also
 * {@link AvatarRenderer}s — go through this constructor; the layer itself filters mannequins out by
 * looking the rendered entity up by id, so no per-caller branching is needed here. A common mixin
 * rather than a loader event because the Fabric API of this version has no layer-registration
 * callback.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V",
            at = @At("TAIL"))
    private void fishtastic$addCompanionPerchLayer(EntityRendererProvider.Context context, boolean slimSteve, CallbackInfo ci) {
        @SuppressWarnings({"unchecked", "rawtypes"})
        RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent) this;
        ((LivingEntityRendererInvoker) this).fishtastic$addLayer(new CompanionPerchLayer(parent));
    }
}
