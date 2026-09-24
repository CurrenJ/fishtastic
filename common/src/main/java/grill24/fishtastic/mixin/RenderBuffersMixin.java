package grill24.fishtastic.mixin;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.renderer.RenderBuffersHelper;
import grill24.fishtastic.itemeffect.ItemEffectManager;
import grill24.fishtastic.mixin.accessor.BufferSourceAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import grill24.fishtastic.client.renderer.FishtasticRenderTypes;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;

/**
 * Mixin to RenderBuffers to:
 * 1. Register ItemEffect render types during initial construction (for early effects)
 * 2. Implement RenderBuffersHelper interface to allow dynamic render type registration
 */
@Mixin(RenderBuffers.class)
public class RenderBuffersMixin implements RenderBuffersHelper {

    @Shadow
    @Final
    private MultiBufferSource.BufferSource bufferSource;

    /**
     * During construction, register any ItemEffect render types that are already available.
     * This handles effects that might be loaded early (though most will load later from datapacks).
     */
    @Inject(method = "put", at = @At("HEAD"))
    private static void addQualityGlowRenderTypes(
            Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder> object2ObjectLinkedOpenHashMap, RenderType renderType, CallbackInfo ci
    ) {
        if(renderType == RenderType.glint()) {
            // PORT-ONLY: the world quality outline gets a fixed buffer here too, so it's drawn in
            // the final batch after the solid sheets (item frame backings, etc.) rather than
            // whenever the shared transient buffer is flushed. It writes no depth, so anything
            // solid drawn after it would paint over it. (26.1.2's feature renderer orders its
            // translucent features last by itself.)
            RenderBuffersMixin.fishtastic$putRenderType(object2ObjectLinkedOpenHashMap, FishtasticRenderTypes.ITEM_OUTLINE);
            // Add any ItemEffect render types that are already available
            // This might be empty on first construction, but we'll add more later
            for (RenderType qualityRenderType : ItemEffectManager.getAllRenderTypes()) {
                RenderBuffersMixin.fishtastic$putRenderType(object2ObjectLinkedOpenHashMap, qualityRenderType);
            }
        }
    }

    /**
     * Dynamically add render types to the buffer source after construction.
     * Called by ItemEffectManager when datapacks are loaded and effects are available.
     * Implements RenderBuffersHelper interface.
     */
    @Override
    public void fishtastic$addRenderTypesToBuffer(List<RenderType> renderTypes) {
        if (!(bufferSource instanceof BufferSourceAccessor accessor)) {
            Fishtastic.LOGGER.error("Cannot add render types - BufferSource is not accessible via mixin");
            return;
        }

        // PORT-ONLY: Map rather than the field's SequencedMap type — naming it in a local would emit
        // a java/util/SequencedMap class constant, which :common:java17ApiGuard bans (1.20.1 is Java 17).
        Map<RenderType, ByteBufferBuilder> fixedBuffers = accessor.fishtastic$getFixedBuffers();
        int added = 0;

        for (RenderType renderType : renderTypes) {
            // Only add if not already present
            if (!fixedBuffers.containsKey(renderType)) {
                fixedBuffers.put(renderType, new ByteBufferBuilder(renderType.bufferSize()));
                added++;
            }
        }

        if (added > 0) {
            Fishtastic.LOGGER.info("Dynamically added {} ItemEffect render types to RenderBuffers", added);
        }
    }

    @Unique
    private static void fishtastic$putRenderType(
        Object2ObjectLinkedOpenHashMap<RenderType, ByteBufferBuilder> map,
        RenderType renderType
    ) {
        map.put(renderType, new ByteBufferBuilder(renderType.bufferSize()));
    }
}
