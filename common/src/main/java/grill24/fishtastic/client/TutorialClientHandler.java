package grill24.fishtastic.client;

import grill24.fishtastic.network.FishtasticTransport;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.item.FishtasticFishingRodItem;
import grill24.fishtastic.network.TutorialAdvancePacket;
import grill24.fishtastic.network.TutorialSyncPacket;
import grill24.fishtastic.tutorial.TutorialStep;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * All client-side FTUE (tutorial) logic lives here.
 * Receives TutorialSyncPacket, manages overlay rendering, and sends advances.
 */
public class TutorialClientHandler {

    private static TutorialStep currentStep = TutorialStep.COMPLETE;
    private static boolean sentAdvanceThisStep = false;
    // How many rods already had bait loaded when BAIT_LOAD started — a player who already owns
    // an unrelated baited rod (a spare, a shop purchase) must not skip the step instantly; only
    // an increase from this baseline counts as "the player just loaded bait."
    private static int baitLoadedRodCountAtStepStart = 0;

    // Slide animation — 0 = fully shown, 1 = fully hidden (start/end position)
    private static float animOffset = 1.0f;
    private static float prevAnimOffset = 1.0f; // previous tick value for partial-tick lerp
    private static boolean isExiting = false;
    private static final int SLIDE_TICKS = 5;  // ~0.25 s

    // Gap between the bottom-center layout box (in-world non-minigame steps) and the screen edge.
    private static final int BOTTOM_LAYOUT_MARGIN = 40;

    // -------------------------------------------------------------------------
    // Packet handler (registered in platform client inits)
    // -------------------------------------------------------------------------

    public static final TutorialSyncPacket.ClientHandler PACKET_HANDLER = packet -> {
        TutorialStep previous = currentStep;
        currentStep = packet.step();
        sentAdvanceThisStep = false;
        animOffset = 1.0f;
        prevAnimOffset = 1.0f;
        isExiting = false;

        Minecraft mc = Minecraft.getInstance();
        if (currentStep == TutorialStep.BAIT_LOAD) {
            baitLoadedRodCountAtStepStart = mc.player != null ? countRodsWithBaitLoaded(mc.player) : 0;
        }
        if (mc.gameRenderer == null) return;

        var activeAnimation = ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation();
        if (activeAnimation instanceof FishingMinigameAnimation animation) {
            if ((currentStep == TutorialStep.WAITING_FOR_CAST || currentStep == TutorialStep.BAIT_LOAD)
                    && (previous.pausesMinigame() || previous == TutorialStep.MINIGAME_CATCH)) {
                ((IGameRendererExtension) mc.gameRenderer).fishtastic$cancelCurrentAnimation();
                FishingMinigameClientHandler.cancel();
                return;
            }
            animation.setPaused(currentStep.pausesMinigame());
        }
    };

    // -------------------------------------------------------------------------
    // Tick — called each client tick from platform client
    // -------------------------------------------------------------------------

