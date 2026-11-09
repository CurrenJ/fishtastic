package grill24.fishtastic.mixin;

import grill24.fishtastic.client.ItemHighlightRules;
import grill24.fishtastic.client.renderer.FishtasticGlintState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
 * {@code graphics.item(...)} resolves the item model synchronously inside {@code extractSlot}, and
 * {@code ItemModelResolverMixin} reads {@link FishtasticGlintState#HIGHLIGHT_REQUESTED} at that
 * moment — same pattern as the silhouette and black-outline effects. The cursor's own item is
 * drawn by {@code extractCarriedItem}, not here, so it is never highlighted.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow protected Slot hoveredSlot;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    /**
     * extractContents, not extractRenderState: recipe-book screens (the survival inventory among them)
     * override extractRenderState without calling it, but every container screen extracts its contents.
     */
    @Inject(method = "extractContents", at = @At("HEAD"))
    private void fishtastic$holdToBond(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // Find the live slot before vanilla's extraction updates hoveredSlot later in the frame.
        int left = leftPos, top = topPos;
        Slot live = null;
        for (Slot slot : menu.slots) {
            if (slot.isActive() && mouseX >= left + slot.x && mouseX < left + slot.x + 16
                    && mouseY >= top + slot.y && mouseY < top + slot.y + 16) live = slot;
        }
        grill24.fishtastic.client.CompanionBondInput.tick(this, live, menu.getCarried().isEmpty());
    }

    @Inject(method = "getTooltipFromContainerItem", at = @At("RETURN"), cancellable = true)
    private void fishtastic$bondTooltip(net.minecraft.world.item.ItemStack stack,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.List<net.minecraft.network.chat.Component>> cir) {
        // Only over the player's own inventory slots: the server equips carried fish only.
        if (hoveredSlot == null || !(hoveredSlot.container instanceof net.minecraft.world.entity.player.Inventory)
                || !grill24.fishtastic.client.CompanionBondInput.eligible(stack)) return;
        var lines = new java.util.ArrayList<>(cir.getReturnValue());
        lines.add(grill24.fishtastic.client.CompanionBondInput.hint(stack));
        cir.setReturnValue(lines);
    }

    @Shadow @Final
    protected AbstractContainerMenu menu;

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void fishtastic$requestHighlight(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (ItemHighlightRules.shouldHighlight(this.menu.getCarried(), slot.getItem())) {
            FishtasticGlintState.HIGHLIGHT_REQUESTED.set(Boolean.TRUE);
        }
    }

    /** The companions heart tab / creative button (companion UI spec §6), drawn over the panel. */
    @Inject(method = "extractContents", at = @At("TAIL"))
    private void fishtastic$companionTab(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        grill24.fishtastic.client.companions.CompanionInventoryButton.render(
                (net.minecraft.client.gui.screens.Screen) (Object) this, graphics, leftPos, topPos, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void fishtastic$companionTabClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (grill24.fishtastic.client.companions.CompanionInventoryButton.click(
                (net.minecraft.client.gui.screens.Screen) (Object) this, leftPos, topPos, event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    private void fishtastic$clearHighlight(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        FishtasticGlintState.HIGHLIGHT_REQUESTED.remove();
    }
}
