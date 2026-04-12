package grill24.fishtastic.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder mixin on LevelRenderer.
 *
 * The old inject that flushed custom quality-glow RenderTypes just before the
 * vanilla glint pass is no longer needed: in the 26.1 pipeline,
 * ItemFeatureRendererMixin replaces the foil RenderType directly inside
 * ItemFeatureRenderer.getFoilRenderType(), so no separate flush step is required.
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
}
