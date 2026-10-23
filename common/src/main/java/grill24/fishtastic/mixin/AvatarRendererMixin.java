package grill24.fishtastic.mixin;

import grill24.fishtastic.client.companion.CompanionPerchLayer;
import grill24.fishtastic.mixin.accessor.LivingEntityRendererInvoker;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the fish companion perch layer to every player renderer (docs/fish-companions.md).
 *
 * <p>Both player model variants go through this constructor. A common mixin rather than a loader
 * event so the registration is identical on both loaders. PORT-ONLY: 1.21.1 names the target
 * {@link PlayerRenderer} (26.1's {@code AvatarRenderer}), and there are no mannequin renderers.
 */
@Mixin(PlayerRenderer.class)
public abstract class AvatarRendererMixin {

    @Inject(method = "<init>(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;Z)V",
            at = @At("TAIL"))
    private void fishtastic$addCompanionPerchLayer(EntityRendererProvider.Context context, boolean slimSteve, CallbackInfo ci) {
        @SuppressWarnings("unchecked")
        RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent =
                (RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>) (Object) this;
        ((LivingEntityRendererInvoker) this).fishtastic$addLayer(new CompanionPerchLayer(parent));
    }
}
