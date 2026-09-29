package grill24.fishtastic.mixin;

import grill24.fishtastic.client.ItemHighlightRules;
import grill24.fishtastic.client.renderer.FishtasticGlintState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Flags each container slot's item for the gold highlight outline while it is being drawn.
 * {@code renderSlot} draws the item through {@code GuiGraphics.renderItem}, whose head
 * ({@code GuiGraphicsMixin} → {@code FishtasticGuiOutlineRenderer}) reads
 * {@link FishtasticGlintState#HIGHLIGHT_REQUESTED} at that moment — same pattern as the silhouette
 * and black-outline effects. The cursor's own item is drawn by {@code renderFloatingItem}, not here,
 * so it is never highlighted. (26.1.2 hooks {@code extractSlot}, the same method after the
 * render-state split.)
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow @Final
    protected AbstractContainerMenu menu;

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void fishtastic$requestHighlight(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (ItemHighlightRules.shouldHighlight(this.menu.getCarried(), slot.getItem())) {
            FishtasticGlintState.HIGHLIGHT_REQUESTED.set(Boolean.TRUE);
        }
    }

    @Inject(method = "renderSlot", at = @At("RETURN"))
    private void fishtastic$clearHighlight(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        FishtasticGlintState.HIGHLIGHT_REQUESTED.remove();
    }
}
