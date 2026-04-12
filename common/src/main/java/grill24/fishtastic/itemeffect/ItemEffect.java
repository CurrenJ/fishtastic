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
                    Codec.BOOL.optionalFieldOf("enabled", true).forGetter(e -> e.enabled)
            ).apply(instance, ItemEffect::new)
    );

    private final Identifier texture;
    private final List<ItemEffectCondition> conditions;
    private final int priority;
    private final boolean enabled;

    // Lazily created render types
    private RenderType qualityGlow;
    private RenderType entityQualityGlow;
    private RenderType entityQualityGlowDirect;
    private RenderType qualityGlowTranslucent;

    public ItemEffect(Identifier texture, List<ItemEffectCondition> conditions, int priority, boolean enabled) {
        this.texture = texture;
        this.conditions = conditions;
        this.priority = priority;
        this.enabled = enabled;
        Fishtastic.LOGGER.debug("ItemEffect created: texture={}, conditions={}, priority={}, enabled={}", texture, conditions, priority, enabled);
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
