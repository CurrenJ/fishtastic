package grill24.fishtastic.mixin;

import grill24.fishtastic.client.perf.CosmeticBenchmark;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Frame timing for the cosmetic rendering benchmark ({@link CosmeticBenchmark}); idle unless a measurement is running.
 * PORT-ONLY target: 1.21.1 has no {@code renderFrame}; its frame is {@code runTick(boolean)}, which also
 * runs the frame's client ticks, so the CPU sample here includes them.
 */
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
