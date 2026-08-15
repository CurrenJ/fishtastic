package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.pipeline.RenderPipeline.Snippet;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public final class FishtasticRenderPipelines {

    /**
     * Same shading as vanilla's {@code entity_translucent} (core/entity shader, per-face lighting,
     * overlay + lightmap samplers) but with depth WRITE disabled. Vanilla's version inherits
     * {@code DepthStencilState.DEFAULT} (write=true) from its base snippet, which is fine for a
     * single translucent surface but wrong for the fish tank's water fill: since the water quad
     * sits nearer the camera than fish deeper in the tank, writing its depth caused every fish (and
     * their quality-outline glow) drawn afterward to fail the depth test and vanish outright. Depth
     * TEST stays on, so opaque geometry (frame, sand) still correctly occludes the water quad.
     * <p>
     * Built by cloning {@code RenderPipelines.ENTITY_TRANSLUCENT} into a {@link Snippet} rather than
     * starting from its base {@code ENTITY_SNIPPET}, which is package-private in vanilla — cloning
     * the finished pipeline gets the identical shader/uniform/sampler layout without needing that access.
     */
    public static final RenderPipeline TANK_WATER_FILL = buildTankWaterFillPipeline();

    private static RenderPipeline buildTankWaterFillPipeline() {
        RenderPipeline base = RenderPipelines.ENTITY_TRANSLUCENT;
        Snippet snippet = new Snippet(
                Optional.of(base.getVertexShader()),
                Optional.of(base.getFragmentShader()),
                Optional.of(base.getShaderDefines()),
                Optional.of(base.getSamplers()),
                Optional.of(base.getUniforms()),
                Optional.of(base.getColorTargetState()),
                Optional.empty(), // depth-stencil state: overridden below with write disabled
                Optional.of(base.getPolygonMode()),
                Optional.of(base.isCull()),
                Optional.of(base.getVertexFormat()),
                Optional.of(base.getVertexFormatMode())
        );

        RenderPipeline pipeline = RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath("fishtastic", "pipeline/tank_water_fill"))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .build();
        // Without this the quad is invisible under every shaderpack — see IrisCompat.
        //
        // ENTITIES_TRANSLUCENT, not BLOCK_TRANSLUCENT. The latter is the intuitive choice (this is
        // a block entity's translucent surface) but it routes the quad into the pack's water/block
        // program, which computes its own water colour, normals and waves and largely discards the
        // incoming albedo — the fill rendered as a flat dark grey sheet with no texture detail and
        // no tint. The entity program applies ordinary translucent shading and preserves both.
        IrisCompat.assignPipeline(pipeline, "ENTITIES_TRANSLUCENT", "SHADOW_TRANSLUCENT");
        return pipeline;
    }

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
    /** UBO name declared in {@code gui_item_silhouette.fsh}. */
    public static final String SILHOUETTE_UBO_NAME = "SilhouetteParams";

    /**
     * std140 size of the {@code SilhouetteParams} UBO.
     * Layout: vec4 color | float opacity | float pulseSpeed | float pulseAmount | float edgeBlurTexels
     *         | float dissolveScale | float dissolveSpeed | float dissolveStrength | float _reserved0
     */
    public static final int SILHOUETTE_PARAMS_UBO_SIZE = new Std140SizeCalculator()
            .putVec4()   // color (RGBA)
            .putFloat()  // opacity
            .putFloat()  // pulseSpeed
            .putFloat()  // pulseAmount
            .putFloat()  // edgeBlurTexels
            .putFloat()  // dissolveScale
            .putFloat()  // dissolveSpeed
            .putFloat()  // dissolveStrength
            .putFloat()  // _reserved0
            .get();

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

    /**
     * Creates an outline pipeline for GUI sprites blitted directly from their own texture rather than
     * through the item atlas. Same vertex shader and UBO as {@link #createOutlinePipeline}; the
     * fragment shader differs in how it measures width and bounds its neighbour search.
     */
    public static RenderPipeline createTextureOutlinePipeline(Identifier location) {
        return RenderPipeline.builder()
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform(BASIC_OUTLINE_UBO_NAME, UniformType.UNIFORM_BUFFER)
                .withLocation(location)
                .withVertexShader(Identifier.fromNamespaceAndPath("fishtastic", "core/gui_item_outline"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("fishtastic", "core/gui_texture_outline"))
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
     * The single pipeline used to draw in-world quality outlines for item entities / item frames.
     *
     * <p><b>Why there is only one.</b>  The outline used to be synthesized per-fragment by
     * {@code world_item_outline.fsh}, which needed a distinct pipeline per effect to carry that
     * effect's params UBO.  The ring is now pre-baked into {@link FishtasticItemOutlineAtlas}
     * (see {@link #createOutlineBakePipeline}), so drawing it is just a textured translucent quad
     * with no per-effect state — one shared pipeline covers every quality tier, basic and legendary
     * alike.
     *
     * <p><b>Why it clones {@code ENTITY_TRANSLUCENT}.</b>  This pipeline is registered with Iris so
     * it survives shaderpack rendering, which means the pack's {@code gbuffers_entities} program
     * replaces our shader.  That program expects the entity vertex format, so the pipeline must use
     * {@code NEW_ENTITY} and the quad must supply light/overlay/normal — see
     * {@link FishtasticWorldOutlineRenderer#submitOutline}.  Cloning the vanilla pipeline into a
     * {@link Snippet} is how we inherit that exact layout without touching the package-private
     * {@code ENTITY_SNIPPET}, the same trick {@link #TANK_WATER_FILL} uses.
     *
     * <p>Depth write is disabled (the outline is a translucent overlay that must not occlude the
     * item or anything behind it) and culling is off (it must be visible from behind the item too).
     */
    public static final RenderPipeline WORLD_OUTLINE = buildWorldOutlinePipeline();

    private static RenderPipeline buildWorldOutlinePipeline() {
        RenderPipeline base = RenderPipelines.ENTITY_TRANSLUCENT;
        Snippet snippet = new Snippet(
                Optional.of(base.getVertexShader()),
                Optional.of(base.getFragmentShader()),
                Optional.of(base.getShaderDefines()),
                Optional.of(base.getSamplers()),
                Optional.of(base.getUniforms()),
                Optional.of(base.getColorTargetState()),
                Optional.empty(), // depth-stencil state: overridden below with write disabled
                Optional.of(base.getPolygonMode()),
                Optional.empty(), // cull: overridden below
                Optional.of(base.getVertexFormat()),
                Optional.of(base.getVertexFormatMode())
        );

        RenderPipeline pipeline = RenderPipeline.builder(snippet)
                .withLocation(Identifier.fromNamespaceAndPath("fishtastic", "pipeline/world_item_outline"))
                .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
                .withCull(false)
                .build();
        // Without this the outline is invisible under every shaderpack — see IrisCompat.
        // No shadow program: a translucent cosmetic overlay must not write to the shadow map.
        IrisCompat.assignPipeline(pipeline, "ENTITIES_TRANSLUCENT", null);
        return pipeline;
    }

    /**
     * Creates a per-effect pipeline for the offscreen outline <em>bake</em> pass, which draws one
     * full-slot quad into {@link FishtasticItemOutlineAtlas}, reading the mask atlas and writing the
     * synthesized ring into the outline atlas.
     *
     * <p>These pipelines keep their custom fragment shaders and are deliberately <em>not</em>
     * registered with Iris: the bake runs offscreen against our own render target, outside the world
     * pass, so Iris never intercepts it.  That is exactly what makes this approach shader-proof —
     * all the procedural work happens where no shaderpack can reach it.
     *
     * <p>Blending is off: the target slot is cleared before the bake, so the ring's alpha must land
     * in the atlas verbatim rather than being composited against it.  Draw-time blending is the
     * render type's job.
     */
    public static RenderPipeline createOutlineBakePipeline(Identifier location) {
        return outlineBakeBuilderBase(location)
                .withUniform(BASIC_OUTLINE_UBO_NAME, UniformType.UNIFORM_BUFFER)
                .withFragmentShader(Identifier.fromNamespaceAndPath("fishtastic", "core/outline_bake"))
                .build();
    }

    /** Creates a per-effect legendary (animated pinwheel) outline bake pipeline. */
    public static RenderPipeline createOutlineBakeLegendaryPipeline(Identifier location) {
        return outlineBakeBuilderBase(location)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform(LEGENDARY_OUTLINE_UBO_NAME, UniformType.UNIFORM_BUFFER)
                .withFragmentShader(Identifier.fromNamespaceAndPath("fishtastic", "core/outline_bake_legendary"))
                .build();
    }

    private static RenderPipeline.Builder outlineBakeBuilderBase(Identifier location) {
        return RenderPipeline.builder()
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withLocation(location)
                .withShaderDefine("FISHTASTIC_ATLAS_SLOT_PX", FishtasticItemOutlineAtlas.SLOT_PX)
                .withShaderDefine("FISHTASTIC_ATLAS_RES", FishtasticItemOutlineAtlas.ITEM_RENDER_PX / 16)
                .withSampler("Sampler0")
                .withVertexShader(Identifier.fromNamespaceAndPath("fishtastic", "core/outline_bake"))
                .withColorTargetState(ColorTargetState.DEFAULT)
                .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
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

    /** Creates the GUI item silhouette fill pipeline. Only one instance is ever needed (see {@link FishtasticSilhouetteEffect}). */
    public static RenderPipeline createSilhouettePipeline(Identifier location) {
        return RenderPipeline.builder()
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform(SILHOUETTE_UBO_NAME, UniformType.UNIFORM_BUFFER)
                .withLocation(location)
                .withVertexShader(Identifier.fromNamespaceAndPath("fishtastic", "core/gui_item_silhouette"))
                .withFragmentShader(Identifier.fromNamespaceAndPath("fishtastic", "core/gui_item_silhouette"))
                .withSampler("Sampler0")
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .build();
    }

    private FishtasticRenderPipelines() {}
}
