package grill24.fishtastic.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import grill24.fishtastic.client.renderer.FishtasticRenderTypes;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to RenderBuffers to register our custom quality glow render types in the buffer source.
 * This ensures they are properly batched and rendered at the correct time, just like vanilla glint.
 */
@Mixin(RenderBuffers.class)
public class RenderBuffersMixin {

    /**
     * Modify the consumer passed to Util.make() to add our custom render types.
     * This wraps the vanilla consumer and adds our quality glow types after vanilla's setup.
     */
    @Inject(method = "put", at = @At("HEAD"))
    private static void addQualityGlowRenderTypes(
            Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder> object2ObjectLinkedOpenHashMap, RenderType renderType, CallbackInfo ci
    ) {
        if(renderType == RenderType.glint()) {
            // Add all custom quality glow render types for each quality level
            for (RenderType qualityRenderType : FishtasticRenderTypes.getAllCustomRenderTypes()) {
                RenderBuffersMixin.fishtastic$putRenderType(object2ObjectLinkedOpenHashMap, qualityRenderType);
            }
        }
    }

    /**
     * Helper method to add a RenderType to the buffer map with appropriate buffer size.
     * Mirrors the private static put() method in RenderBuffers.
     */
    @Unique
    private static void fishtastic$putRenderType(
        Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder> map,
        RenderType renderType
    ) {
        map.put(renderType, new ByteBufferBuilder(renderType.bufferSize()));
    }
}
