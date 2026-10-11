package grill24.fishtastic.mixin;

import grill24.fishtastic.client.companion.ClientCompanions;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the companion sim a chunk arrived: blocks its fish sampled while the chunk was missing read
 * as air and must be resampled.
 */
@Mixin(ClientLevel.class)
public class ClientLevelCompanionMixin {
    @Inject(method = "onChunkLoaded", at = @At("TAIL"))
    private void fishtastic$companionChunkLoaded(ChunkPos pos, CallbackInfo ci) {
        ClientCompanions.onChunkLoaded(pos);
    }
}
