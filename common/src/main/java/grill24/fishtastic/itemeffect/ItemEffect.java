package grill24.fishtastic.itemeffect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.Fishtastic;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class ItemEffect {
    public static final Codec<ItemEffect> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("texture").forGetter(e -> e.texture),
                    ItemEffectCondition.DISPATCH_CODEC.listOf().fieldOf("conditions").forGetter(e -> e.conditions),
                    Codec.INT.optionalFieldOf("priority", 0).forGetter(e -> e.priority),
                    Codec.BOOL.optionalFieldOf("enabled", true).forGetter(e -> e.enabled),
                    Codec.INT.optionalFieldOf("outline_color", 0).forGetter(e -> e.outlineColor),
                    Codec.FLOAT.optionalFieldOf("outline_falloff", 0.0f).forGetter(e -> e.outlineFalloff),
                    Codec.INT.optionalFieldOf("outline_width", 1).forGetter(e -> e.outlineWidth)
            ).apply(instance, ItemEffect::new)
    );

    private final Identifier texture;
    private final List<ItemEffectCondition> conditions;
    private final int priority;
    private final boolean enabled;
    /** ARGB packed outline colour for GUI items. 0 means no outline. */
    private final int outlineColor;
    /** 0 = solid outline, 1 = gradient that fades to transparent at the outer edge. */
    private final float outlineFalloff;
    /** Outline thickness in item pixels (1 item pixel = guiScale atlas texels). Clamped to [1, 4]. */
    private final int outlineWidth;

    // Lazily created render types
    private RenderType qualityGlow;
    private RenderType entityQualityGlow;
    private RenderType entityQualityGlowDirect;
    private RenderType qualityGlowTranslucent;

    public ItemEffect(Identifier texture, List<ItemEffectCondition> conditions, int priority, boolean enabled, int outlineColor, float outlineFalloff, int outlineWidth) {
        this.texture = texture;
        this.conditions = conditions;
        this.priority = priority;
        this.enabled = enabled;
        this.outlineColor = outlineColor;
        this.outlineFalloff = Math.clamp(outlineFalloff, 0.0f, 1.0f);
        this.outlineWidth = Math.clamp(outlineWidth, 1, 4);
        Fishtastic.LOGGER.debug("ItemEffect created: texture={}, conditions={}, priority={}, enabled={}, outlineColor={}, outlineFalloff={}, outlineWidth={}", texture, conditions, priority, enabled, outlineColor, outlineFalloff, outlineWidth);
    }

    public Identifier texture() {
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

    public boolean hasOutline() {
        return outlineColor != 0;
    }

    public int outlineColor() {
        return outlineColor;
    }

    /**
     * Packs width and falloff into the alpha byte so the shader can decode both
     * from the single {@code vertexColor} parameter.
     *
     * <p>Alpha byte layout (8 bits):
     * <ul>
     *   <li>Bits 7–6 (2 bits): {@code outlineWidth - 1}  →  widths 1–4 item pixels</li>
     *   <li>Bits 5–0 (6 bits): solidness  →  0 = full falloff, 63 = solid</li>
     * </ul>
     */
    public int outlinePackedColor() {
        int w = (Math.clamp(outlineWidth, 1, 4) - 1) & 0x3;      // 0-3
        int s = Math.clamp((int) ((1.0f - outlineFalloff) * 63f), 0, 63); // 0-63
        return ((w << 6 | s) << 24) | (outlineColor & 0x00FFFFFF);
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

    private static final class RenderTypeFactory {
        private RenderTypeFactory() {}

        private static String makeName(String prefix, Identifier texture) {
            return prefix + texture.getNamespace() + "_" + texture.getPath().replace('/', '_');
        }

        static RenderType createQualityGlow(Identifier texture) {
            return RenderType.create(
                    makeName("quality_glow_", texture),
                    RenderSetup.builder(RenderPipelines.GLINT)
                            .withTexture("Sampler0", texture)
                            .setTextureTransform(TextureTransform.GLINT_TEXTURING)
                            .createRenderSetup()
            );
        }

        static RenderType createEntityQualityGlow(Identifier texture) {
            return RenderType.create(
                    makeName("entity_quality_glow_", texture),
                    RenderSetup.builder(RenderPipelines.GLINT)
                            .withTexture("Sampler0", texture)
                            .setTextureTransform(TextureTransform.ENTITY_GLINT_TEXTURING)
                            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                            .createRenderSetup()
            );
        }

        static RenderType createEntityQualityGlowDirect(Identifier texture) {
            return RenderType.create(
                    makeName("entity_quality_glow_direct_", texture),
                    RenderSetup.builder(RenderPipelines.GLINT)
                            .withTexture("Sampler0", texture)
                            .setTextureTransform(TextureTransform.ENTITY_GLINT_TEXTURING)
                            .createRenderSetup()
            );
        }

        static RenderType createQualityGlowTranslucent(Identifier texture) {
            return RenderType.create(
                    makeName("quality_glow_translucent_", texture),
                    RenderSetup.builder(RenderPipelines.GLINT)
                            .withTexture("Sampler0", texture)
                            .setTextureTransform(TextureTransform.GLINT_TEXTURING)
                            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                            .createRenderSetup()
            );
        }
    }
}
