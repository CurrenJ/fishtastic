package grill24.fishtastic.itemeffect;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.Fishtastic;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ItemEffect {
    public static final Codec<ItemEffect> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("texture").forGetter(e -> e.texture),
                    ItemEffectCondition.DISPATCH_CODEC.listOf().fieldOf("conditions").forGetter(e -> e.conditions),
                    Codec.INT.optionalFieldOf("priority", 0).forGetter(e -> e.priority),
                    Codec.BOOL.optionalFieldOf("enabled", true).forGetter(e -> e.enabled)
            ).apply(instance, ItemEffect::new)
    );

    private final ResourceLocation texture;
    private final List<ItemEffectCondition> conditions;
    private final int priority;
    private final boolean enabled;

    // Lazily created render types
    private RenderType qualityGlow;
    private RenderType entityQualityGlow;
    private RenderType entityQualityGlowDirect;
    private RenderType qualityGlowTranslucent;

    public ItemEffect(ResourceLocation texture, List<ItemEffectCondition> conditions, int priority, boolean enabled) {
        this.texture = texture;
        this.conditions = conditions;
        this.priority = priority;
        this.enabled = enabled;
        Fishtastic.LOGGER.debug("ItemEffect created: texture={}, conditions={}, priority={}, enabled={}", texture, conditions, priority, enabled);
    }

    public ResourceLocation texture() {
        return texture;
    }

    public int priority() {
        return priority;
    }

    public boolean enabled() {
        return enabled;
    }

    public List<ItemEffectCondition> conditions() {
        return conditions;
    }

    public boolean matches(ItemStack stack) {
        if (!enabled) return false;
        return conditions.stream().allMatch(c -> c.matches(stack));
    }

    public RenderType qualityGlow() {
        if (qualityGlow == null) {
            qualityGlow = RenderTypeFactory.createQualityGlow(texture);
        }
        return qualityGlow;
    }

    public RenderType entityQualityGlow() {
        if (entityQualityGlow == null) {
            entityQualityGlow = RenderTypeFactory.createEntityQualityGlow(texture);
        }
        return entityQualityGlow;
    }

    public RenderType entityQualityGlowDirect() {
        if (entityQualityGlowDirect == null) {
            entityQualityGlowDirect = RenderTypeFactory.createEntityQualityGlowDirect(texture);
        }
        return entityQualityGlowDirect;
    }

    public RenderType qualityGlowTranslucent() {
        if (qualityGlowTranslucent == null) {
            qualityGlowTranslucent = RenderTypeFactory.createQualityGlowTranslucent(texture);
        }
        return qualityGlowTranslucent;
    }

    public List<RenderType> getAllRenderTypes() {
        return List.of(qualityGlow(), entityQualityGlow(), entityQualityGlowDirect(), qualityGlowTranslucent());
    }

    /**
     * Extends RenderType to access protected RenderStateShard fields for creating custom render types.
     */
    private static class RenderTypeFactory extends RenderType {
        // Dummy constructor - never instantiated
        private RenderTypeFactory() {
            super("dummy", DefaultVertexFormat.POSITION_TEX, VertexFormat.Mode.QUADS, 1536, false, false, () -> {}, () -> {});
        }

        private static String makeName(String prefix, ResourceLocation texture) {
            return prefix + texture.getNamespace() + "_" + texture.getPath().replace('/', '_');
        }

        static RenderType createQualityGlow(ResourceLocation texture) {
            return create(
                    makeName("quality_glow_", texture),
                    DefaultVertexFormat.POSITION_TEX,
                    VertexFormat.Mode.QUADS,
                    1536,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_GLINT_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .setDepthTestState(EQUAL_DEPTH_TEST)
                            .setTransparencyState(GLINT_TRANSPARENCY)
                            .setTexturingState(GLINT_TEXTURING)
                            .createCompositeState(false)
            );
        }

        static RenderType createEntityQualityGlow(ResourceLocation texture) {
            return create(
                    makeName("entity_quality_glow_", texture),
                    DefaultVertexFormat.POSITION_TEX,
                    VertexFormat.Mode.QUADS,
                    1536,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_GLINT_SHADER)
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

        static RenderType createEntityQualityGlowDirect(ResourceLocation texture) {
            return create(
                    makeName("entity_quality_glow_direct_", texture),
                    DefaultVertexFormat.POSITION_TEX,
                    VertexFormat.Mode.QUADS,
                    1536,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_GLINT_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .setDepthTestState(EQUAL_DEPTH_TEST)
                            .setTransparencyState(GLINT_TRANSPARENCY)
                            .setTexturingState(ENTITY_GLINT_TEXTURING)
                            .createCompositeState(false)
            );
        }

        static RenderType createQualityGlowTranslucent(ResourceLocation texture) {
            return create(
                    makeName("quality_glow_translucent_", texture),
                    DefaultVertexFormat.POSITION_TEX,
                    VertexFormat.Mode.QUADS,
                    1536,
                    CompositeState.builder()
                            .setShaderState(RENDERTYPE_GLINT_SHADER)
                            .setTextureState(new RenderStateShard.TextureStateShard(texture, true, false))
                            .setWriteMaskState(COLOR_WRITE)
                            .setCullState(NO_CULL)
                            .setDepthTestState(EQUAL_DEPTH_TEST)
                            .setTransparencyState(GLINT_TRANSPARENCY)
                            .setTexturingState(GLINT_TEXTURING)
                            .setOutputState(ITEM_ENTITY_TARGET)
                            .createCompositeState(false)
            );
        }
    }
}
