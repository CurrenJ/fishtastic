package grill24.fishtastic.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import grill24.fishtastic.network.SetDayRatePacket;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The client half of {@link ServerLevelTickTimeMixin}: predicts day time at the server's rate
 * ({@link SetDayRatePacket}) between the server's time packets, so the sky doesn't run ahead and
 * snap back every second while the Sunset Postcard is slowing dusk down. Applies in every
 * dimension because the server always sends the overworld's day time.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelTickTimeMixin {
    @Unique
    private double fishtastic$dayTimeAccumulator;

    @WrapOperation(method = "tickTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/ClientLevel;setDayTime(J)V"))
    private void fishtastic$scaleDayTime(ClientLevel level, long nextDayTime, Operation<Void> original) {
        fishtastic$dayTimeAccumulator += SetDayRatePacket.clientRate();
        long steps = (long) Math.floor(fishtastic$dayTimeAccumulator);
        fishtastic$dayTimeAccumulator -= steps;
        original.call(level, nextDayTime - 1L + steps);
    }
}
