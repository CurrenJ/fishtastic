package grill24.fishtastic.mixin;

import grill24.fishtastic.server.FishSchoolManager;
import grill24.fishtastic.util.IServerLevelSchools;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells the level's fish schools about every block change, so a school's water is rescanned only
 * when something inside it changed (docs/interactive-fish-schools-performance-plan.md, step A).
 * {@code onBlockStateChange} is the one {@code ServerLevel} method every successful
 * {@code setBlock} calls on Fabric, NeoForge and Forge 26.1.2 alike.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelSchoolsMixin implements IServerLevelSchools {
    @Unique
    private @Nullable FishSchoolManager fishtastic$schools;

    @Override
    public @Nullable FishSchoolManager fishtastic$schools() {
        return fishtastic$schools;
    }

    @Override
    public void fishtastic$setSchools(FishSchoolManager schools) {
        fishtastic$schools = schools;
    }

    @Inject(method = "onBlockStateChange", at = @At("HEAD"))
    private void fishtastic$onBlockStateChange(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        FishSchoolManager schools = fishtastic$schools;
        if (schools != null) schools.onBlockChanged(pos);
    }
}
