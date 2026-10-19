package grill24.fishtastic.forge.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.ambient.ClientAmbientFish;
import grill24.fishtastic.client.companion.ClientCompanions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge's narrow submit/extract seam for world-space ambient fish and companions. */
@Mixin(LevelRenderer.class)
public class LevelRendererForgeMixin {
    @Inject(method = "submitEntities", at = @At("TAIL"))
    private void fishtastic$submitWorld(PoseStack poses, LevelRenderState state,
                                        SubmitNodeCollector nodes, CallbackInfo ci) {
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ClientAmbientFish.submitWorld(poses, nodes, partialTick);
        ClientCompanions.submitWorld(poses, nodes, partialTick);
    }
}
