package grill24.fishtastic.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import grill24.fishtastic.client.renderer.FishtasticOutlineUboRegistry;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(GuiRenderer.class)
public class GuiRendererExecuteDrawMixin {

    @Inject(
            method = "executeDraw",
            locals = LocalCapture.CAPTURE_FAILHARD,
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/systems/RenderPass;setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void fishtastic$bindOutlineParams(
            @Coerce Object draw,
            RenderPass renderPass,
            GpuBuffer indexBuffer,
            VertexFormat.IndexType indexType,
            CallbackInfo ci,
            RenderPipeline pipeline
    ) {
        FishtasticOutlineUboRegistry.bind(pipeline, renderPass);
    }
}
