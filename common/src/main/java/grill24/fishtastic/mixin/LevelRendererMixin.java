package grill24.fishtastic.mixin;

import grill24.fishtastic.client.renderer.FishtasticRenderTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderBuffers;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow
    @Final
    private RenderBuffers renderBuffers;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/RenderType;glint()Lnet/minecraft/client/renderer/RenderType;"))
    private void fishtastic$beforeGlintRender(DeltaTracker deltaTracker, boolean bl, Camera camera, GameRenderer gameRenderer, LightTexture lightTexture, Matrix4f matrix4f, Matrix4f matrix4f2, CallbackInfo ci) {
        this.renderBuffers.bufferSource().endBatch(FishtasticRenderTypes.qualityGlow());
        this.renderBuffers.bufferSource().endBatch(FishtasticRenderTypes.qualityGlowTranslucent());
        this.renderBuffers.bufferSource().endBatch(FishtasticRenderTypes.entityQualityGlow());
        this.renderBuffers.bufferSource().endBatch(FishtasticRenderTypes.entityQualityGlowDirect());
    }
}
