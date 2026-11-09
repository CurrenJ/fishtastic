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
 * {@code guiGraphics.renderItem(...)} runs synchronously inside {@code renderSlot} (26.1.2's
 * {@code extractSlot}), and {@code FishtasticGuiOutlineRenderer} reads
 * {@link FishtasticGlintState#HIGHLIGHT_REQUESTED} at its head — same pattern as the silhouette and
 * black-outline effects. The cursor's own item is drawn by {@code renderFloatingItem}, not here, so
 * it is never highlighted.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Shadow protected Slot hoveredSlot;
    @Shadow protected int leftPos;
    @Shadow protected int topPos;

    /**
     * Every container screen, not only the survival inventory (the companion UI redesign widened it):
     * the server equips carried fish from the player's own inventory slots only.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void fishtastic$holdToBond(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // Find the live slot before vanilla's render updates hoveredSlot later in the frame.
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

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void fishtastic$requestHighlight(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        if (ItemHighlightRules.shouldHighlight(this.menu.getCarried(), slot.getItem())) {
            FishtasticGlintState.HIGHLIGHT_REQUESTED.set(Boolean.TRUE);
        }
    }

    /** The companions heart tab / creative button (companion UI spec §6), drawn over the panel. */
    @Inject(method = "render", at = @At("TAIL"))
    private void fishtastic$companionTab(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        grill24.fishtastic.client.companions.CompanionInventoryButton.render(
                (net.minecraft.client.gui.screens.Screen) (Object) this, graphics, leftPos, topPos, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void fishtastic$companionTabClick(double mouseX, double mouseY, int button,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (grill24.fishtastic.client.companions.CompanionInventoryButton.click(
                (net.minecraft.client.gui.screens.Screen) (Object) this, leftPos, topPos, mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "renderSlot", at = @At("RETURN"))
    private void fishtastic$clearHighlight(GuiGraphics graphics, Slot slot, CallbackInfo ci) {
        FishtasticGlintState.HIGHLIGHT_REQUESTED.remove();
    }
}
