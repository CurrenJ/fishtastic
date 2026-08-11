package grill24.fishtastic.client;

import grill24.fishtastic.item.FishtasticFishingRodItem;
import grill24.fishtastic.network.TutorialAdvancePacket;
import grill24.fishtastic.network.TutorialSyncPacket;
import grill24.fishtastic.tutorial.TutorialStep;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    private static int stepTicks = 0;
    private static boolean sentAdvanceThisStep = false;
    private static boolean minigameControlAdvancePending = false;
    // How many rods already had bait loaded when BAIT_LOAD started — a player who already owns
    // an unrelated baited rod (a spare, a shop purchase) must not skip the step instantly; only
    // an increase from this baseline counts as "the player just loaded bait."
    private static int baitLoadedRodCountAtStepStart = 0;

    // Slide animation — 0 = fully shown, 1 = fully hidden (start/end position)
    private static float animOffset = 1.0f;
    private static float prevAnimOffset = 1.0f; // previous tick value for partial-tick lerp
    private static boolean isExiting = false;
    private static final int SLIDE_TICKS = 5;  // ~0.25 s

    // Timing constants
    private static final int MINIGAME_INTRO_AUTO_ADVANCE_TICKS = 140; // 7 seconds
    private static final int CATCH_RESULT_AUTO_ADVANCE_TICKS = 200;  // 10 seconds
    private static final int SHOP_BROWSE_AUTO_ADVANCE_TICKS = 200;   // 15 seconds
    private static final int MINIGAME_CONTROL_MIN_SHOW_TICKS = 60;   // 3 seconds, even if right-click is pressed early

    // Gap between the bottom-center layout box (in-world non-minigame steps) and the screen edge.
    private static final int BOTTOM_LAYOUT_MARGIN = 40;

    // -------------------------------------------------------------------------
    // Packet handler (registered in platform client inits)
    // -------------------------------------------------------------------------

    public static final TutorialSyncPacket.ClientHandler PACKET_HANDLER = packet -> {
        TutorialStep previous = currentStep;
        currentStep = packet.step();
        stepTicks = 0;
        sentAdvanceThisStep = false;
        minigameControlAdvancePending = false;
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
            animation.setPaused(currentStep.pausesMinigame());
        }
    };

    // -------------------------------------------------------------------------
    // Tick — called each client tick from platform client
    // -------------------------------------------------------------------------

    public static void tick() {
        if (currentStep == TutorialStep.COMPLETE) return;
        stepTicks++;

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
            case MINIGAME_INTRO -> {
                if (stepTicks >= MINIGAME_INTRO_AUTO_ADVANCE_TICKS) sendAdvance();
            }
            case MINIGAME_CONTROL -> {
                if (minigameControlAdvancePending && stepTicks >= MINIGAME_CONTROL_MIN_SHOW_TICKS) sendAdvance();
            }
            case CATCH_RESULT -> {
                if (stepTicks >= CATCH_RESULT_AUTO_ADVANCE_TICKS) sendAdvance();
            }
            case SHOP_BROWSE -> {
                if (stepTicks >= SHOP_BROWSE_AUTO_ADVANCE_TICKS) sendAdvance();
            }
            default -> {}
        }
    }

    // -------------------------------------------------------------------------
    // Key-press hooks — called from FishtasticKeyBinds
    // -------------------------------------------------------------------------

    /** Called when the player presses the quest-log key. */
    public static void onQuestLogKeyPressed() {
        if (currentStep == TutorialStep.QUEST_INTRO) {
            sendAdvance();
        }
    }

    /** Called when the player applies an impulse during the minigame. */
    public static void onMinigameImpulse() {
        if (currentStep != TutorialStep.MINIGAME_CONTROL || sentAdvanceThisStep) return;
        if (stepTicks >= MINIGAME_CONTROL_MIN_SHOW_TICKS) {
            sendAdvance();
        } else {
            minigameControlAdvancePending = true;
        }
    }

    /** Called when any key is pressed on screen (for CATCH_RESULT dismiss). */
    public static void onAnyKeyPressed() {
        if (currentStep == TutorialStep.CATCH_RESULT && !sentAdvanceThisStep) {
            sendAdvance();
        }
    }

    public static TutorialStep getCurrentStep() { return currentStep; }

    /** Call on disconnect/world-leave so the overlay doesn't persist into the main menu. */
    public static void reset() {
        currentStep = TutorialStep.COMPLETE;
        stepTicks = 0;
        sentAdvanceThisStep = false;
        minigameControlAdvancePending = false;
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

    private static boolean isScreenStep() {
        // Only steps where the player must interact with an open screen
        // QUEST_INTRO is a passive world hint — it renders via the HUD path
        return currentStep == TutorialStep.QUEST_CLAIM
            || currentStep == TutorialStep.SHOP_BROWSE;
    }

    // -------------------------------------------------------------------------
    // Overlay rendering
    // -------------------------------------------------------------------------

    /** Called from HUD hooks — renders in-world and minigame steps. */
    public static void render(GuiGraphicsExtractor graphics, float partialTick) {
        if (!currentStep.hasOverlay()) return;
        if (isScreenStep()) return;  // rendered on top of screen via renderScreenOverlay()

        Minecraft mc = Minecraft.getInstance();
        // World-space hint only — bail whenever any screen (encyclopedia, quest log, inventory,
        // pause, ...) is open rather than just PauseScreen, so it doesn't bleed through behind it.
        if (mc.level == null || mc.screen != null) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // No dim while the player is free to move/fish — they need to see the world
        if (currentStep != TutorialStep.WAITING_FOR_CAST && currentStep != TutorialStep.HOOK_IN_WATER) {
            renderDarkOverlay(graphics, sw, sh);
        }
        renderStepHighlight(graphics, mc, sw, sh);
        renderTextBox(graphics, mc, sw, sh, partialTick);
    }

    /**
     * Called after the screen renders so the text box appears on top of the quest/shop UI.
     * Gated to {@link QuestLogScreen} specifically — this hook fires after any screen renders,
     * and without the check a step that outlives its screen (player closes the Quest Log before
     * the auto-advance timer fires, then opens something else, e.g. the Fish Encyclopedia) would
     * bleed the box onto whatever they open next.
     */
    public static void renderScreenOverlay(GuiGraphicsExtractor graphics, float partialTick) {
        if (!isScreenStep()) return;

        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof QuestLogScreen)) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // Screen render hooks hand us Screen.render's "partial tick" param, which on this MC version is actually
        // deltaTracker.getGameTimeDeltaTicks() (a frame-to-frame tick delta) — not the 0-1 interpolation fraction
        // our slide animation needs. Pull the real interpolation fraction directly instead.
        float realPartialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        renderTextBox(graphics, mc, sw, sh, realPartialTick);
    }

    private static void renderDarkOverlay(GuiGraphicsExtractor graphics, int sw, int sh) {
        graphics.fill(0, 0, sw, sh, 0xA6000000);
    }

    private static void renderStepHighlight(GuiGraphicsExtractor graphics, Minecraft mc, int sw, int sh) {
        if (currentStep == TutorialStep.BAIT_LOAD) {
            highlightHotbar(graphics, mc, sw, sh);
        }
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

        // Minigame steps dock left/vertically-centered; QUEST_CLAIM and SHOP_BROWSE (rendered via
        // renderScreenOverlay, on top of the Quest Log screen) dock top-left; everything else
        // (in-world non-minigame hints) is bottom-center.
        boolean sideLayout = isMinigameStep();
        boolean topLeftLayout = isScreenStep();

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
            case BAIT_LOAD        -> "tutorial.fishtastic.bait_load.title";
            case WAITING_FOR_CAST -> "tutorial.fishtastic.waiting_for_cast.title";
            case HOOK_IN_WATER    -> "tutorial.fishtastic.hook_in_water.title";
            case MINIGAME_INTRO   -> "tutorial.fishtastic.minigame_intro.title";
            case MINIGAME_CONTROL -> "tutorial.fishtastic.minigame_control.title";
            case MINIGAME_CATCH   -> "tutorial.fishtastic.minigame_catch.title";
            case CATCH_RESULT     -> "tutorial.fishtastic.catch_result.title";
            case QUEST_INTRO      -> "tutorial.fishtastic.quest_intro.title";
            case QUEST_CLAIM      -> "tutorial.fishtastic.quest_claim.title";
            case SHOP_BROWSE      -> "tutorial.fishtastic.shop_browse.title";
            default               -> null;
        };
        return key != null ? Component.translatable(key) : null;
    }

    private static Component getBody(TutorialStep step) {
        return switch (step) {
            case BAIT_LOAD        -> Component.translatable("tutorial.fishtastic.bait_load.body");
            case WAITING_FOR_CAST -> null;
            case HOOK_IN_WATER    -> Component.translatable("tutorial.fishtastic.hook_in_water.body");
            case MINIGAME_INTRO   -> Component.translatable("tutorial.fishtastic.minigame_intro.body");
            case MINIGAME_CONTROL -> Component.translatable("tutorial.fishtastic.minigame_control.body");
            case MINIGAME_CATCH   -> null; // title alone ("Keep the overlap going!") already says it; body would just repeat it
            case CATCH_RESULT     -> Component.translatable("tutorial.fishtastic.catch_result.body",
                    FishtasticKeyBinds.openFishEncyclopedia.getTranslatedKeyMessage());
            case QUEST_INTRO      -> Component.translatable("tutorial.fishtastic.quest_intro.body",
                    FishtasticKeyBinds.openQuestLog.getTranslatedKeyMessage());
            case QUEST_CLAIM      -> Component.translatable("tutorial.fishtastic.quest_claim.body");
            case SHOP_BROWSE      -> Component.translatable("tutorial.fishtastic.shop_browse.body");
            default               -> null;
        };
    }

    private static Component getHint(TutorialStep step) {
        return switch (step) {
            case BAIT_LOAD        -> null;
            case WAITING_FOR_CAST -> Component.translatable("tutorial.fishtastic.waiting_for_cast.hint");
            case HOOK_IN_WATER    -> null; // body ("Wait for a bite — then reel in!") already says it
            case MINIGAME_INTRO   -> Component.translatable("tutorial.fishtastic.minigame_intro.hint");
            case MINIGAME_CONTROL -> FishtasticKeyBinds.fishingMinigameImpulse.isUnbound()
                    ? Component.translatable("tutorial.fishtastic.minigame_control.hint")
                    : Component.translatable("tutorial.fishtastic.minigame_control.hint_with_key",
                            FishtasticKeyBinds.fishingMinigameImpulse.getTranslatedKeyMessage());
            case MINIGAME_CATCH   -> Component.translatable("tutorial.fishtastic.minigame_catch.hint");
            case CATCH_RESULT     -> null; // encyclopedia key mention moved into the body
            case QUEST_INTRO      -> Component.translatable("tutorial.fishtastic.quest_intro.hint",
                    FishtasticKeyBinds.openQuestLog.getTranslatedKeyMessage());
            case QUEST_CLAIM      -> Component.translatable("tutorial.fishtastic.quest_claim.hint");
            case SHOP_BROWSE      -> Component.translatable("tutorial.fishtastic.shop_browse.hint");
            default               -> null;
        };
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
        mc.player.connection.send(new ServerboundCustomPayloadPacket(new TutorialAdvancePacket(currentStep)));
    }
}
