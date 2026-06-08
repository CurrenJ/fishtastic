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
                    Codec.INT.optionalFieldOf("outline_width", 1).forGetter(e -> e.outlineWidth),
                    Codec.FLOAT.optionalFieldOf("outline_opacity", 1.0f).forGetter(e -> e.outlineOpacity),
                    Codec.BOOL.optionalFieldOf("outline_pinwheel", false).forGetter(e -> e.outlinePinwheel),
                    Codec.BOOL.optionalFieldOf("outline_debug_uv", false).forGetter(e -> e.outlineDebugUv)
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
    /** Overall outline opacity multiplier, 0.0–1.0. Applied on top of any falloff gradient. */
    private final float outlineOpacity;
    /** When true, uses the animated pinwheel shader instead of the basic outline shader. */
    private final boolean outlinePinwheel;
    /** When true, uses the UV debug shader (slot-relative UV as RG). Overrides outlinePinwheel. */
    private final boolean outlineDebugUv;

    // Lazily created render types
    private RenderType qualityGlow;
    private RenderType entityQualityGlow;
    private RenderType entityQualityGlowDirect;
    private RenderType qualityGlowTranslucent;

    public ItemEffect(Identifier texture, List<ItemEffectCondition> conditions, int priority, boolean enabled, int outlineColor, float outlineFalloff, int outlineWidth, float outlineOpacity, boolean outlinePinwheel, boolean outlineDebugUv) {
        this.texture = texture;
        this.conditions = conditions;
        this.priority = priority;
        this.enabled = enabled;
        this.outlineColor = outlineColor;
        this.outlineFalloff = Math.clamp(outlineFalloff, 0.0f, 1.0f);
        this.outlineWidth = Math.clamp(outlineWidth, 1, 4);
        this.outlineOpacity = Math.clamp(outlineOpacity, 0.0f, 1.0f);
        this.outlinePinwheel = outlinePinwheel;
        this.outlineDebugUv = outlineDebugUv;
        Fishtastic.LOGGER.debug("ItemEffect created: texture={}, conditions={}, priority={}, enabled={}, outlineColor={}, outlineFalloff={}, outlineWidth={}, outlineOpacity={}, outlinePinwheel={}, outlineDebugUv={}", texture, conditions, priority, enabled, outlineColor, outlineFalloff, outlineWidth, outlineOpacity, outlinePinwheel, outlineDebugUv);
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

    public boolean outlinePinwheel() {
        return outlinePinwheel;
    }

    public boolean outlineDebugUv() {
        return outlineDebugUv;
    }

    /**
     * Packs width, falloff, and opacity into the alpha byte for the shader.
     *
     * <p>Alpha byte layout (8 bits):
     * <ul>
     *   <li>Bits 7–6 (2 bits): {@code outlineWidth - 1}  →  1–4 item pixels</li>
     *   <li>Bits 5–3 (3 bits): solidness (1 − falloff)  →  0 = full falloff, 7 = solid</li>
     *   <li>Bits 2–0 (3 bits): opacity  →  0 = transparent, 7 = fully opaque</li>
     * </ul>
     * Defaults (width=1, falloff=0, opacity=1) pack to 0x3F = 63, same as the
     * previous two-field encoding so existing items are unaffected.
     */
    public int outlinePackedColor() {
        int w = (Math.clamp(outlineWidth, 1, 4) - 1) & 0x3;           // bits 7-6
        int s = Math.clamp((int) ((1.0f - outlineFalloff) * 7f), 0, 7); // bits 5-3
        int o = Math.clamp((int) (outlineOpacity * 7f), 0, 7);          // bits 2-0
        return ((w << 6 | s << 3 | o) << 24) | (outlineColor & 0x00FFFFFF);
    }

    /**
     * Packed colour for the legendary/debug-UV shaders. guiScale is no longer packed here —
     * both shaders derive it from dFdx(modelViewPos.x) to avoid the 2-bit cap.
     *   bits 5-3 = solidness (0-7 → 0.0–1.0)
     *   bits 2-0 = opacity   (0-7 → 0.0–1.0)
     */
    public int outlineLegendaryPackedColor() {
        int s = Math.clamp((int) ((1.0f - outlineFalloff) * 7f), 0, 7); // bits 5-3
        int o = Math.clamp((int) (outlineOpacity * 7f), 0, 7);          // bits 2-0
        return ((s << 3 | o) << 24) | (outlineColor & 0x00FFFFFF);
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
