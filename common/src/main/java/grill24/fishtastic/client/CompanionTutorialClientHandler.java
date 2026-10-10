package grill24.fishtastic.client;

import grill24.fishtastic.network.CompanionTutorialAdvancePacket;
import grill24.fishtastic.network.CompanionTutorialSyncPacket;
import grill24.fishtastic.tutorial.CompanionTutorialStep;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Client half of the companion FTUE (docs/fish-companions.md §8.4, R7.7). Same shape as
 * {@link EncyclopediaTutorialClientHandler}: a side panel over the encyclopedia, advanced by the
 * concrete action it asks for — here, opening the Companions tab — not a timer. The step starts
 * server-side the first time any bond unlocks.
 */
public final class CompanionTutorialClientHandler {
    private CompanionTutorialClientHandler() {}

    private static CompanionTutorialStep currentStep = CompanionTutorialStep.COMPLETE;

    public static final CompanionTutorialSyncPacket.ClientHandler PACKET_HANDLER = packet -> currentStep = packet.step();

    /** Call when the player opens the Companions tab — completes the INTRO step. */
    public static void onCompanionsTabOpened() {
        if (currentStep != CompanionTutorialStep.INTRO) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.connection.send(new ServerboundCustomPayloadPacket(
                new CompanionTutorialAdvancePacket(CompanionTutorialStep.INTRO)));
    }

    // Same side-panel geometry as the encyclopedia FTUE's, on the other edge so the two never
    // stack on top of each other if both are somehow active.
    private static final int SIDE_PANEL_WIDTH = 150;
    private static final int SIDE_PANEL_MARGIN = 16;

    /** Called after any screen renders; gated to the encyclopedia, the screen the panel teaches. */
    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        if (!currentStep.hasOverlay()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof FishEncyclopediaScreen)) return;

        Component title = Component.translatable("tutorial.fishtastic.companion_intro.title");
        Component body = Component.translatable("tutorial.fishtastic.companion_intro.body");

        Font font = mc.font;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int innerPad = 10;
        int boxWidth = Math.min(SIDE_PANEL_WIDTH, sw - SIDE_PANEL_MARGIN * 2);
        int lineH = font.lineHeight + 2;
        int maxLineW = boxWidth - innerPad * 2;

        List<FormattedCharSequence> titleLines = font.split(title.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE), maxLineW);
        List<FormattedCharSequence> bodyLines = font.split(body.copy().withStyle(ChatFormatting.GRAY), maxLineW);

        int boxH = innerPad * 2 + titleLines.size() * lineH + 4 + bodyLines.size() * lineH;
        int boxX = SIDE_PANEL_MARGIN;
        int boxY = (sh - boxH) / 2;

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxH, 0xD0101820);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFF44CC88);
        graphics.fill(boxX, boxY + boxH - 2, boxX + boxWidth, boxY + boxH, 0xFF44CC88);
        graphics.fill(boxX, boxY, boxX + 2, boxY + boxH, 0xFF44CC88);
        graphics.fill(boxX + boxWidth - 2, boxY, boxX + boxWidth, boxY + boxH, 0xFF44CC88);

        int tx = boxX + innerPad;
        int ty = boxY + innerPad;
        for (FormattedCharSequence line : titleLines) {
            graphics.text(font, line, tx, ty, 0xFFFFFFFF);
            ty += lineH;
        }
        ty += 4;
        for (FormattedCharSequence line : bodyLines) {
            graphics.text(font, line, tx, ty, 0xFFAAAAAA);
            ty += lineH;
        }
    }

    /** Dev accessor for the self-test scene. */
    public static CompanionTutorialStep currentStep() {
        return currentStep;
    }

    /** Call on disconnect/world-leave so the overlay doesn't persist into the main menu. */
    public static void reset() {
        currentStep = CompanionTutorialStep.COMPLETE;
    }
}