    public static void tick() {
        if (!currentStep.hasOverlay()) return;

        // Drive slide animation
        prevAnimOffset = animOffset;
        float animDelta = 1.0f / SLIDE_TICKS;
        if (isExiting) {
            animOffset = Math.min(1.0f, animOffset + animDelta);
        } else {
            animOffset = Math.max(0.0f, animOffset - animDelta);
        }

        if (sentAdvanceThisStep) return;

        switch (currentStep) {
            case BAIT_LOAD -> {
                // Creative's Inventory tab never sends its rearrangement clicks to the server, so
                // the server-side FishtasticFishingRodItem bait-insert path that normally calls
                // TutorialManager.onBaitLoaded never runs there. Poll for the resulting state
                // instead — works identically for survival and creative, and for however the
                // player got bait onto a rod. Compared against the step-start baseline (not just
                // "any rod has bait") so an unrelated already-baited rod can't skip the step.
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null && countRodsWithBaitLoaded(mc.player) > baitLoadedRodCountAtStepStart) {
                    sendAdvance();
                }
            }
            case FISHING_OFFER -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null && mc.player != null && !mc.player.isDeadOrDying()) {
                    mc.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(show -> {
                        sentAdvanceThisStep = true;
                        FishtasticTransport.sendToServer(new TutorialAdvancePacket(currentStep, show));
                        mc.setScreen(null);
                    }, Component.translatable("tutorial.fishtastic.fishing_offer.title"),
                            Component.translatable("tutorial.fishtastic.fishing_offer.body"),
                            Component.translatable("tutorial.fishtastic.fishing_offer.show"),
                            Component.translatable("tutorial.fishtastic.fishing_offer.later")) {
                        @Override public boolean isPauseScreen() { return false; }
                    });
                }
            }
            default -> {}
        }
    }

    // -------------------------------------------------------------------------
    // Action hooks — called by the screen/minigame being taught
    // -------------------------------------------------------------------------

    public static TutorialStep getCurrentStep() { return currentStep; }

    /** Where the player is within the BAIT_LOAD step. Derived each frame from the open screen and cursor — no server state. */
    private enum BaitLoadPhase { OPEN_INVENTORY, PICK_UP_BAIT, LOAD_ROD }

    private static BaitLoadPhase baitLoadPhase(Minecraft mc) {
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return BaitLoadPhase.OPEN_INVENTORY;
        return screen.getMenu().getCarried().is(FishtasticItemTags.FISHING_BAIT)
                ? BaitLoadPhase.LOAD_ROD
                : BaitLoadPhase.PICK_UP_BAIT;
    }

    private static boolean isBaitLoadOnContainerScreen(Minecraft mc) {
        return currentStep == TutorialStep.BAIT_LOAD && mc.screen instanceof AbstractContainerScreen<?>;
    }

    /**
     * True while the tutorial wants bait slots to glow even though the cursor isn't holding a
     * rod — i.e. BAIT_LOAD is active and the cursor isn't already carrying bait.
     * Read by {@link ItemHighlightRules#shouldHighlight}.
     */
    public static boolean isPromptingBaitPickup(ItemStack cursor) {
        return currentStep == TutorialStep.BAIT_LOAD && !sentAdvanceThisStep
                && !cursor.is(FishtasticItemTags.FISHING_BAIT);
    }

    /** Call on disconnect/world-leave so the overlay doesn't persist into the main menu. */
    public static void reset() {
        currentStep = TutorialStep.COMPLETE;
        sentAdvanceThisStep = false;
        baitLoadedRodCountAtStepStart = 0;
        animOffset = 1.0f;
        prevAnimOffset = 1.0f;
        isExiting = false;
    }

    private static boolean isMinigameStep() {
        return currentStep == TutorialStep.MINIGAME_INTRO
            || currentStep == TutorialStep.MINIGAME_CONTROL
            || currentStep == TutorialStep.MINIGAME_CATCH;
    }

    public static boolean shouldHighlightBook(ItemStack stack) {
        return false;
    }

    // -------------------------------------------------------------------------
    // Overlay rendering
    // -------------------------------------------------------------------------

    /** Called from HUD hooks — renders in-world and minigame steps. */
    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        if (!currentStep.hasOverlay()) return;

        Minecraft mc = Minecraft.getInstance();
        // World-space hint only — bail whenever any screen (encyclopedia, quest log, inventory,
        // pause, ...) is open rather than just PauseScreen, so it doesn't bleed through behind it.
        if (mc.level == null || mc.screen != null) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        renderStepHighlight(graphics, mc, sw, sh);
        renderTextBox(graphics, mc, sw, sh, partialTick);
    }

    /** Inventory guidance only, while loading bait. */
    public static void renderScreenOverlay(GuiGraphicsExtractor graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!isBaitLoadOnContainerScreen(mc)) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // Screen render hooks hand us Screen.render's "partial tick" param, which on this MC version is actually
        // deltaTracker.getGameTimeDeltaTicks() (a frame-to-frame tick delta) — not the 0-1 interpolation fraction
        // our slide animation needs. Pull the real interpolation fraction directly instead.
        float realPartialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        renderTextBox(graphics, mc, sw, sh, realPartialTick);
    }

    private static void renderStepHighlight(GuiGraphicsExtractor graphics, Minecraft mc, int sw, int sh) {
        if (currentStep == TutorialStep.BAIT_LOAD) highlightHotbar(graphics, mc, sw, sh);
    }

    private static void highlightHotbar(GuiGraphicsExtractor graphics, Minecraft mc, int sw, int sh) {
        int hotbarX = sw / 2 - 91;
        int hotbarY = sh - 22;
        int hotbarW = 182;
        int hotbarH = 22;
        int pad = 2;
        int borderColor = 0xFFFFDD44;
        graphics.fill(hotbarX - pad, hotbarY - pad, hotbarX + hotbarW + pad, hotbarY - pad + 2, borderColor);
        graphics.fill(hotbarX - pad, hotbarY + hotbarH + pad - 2, hotbarX + hotbarW + pad, hotbarY + hotbarH + pad, borderColor);
        graphics.fill(hotbarX - pad, hotbarY - pad, hotbarX - pad + 2, hotbarY + hotbarH + pad, borderColor);
        graphics.fill(hotbarX + hotbarW + pad - 2, hotbarY - pad, hotbarX + hotbarW + pad, hotbarY + hotbarH + pad, borderColor);
    }

    private static void renderTextBox(GuiGraphicsExtractor graphics, Minecraft mc, int sw, int sh, float partialTick) {
        Component title = getTitle(currentStep);
        Component body = getBody(currentStep);
        Component hint = getHint(currentStep);
        if (title == null) return;

        // Minigame prompts dock left; inventory prompts dock top-left; world hints dock at the bottom.
        boolean sideLayout = isMinigameStep();
        boolean topLeftLayout = mc.screen != null;

        net.minecraft.client.gui.Font font = mc.font;
        int innerPad = 10;
        // Cap used only to bound word-wrap — the bottom-center and top-left layouts' actual box
        // width shrink-wraps to its widest line below rather than always filling this cap.
        int wrapCap = sideLayout ? Math.min(220, sw / 3) : Math.min(360, sw - 40);
        int lineH = font.lineHeight + 2;
        int maxLineW = wrapCap - innerPad * 2;

        // Pre-split all text so box height and width are computed correctly
        var titleLines = font.split(title.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE), maxLineW);
        var bodyLines = body != null
                ? font.split(body.copy().withStyle(ChatFormatting.GRAY), maxLineW)
                : java.util.List.<net.minecraft.util.FormattedCharSequence>of();
        var hintLines = hint != null
                ? font.split(hint, maxLineW)
                : java.util.List.<net.minecraft.util.FormattedCharSequence>of();

        int boxWidth = wrapCap;
        if (!sideLayout) {
            int contentW = 0;
            for (var line : titleLines) contentW = Math.max(contentW, font.width(line));
            for (var line : bodyLines) contentW = Math.max(contentW, font.width(line));
            for (var line : hintLines) contentW = Math.max(contentW, font.width(line));
            boxWidth = contentW + innerPad * 2;
        }

        int boxH = innerPad * 2 + titleLines.size() * lineH;
        if (!bodyLines.isEmpty()) boxH += 4 + bodyLines.size() * lineH;
        if (!hintLines.isEmpty()) boxH += 4 + hintLines.size() * lineH;

        int boxX = sideLayout || topLeftLayout ? 12 : (sw - boxWidth) / 2;
        int boxY = sideLayout ? (sh - boxH) / 2 : topLeftLayout ? 12 : sh - boxH - BOTTOM_LAYOUT_MARGIN;

        // Slide animation offset — lerp prev→current for sub-tick smoothness, then smoothstep
        float t = prevAnimOffset + (animOffset - prevAnimOffset) * partialTick;
        float eased = t * t * (3 - 2 * t);
        float slideX = sideLayout ? -(boxX + boxWidth) * eased : 0;
        float slideY = sideLayout ? 0 : topLeftLayout ? -(boxY + boxH + 8) * eased : (sh - boxY + 8) * eased;

        graphics.pose().pushMatrix();
        graphics.pose().translate(slideX, slideY);

        // Background
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxH, 0xD0101820);
        // Border
        graphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFF4488CC);
        graphics.fill(boxX, boxY + boxH - 2, boxX + boxWidth, boxY + boxH, 0xFF4488CC);
        graphics.fill(boxX, boxY, boxX + 2, boxY + boxH, 0xFF4488CC);
        graphics.fill(boxX + boxWidth - 2, boxY, boxX + boxWidth, boxY + boxH, 0xFF4488CC);

        int tx = boxX + innerPad;
        int ty = boxY + innerPad;

        // Title (word-wrapped)
        for (net.minecraft.util.FormattedCharSequence line : titleLines) {
            graphics.text(font, line, tx, ty, 0xFFFFFFFF);
            ty += lineH;
        }
        ty += 4;

        // Body (word-wrapped)
        for (net.minecraft.util.FormattedCharSequence line : bodyLines) {
            graphics.text(font, line, tx, ty, 0xFFAAAAAA);
            ty += lineH;
        }

        // Hint (word-wrapped, dimmer)
        if (!hintLines.isEmpty()) {
            ty += 4;
            for (net.minecraft.util.FormattedCharSequence line : hintLines) {
                graphics.text(font, line, tx, ty, 0xFF888888);
                ty += lineH;
            }
        }

        graphics.pose().popMatrix();
    }

    // -------------------------------------------------------------------------
    // Step text definitions — all in one place
    // -------------------------------------------------------------------------

    private static Component getTitle(TutorialStep step) {
        String key = switch (step) {
            case BAIT_LOAD -> "tutorial.fishtastic.bait_load.title";
            case WAITING_FOR_CAST -> "tutorial.fishtastic.waiting_for_cast.title";
            case HOOK_IN_WATER -> "tutorial.fishtastic.hook_in_water.title";
            case MINIGAME_INTRO, MINIGAME_CONTROL, MINIGAME_CATCH -> "tutorial.fishtastic.minigame_catch.title";
            default -> null;
        };
        return key == null ? null : Component.translatable(key);
    }

    private static Component getBody(TutorialStep step) {
        var mc = Minecraft.getInstance();
        return switch (step) {
            case BAIT_LOAD -> switch (baitLoadPhase(mc)) {
                case OPEN_INVENTORY -> Component.translatable("tutorial.fishtastic.bait_load.body_open_inventory",
                        mc.options.keyInventory.getTranslatedKeyMessage());
                case PICK_UP_BAIT -> Component.translatable("tutorial.fishtastic.bait_load.body_pick_up");
                case LOAD_ROD -> Component.translatable("tutorial.fishtastic.bait_load.body_load_rod");
            };
            case WAITING_FOR_CAST -> Component.translatable("tutorial.fishtastic.waiting_for_cast.hint",
                    mc.options.keyUse.getTranslatedKeyMessage());
            case HOOK_IN_WATER -> Component.translatable("tutorial.fishtastic.hook_in_water.body",
                    mc.options.keyUse.getTranslatedKeyMessage());
            case MINIGAME_INTRO, MINIGAME_CONTROL, MINIGAME_CATCH ->
                    Component.translatable("tutorial.fishtastic.minigame_control.body", mc.options.keyUse.getTranslatedKeyMessage());
            default -> null;
        };
    }

    private static Component getHint(TutorialStep step) {
        return null;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static int countRodsWithBaitLoaded(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.getItem() instanceof FishtasticFishingRodItem
                    && !FishtasticFishingRodItem.getBait(stack).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private static void sendAdvance() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        sentAdvanceThisStep = true;
        isExiting = true;
        FishtasticTransport.sendToServer(new TutorialAdvancePacket(currentStep));
    }
}
