package grill24.fishtastic.client.renderer;

import grill24.fishtastic.util.Ids;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/**
 * Lazily-created GPU resources for the animated warm-gold "look here" outline drawn around GUI
 * items the player is being steered toward (rods while holding bait/hook/charm on the cursor,
 * bait/hooks/charms while holding a rod, and the tutorial's bait/rod prompts).
 *
 * <p>Same shape as {@link FishtasticBlackOutlineEffect}: UI-context driven (see
 * {@link FishtasticGlintState#HIGHLIGHT_REQUESTED}), fixed look, so one shared pipeline and one
 * params buffer. Unlike the quality-tier outlines it pulses, so it can't be mistaken for a rarity.
 */
public final class FishtasticHighlightEffect {
    private static final Identifier PIPELINE_ID = Ids.of("fishtastic", "pipeline/gui_item_highlight");

    /** How far past the 16x16 item slot the blit quad extends, in item pixels, so the glow isn't clipped. */
    public static final int PAD_PX = 2;

    /** Warm gold, matching the tutorial's hotbar box (0xFFFFDD44). */
    private static final float COLOR_R = 1.0f, COLOR_G = 0.867f, COLOR_B = 0.267f;
    private static final float OPACITY = 1.0f;
    /** Solid ring thickness, item pixels. */
    private static final float WIDTH = 0.75f;
    /** Soft falloff beyond the ring at the top of the pulse, item pixels. WIDTH + GLOW must stay within {@link #PAD_PX}. */
    private static final float GLOW = 1.25f;
    /** Cycles per in-game day (24000 ticks) — 900 = 0.75 pulses per real second. Keep it a whole number. */
    private static final float PULSE_SPEED = 900.0f;
    /** How far the glow's reach shrinks at the bottom of each pulse (0.45 = down to 55%). The ring itself doesn't pulse. */
    private static final float PULSE_AMOUNT = 0.45f;

    private static RenderPipeline pipeline;
    private static GpuBuffer paramsBuffer;

    private FishtasticHighlightEffect() {}

    /** Must be called on the render thread. */
    public static RenderPipeline getOrCreatePipeline() {
        if (pipeline == null) {
            pipeline = FishtasticRenderPipelines.createHighlightPipeline(PIPELINE_ID);
            FishtasticOutlineUboRegistry.register(pipeline, FishtasticRenderPipelines.HIGHLIGHT_UBO_NAME, getOrCreateParamsBuffer());
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
        int size = FishtasticRenderPipelines.HIGHLIGHT_PARAMS_UBO_SIZE;
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "Item Highlight Params",
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
        );
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, size)
                    .putVec4(COLOR_R, COLOR_G, COLOR_B, 1.0f)
                    .putFloat(OPACITY)
                    .putFloat(WIDTH)
                    .putFloat(GLOW)
                    .putFloat(PULSE_SPEED)
                    .putFloat(PULSE_AMOUNT)
                    .putFloat(0.0f) // _reserved0
                    .putFloat(0.0f) // _reserved1
                    .putFloat(0.0f) // _reserved2
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
        return buffer;
    }
}
