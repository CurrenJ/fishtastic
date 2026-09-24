package grill24.fishtastic.client.renderer;

import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.Nullable;

/**
 * PORT-ONLY: the 1.21.1 counterpart of 26.1's Iris registration, kept as a <b>no-op with the same
 * shape</b> so the call sites in {@link FishtasticRenderTypes} read the same as 26.1's
 * {@code FishtasticRenderPipelines}.
 *
 * <p><b>Why it's a no-op here.</b> 26.1 has to register each {@code RenderPipeline} with Iris
 * ({@code IrisApi#assignPipeline}). Iris swaps every unregistered pipeline for the pack's
 * equivalent gbuffers program, and our single-output shaders then write into a gbuffer the pack's
 * deferred passes never resolve as scene colour — the draw is silently discarded and the geometry
 * is completely invisible under every shaderpack. Pipeline objects are new to 26.1 and are exactly
 * what Iris 1.8's override list is keyed on, so 1.21.1 has no equivalent API: a render type is
 * recognised by Iris through the vanilla shader it names ({@code rendertype_entity_translucent}
 * here), which the pack already overrides, and the pack's program draws our quads like any other
 * entity. The rendering spike confirmed it: {@code TANK_WATER_FILL} and {@code ITEM_OUTLINE} draw
 * correctly under Complementary Reimagined with no registration.
 *
 * <p><b>[1.20.1]</b> the same no-op.
 */
public final class IrisCompat {

    /**
     * No-op on 1.21.1 — see the class comment. Kept with 26.1's parameter list (a render type in
     * place of a pipeline) so the two branches' call sites differ only in the type they pass.
     *
     * @param renderType     the render type this would be registered for
     * @param program        the shaderpack program name 26.1 assigns for the main pass
     * @param shadowProgram  the shadow-pass program, or {@code null} for none
     */
    @SuppressWarnings("unused")
    public static void assignPipeline(RenderType renderType, String program, @Nullable String shadowProgram) {
        // Intentionally empty: nothing to register with Iris 1.8. See the class comment.
    }

    private IrisCompat() {}
}
