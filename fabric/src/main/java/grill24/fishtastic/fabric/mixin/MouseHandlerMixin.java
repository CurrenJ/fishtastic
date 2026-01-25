package grill24.fishtastic.fabric.mixin;

import grill24.fishtastic.client.FishTankCustomizationHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {
    @Shadow @Final private Minecraft minecraft;

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double xOffset, double yOffset, CallbackInfo ci) {
        if (this.minecraft.getOverlay() == null && window == this.minecraft.getWindow().getWindow()) {
            double scrollDelta = yOffset * this.minecraft.options.mouseWheelSensitivity().get();

            if (FishTankCustomizationHandler.handleMouseScroll(scrollDelta)) {
                ci.cancel();
            }
        }
    }
}
