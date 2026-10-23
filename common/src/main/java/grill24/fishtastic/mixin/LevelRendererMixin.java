package grill24.fishtastic.mixin;

import grill24.fishtastic.client.companion.ClientCompanions;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the companion sim about client block changes, so the blocks its fish sampled round their
 * owner are resampled (docs/fish-companions.md: "dirty updates for block changes").
 */
@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Inject(method = "blockChanged", at = @At("HEAD"))
    private void fishtastic$companionBlockChanged(BlockGetter level, BlockPos pos, BlockState old, BlockState current,
                                                  int updateFlags, CallbackInfo ci) {
        ClientCompanions.onBlockChanged(pos);
    }
}
