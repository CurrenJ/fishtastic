package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * Every render type Fishtastic defines. 1.21.1 has no {@code RenderPipeline}: a render type is a
 * {@link RenderType.CompositeState} of {@code RenderStateShard}s, and the shards are
 * {@code protected}, so the types live in this {@link RenderType} subclass to reach them.
 * {@code RenderType.create} itself is private in vanilla and widened by {@code fishtastic.accesswidener}.
 * This is the 1.21.1 counterpart of 26.1.2's {@code FishtasticRenderPipelines}.
 */
public final class FishtasticRenderTypes extends RenderType {

    /**
     * Same shading as vanilla's {@code entity_translucent} (entity shader, lightmap, overlay, no
     * cull) on the block atlas, but with depth WRITE disabled. Vanilla's version writes depth, which
     * is fine for a single translucent surface but wrong for the fish tank's water fill: since the
     * water quad sits nearer the camera than fish deeper in the tank, writing its depth caused every
     * fish (and their quality-outline glow) drawn afterward to fail the depth test and vanish
     * outright. Depth TEST stays on, so opaque geometry (frame, sand) still correctly occludes the
     * water quad.
     */
    public static final RenderType TANK_WATER_FILL = create(
            "fishtastic_tank_water_fill",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            TRANSIENT_BUFFER_SIZE,
            true,
            true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(true));

    // Without this the quad is invisible under every shaderpack — see IrisCompat.
    //
    // ENTITIES_TRANSLUCENT, not BLOCK_TRANSLUCENT. The latter is the intuitive choice (this is a
    // block entity's translucent surface) but it routes the quad into the pack's water/block
    // program, which computes its own water colour, normals and waves and largely discards the
    // incoming albedo — the fill rendered as a flat dark grey sheet with no texture detail and no
    // tint. The entity program applies ordinary translucent shading and preserves both.
    static {
        IrisCompat.assignPipeline(TANK_WATER_FILL, "ENTITIES_TRANSLUCENT", "SHADOW_TRANSLUCENT");
    }

    /**
     * In-world quality outlines (item entities, item frames, tank fish, first-person held items):
     * one textured quad sampling {@link FishtasticItemOutlineAtlas}. Entity-translucent shading
     * with NEAREST sampling (the texture shard sets the filter each time it binds; the GUI path
     * samples the same texture LINEAR), no cull (visible from behind the item too) and no depth
     * write (a translucent overlay must not occlude the item or anything behind it). Drawn into
     * the item-entity target like vanilla's {@code itemEntityTranslucentCull}, so it composites
     * correctly under Fabulous graphics (spike finding F4).
     */
    public static final RenderType ITEM_OUTLINE = create(
            "fishtastic_item_outline",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            TRANSIENT_BUFFER_SIZE,
            true,
            true,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER)
                    .setTextureState(new TextureStateShard(FishtasticItemOutlineAtlas.TEXTURE_ID, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setCullState(NO_CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_WRITE)
                    .createCompositeState(true));

    // Without this the outline is invisible under every shaderpack — see IrisCompat.
    // No shadow program: a translucent cosmetic overlay must not write to the shadow map.
    static {
        IrisCompat.assignPipeline(ITEM_OUTLINE, "ENTITIES_TRANSLUCENT", null);
    }

    /**
     * PORT-ONLY: the cosmetic-capture wand's selection boxes (see
     * {@code client.CosmeticCaptureClientState}). Vanilla's {@code RenderType.lines()} with a 2 px
     * line width, which is what 26.1's {@code GizmoStyle.stroke(…, 2f)} asks for: 1.21.1 has no
     * {@code Gizmos}, so the preview is drawn with the vanilla debug primitives, and the
     * {@link LineStateShard} is the only place the width can be requested. Everything else (depth
     * write, view-offset layering, the item-entity target) is vanilla's own line type.
     */
    public static final RenderType GIZMO_LINES = create(
            "fishtastic_gizmo_lines",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RENDERTYPE_LINES_SHADER)
                    .setLineState(new LineStateShard(OptionalDouble.of(2.0)))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING)
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .setCullState(NO_CULL)
                    .createCompositeState(false));

    // ── Quality glints (the 1.21.1 ancestor's ItemEffect.RenderTypeFactory) ──────────────
    // One family per ItemEffect texture, standing in for vanilla's glint(), entityGlint(),
    // entityGlintDirect() and glintTranslucent() when ItemRendererMixin swaps the foil buffer.
    // Registered with RenderBuffers as fixed buffers (RenderBuffersMixin) so they draw after the
    // item, like vanilla's glints.

    private static final Function<ResourceLocation, RenderType> QUALITY_GLOW = Util.memoize(
            texture -> glint("quality_glow_", texture, GLINT_TEXTURING, MAIN_TARGET));
    private static final Function<ResourceLocation, RenderType> ENTITY_QUALITY_GLOW = Util.memoize(
            texture -> glint("entity_quality_glow_", texture, ENTITY_GLINT_TEXTURING, ITEM_ENTITY_TARGET));
    private static final Function<ResourceLocation, RenderType> ENTITY_QUALITY_GLOW_DIRECT = Util.memoize(
            texture -> glint("entity_quality_glow_direct_", texture, ENTITY_GLINT_TEXTURING, MAIN_TARGET));
    private static final Function<ResourceLocation, RenderType> QUALITY_GLOW_TRANSLUCENT = Util.memoize(
            texture -> glint("quality_glow_translucent_", texture, GLINT_TEXTURING, ITEM_ENTITY_TARGET));

    public static RenderType qualityGlow(ResourceLocation texture) {
        return QUALITY_GLOW.apply(texture);
    }

    public static RenderType entityQualityGlow(ResourceLocation texture) {
        return ENTITY_QUALITY_GLOW.apply(texture);
    }

    public static RenderType entityQualityGlowDirect(ResourceLocation texture) {
        return ENTITY_QUALITY_GLOW_DIRECT.apply(texture);
    }

    public static RenderType qualityGlowTranslucent(ResourceLocation texture) {
        return QUALITY_GLOW_TRANSLUCENT.apply(texture);
    }

    /** Every glint render type for {@code texture}, for the fixed-buffer registration. */
    public static List<RenderType> qualityGlows(ResourceLocation texture) {
        return List.of(qualityGlow(texture), entityQualityGlow(texture), entityQualityGlowDirect(texture),
                qualityGlowTranslucent(texture));
    }

    private static RenderType glint(String prefix, ResourceLocation texture, TexturingStateShard texturing,
                                    OutputStateShard output) {
        return create(
                prefix + texture.getNamespace() + "_" + texture.getPath().replace('/', '_'),
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                TRANSIENT_BUFFER_SIZE,
                false,
                false,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_GLINT_SHADER)
                        .setTextureState(new TextureStateShard(texture, true, false))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(EQUAL_DEPTH_TEST)
                        .setTransparencyState(GLINT_TRANSPARENCY)
                        .setTexturingState(texturing)
                        .setOutputState(output)
                        .createCompositeState(false));
    }

    private FishtasticRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}
