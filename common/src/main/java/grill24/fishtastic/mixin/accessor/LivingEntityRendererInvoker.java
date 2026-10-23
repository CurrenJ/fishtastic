package grill24.fishtastic.mixin.accessor;

import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Invoker for {@link LivingEntityRenderer}'s protected {@code addLayer}, so the common
 * {@code AvatarRendererMixin} can register the fish companion perch layer on both loaders
 * (docs/fish-companions.md).
 */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererInvoker {
    @Invoker("addLayer")
    boolean fishtastic$addLayer(RenderLayer<?, ?> layer);
}
