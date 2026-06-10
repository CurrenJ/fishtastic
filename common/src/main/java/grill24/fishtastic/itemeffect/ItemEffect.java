package grill24.fishtastic.itemeffect;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.renderer.FishtasticItemOutlineAtlas;
import grill24.fishtastic.client.renderer.FishtasticOutlineUboRegistry;
import grill24.fishtastic.client.renderer.FishtasticRenderPipelines;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.TextureTransform;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
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
                    Codec.FLOAT.optionalFieldOf("outline_width", 1.0f).forGetter(e -> e.outlineWidth),
                    Codec.FLOAT.optionalFieldOf("outline_opacity", 1.0f).forGetter(e -> e.outlineOpacity),
                    Codec.BOOL.optionalFieldOf("outline_pinwheel", false).forGetter(e -> e.outlinePinwheel),
                    Codec.BOOL.optionalFieldOf("outline_debug_uv", false).forGetter(e -> e.outlineDebugUv),
                    Codec.FLOAT.optionalFieldOf("outline_anim_speed", 150.0f).forGetter(e -> e.outlineAnimSpeed),
                    Codec.INT.optionalFieldOf("outline_num_blades", 5).forGetter(e -> e.outlineNumBlades),
                    Codec.FLOAT.optionalFieldOf("outline_blade_fill", 0.65f).forGetter(e -> e.outlineBladeFill)
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
    /** Outline thickness in item pixels; fractional values allowed. Minimum rendered size is 1 screen pixel. */
    private final float outlineWidth;
    /** Overall outline opacity multiplier, 0.0–1.0. */
    private final float outlineOpacity;
    /** When true, uses the animated pinwheel shader instead of the basic outline shader. */
    private final boolean outlinePinwheel;
    /** When true, uses the UV debug shader (slot-relative UV as RG). Overrides outlinePinwheel. */
    private final boolean outlineDebugUv;
    /** Pinwheel rotation speed in full rotations per in-game day (default 150). */
    private final float outlineAnimSpeed;
    /** Number of pinwheel blades (default 3). */
    private final int outlineNumBlades;
    /** Fraction of each blade's sector that is filled (default 0.65). */
    private final float outlineBladeFill;

    // Lazily created on the render thread — null until first use.
    private RenderPipeline outlinePipeline;
    private RenderPipeline worldOutlinePipeline;
    private RenderType worldOutlineRenderType;
    private GpuBuffer outlineParamsBuffer;

    // Lazily created render types for entity/world rendering.
    private RenderType qualityGlow;
    private RenderType entityQualityGlow;
    private RenderType entityQualityGlowDirect;
    private RenderType qualityGlowTranslucent;

    public ItemEffect(Identifier texture, List<ItemEffectCondition> conditions, int priority, boolean enabled,
                      int outlineColor, float outlineFalloff, float outlineWidth, float outlineOpacity,
                      boolean outlinePinwheel, boolean outlineDebugUv,
                      float outlineAnimSpeed, int outlineNumBlades, float outlineBladeFill) {
        this.texture = texture;
        this.conditions = conditions;
        this.priority = priority;
        this.enabled = enabled;
        this.outlineColor = outlineColor;
        this.outlineFalloff = Math.clamp(outlineFalloff, 0.0f, 1.0f);
        this.outlineWidth = outlineWidth;
        this.outlineOpacity = Math.clamp(outlineOpacity, 0.0f, 1.0f);
        this.outlinePinwheel = outlinePinwheel;
        this.outlineDebugUv = outlineDebugUv;
        this.outlineAnimSpeed = outlineAnimSpeed;
        this.outlineNumBlades = outlineNumBlades;
        this.outlineBladeFill = Math.clamp(outlineBladeFill, 0.0f, 1.0f);
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
     * Returns the outline {@link RenderPipeline} for this effect, creating it (and its
     * {@link GpuBuffer} params) on the first call.  Must be called on the render thread.
     */
    public RenderPipeline getOrCreateOutlinePipeline() {
        if (outlinePipeline == null) {
            String texPath = texture.getNamespace() + "_" + texture.getPath().replace('/', '_');
            Identifier pipelineId = Identifier.fromNamespaceAndPath("fishtastic", "pipeline/gui_item_outline_" + texPath);

            String uboName;
            if (outlineDebugUv) {
                outlinePipeline = FishtasticRenderPipelines.createDebugUvPipeline(pipelineId);
                uboName = FishtasticRenderPipelines.DEBUG_UV_UBO_NAME;
            } else if (outlinePinwheel) {
                outlinePipeline = FishtasticRenderPipelines.createLegendaryOutlinePipeline(pipelineId);
                uboName = FishtasticRenderPipelines.LEGENDARY_OUTLINE_UBO_NAME;
            } else {
                outlinePipeline = FishtasticRenderPipelines.createOutlinePipeline(pipelineId);
                uboName = FishtasticRenderPipelines.BASIC_OUTLINE_UBO_NAME;
            }

            FishtasticOutlineUboRegistry.register(outlinePipeline, uboName, getOrCreateOutlineParamsBuffer());
        }
        return outlinePipeline;
    }

    /**
     * Returns the outline {@link RenderPipeline} for in-world item entity / item frame
     * rendering, creating it on first call.  Must be called on the render thread.
     *
     * <p>Shares the params {@link GpuBuffer} with the GUI pipeline — both UBO layouts are
     * identical; only the pipeline (vertex format, depth state, slot-geometry defines) differs.
     * The debug-UV variant has no world equivalent and falls back to the basic shader.
     */
    public RenderPipeline getOrCreateWorldOutlinePipeline() {
        if (worldOutlinePipeline == null) {
            String texPath = texture.getNamespace() + "_" + texture.getPath().replace('/', '_');
            Identifier pipelineId = Identifier.fromNamespaceAndPath("fishtastic", "pipeline/world_item_outline_" + texPath);

            String uboName;
            if (outlinePinwheel) {
                worldOutlinePipeline = FishtasticRenderPipelines.createWorldLegendaryOutlinePipeline(pipelineId);
                uboName = FishtasticRenderPipelines.LEGENDARY_OUTLINE_UBO_NAME;
            } else {
                worldOutlinePipeline = FishtasticRenderPipelines.createWorldOutlinePipeline(pipelineId);
                uboName = FishtasticRenderPipelines.BASIC_OUTLINE_UBO_NAME;
            }

            FishtasticOutlineUboRegistry.register(worldOutlinePipeline, uboName, getOrCreateOutlineParamsBuffer());
        }
        return worldOutlinePipeline;
    }

    /**
     * Returns the {@link RenderType} used to draw the in-world outline quad, creating it on
     * first call.  Must be called on the render thread (creates the pipeline + params buffer).
     * Binds the {@link FishtasticItemOutlineAtlas} texture and targets the item-entity output
     * like vanilla item entity render types.
     */
    public RenderType worldOutlineRenderType() {
        if (worldOutlineRenderType == null) {
            worldOutlineRenderType = RenderType.create(
                    RenderTypeFactory.makeName("world_item_outline_", texture),
                    RenderSetup.builder(getOrCreateWorldOutlinePipeline())
                            .withTexture("Sampler0", FishtasticItemOutlineAtlas.TEXTURE_ID)
                            .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                            .createRenderSetup()
            );
        }
        return worldOutlineRenderType;
    }

    private GpuBuffer getOrCreateOutlineParamsBuffer() {
        if (outlineParamsBuffer == null) {
            outlineParamsBuffer = buildOutlineParamsBuffer();
        }
        return outlineParamsBuffer;
    }

    private GpuBuffer buildOutlineParamsBuffer() {
        int size = FishtasticRenderPipelines.OUTLINE_PARAMS_UBO_SIZE;
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(
                () -> "Item Outline Params: " + texture,
                GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                size
        );
        float r = ((outlineColor >> 16) & 0xFF) / 255.0f;
        float g = ((outlineColor >> 8) & 0xFF) / 255.0f;
        float b = (outlineColor & 0xFF) / 255.0f;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, size)
                    .putVec4(r, g, b, 1.0f)
                    .putFloat(outlineFalloff)
                    .putFloat(outlineOpacity)
                    .putFloat(outlineWidth)
                    .putFloat(outlineAnimSpeed)
                    .putInt(outlineNumBlades)
                    .putFloat(outlineBladeFill)
                    .putFloat(0.0f) // _reserved0
                    .putFloat(0.0f) // _reserved1
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
        }
        return buffer;
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
