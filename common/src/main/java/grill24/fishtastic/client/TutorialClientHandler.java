package grill24.fishtastic.client;

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
import net.minecraft.client.gui.GuiGraphics;
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
            if ((currentStep == TutorialStep.WAITING_FOR_CAST || !currentStep.hasOverlay())
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
            case MINIGAME_CONTROL -> {
                if (minigameControlAdvancePending && stepTicks >= MINIGAME_CONTROL_MIN_SHOW_TICKS) sendAdvance();
            }
            case FISHING_OFFER, ENCYCLOPEDIA_OFFER -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen == null && mc.player != null && !mc.player.isDeadOrDying()) {
                    mc.setScreen(new net.minecraft.client.gui.screens.ConfirmScreen(show -> {
                        sentAdvanceThisStep = true;
                        mc.player.connection.send(new ServerboundCustomPayloadPacket(
                                new TutorialAdvancePacket(currentStep, show)));
                        mc.setScreen(null);
                    }, Component.translatable((currentStep == TutorialStep.FISHING_OFFER ? "tutorial.fishtastic.fishing_offer.title" : "tutorial.fishtastic.encyclopedia_offer.title")),
                            Component.translatable((currentStep == TutorialStep.FISHING_OFFER ? "tutorial.fishtastic.fishing_offer.body" : "tutorial.fishtastic.encyclopedia_offer.body")),
                            Component.translatable((currentStep == TutorialStep.FISHING_OFFER ? "tutorial.fishtastic.fishing_offer.show" : "tutorial.fishtastic.encyclopedia_offer.show")),
                            Component.translatable((currentStep == TutorialStep.FISHING_OFFER ? "tutorial.fishtastic.fishing_offer.later" : "tutorial.fishtastic.encyclopedia_offer.later"))) {
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

    /** The shop lesson is satisfied by visiting the tab, without requiring a purchase. */
    public static void onShopVisited() {
        if (currentStep == TutorialStep.SHOP_BROWSE && !sentAdvanceThisStep) sendAdvance();
    }

    /** Called when the player applies an impulse during the minigame. */
    public static void onMinigameImpulse() {
        if (currentStep == TutorialStep.MINIGAME_INTRO && !sentAdvanceThisStep) {
            sendAdvance();
            return;
        }
        if (currentStep != TutorialStep.MINIGAME_CONTROL || sentAdvanceThisStep) return;
        if (stepTicks >= MINIGAME_CONTROL_MIN_SHOW_TICKS) {
            sendAdvance();
        } else {
            minigameControlAdvancePending = true;
        }
    }

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
        // These also render a world reminder when the player closes the book.
        return currentStep == TutorialStep.QUEST_CLAIM
            || currentStep == TutorialStep.SHOP_BROWSE;
    }

    public static boolean shouldHighlightBook(ItemStack stack) {
        return (currentStep == TutorialStep.CATCH_RESULT || currentStep == TutorialStep.QUEST_INTRO
                || currentStep == TutorialStep.QUEST_CLAIM || currentStep == TutorialStep.SHOP_BROWSE)
                && stack.is(FishtasticItems.QUEST_BOOK.value())
                || currentStep == TutorialStep.ENCYCLOPEDIA_OPEN && stack.is(FishtasticItems.FISHOPEDIA.value());
    }

    public static Component questGuideTitle() {
        return isScreenStep() || currentStep == TutorialStep.ENCYCLOPEDIA_OFFER ? getTitle(currentStep) : null;
    }

    public static Component questGuideBody() {
        Component body = questGuideTitle() == null ? null : getBody(currentStep);
        Component hint = getHint(currentStep);
        return body == null || hint == null ? body : body.copy().append(" ").append(hint);
    }

    // -------------------------------------------------------------------------
    // Overlay rendering
    // -------------------------------------------------------------------------

    /** Called from HUD hooks — renders in-world and minigame steps. */
    public static void render(GuiGraphics graphics, float partialTick) {
        if (!currentStep.hasOverlay()) return;

        Minecraft mc = Minecraft.getInstance();
        // World-space hint only — bail whenever any screen (encyclopedia, quest log, inventory,
        // pause, ...) is open rather than just PauseScreen, so it doesn't bleed through behind it.
        if (mc.level == null || mc.screen != null) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // No dim while the player is free to move/fish — they need to see the world
        if (currentStep == TutorialStep.MINIGAME_INTRO) {
            renderDarkOverlay(graphics, sw, sh);
        }
        renderStepHighlight(graphics, mc, sw, sh);
        renderTextBox(graphics, mc, sw, sh, partialTick);
    }

    /** Inventory guidance only; the Quest Log includes its guide in its own layout. */
    public static void renderScreenOverlay(GuiGraphics graphics, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof QuestLogScreen) return; // Its guide participates in the screen layout.
        // Item outlines are drawn by the slots themselves.
        boolean onInventory = isBaitLoadOnContainerScreen(mc)
                || isBookStep() && (mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen);
        if (!onInventory) return;
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // Screen render hooks hand us Screen.render's "partial tick" param, which on this MC version is actually
        // deltaTracker.getGameTimeDeltaTicks() (a frame-to-frame tick delta) — not the 0-1 interpolation fraction
        // our slide animation needs. Pull the real interpolation fraction directly instead.
        float realPartialTick = mc.getTimer().getGameTimeDeltaPartialTick(false);
        renderTextBox(graphics, mc, sw, sh, realPartialTick);
    }

    private static void renderDarkOverlay(GuiGraphics graphics, int sw, int sh) {
        graphics.fill(0, 0, sw, sh, 0xA6000000);
    }

    private static void renderStepHighlight(GuiGraphics graphics, Minecraft mc, int sw, int sh) {
        if (currentStep == TutorialStep.BAIT_LOAD) {
            highlightHotbar(graphics, mc, sw, sh);
        }
        if (mc.player != null && isBookStep()) {
            int hotbarX = sw / 2 - 91;
            int y = sh - 23;
            for (int i = 0; i < 9; i++) {
                if (!shouldHighlightBook(mc.player.getInventory().getItem(i))) continue;
                int x = hotbarX + i * 20;
                graphics.fill(x, y, x + 22, y + 2, 0xFFFFDD44);
                graphics.fill(x, y + 22, x + 22, y + 24, 0xFFFFDD44);
                graphics.fill(x, y, x + 2, y + 24, 0xFFFFDD44);
                graphics.fill(x + 20, y, x + 22, y + 24, 0xFFFFDD44);
            }
        }
    }

    private static boolean isBookStep() {
        return currentStep == TutorialStep.CATCH_RESULT || currentStep == TutorialStep.QUEST_INTRO
                || isScreenStep() || currentStep == TutorialStep.ENCYCLOPEDIA_OPEN;
    }

    private static void highlightHotbar(GuiGraphics graphics, Minecraft mc, int sw, int sh) {
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

    private static void renderTextBox(GuiGraphics graphics, Minecraft mc, int sw, int sh, float partialTick) {
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

        graphics.pose().pushPose();
        graphics.pose().translate(slideX, slideY, 0);

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
            graphics.drawString(font, line, tx, ty, 0xFFFFFFFF);
            ty += lineH;
        }
        ty += 4;

        // Body (word-wrapped)
        for (net.minecraft.util.FormattedCharSequence line : bodyLines) {
            graphics.drawString(font, line, tx, ty, 0xFFAAAAAA);
            ty += lineH;
        }

        // Hint (word-wrapped, dimmer)
        if (!hintLines.isEmpty()) {
            ty += 4;
            for (net.minecraft.util.FormattedCharSequence line : hintLines) {
                graphics.drawString(font, line, tx, ty, 0xFF888888);
                ty += lineH;
            }
        }

        graphics.pose().popPose();
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
            case ENCYCLOPEDIA_OFFER -> "tutorial.fishtastic.encyclopedia_offer.title";
            case ENCYCLOPEDIA_OPEN -> "tutorial.fishtastic.encyclopedia_open.title";
            default               -> null;
        };
        return key != null ? Component.translatable(key) : null;
    }

    private static Component getBody(TutorialStep step) {
        return switch (step) {
            case BAIT_LOAD        -> switch (baitLoadPhase(Minecraft.getInstance())) {
                case OPEN_INVENTORY -> Component.translatable("tutorial.fishtastic.bait_load.body_open_inventory",
                        Minecraft.getInstance().options.keyInventory.getTranslatedKeyMessage());
                case PICK_UP_BAIT   -> Component.translatable("tutorial.fishtastic.bait_load.body_pick_up");
                case LOAD_ROD       -> Component.translatable("tutorial.fishtastic.bait_load.body_load_rod");
            };
            case WAITING_FOR_CAST -> Component.translatable("tutorial.fishtastic.waiting_for_cast.body");
            case HOOK_IN_WATER    -> Component.translatable("tutorial.fishtastic.hook_in_water.body",
                    Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage());
            case MINIGAME_INTRO   -> Component.translatable("tutorial.fishtastic.minigame_intro.body");
            case MINIGAME_CONTROL -> Component.translatable("tutorial.fishtastic.minigame_control.body",
                    Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage());
            case MINIGAME_CATCH   -> null; // title alone ("Keep the overlap going!") already says it; body would just repeat it
            case CATCH_RESULT, QUEST_INTRO -> bookInstruction("tutorial.fishtastic.catch_result.body");
            case QUEST_CLAIM -> Minecraft.getInstance().screen instanceof QuestLogScreen
                    ? Component.translatable("tutorial.fishtastic.quest_claim.body")
                    : bookInstruction("tutorial.fishtastic.reopen_quest_book");
            case SHOP_BROWSE -> Minecraft.getInstance().screen instanceof QuestLogScreen
                    ? Component.translatable("tutorial.fishtastic.shop_browse.body")
                    : bookInstruction("tutorial.fishtastic.reopen_quest_book");
            case ENCYCLOPEDIA_OFFER -> Component.translatable("tutorial.fishtastic.encyclopedia_offer.close_book");
            case ENCYCLOPEDIA_OPEN -> bookInstruction("tutorial.fishtastic.encyclopedia_open.body");
            default               -> null;
        };
    }

    private static Component getHint(TutorialStep step) {
        return switch (step) {
            case BAIT_LOAD        -> null;
            case WAITING_FOR_CAST -> Component.translatable("tutorial.fishtastic.waiting_for_cast.hint",
                    Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage());
            case HOOK_IN_WATER    -> null; // body ("Wait for a bite — then reel in!") already says it
            case MINIGAME_INTRO, MINIGAME_CONTROL -> Component.translatable("tutorial.fishtastic.minigame_control.hint",
                    Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage());
            case MINIGAME_CATCH   -> Component.translatable("tutorial.fishtastic.minigame_catch.hint");
            case CATCH_RESULT     -> null;
            case QUEST_INTRO -> null;
            case QUEST_CLAIM -> Minecraft.getInstance().screen instanceof QuestLogScreen
                    ? Component.translatable("tutorial.fishtastic.quest_claim.hint") : null;
            case SHOP_BROWSE -> Minecraft.getInstance().screen instanceof QuestLogScreen
                    ? Component.translatable("tutorial.fishtastic.shop_browse.hint") : null;
            default               -> null;
        };
    }

    private static Component bookInstruction(String key) {
        return Component.translatable(key, Minecraft.getInstance().options.keyUse.getTranslatedKeyMessage(),
                Minecraft.getInstance().options.keyInventory.getTranslatedKeyMessage());
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static int countRodsWithBaitLoaded(Player player) {
        int count = 0;
        // 1.21.1's Inventory container is exactly the 26.1 getNonEquipmentItems() list.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
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
