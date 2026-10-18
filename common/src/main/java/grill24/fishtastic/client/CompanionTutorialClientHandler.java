package grill24.fishtastic.client;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.network.CompanionTutorialAdvancePacket;
import grill24.fishtastic.network.CompanionTutorialSyncPacket;
import grill24.fishtastic.tutorial.CompanionTutorialStep;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import grill24.fishtastic.network.NetworkApiSided;
import net.minecraft.world.item.ItemStack;

/** Companion lesson copy and book-first discovery. The party screen owns its own guide layout. */
public final class CompanionTutorialClientHandler {
    private CompanionTutorialClientHandler() {}

    private static CompanionTutorialStep currentStep = CompanionTutorialStep.COMPLETE;
    private static boolean awaitingReply;

    public static final CompanionTutorialSyncPacket.ClientHandler PACKET_HANDLER = packet -> {
        currentStep = packet.step();
        awaitingReply = false;
    };

    public static CompanionTutorialStep currentStep() { return currentStep; }

    /** The fishing introduction always gets priority. */
    public static boolean isActive() {
        return currentStep.hasOverlay() && !TutorialClientHandler.getCurrentStep().hasOverlay();
    }

    public static boolean awaitingReply() { return awaitingReply; }

    public static Component title() {
        return Component.translatable("tutorial.fishtastic.companion." + currentStep.name().toLowerCase(java.util.Locale.ROOT) + ".title");
    }

    public static Component body() {
        return Component.translatable("tutorial.fishtastic.companion." + currentStep.name().toLowerCase(java.util.Locale.ROOT) + ".body");
    }

    public static void advance(boolean skip) {
        Minecraft mc = Minecraft.getInstance();
        if (awaitingReply || !isActive() || !(mc.screen instanceof CompanionScreen) || mc.player == null
                || !mc.player.containerMenu.getCarried().isEmpty() || !skip && !currentStep.canContinue()) return;
        awaitingReply = true;
        NetworkApiSided.sendToServer(new CompanionTutorialAdvancePacket(currentStep, skip), CompanionTutorialAdvancePacket.STREAM_CODEC);
    }

    public static boolean shouldHighlightBook(ItemStack stack) {
        return isActive() && stack.is(FishtasticItems.FISHOPEDIA.value());
    }

    /** Called by each loader's HUD hook; never darkens the world or requires a custom key. */
    public static void renderHud(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!isActive() || mc.level == null || mc.screen != null) return;
        if (mc.player != null) {
            for (int slot = 0; slot < 9; slot++) {
                if (!shouldHighlightBook(mc.player.getInventory().getItem(slot))) continue;
                graphics.renderOutline(mc.getWindow().getGuiScaledWidth() / 2 - 91 + slot * 20,
                        mc.getWindow().getGuiScaledHeight() - 23, 22, 24, 0xFFFFDD44);
            }
        }
        int width = Math.min(340, mc.getWindow().getGuiScaledWidth() - 32);
        Component message = Component.translatable("tutorial.fishtastic.companion.open_book",
                mc.options.keyUse.getTranslatedKeyMessage(), mc.options.keyInventory.getTranslatedKeyMessage());
        drawPanel(graphics, (mc.getWindow().getGuiScaledWidth() - width) / 2,
                mc.getWindow().getGuiScaledHeight() - 120, width,
                Component.translatable("tutorial.fishtastic.companion.resume.title"), message);
    }

    /** Wait for the encyclopedia's own introduction before suggesting companion management. */
    public static void render(GuiGraphics graphics, float partialTick) {
        if (!isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof FishEncyclopediaScreen encyclopedia && !EncyclopediaTutorialClientHandler.hasActiveLesson()) {
            encyclopedia.highlightCompanionEntryButton(graphics);
            int width = Math.min(150, mc.getWindow().getGuiScaledWidth() / 3);
            drawPanel(graphics, 12, 52, width,
                    Component.translatable("tutorial.fishtastic.companion.resume.title"),
                    Component.translatable(encyclopedia.hasCompanionEntryButton()
                            ? "tutorial.fishtastic.companion.choose_fish" : "tutorial.fishtastic.companion.open_party"));
        }
    }

    private static void drawPanel(GuiGraphics graphics, int x, int y, int width, Component title, Component body) {
        var font = Minecraft.getInstance().font;
        var heading = font.split(title.copy().withStyle(ChatFormatting.BOLD), width - 20);
        var lines = font.split(body, width - 20);
        int height = 24 + (heading.size() + lines.size()) * 11;
        y = Math.max(8, Math.min(y, Minecraft.getInstance().getWindow().getGuiScaledHeight() - height - 30));
        graphics.fill(x, y, x + width, y + height, 0xE0101820);
        graphics.renderOutline(x, y, width, height, 0xFF44CC88);
        int textY = y + 10;
        for (var line : heading) {
            graphics.drawString(font, line, x + 10, textY, 0xFFFFFFFF);
            textY += 11;
        }
        textY += 4;
        for (var line : lines) {
            graphics.drawString(font, line, x + 10, textY, 0xFFDDDDDD);
            textY += 11;
        }
    }

    public static void reset() {
        currentStep = CompanionTutorialStep.COMPLETE;
        awaitingReply = false;
    }
}
