package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.resources.Identifier;

public final class FishtasticRenderPipelines {

    /**
     * std140 size of the {@code ItemOutlineParams} UBO shared by all outline shaders.
     * Layout: vec4 color | float falloff | float opacity | float width | float animSpeed
     *         | int numBlades | float bladeFill | float _reserved0 | float _reserved1
     */
    public static final int OUTLINE_PARAMS_UBO_SIZE = new Std140SizeCalculator()
            .putVec4()   // color (RGBA)
            .putFloat()  // falloff
            .putFloat()  // opacity
            .putFloat()  // width
            .putFloat()  // animSpeed
            .putInt()    // numBlades
            .putFloat()  // bladeFill
            .putFloat()  // _reserved0
            .putFloat()  // _reserved1
            .get();

    /** UBO name declared in {@code gui_item_outline.fsh}. */
    public static final String BASIC_OUTLINE_UBO_NAME = "BasicOutlineParams";
    /** UBO name declared in {@code gui_item_outline_legendary.fsh}. */
    public static final String LEGENDARY_OUTLINE_UBO_NAME = "LegendaryOutlineParams";
    /** UBO name declared in {@code gui_item_outline_debug_uv.fsh}. */
    public static final String DEBUG_UV_UBO_NAME = "DebugUvOutlineParams";

    /** Creates a per-effect basic outline pipeline with a unique location ID. */
    public static RenderPipeline createOutlinePipeline(Identifier location) {
        return RenderPipeline.builder()
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform(BASIC_OUTLINE_UBO_NAME, UniformType.UNIFORM_BUFFER)
                .withLocation(location)
                .withVertexShader(Identifier.fromNamespaceAndPath("fishtastic", "core/gui_item_outline"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("fishtastic", "core/gui_item_outline"))
                .withSampler("Sampler0")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();
    }

    /** Creates a per-effect legendary (animated pinwheel) outline pipeline with a unique location ID. */
    public static RenderPipeline createLegendaryOutlinePipeline(Identifier location) {
        return RenderPipeline.builder()
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform(LEGENDARY_OUTLINE_UBO_NAME, UniformType.UNIFORM_BUFFER)
                .withLocation(location)
                .withVertexShader(Identifier.fromNamespaceAndPath("fishtastic", "core/gui_item_outline_legendary"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("fishtastic", "core/gui_item_outline_legendary"))
                .withSampler("Sampler0")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();
    }

    /**
     * Creates a per-effect basic outline pipeline for in-world item entities / item frames.
     *
     * <p>Differs from the GUI variant: vertices are pose-transformed world-space quads (depth
     * tested against the level, no depth write, double-sided), and the atlas slot geometry is
     * passed as compile-time defines instead of being derived from screen-space derivatives —
     * {@link FishtasticItemOutlineAtlas} has a fixed slot size, unlike the guiScale-dependent
     * vanilla GUI atlas.
     */
    public static RenderPipeline createWorldOutlinePipeline(Identifier location) {
        return worldOutlineBuilderBase(location)
                .withUniform(BASIC_OUTLINE_UBO_NAME, UniformType.UNIFORM_BUFFER)
                .withVertexShader(Identifier.fromNamespaceAndPath("fishtastic", "core/world_item_outline"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("fishtastic", "core/world_item_outline"))
                .build();
    }

    /** Creates a per-effect legendary (animated pinwheel) world outline pipeline. */
    public static RenderPipeline createWorldLegendaryOutlinePipeline(Identifier location) {
        return worldOutlineBuilderBase(location)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform(LEGENDARY_OUTLINE_UBO_NAME, UniformType.UNIFORM_BUFFER)
                .withVertexShader(Identifier.fromNamespaceAndPath("fishtastic", "core/world_item_outline"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("fishtastic", "core/world_item_outline_legendary"))
                .build();
    }

    private static RenderPipeline.Builder worldOutlineBuilderBase(Identifier location) {
        return RenderPipeline.builder()
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withLocation(location)
                .withShaderDefine("FISHTASTIC_ATLAS_SLOT_PX", FishtasticItemOutlineAtlas.SLOT_PX)
                .withShaderDefine("FISHTASTIC_ATLAS_RES", FishtasticItemOutlineAtlas.ITEM_RENDER_PX / 16)
                .withSampler("Sampler0")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                // Depth-test against the world but never write: the outline is a translucent
                // overlay that must not occlude the item or anything behind it.
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                // Double-sided: the outline quad must be visible from behind the item too.
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS);
    }

    /** Creates a per-effect debug-UV pipeline with a unique location ID. */
    public static RenderPipeline createDebugUvPipeline(Identifier location) {
        return RenderPipeline.builder()
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform(DEBUG_UV_UBO_NAME, UniformType.UNIFORM_BUFFER)
                .withLocation(location)
                .withVertexShader(Identifier.fromNamespaceAndPath("fishtastic", "core/gui_item_outline_debug_uv"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("fishtastic", "core/gui_item_outline_debug_uv"))
                .withSampler("Sampler0")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();
    }

    private FishtasticRenderPipelines() {}
}
