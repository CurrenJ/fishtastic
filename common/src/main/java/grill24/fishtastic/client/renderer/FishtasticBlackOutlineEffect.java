package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Lazily-created GPU resources for a solid black GUI item edge outline, used by the fishing
 * minigame's gear readout (equipped hook/charm icons) — see {@code FishingMinigameAnimation}.
 *
 * <p>Reuses the exact same shader/pipeline shape as the per-quality-tier outlines
 * ({@code gui_item_outline.fsh}, driven by {@link grill24.fishtastic.itemeffect.ItemEffect}),
 * since that shader is already fully parameterized (color/falloff/opacity/width) and a solid
 * black edge is just one more parameter combination — no new GLSL needed. But like
 * {@link FishtasticSilhouetteEffect}, it isn't data-driven or keyed by an {@code ItemStack}'s
 * rarity component: every caller wants the same fixed look, driven by UI context
 * ({@link FishtasticGlintState#BLACK_OUTLINE_REQUESTED}) rather than item data, so one shared
 * pipeline + params buffer suffices instead of one per effect instance.
 */
public final class FishtasticBlackOutlineEffect {
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath("fishtastic", "pipeline/gui_item_black_outline");

    /** Solid black, no gradient fade, full opacity, one item-pixel thick. */
    private static final float COLOR_R = 0.0f, COLOR_G = 0.0f, COLOR_B = 0.0f;
    private static final float FALLOFF = 0.0f;
    private static final float OPACITY = 1.0f;
    private static final float WIDTH = 0.5f;

    private static RenderPipeline pipeline;
    private static GpuBuffer paramsBuffer;

    private FishtasticBlackOutlineEffect() {}

    /** Must be called on the render thread. */
    public static RenderPipeline getOrCreatePipeline() {
        if (pipeline == null) {
            pipeline = FishtasticRenderPipelines.createOutlinePipeline(PIPELINE_ID);
            FishtasticOutlineUboRegistry.register(pipeline, FishtasticRenderPipelines.BASIC_OUTLINE_UBO_NAME, getOrCreateParamsBuffer());
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
                () -> "Gear Black Outline Params",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
        );
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, size)
                    .putVec4(COLOR_R, COLOR_G, COLOR_B, 1.0f)
                    .putFloat(FALLOFF)
                    .putFloat(OPACITY)
                    .putFloat(WIDTH)
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
