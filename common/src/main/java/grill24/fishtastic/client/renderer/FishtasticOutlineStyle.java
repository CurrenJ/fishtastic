package grill24.fishtastic.client.renderer;

import grill24.fishtastic.itemeffect.ItemEffect;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;

/**
 * PORT-ONLY: one outline ring's parameters, as baked into {@link FishtasticItemOutlineAtlas}. 26.1.2
 * keeps these in a per-effect params UBO ({@code FishtasticOutlineUboRegistry}); 1.21.1 has no UBOs,
 * so they're plain uniforms set before each slot's bake draw ({@link #applyUniforms}).
 *
 * <p>Built from a quality {@link ItemEffect} ({@link #of}), or the fixed {@link #BLACK} edge the
 * fishing minigame's gear readout asks for ({@link FishtasticBlackOutlineEffect}); 26.1.2 draws that
 * one with the same basic outline shader and these parameters.
 *
 * @param color    RGB outline colour (alpha ignored)
 * @param falloff  0 = solid, 1 = gradient that fades to transparent at the outer edge
 * @param width    outline thickness in item pixels; fractional values allowed
 * @param opacity  overall outline opacity multiplier
 * @param pinwheel animated pinwheel ring (the legendary bake shader) instead of the basic one
 */
public record FishtasticOutlineStyle(int color, float falloff, float width, float opacity,
                                     boolean pinwheel, float animSpeed, int numBlades, float bladeFill) {

    /** Solid black, no gradient fade, full opacity, half an item pixel thick (26.1.2's gear outline). */
    public static final FishtasticOutlineStyle BLACK = new FishtasticOutlineStyle(0x000000, 0.0f, 0.5f, 1.0f,
            false, 0.0f, 0, 0.0f);

    /** The outline {@code effect} asks for, or null if it has none. */
    public static @Nullable FishtasticOutlineStyle of(@Nullable ItemEffect effect) {
        if (effect == null || !effect.hasOutline()) return null;
        return new FishtasticOutlineStyle(effect.outlineColor(), effect.outlineFalloff(), effect.outlineWidth(),
                effect.outlineOpacity(), effect.outlinePinwheel(), effect.outlineAnimSpeed(), effect.outlineNumBlades(),
                effect.outlineBladeFill());
    }

    /** True when the ring animates and its atlas slot must be re-composed every frame. */
    public boolean isAnimated() {
        return pinwheel;
    }

    /** The bake program for this ring; null until shaders have loaded. */
    public @Nullable ShaderInstance bakeShader() {
        return pinwheel ? FishtasticShaders.outlineBakeLegendary : FishtasticShaders.outlineBake;
    }

    /** 26.1.2's {@code OutlineParams} block, as the bake programs' plain uniforms. */
    public void applyUniforms(ShaderInstance shader) {
        shader.safeGetUniform("OutlineColor").set(
                ((color >> 16) & 0xFF) / 255.0f,
                ((color >> 8) & 0xFF) / 255.0f,
                (color & 0xFF) / 255.0f,
                1.0f);
        shader.safeGetUniform("OutlineFalloff").set(falloff);
        shader.safeGetUniform("OutlineOpacity").set(opacity);
        shader.safeGetUniform("OutlineWidth").set(width);
        if (pinwheel) {
            shader.safeGetUniform("AnimSpeed").set(animSpeed);
            shader.safeGetUniform("NumBlades").set(numBlades);
            shader.safeGetUniform("BladeFill").set(bladeFill);
        }
    }
}
