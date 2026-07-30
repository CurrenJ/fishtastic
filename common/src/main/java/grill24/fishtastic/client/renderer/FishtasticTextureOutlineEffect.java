package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Lazily-created GPU resources for a solid black edge outline on GUI sprites blitted directly from
 * their own texture — currently the fishing minigame's zone icons.
 *
 * <p>Sibling of {@link FishtasticBlackOutlineEffect}, which does the same job for sprites drawn
 * through the item atlas. Separate because the two shaders measure outline width in different units:
 * physical pixels in the atlas, source texels here.
 */
public final class FishtasticTextureOutlineEffect {
    private static final Identifier PIPELINE_ID =
            Identifier.fromNamespaceAndPath("fishtastic", "pipeline/gui_texture_black_outline");

    /** Solid black, no gradient fade, full opacity, one source texel thick. */
    private static final float COLOR_R = 0.0f, COLOR_G = 0.0f, COLOR_B = 0.0f;
    private static final float FALLOFF = 0.0f;
    private static final float OPACITY = 1.0f;
    private static final float WIDTH_TEXELS = 1.0f;

    private static RenderPipeline pipeline;
    private static GpuBuffer paramsBuffer;

    private FishtasticTextureOutlineEffect() {}

    /** Must be called on the render thread. */
    public static RenderPipeline getOrCreatePipeline() {
        if (pipeline == null) {
            pipeline = FishtasticRenderPipelines.createTextureOutlinePipeline(PIPELINE_ID);
            FishtasticOutlineUboRegistry.register(
                    pipeline, FishtasticRenderPipelines.BASIC_OUTLINE_UBO_NAME, getOrCreateParamsBuffer());
        }
        return pipeline;
    }

    private static GpuBuffer getOrCreateParamsBuffer() {
        if (paramsBuffer == null) {
            paramsBuffer = buildParamsBuffer();
        }
        return paramsBuffer;
    }

    private static GpuBuffer buildParamsBuffer() {
        int size = FishtasticRenderPipelines.OUTLINE_PARAMS_UBO_SIZE;
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "Texture Black Outline Params",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
        );
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, size)
                    .putVec4(COLOR_R, COLOR_G, COLOR_B, 1.0f)
                    .putFloat(FALLOFF)
                    .putFloat(OPACITY)
                    .putFloat(WIDTH_TEXELS)
                    .putFloat(0.0f) // animSpeed — unused by the basic outline shader
                    .putInt(0)      // numBlades — unused by the basic outline shader
                    .putFloat(0.0f) // bladeFill — unused by the basic outline shader
                    .putFloat(0.0f) // _reserved0
                    .putFloat(0.0f) // _reserved1
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
        return buffer;
    }
}
