package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import grill24.fishtastic.component.FishQuality;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static grill24.fishtastic.util.Utility.ft;

/**
 * Custom render types for Fishtastic mod, including the quality glow effect for fish items.
 * Supports different textures for each quality level.
 */
public class FishtasticRenderTypes extends RenderType {
    // Dummy constructor - RenderType is abstract but we're just using it as a namespace
    private FishtasticRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    /**
     * Configuration for quality glow textures. Change these paths to use different textures.
     * Textures should be located in assets/fishtastic/textures/misc/
     */
    private static final Map<FishQuality.Quality, ResourceLocation> QUALITY_TEXTURES = new EnumMap<>(FishQuality.Quality.class);

    static {
        // Configure textures for each quality level
        // Modify these paths to change the glow texture for each quality
        QUALITY_TEXTURES.put(FishQuality.Quality.COMMON, ft("textures/misc/common_quality_glow.png"));
        QUALITY_TEXTURES.put(FishQuality.Quality.UNCOMMON, ft("textures/misc/uncommon_quality_glow.png"));
        QUALITY_TEXTURES.put(FishQuality.Quality.RARE, ft("textures/misc/rare_quality_glow.png"));
        QUALITY_TEXTURES.put(FishQuality.Quality.EPIC, ft("textures/misc/epic_quality_glow.png"));
        QUALITY_TEXTURES.put(FishQuality.Quality.LEGENDARY, ft("textures/misc/legendary_quality_glow.png"));
    }

    /**
     * Gets the texture location for a specific quality level.
     * @param quality The quality level
     * @return The ResourceLocation for the quality's glow texture
     */
    public static ResourceLocation getQualityTexture(FishQuality.Quality quality) {
        return QUALITY_TEXTURES.getOrDefault(quality, QUALITY_TEXTURES.get(FishQuality.Quality.UNCOMMON));
    }

    // Pre-built render types for each quality level and each variant
    private static final Map<FishQuality.Quality, RenderType> QUALITY_GLOW_MAP = new EnumMap<>(FishQuality.Quality.class);
    private static final Map<FishQuality.Quality, RenderType> ENTITY_QUALITY_GLOW_MAP = new EnumMap<>(FishQuality.Quality.class);
    private static final Map<FishQuality.Quality, RenderType> ENTITY_QUALITY_GLOW_DIRECT_MAP = new EnumMap<>(FishQuality.Quality.class);
    private static final Map<FishQuality.Quality, RenderType> QUALITY_GLOW_TRANSLUCENT_MAP = new EnumMap<>(FishQuality.Quality.class);

    static {
        // Initialize render types for each quality level
        for (FishQuality.Quality quality : FishQuality.Quality.values()) {
            ResourceLocation texture = getQualityTexture(quality);
            String qualityName = quality.getSerializedName();

            QUALITY_GLOW_MAP.put(quality, createQualityGlow(qualityName, texture));
            ENTITY_QUALITY_GLOW_MAP.put(quality, createEntityQualityGlow(qualityName, texture));
            ENTITY_QUALITY_GLOW_DIRECT_MAP.put(quality, createEntityQualityGlowDirect(qualityName, texture));
            QUALITY_GLOW_TRANSLUCENT_MAP.put(quality, createQualityGlowTranslucent(qualityName, texture));
        }
    }

    // Factory methods to create render types with specific textures
    private static RenderType createQualityGlow(String qualityName, ResourceLocation texture) {
        return RenderType.create(
                "quality_glow_" + qualityName,
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                1536,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_GLINT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(EQUAL_DEPTH_TEST)
                        .setTransparencyState(GLINT_TRANSPARENCY)
                        .setTexturingState(GLINT_TEXTURING)
                        .createCompositeState(false)
        );
    }

    private static RenderType createEntityQualityGlow(String qualityName, ResourceLocation texture) {
        return RenderType.create(
                "entity_quality_glow_" + qualityName,
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                1536,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_GLINT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(EQUAL_DEPTH_TEST)
                        .setTransparencyState(GLINT_TRANSPARENCY)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .setTexturingState(ENTITY_GLINT_TEXTURING)
                        .createCompositeState(false)
        );
    }

