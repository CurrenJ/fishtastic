package grill24.fishtastic.itemeffect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.Fishtastic;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * A datapack-defined item effect (glint texture + GUI outline parameters) and the conditions that
 * select it. Data only: the GPU-side resources built from it (outline pipelines, the params UBO,
 * glint render types) belong to the client renderer, so this class compiles the same on every MC
 * version and on a dedicated server.
 */
public class ItemEffect {
    public static final Codec<ItemEffect> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("texture").forGetter(e -> e.texture),
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

    private final ResourceLocation texture;
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

    public ItemEffect(ResourceLocation texture, List<ItemEffectCondition> conditions, int priority, boolean enabled,
                      int outlineColor, float outlineFalloff, float outlineWidth, float outlineOpacity,
                      boolean outlinePinwheel, boolean outlineDebugUv,
                      float outlineAnimSpeed, int outlineNumBlades, float outlineBladeFill) {
        this.texture = texture;
        this.conditions = conditions;
        this.priority = priority;
        this.enabled = enabled;
        this.outlineColor = outlineColor;
        this.outlineFalloff = Mth.clamp(outlineFalloff, 0.0f, 1.0f);
        this.outlineWidth = outlineWidth;
        this.outlineOpacity = Mth.clamp(outlineOpacity, 0.0f, 1.0f);
        this.outlinePinwheel = outlinePinwheel;
        this.outlineDebugUv = outlineDebugUv;
        this.outlineAnimSpeed = outlineAnimSpeed;
        this.outlineNumBlades = outlineNumBlades;
        this.outlineBladeFill = Mth.clamp(outlineBladeFill, 0.0f, 1.0f);
        Fishtastic.LOGGER.debug("ItemEffect created: texture={}, conditions={}, priority={}, enabled={}, outlineColor={}, outlineFalloff={}, outlineWidth={}, outlineOpacity={}, outlinePinwheel={}, outlineDebugUv={}", texture, conditions, priority, enabled, outlineColor, outlineFalloff, outlineWidth, outlineOpacity, outlinePinwheel, outlineDebugUv);
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

    public float outlineFalloff() {
        return outlineFalloff;
    }

    public float outlineWidth() {
        return outlineWidth;
    }

    public float outlineOpacity() {
        return outlineOpacity;
    }

    public float outlineAnimSpeed() {
        return outlineAnimSpeed;
    }

    public int outlineNumBlades() {
        return outlineNumBlades;
    }

    public float outlineBladeFill() {
        return outlineBladeFill;
    }

    /** True when this effect's outline animates and its atlas slot must be re-composed each frame. */
    public boolean isOutlineAnimated() {
        return outlinePinwheel;
    }

    // PORT A5.4: the outline pipelines, the params UBO and the glint render types that 26.1.2 builds
    // here move to the client-side ItemEffectRenderData, keyed by this effect.
}
