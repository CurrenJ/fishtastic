package grill24.fishtastic.mixin;

import grill24.fishtastic.client.companion.CompanionMusic;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelEventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelEventHandler.class)
public abstract class LevelEventCompanionMixin {
    @Shadow @Final private ClientLevel level;

    @Inject(method = "levelEvent", at = @At("HEAD"))
    private void fishtastic$music(int type, BlockPos pos, int data, CallbackInfo ci) {
        if (type == 1010) level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG).get(data)
                .ifPresent(song -> CompanionMusic.jukebox(level, pos, (long) Math.ceil(song.value().lengthInSeconds() * 20)));
        else if (type == 1011) CompanionMusic.stop(level, pos);
    }
}
