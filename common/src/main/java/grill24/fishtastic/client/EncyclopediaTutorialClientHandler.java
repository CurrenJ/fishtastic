package grill24.fishtastic.client;

import grill24.fishtastic.network.EncyclopediaTutorialAdvancePacket;
import grill24.fishtastic.network.EncyclopediaTutorialSyncPacket;
import grill24.fishtastic.tutorial.EncyclopediaTutorialStep;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Client-side half of the small, independent Fish Encyclopedia FTUE — see
 * {@link grill24.fishtastic.tutorial.EncyclopediaTutorialManager} for why this isn't just more
 * cases in {@link TutorialClientHandler}.
 * <p>
 * Responsive rather than timer-driven: each step advances on a concrete action the player takes
 * in the screen it's teaching, not after N ticks elapse. {@link FishEncyclopediaScreen} calls
 * {@link #onFishClicked()} and {@link #onInfoPageClosed()} at the relevant points.
 */
public class EncyclopediaTutorialClientHandler {

    private static EncyclopediaTutorialStep currentStep = EncyclopediaTutorialStep.COMPLETE;
    private static boolean sentAdvanceThisStep = false;

    public static final EncyclopediaTutorialSyncPacket.ClientHandler PACKET_HANDLER = packet -> {
        currentStep = packet.step();
        sentAdvanceThisStep = false;
    };

    /** Call when the player clicks a fish icon on the encyclopedia disc — advances past INTRO. */
    public static void onFishClicked() {
        if (currentStep == EncyclopediaTutorialStep.INTRO) sendAdvance();
    }

    /** Call when the info page is left (back to disc, or the screen closes from it) — completes ZONES_AND_REWARDS. */
    public static void onInfoPageClosed() {
        if (currentStep == EncyclopediaTutorialStep.ZONES_AND_REWARDS) sendAdvance();
    }

    // Docked to the screen's right edge as a side panel, rather than a bottom-center bar, so it
    // never overlaps the disc/info page content which are centered, or the back button (top-left).
    private static final int SIDE_PANEL_WIDTH = 150;
    private static final int SIDE_PANEL_MARGIN = 16;

    /**
     * Called after any screen renders. Gated to {@link FishEncyclopediaScreen} specifically
     * (unlike {@link TutorialClientHandler}'s screen overlay, which is only ever active for the
     * one or two screen-bound tutorial steps) because this step can outlive the screen it was
     * written for — a player who closes the encyclopedia before advancing this step would
     * otherwise see the panel bleed onto whatever screen they open next.
     */
    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        if (!currentStep.hasOverlay()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof FishEncyclopediaScreen)) return;

        Component title = getTitle(currentStep);
        Component body = getBody(currentStep);
        if (title == null || body == null) return;

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
        int boxX = sw - boxWidth - SIDE_PANEL_MARGIN;
        int boxY = (sh - boxH) / 2;

        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxH, 0xD0101820);
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFF4488CC);
        graphics.fill(boxX, boxY + boxH - 2, boxX + boxWidth, boxY + boxH, 0xFF4488CC);
        graphics.fill(boxX, boxY, boxX + 2, boxY + boxH, 0xFF4488CC);
        graphics.fill(boxX + boxWidth - 2, boxY, boxX + boxWidth, boxY + boxH, 0xFF4488CC);

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

    /** Call on disconnect/world-leave so the overlay doesn't persist into the main menu. */
    public static void reset() {
        currentStep = EncyclopediaTutorialStep.COMPLETE;
        sentAdvanceThisStep = false;
    }

    private static Component getTitle(EncyclopediaTutorialStep step) {
        String key = switch (step) {
            case INTRO -> "tutorial.fishtastic.encyclopedia_intro.title";
            case ZONES_AND_REWARDS -> "tutorial.fishtastic.encyclopedia_zones_and_rewards.title";
            default -> null;
        };
        return key != null ? Component.translatable(key) : null;
    }

    private static Component getBody(EncyclopediaTutorialStep step) {
        return switch (step) {
            case INTRO -> Component.translatable("tutorial.fishtastic.encyclopedia_intro.body");
            case ZONES_AND_REWARDS -> Component.translatable("tutorial.fishtastic.encyclopedia_zones_and_rewards.body");
            default -> null;
        };
    }

    private static void sendAdvance() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        sentAdvanceThisStep = true;
        mc.player.connection.send(new ServerboundCustomPayloadPacket(new EncyclopediaTutorialAdvancePacket(currentStep)));
    }
}
