package grill24.fishtastic.mixin;

import grill24.fishtastic.client.TutorialHelpScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
    @Shadow @Final private boolean showPauseMenu;

    protected PauseScreenMixin(Component title) { super(title); }

    @Inject(method = "init()V", at = @At("TAIL"))
    private void fishtastic$help(CallbackInfo ci) {
        if (showPauseMenu) {
            addRenderableWidget(Button.builder(Component.translatable("tutorial.fishtastic.help.title"),
                    b -> minecraft.setScreen(new TutorialHelpScreen((Screen) (Object) this)))
                    .bounds(8, height - 28, 124, 20).build());
        }
    }
}
