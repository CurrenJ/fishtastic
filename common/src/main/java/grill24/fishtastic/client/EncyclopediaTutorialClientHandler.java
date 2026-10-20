package grill24.fishtastic.client;

import grill24.fishtastic.network.EncyclopediaTutorialAdvancePacket;
import grill24.fishtastic.network.EncyclopediaTutorialSyncPacket;
import grill24.fishtastic.network.TutorialActionPacket;
import grill24.fishtastic.tutorial.EncyclopediaTutorialStep;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

/** One dismissible hint; zones and rewards explain themselves beside their controls. */
public final class EncyclopediaTutorialClientHandler {
    private static EncyclopediaTutorialStep currentStep = EncyclopediaTutorialStep.COMPLETE;
    private static boolean awaitingReply;

    public static boolean hasActiveLesson() { return currentStep.hasOverlay() && !awaitingReply; }

    public static final EncyclopediaTutorialSyncPacket.ClientHandler PACKET_HANDLER = packet -> {
        currentStep = packet.step();
        awaitingReply = false;
    };

    public static void onFishClicked() {
        var mc = Minecraft.getInstance();
        if (!hasActiveLesson() || mc.player == null) return;
        awaitingReply = true;
        mc.player.connection.send(new ServerboundCustomPayloadPacket(new EncyclopediaTutorialAdvancePacket(currentStep)));
    }

    /** Leaving an info page is no longer a lesson. */
    public static void onInfoPageClosed() { }

    public static void dismiss() {
        if (!hasActiveLesson()) return;
        awaitingReply = true;
        TutorialHelpScreen.send(TutorialActionPacket.Action.DISMISS_ENCYCLOPEDIA);
    }

    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        var mc = Minecraft.getInstance();
        if (!hasActiveLesson() || TutorialClientHandler.getCurrentStep().hasOverlay()
                || !(mc.screen instanceof FishEncyclopediaScreen)) return;
        // Leave the top-right toast area and the central fish display clear, even at GUI 320x240.
        int width = Math.min(220, mc.getWindow().getGuiScaledWidth() / 2 - 24);
        var lines = mc.font.split(Component.translatable("tutorial.fishtastic.encyclopedia_intro.body"), width - 20);
        width = lines.stream().mapToInt(mc.font::width).max().orElse(0) + 20;
        int height = lines.size() * 11 + 20;
        int x = 12;
        int y = 8;
        graphics.fill(x, y, x + width, y + height, 0xE0101820);
        for (var line : lines) {
            graphics.text(mc.font, line, x + 10, y + 10, 0xFFDDDDDD);
            y += 11;
        }
    }

    public static void reset() {
        currentStep = EncyclopediaTutorialStep.COMPLETE;
        awaitingReply = false;
    }
}
