package grill24.fishtastic.mixin.accessor;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.SequencedMap;

/**
 * Accessor mixin to access the internal fixedBuffers map in MultiBufferSource.BufferSource.
 * This allows us to dynamically add new RenderTypes after initialization.
 */
@Mixin(MultiBufferSource.BufferSource.class)
public interface BufferSourceAccessor {
    @Accessor("fixedBuffers")
    SequencedMap<RenderType, ByteBufferBuilder> fishtastic$getFixedBuffers();
}

