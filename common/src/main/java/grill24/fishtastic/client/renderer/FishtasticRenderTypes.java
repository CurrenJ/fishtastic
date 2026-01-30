package grill24.fishtastic.client.renderer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;

import static grill24.fishtastic.util.Utility.ft;

/**
 * Custom render types for Fishtastic mod, including the quality glow effect for fish items.
 */
public class FishtasticRenderTypes extends RenderType {
    // Dummy constructor - RenderType is abstract but we're just using it as a namespace
    private FishtasticRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
    private static final ResourceLocation QUALITY_GLOW_TEXTURE = ft("textures/misc/uncommon_quality_glow.png");
    private static final RenderType QUALITY_GLOW = RenderType.create(
            "quality_glow",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            1536,
            RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_GLINT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(QUALITY_GLOW_TEXTURE, true, false))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(EQUAL_DEPTH_TEST)
                        .setTransparencyState(GLINT_TRANSPARENCY)
                        .setTexturingState(GLINT_TEXTURING)
                        .createCompositeState(false)
        );
    private static final RenderType ENTITY_QUALITY_GLOW = create(
            "entity_quality_glow",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_GLINT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(QUALITY_GLOW_TEXTURE, true, false))
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(EQUAL_DEPTH_TEST)
                    .setTransparencyState(GLINT_TRANSPARENCY)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .setTexturingState(ENTITY_GLINT_TEXTURING)
                    .createCompositeState(false)
    );
    private static final RenderType ENTITY_QUALITY_GLOW_DIRECT = RenderType.create(
            "entity_quality_glow_direct",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_GLINT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(QUALITY_GLOW_TEXTURE, true, false))
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(EQUAL_DEPTH_TEST)
                    .setTransparencyState(GLINT_TRANSPARENCY)
                    .setTexturingState(ENTITY_GLINT_TEXTURING)
                    .createCompositeState(false)
    );
    private static final RenderType QUALITY_GLOW_TRANSLUCENT = RenderType.create(
            "quality_glow_translucent",
            DefaultVertexFormat.POSITION_TEX,
            VertexFormat.Mode.QUADS,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_GLINT_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(QUALITY_GLOW_TEXTURE, true, false))
                    .setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL)
                    .setDepthTestState(RenderStateShard.EQUAL_DEPTH_TEST)
                    .setTransparencyState(GLINT_TRANSPARENCY)
                    .setTexturingState(GLINT_TEXTURING)
                    .setOutputState(ITEM_ENTITY_TARGET)
                    .createCompositeState(false)
    );

    /**
     * Quality glow effect - similar to glint but using our custom shader
     */
    public static RenderType qualityGlow() {
        return QUALITY_GLOW;
    }

    /**
     * Quality glow for entity rendering (in world)
     */
    public static RenderType entityQualityGlowDirect() {
        return ENTITY_QUALITY_GLOW_DIRECT;
    }

    /**
     * Quality glow for translucent items
     */
    public static RenderType qualityGlowTranslucent() {
        return QUALITY_GLOW_TRANSLUCENT;
    }

    /**
     * Quality glow for entities
     */
    public static RenderType entityQualityGlow() {
        return ENTITY_QUALITY_GLOW;
    }
}
