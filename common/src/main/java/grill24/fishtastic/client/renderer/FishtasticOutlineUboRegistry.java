package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderPass;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Registry mapping each outline {@link RenderPipeline} to its UBO name and {@link GpuBuffer}.
 *
 * <p>Uses identity equality on {@link RenderPipeline} so multiple effect instances that
 * happen to share the same shader (e.g., uncommon, rare, and epic all use
 * {@code gui_item_outline.fsh}) each get their own entry and correct params.
 *
 * <p>{@link #bind(RenderPipeline, RenderPass)} is called per-draw from
 * {@link grill24.fishtastic.mixin.GuiRendererExecuteDrawMixin} to bind the right buffer
 * just before each outline draw executes.
 */
@Environment(EnvType.CLIENT)
public final class FishtasticOutlineUboRegistry {

    private record Entry(String uboName, GpuBuffer buffer) {}

    private static final Map<RenderPipeline, Entry> REGISTRY = new IdentityHashMap<>();

    /** Register a pipeline with its UBO name and params buffer. */
    public static void register(RenderPipeline pipeline, String uboName, GpuBuffer buffer) {
        REGISTRY.put(pipeline, new Entry(uboName, buffer));
    }

    /**
     * If {@code pipeline} is a registered outline pipeline, binds its params UBO
     * to the render pass.  No-op for unregistered pipelines.
     */
    public static void bind(RenderPipeline pipeline, RenderPass renderPass) {
        Entry entry = REGISTRY.get(pipeline);
        if (entry != null) {
            renderPass.setUniform(entry.uboName(), entry.buffer().slice());
        }
    }

    /** Returns the registered params buffer for {@code pipeline}, or {@code null}. */
    public static @Nullable GpuBuffer getBuffer(RenderPipeline pipeline) {
        Entry entry = REGISTRY.get(pipeline);
        return entry != null ? entry.buffer() : null;
    }

    private FishtasticOutlineUboRegistry() {}
}
