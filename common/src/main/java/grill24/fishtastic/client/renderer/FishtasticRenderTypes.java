package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;

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

    private FishtasticRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
            boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}
