package grill24.fishtastic.client.renderer;

import net.minecraft.client.renderer.RenderType;

import java.util.List;

/**
 * Interface for accessing RenderBuffers functionality added by our mixin.
 * This allows us to dynamically register render types after RenderBuffers initialization.
 */
public interface RenderBuffersHelper {
    /**
     * Dynamically add render types to the RenderBuffers.
     * This is implemented by RenderBuffersMixin.
     *
     * @param renderTypes List of render types to add
     */
    void fishtastic$addRenderTypesToBuffer(List<RenderType> renderTypes);
}

