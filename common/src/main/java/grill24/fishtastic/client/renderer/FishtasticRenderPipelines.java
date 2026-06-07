package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

public final class FishtasticRenderPipelines {

    /**
     * Outline blit pipeline for GUI items.
     *
     * Shares the same vertex format and sampler setup as {@code GUI_TEXTURED_PREMULTIPLIED_ALPHA}
     * so it can be used as a drop-in additional blit call on the same atlas texture.
     *
     * The fragment shader discards opaque item pixels and instead draws the outline colour
     * (passed via the blit's {@code color} parameter → {@code vertexColor}) only in the
     * transparent pixels that are 8-connected to an opaque item pixel.
     */
    public static final RenderPipeline GUI_ITEM_OUTLINE = RenderPipeline.builder()
            .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
            .withUniform("Projection", UniformType.UNIFORM_BUFFER)
            .withLocation(Identifier.fromNamespaceAndPath("fishtastic", "pipeline/gui_item_outline"))
            .withVertexShader(Identifier.fromNamespaceAndPath("fishtastic", "core/gui_item_outline"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("fishtastic", "core/gui_item_outline"))
            .withSampler("Sampler0")
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
            .build();

    private FishtasticRenderPipelines() {}
}
