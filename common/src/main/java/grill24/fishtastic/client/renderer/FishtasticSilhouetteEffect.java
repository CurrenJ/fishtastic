package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Lazily-created GPU resources for the GUI item silhouette fill effect used by
 * {@code SilhouetteItemButton} (fish encyclopedia "never caught" icons).
 *
 * <p>Single shared pipeline + params buffer — unlike {@code ItemEffect} (one instance per
 * rarity tier, each potentially configured differently), every silhouette currently wants the
 * same look, so one instance suffices. Pulled out as its own class rather than added to
 * {@code ItemEffect} because it isn't data-driven (no datapack JSON) and isn't keyed by an
 * {@code ItemStack}'s rarity-tier data component — it's driven by UI state (has this player
 * caught this fish?), via {@link FishtasticGlintState#SILHOUETTE_REQUESTED}.
 */
public final class FishtasticSilhouetteEffect {
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath("fishtastic", "pipeline/gui_item_silhouette");

    /** Fill colour (opaque black) and overall opacity. */
    private static final float COLOR_R = 0.0f, COLOR_G = 0.0f, COLOR_B = 0.0f;
    private static final float OPACITY = 1.0f;
    /** Gentle breathing alpha — 0 disables the pulse entirely. Cycles per in-game day. */
    private static final float PULSE_SPEED = 0.0f;
    private static final float PULSE_AMOUNT = 0.35f;

    private static RenderPipeline pipeline;
    private static GpuBuffer paramsBuffer;

    private FishtasticSilhouetteEffect() {}

    /** Must be called on the render thread. */
    public static RenderPipeline getOrCreatePipeline() {
        if (pipeline == null) {
            pipeline = FishtasticRenderPipelines.createSilhouettePipeline(PIPELINE_ID);
            FishtasticOutlineUboRegistry.register(pipeline, FishtasticRenderPipelines.SILHOUETTE_UBO_NAME, getOrCreateParamsBuffer());
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
        int size = FishtasticRenderPipelines.SILHOUETTE_PARAMS_UBO_SIZE;
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "Item Silhouette Params",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
        );
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, size)
                    .putVec4(COLOR_R, COLOR_G, COLOR_B, 1.0f)
                    .putFloat(OPACITY)
                    .putFloat(PULSE_SPEED)
                    .putFloat(PULSE_AMOUNT)
                    .putFloat(0.0f) // _reserved0
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
        return buffer;
    }
}
