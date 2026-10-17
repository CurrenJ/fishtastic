package grill24.fishtastic.mixin;

import grill24.fishtastic.client.companion.CompanionMusic;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoteBlock.class)
public abstract class NoteBlockCompanionMixin {
    @Inject(method = "triggerEvent", at = @At("RETURN"))
    private void fishtastic$note(BlockState state, Level level, BlockPos pos, int b0, int b1,
                                 CallbackInfoReturnable<Boolean> cir) {
        if (level instanceof ClientLevel client && cir.getReturnValue())
            CompanionMusic.note(client, pos, state.getValue(NoteBlock.NOTE) / 24f);
    }
}
