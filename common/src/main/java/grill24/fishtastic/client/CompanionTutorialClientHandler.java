package grill24.fishtastic.client;

import grill24.fishtastic.network.CompanionTutorialSyncPacket;
import grill24.fishtastic.network.TutorialActionPacket;
import grill24.fishtastic.tutorial.CompanionTutorialStep;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The first equip is taught inside the party screen. Unlock banners handle discovery. */
public final class CompanionTutorialClientHandler {
    private CompanionTutorialClientHandler() {}
    private static CompanionTutorialStep currentStep = CompanionTutorialStep.COMPLETE;
    private static boolean awaitingReply;

    public static final CompanionTutorialSyncPacket.ClientHandler PACKET_HANDLER = packet -> {
        currentStep = packet.step();
        awaitingReply = false;
    };

    public static CompanionTutorialStep currentStep() { return currentStep; }
    public static boolean isActive() {
        return currentStep.hasOverlay() && !TutorialClientHandler.getCurrentStep().hasOverlay();
    }
    public static boolean awaitingReply() { return awaitingReply; }
    public static Component title() { return Component.translatable("tutorial.fishtastic.companion.equip.title"); }
    public static Component body() { return Component.translatable("tutorial.fishtastic.companion.equip.body"); }

    public static void advance(boolean skip) {
        if (!skip || awaitingReply || !isActive()) return;
        awaitingReply = true;
        TutorialHelpScreen.send(TutorialActionPacket.Action.DISMISS_COMPANIONS);
    }

    // Loader hooks remain stable; no persistent world reminder or book detour.
    public static boolean shouldHighlightBook(ItemStack stack) { return false; }
    public static void renderHud(GuiGraphics graphics, float partialTick) { }
    public static void render(GuiGraphics graphics, float partialTick) { }

    public static void reset() {
        currentStep = CompanionTutorialStep.COMPLETE;
        awaitingReply = false;
    }
}