    private static RenderType createEntityQualityGlowDirect(String qualityName, ResourceLocation texture) {
        return RenderType.create(
                "entity_quality_glow_direct_" + qualityName,
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                1536,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_GLINT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(EQUAL_DEPTH_TEST)
                        .setTransparencyState(GLINT_TRANSPARENCY)
                        .setTexturingState(ENTITY_GLINT_TEXTURING)
                        .createCompositeState(false)
        );
    }

    private static RenderType createQualityGlowTranslucent(String qualityName, ResourceLocation texture) {
        return RenderType.create(
                "quality_glow_translucent_" + qualityName,
                DefaultVertexFormat.POSITION_TEX,
                VertexFormat.Mode.QUADS,
                1536,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_GLINT_SHADER)
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                        .setWriteMaskState(COLOR_WRITE)
                        .setCullState(NO_CULL)
                        .setDepthTestState(RenderStateShard.EQUAL_DEPTH_TEST)
                        .setTransparencyState(GLINT_TRANSPARENCY)
                        .setTexturingState(GLINT_TEXTURING)
                        .setOutputState(ITEM_ENTITY_TARGET)
                        .createCompositeState(false)
        );
    }

    // Public API methods - quality-specific versions

    /**
     * Quality glow effect for a specific quality level
     */
    public static RenderType qualityGlow(FishQuality.Quality quality) {
        return QUALITY_GLOW_MAP.get(quality);
    }

    /**
     * Quality glow for entity rendering (in world) for a specific quality level
     */
    public static RenderType entityQualityGlowDirect(FishQuality.Quality quality) {
        return ENTITY_QUALITY_GLOW_DIRECT_MAP.get(quality);
    }

    /**
     * Quality glow for translucent items for a specific quality level
     */
    public static RenderType qualityGlowTranslucent(FishQuality.Quality quality) {
        return QUALITY_GLOW_TRANSLUCENT_MAP.get(quality);
    }

    /**
     * Quality glow for entities for a specific quality level
     */
    public static RenderType entityQualityGlow(FishQuality.Quality quality) {
        return ENTITY_QUALITY_GLOW_MAP.get(quality);
    }

    // Convenience methods to get all render types (for buffer registration)

    /**
     * Returns all quality glow render types across all qualities
     */
    public static List<RenderType> getAllQualityGlowTypes() {
        return Arrays.stream(FishQuality.Quality.values())
                .map(QUALITY_GLOW_MAP::get)
                .toList();
    }

    /**
     * Returns all entity quality glow render types across all qualities
     */
    public static List<RenderType> getAllEntityQualityGlowTypes() {
        return Arrays.stream(FishQuality.Quality.values())
                .map(ENTITY_QUALITY_GLOW_MAP::get)
                .toList();
    }

    /**
     * Returns all entity quality glow direct render types across all qualities
     */
    public static List<RenderType> getAllEntityQualityGlowDirectTypes() {
        return Arrays.stream(FishQuality.Quality.values())
                .map(ENTITY_QUALITY_GLOW_DIRECT_MAP::get)
                .toList();
    }

    /**
     * Returns all quality glow translucent render types across all qualities
     */
    public static List<RenderType> getAllQualityGlowTranslucentTypes() {
        return Arrays.stream(FishQuality.Quality.values())
                .map(QUALITY_GLOW_TRANSLUCENT_MAP::get)
                .toList();
    }

    /**
     * Returns all custom render types that need to be registered in the buffer source
     */
    public static List<RenderType> getAllCustomRenderTypes() {
        return Arrays.stream(FishQuality.Quality.values())
                .flatMap(quality -> Stream.of(
                        QUALITY_GLOW_MAP.get(quality),
                        ENTITY_QUALITY_GLOW_MAP.get(quality),
                        ENTITY_QUALITY_GLOW_DIRECT_MAP.get(quality),
                        QUALITY_GLOW_TRANSLUCENT_MAP.get(quality)
                ))
                .toList();
    }
}
