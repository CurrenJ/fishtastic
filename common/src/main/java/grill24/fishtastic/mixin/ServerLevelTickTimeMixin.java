package grill24.fishtastic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import grill24.fishtastic.server.SunsetExtensionHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies {@link SunsetExtensionHandler}'s day-time rate to the overworld (1.21.1 has no world
 * clocks to set a rate on). Each tick adds the rate to a fractional accumulator and advances day
 * time by its whole part, so a rate of 1.0 is exactly vanilla. Other dimensions derive their day
 * time from the overworld, so only the overworld's own advance is scaled.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelTickTimeMixin {
    @Unique
    private double fishtastic$dayTimeAccumulator;

    @WrapOperation(method = "tickTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"))
    private void fishtastic$scaleDayTime(ServerLevel level, long nextDayTime, Operation<Void> original) {
        if (level.dimension() != Level.OVERWORLD) {
            original.call(level, nextDayTime);
            return;
        }
        fishtastic$dayTimeAccumulator += SunsetExtensionHandler.currentRate();
        long steps = (long) Math.floor(fishtastic$dayTimeAccumulator);
        fishtastic$dayTimeAccumulator -= steps;
        original.call(level, nextDayTime - 1L + steps);
    }
}
