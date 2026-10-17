package grill24.fishtastic.mixin;

import grill24.fishtastic.client.companion.CompanionMusic;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.RecordItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelEventCompanionMixin {
    @Shadow private ClientLevel level;

    @Inject(method = "levelEvent", at = @At("HEAD"))
    private void fishtastic$music(int type, BlockPos pos, int data, CallbackInfo ci) {
        // 1.20.1: a record is an item (data = Item.getId), not a JukeboxSong holder.
        if (type == 1010) {
            if (Item.byId(data) instanceof RecordItem record) CompanionMusic.jukebox(level, pos, record.getLengthInTicks());
        } else if (type == 1011) CompanionMusic.stop(level, pos);
    }
}
