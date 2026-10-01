package grill24.fishtastic.mixin;

import grill24.fishtastic.client.perf.CosmeticBenchmark;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Frame timing for the cosmetic rendering benchmark ({@link CosmeticBenchmark}); idle unless a measurement is running. */
@Mixin(Minecraft.class)
public abstract class MinecraftFrameBenchMixin {

    @Inject(method = "runTick", at = @At("HEAD"))
    private void fishtastic$benchFrameStart(boolean advanceGameTime, CallbackInfo ci) {
        CosmeticBenchmark.frameStart();
    }

    @Inject(method = "runTick", at = @At("TAIL"))
    private void fishtastic$benchFrameEnd(boolean advanceGameTime, CallbackInfo ci) {
        CosmeticBenchmark.frameEnd(((Minecraft) (Object) this).getFrameTimeNs());
    }
}
