package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticSounds;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestDifficulty;
import grill24.fishtastic.util.MathUtil;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.components.SpriteProgressBar;
import io.github.currenj.gelatinui.gui.minecraft.MinecraftRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;

import java.awt.geom.Rectangle2D;
import java.util.Map;

/**
 * Manages the lifecycle and rendering of a single quest-progress notification banner
 * that slides into the top-right corner of the screen.
 */
public class QuestProgressNotification {

    enum Phase { SLIDE_IN, HOLD, SLIDE_OUT, DONE }

    // Layout
    private static final float SCALE = 0.5f;
    private static final int PADDING = 8;
    static final int MARGIN = 10;
    /** Vertical gap between stacked banners, applied by {@link QuestProgressNotificationManager}. */
    static final int STACK_GAP = 6;
    private static final int ROW_SPACING = 4;
    private static final int BAR_COUNT_GAP = 6;
    private static final int ITEM_SIZE = 16;
    private static final int ITEM_TEXT_GAP = 4;
    // Fixed-width background variants, picked by content width so the panel is never
    // stretched non-uniformly (which distorts the rounded corners/border). Each variant's
    // opaque artwork is PANEL_BG_TEXTURE_CONTENT_HEIGHT rows tall regardless of its content
    // width or file canvas size — only width differs between variants. Must stay ordered
    // ascending by contentWidth — selection below picks the first one wide enough.
    private record BackgroundVariant(Identifier texture, int fileSize, int contentWidth) {}

    private static final int PANEL_BG_TEXTURE_CONTENT_HEIGHT = 22;

    // One variant set per QuestDifficulty tier, same 5 sizes each, only the texture
    // differs (filename suffixed with the tier, e.g. "_silver"/"_gold"). BRONZE reuses the
    // original unsuffixed files. Keyed by ordinal via EnumMap-style array indexing.
    private static BackgroundVariant[] variantsForTier(String suffix) {
        return new BackgroundVariant[] {
                new BackgroundVariant(Fishtastic.id("textures/gui/notification_banner_tiny" + suffix + ".png"), 64, 48),
                new BackgroundVariant(Fishtastic.id("textures/gui/notification_banner_small" + suffix + ".png"), 64, 64),
                new BackgroundVariant(Fishtastic.id("textures/gui/notification_banner_smedium" + suffix + ".png"), 128, 80),
                new BackgroundVariant(Fishtastic.id("textures/gui/notification_banner_medium" + suffix + ".png"), 128, 96),
                new BackgroundVariant(Fishtastic.id("textures/gui/notification_banner_large" + suffix + ".png"), 128, 128),
        };
    }

    private static final Map<QuestDifficulty, BackgroundVariant[]> PANEL_BG_VARIANTS_BY_DIFFICULTY = Map.of(
            QuestDifficulty.BRONZE, variantsForTier(""),
            QuestDifficulty.SILVER, variantsForTier("_silver"),
            QuestDifficulty.GOLD, variantsForTier("_gold")
    );

    // Animation durations (ticks)
    private static final int SLIDE_IN_DURATION = 15;
    private static final int HOLD_DURATION_NORMAL = 60;
    private static final int HOLD_DURATION_COMPLETE = 80;
    private static final int SLIDE_OUT_DURATION = 15;

    // Minimum gap enforced between successive plays of the new-species fanfare, so catching
    // several new fish in quick succession (e.g. one sync packet triggering several first-catch
    // banners at once) doesn't stack the sound on top of itself. Tracked globally (static) since
    // it applies across all banner instances, not per-banner; delay is always clamped to this
    // window so a stale value left over from a previous world (game time isn't continuous across
    // world switches) can never cause an unexpectedly long wait — see scheduleActivationSound().
    private static final int NEW_SPECIES_SOUND_COOLDOWN_TICKS = 15;
    private static long nextNewSpeciesSoundTick = Long.MIN_VALUE;
    // Ticks left before a delayed activation sound plays; 0 = none pending. Counted down in
    // tick() independent of phase, since it's set once SLIDE_IN completes (phase is HOLD by then).
    private int pendingSoundDelayTicks;

    private Phase phase = Phase.SLIDE_IN;
    private int tickCounter;
    private final QuestProgressEvent event;
    private final NotificationPriority priority;
    private final String displayName;
    private final int targetCount;
    private final ItemStack targetItem;
    // False for one-shot announcement banners (out-of-bait, first-catch) that have no
    // meaningful progress/count to show — only real quests and the cleanup-goal milestone
    // (which does track a real running total against a threshold) render the bar/badge.
    private final boolean showProgress;
    private int panelWidth;
    private final int panelHeight;
    private final BackgroundVariant bgVariant;
    private float previousDisplayX; // for partial-tick interpolation
    private float displayX;
    // Vertical slot position, eased toward targetY rather than snapped — when a banner
    // above this one finishes and is removed, the remaining stack should slide smoothly
    // up into place instead of popping to the new position. See setTargetY().
    private float previousDisplayY;
    private float displayY;
    private float targetY;
    private boolean yInitialized;
    private static final float Y_EASE_SPEED = 0.25f; // fraction of remaining distance closed per tick
    private final SpriteProgressBar progressBar;
    private float barTargetFraction;
    private float displayedBarFraction; // manually interpolated — bar's built-in animation has lifecycle issues standalone
    private boolean soundPlayed;
    private int completeFlashTimer;
    private boolean barAnimationStarted;
    private static final int COMPLETE_FLASH_DURATION = 12; // ticks for green pulse
    // Ticks to wait before beginning SLIDE_IN, so several notifications activated in the
    // same burst don't all animate in on top of each other. Set by the manager via
    // setStartDelay() at activation time; counts down to 0 before the phase machine runs.
    private int startDelay;

    private static final Rectangle2D FULL_VIEWPORT = new Rectangle2D.Float(0, 0, 4096, 4096);

    public QuestProgressNotification(QuestProgressEvent event) {
        this.event = event;
        this.priority = NotificationPriority.classify(event);

        // Resolve quest display name and target count
        Minecraft mc = Minecraft.getInstance();
        boolean isSyntheticBanner = true;
        boolean showProgress = true;
        String name;
        if (event.questId().equals(QuestProgressNotificationManager.CLEANUP_GOAL_MILESTONE_ID)) {
            name = "Clean Up the Waters";
        } else if (event.questId().equals(QuestProgressNotificationManager.OUT_OF_BAIT_ID)) {
            name = "Out of Bait!";
            showProgress = false;
        } else if (event.questId().getPath().startsWith(QuestProgressNotificationManager.FIRST_CATCH_ID_PREFIX)) {
            name = event.triggeringItem().isEmpty()
                    ? "New Encyclopedia Entry!"
                    : event.triggeringItem().getHoverName().getString() + " Discovered!";
            showProgress = false;
        } else {
            name = event.questId().getPath(); // fallback
            isSyntheticBanner = false;
        }
        this.showProgress = showProgress;
        int tgt = event.targetCount();
        // Synthetic banners (cleanup goal, out-of-bait, first-catch) have no backing Quest
        // to read a difficulty off, so they always render at the base BRONZE tier.
        QuestDifficulty difficulty = QuestDifficulty.BRONZE;
        try {
            if (!isSyntheticBanner && mc.level != null) {
                Registry<Quest> questRegistry = mc.level.registryAccess()
                        .lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
                ResourceKey<Quest> questKey = ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, event.questId());
                Quest quest = questRegistry.getOptional(questKey).orElse(null);
                if (quest != null) {
                    name = quest.displayName().isEmpty() ? event.questId().getPath() : quest.displayName();
                    tgt = quest.objective().effectiveTargetCount(mc.level.registryAccess());
                    difficulty = quest.difficulty();
                }
            }
        } catch (Exception ignored) {
            // Fall back to quest ID path and targetCount from event
        }
        this.displayName = name;
        this.targetCount = tgt;
        this.targetItem = event.triggeringItem();
        boolean hasItem = !targetItem.isEmpty();

        // Compute panel width from content (unscaled — scale applied in render)
        int barWidth = SpriteProgressBar.DEFAULT_WIDTH;
        String countText = event.newCount() + " / " + tgt;
        int countTextWidth = mc.font.width(countText);
        int barRowWidth = barWidth + BAR_COUNT_GAP + countTextWidth;
        int nameWidth = mc.font.width(name);
        int completeWidth = (event.completed() && showProgress) ? mc.font.width("Complete!") + 4 : 0;
        int textRowWidth = nameWidth + completeWidth;
        if (hasItem) textRowWidth += ITEM_SIZE + ITEM_TEXT_GAP;
        int desiredWidth = PADDING + (showProgress ? Math.max(textRowWidth, barRowWidth) : textRowWidth) + PADDING;

        // panelHeight is constant (independent of content), which fixes a single
        // uniform scale factor for the background art. Pick the narrowest background
        // variant that's still wide enough at that scale, so the art is never
        // stretched unevenly on x vs y. Falls back to the largest variant (with a
        // small residual stretch) only if content is wider than every variant covers.
        int fontHeight = mc.font.lineHeight;
        this.panelHeight = showProgress
                ? PADDING + fontHeight + ROW_SPACING + SpriteProgressBar.DEFAULT_HEIGHT + PADDING
                : PADDING + fontHeight + PADDING;
        float bgScale = (float) panelHeight / PANEL_BG_TEXTURE_CONTENT_HEIGHT;

        BackgroundVariant[] tierVariants = PANEL_BG_VARIANTS_BY_DIFFICULTY.get(difficulty);
        BackgroundVariant chosen = tierVariants[tierVariants.length - 1];
        for (BackgroundVariant variant : tierVariants) {
            if (Math.round(variant.contentWidth() * bgScale) >= desiredWidth) {
                chosen = variant;
                break;
            }
        }
        this.bgVariant = chosen;
        this.panelWidth = Math.max(desiredWidth, Math.round(chosen.contentWidth() * bgScale));
        int visualPanelWidth = (int) (panelWidth * SCALE);

        // Create progress bar — start at old fraction, manual lerp to target during HOLD
        this.progressBar = UI.progressBar();
        float oldFraction = targetCount > 0 ? (float) event.oldCount() / targetCount : 0f;
        this.barTargetFraction = targetCount > 0 ? Math.min(1f, (float) event.newCount() / targetCount) : 0f;
        this.displayedBarFraction = oldFraction;
        this.progressBar.progressImmediate(oldFraction);

        // Initial X position: off-screen to the right (use visual/scaled width)
        this.displayX = mc.getWindow().getGuiScaledWidth() + visualPanelWidth;
        this.previousDisplayX = this.displayX;
    }

    // ---- Lifecycle ----

    /**
     * Advances this banner by {@code steps} virtual ticks, called once per real client tick.
     * {@code steps} is normally 1, but the manager batches more when the notification system
     * is running fast-forwarded to clear a long chain of banners (see
     * {@link QuestProgressNotificationManager}) — every duration constant below stays fixed in
     * virtual-tick units, so a caller running several steps per real tick simply compresses the
     * whole animation/hold/sound timeline proportionally. Position is only saved for partial-tick
     * interpolation once, before the batch, so motion across several steps still renders smoothly.
     */
    public void tick(int steps) {
        previousDisplayX = displayX;
        previousDisplayY = displayY;
        for (int i = 0; i < steps; i++) {
            stepOnce();
        }
    }

    private void stepOnce() {
        Minecraft mc = Minecraft.getInstance();
        float screenWidth = mc.getWindow().getGuiScaledWidth();

        displayY += (targetY - displayY) * Y_EASE_SPEED;
        if (Math.abs(targetY - displayY) < 0.05f) {
            displayY = targetY;
        }

        if (startDelay > 0) {
            startDelay--;
            return;
        }

        tickCounter++;

        switch (phase) {
            case SLIDE_IN -> {
                float visualW = panelWidth * SCALE;
                float progress = MathUtil.clamp((float) tickCounter / SLIDE_IN_DURATION, 0f, 1f);
                float targetX = screenWidth - visualW - MARGIN;
                float startX = screenWidth + visualW;
                displayX = MathUtil.easedLerp(startX, targetX, progress, MathUtil::easeOutCubic);

                if (tickCounter >= SLIDE_IN_DURATION) {
                    phase = Phase.HOLD;
                    tickCounter = 0;
                    scheduleActivationSound(mc);
                    soundPlayed = true;
                    // Kick off bar fill animation now that the banner is fully visible
                    barAnimationStarted = true;
                }
            }
            case HOLD -> {
                int maxHold = event.completed() ? HOLD_DURATION_COMPLETE : HOLD_DURATION_NORMAL;
                if (tickCounter >= maxHold) {
                    phase = Phase.SLIDE_OUT;
                    tickCounter = 0;
                }
                // Animate the completion flash
                if (event.completed() && completeFlashTimer < COMPLETE_FLASH_DURATION) {
                    completeFlashTimer++;
                }
            }
            case SLIDE_OUT -> {
                float visualW = panelWidth * SCALE;
                float progress = MathUtil.clamp((float) tickCounter / SLIDE_OUT_DURATION, 0f, 1f);
                // Ease-in quadratic: starts slow, accelerates off-screen
                float eased = progress * progress;
                float targetX = screenWidth + visualW;
                float startX = screenWidth - visualW - MARGIN;
                displayX = MathUtil.lerp(startX, targetX, eased);

                if (tickCounter >= SLIDE_OUT_DURATION) {
                    phase = Phase.DONE;
                }
            }
        }

        // Count down and fire a sound whose play was pushed back by scheduleActivationSound()
        // to respect NEW_SPECIES_SOUND_COOLDOWN_TICKS. Independent of phase — set once SLIDE_IN
        // completes above, so this always runs during HOLD (or later, if HOLD is very short).
        if (pendingSoundDelayTicks > 0 && --pendingSoundDelayTicks == 0) {
            playSound(mc, activationSound());
        }

        // Manually interpolate bar fill — SpriteProgressBar's built-in animation has
        // lifecycle issues outside a full GelatinUI tree (needsUpdate guard blocks it).
        if (barAnimationStarted) {
            displayedBarFraction += (barTargetFraction - displayedBarFraction) * 5f / 20f;
            if (Math.abs(barTargetFraction - displayedBarFraction) < 0.005f) {
                displayedBarFraction = barTargetFraction;
            }
            progressBar.progressImmediate(displayedBarFraction);
        }
    }

    /** Called when the same quest progresses again while already visible. */
    public void updateProgress(QuestProgressEvent newEvent) {
        // Animate from current displayed fraction to the new target
        barTargetFraction = targetCount > 0 ? Math.min(1f, (float) newEvent.newCount() / targetCount) : 0f;
        barAnimationStarted = true;

        // Reset hold timer
        tickCounter = 0;

        // If new event completes the quest, trigger completion sound + flash
        if (newEvent.completed() && !event.completed()) {
            playSound(Minecraft.getInstance(), completionSound(NotificationPriority.classify(newEvent)));
            completeFlashTimer = 0;
        }
    }

    /** Ticks to wait before this notification begins its SLIDE_IN animation. */
    void setStartDelay(int startDelay) {
        this.startDelay = startDelay;
    }

    /** This banner's on-screen height (post-SCALE), used by the manager to stack banners vertically. */
    int getScaledHeight() {
        return Math.round(panelHeight * SCALE);
    }

    /**
     * Assigns this banner's vertical slot. The first call snaps immediately (a freshly
     * activated banner should appear in the right row right away); later calls just move
     * the easing target, so a shift caused by another banner above leaving the stack
     * animates smoothly instead of snapping.
     */
    void setTargetY(int y) {
        if (!yInitialized) {
            displayY = y;
            previousDisplayY = y;
            yInitialized = true;
        }
        targetY = y;
    }

    // ---- Render ----

    public void render(Minecraft mc, GuiGraphicsExtractor graphics, float partialTick) {
        if (phase == Phase.DONE) return;

        int fontHeight = mc.font.lineHeight;
        int barHeight = (int) progressBar.getSize().y;

        // Interpolate X/Y for smooth motion using partial tick between last two tick values
        int panelX = (int) MathUtil.lerp(previousDisplayX, displayX, partialTick);
        int panelY = (int) MathUtil.lerp(previousDisplayY, displayY, partialTick);

        // Apply uniform scale to the entire notification
        var pose = graphics.pose();
        pose.pushMatrix();
        pose.translate(panelX, panelY);
        pose.scale(SCALE, SCALE);

        // Background panel (local coords: 0,0 = panel top-left). bgVariant was chosen in the
        // constructor so that its scaled width already matches panelWidth — no nine-slicing
        // needed, and x/y scale stay equal so the art is never stretched unevenly.
        graphics.blit(RenderPipelines.GUI_TEXTURED, bgVariant.texture(), 0, 0, 0f, 0f,
                panelWidth, panelHeight, bgVariant.contentWidth(), PANEL_BG_TEXTURE_CONTENT_HEIGHT,
                bgVariant.fileSize(), bgVariant.fileSize());

        MinecraftRenderContext ctx = new MinecraftRenderContext(graphics, mc.font);

        // Triggering item icon
        int textX = PADDING;
        int textY = PADDING;
        if (!targetItem.isEmpty()) {
            // Use fakeItem directly — fishtastic$renderFakeItem applies an internal
            // scale(1/16) for model-space rendering, which at the notification's
            // outer 0.5 scale makes the item invisible.
            int iconX = PADDING;
            int iconY = PADDING + (fontHeight - ITEM_SIZE) / 2;
            graphics.fakeItem(targetItem, iconX, iconY);
            textX = PADDING + ITEM_SIZE + ITEM_TEXT_GAP;
        }

        // Quest name
        graphics.text(mc.font, displayName, textX, textY, 0xFFFFFFFF, false);

        // "Complete!" badge
        if (event.completed() && showProgress) {
            float flashScale = 1.0f;
            if (completeFlashTimer < COMPLETE_FLASH_DURATION) {
                float t = (float) completeFlashTimer / COMPLETE_FLASH_DURATION;
                flashScale = 1.0f + 0.15f * (float) Math.sin(t * Math.PI);
            }
            String completeText = "Complete!";
            int completeWidth = mc.font.width(completeText);
            int completeX = panelWidth - PADDING - completeWidth;
            pose.pushMatrix();
            pose.translate(completeX + completeWidth / 2f, textY + fontHeight / 2f);
            pose.scale(flashScale, flashScale);
            graphics.text(mc.font, completeText,
                    (int) (-completeWidth / 2f), (int) (-fontHeight / 2f),
                    0xFF55FF55, false);
            pose.popMatrix();
        }

        // Progress bar + count label
        if (showProgress) {
            int barX = PADDING;
            int barY = PADDING + fontHeight + ROW_SPACING;
            progressBar.setPosition(new Vector2f(barX, barY));
            progressBar.render(ctx, FULL_VIEWPORT);

            String countText = event.newCount() + " / " + targetCount;
            int countColor = event.completed() ? 0xFFFFFFFF : 0xFFAAAAAA;
            int barWidth = (int) progressBar.getSize().x;
            int countX = barX + barWidth + BAR_COUNT_GAP;
            int countY = barY + (barHeight - fontHeight) / 2;
            graphics.text(mc.font, countText, countX, countY, countColor, false);
        }

        pose.popMatrix();
    }

    // ---- Accessors ----

    public boolean isDone() { return phase == Phase.DONE; }
    public Identifier questId() { return event.questId(); }
    public QuestProgressEvent event() { return event; }
    public Phase phase() { return phase; }

    // ---- Helpers ----

    /**
     * Sound played once, when this banner finishes sliding in. Picks the tier-specific
     * progress sound for real quests still in progress, the tier-specific completion sound
     * for quests that are already complete on first display (e.g. the final increment also
     * completed it), the dedicated fanfare for new-species banners, and the generic
     * fallback for other one-shot announcements (out-of-bait, cleanup-goal milestone).
     */
    /**
     * Plays the activation sound, unless it's the new-species fanfare and another one played
     * (or was itself scheduled) too recently — in that case, pushes this one back just far
     * enough to land NEW_SPECIES_SOUND_COOLDOWN_TICKS after the last one, via pendingSoundDelayTicks.
     * The computed delay is always clamped to that same window, so a stale nextNewSpeciesSoundTick
     * left over from a previous world (game time resets/differs per world) can't cause a long wait.
     * Timed off the manager's virtual clock rather than real game time, so the cooldown compresses
     * along with the rest of the animation timeline when the notification system fast-forwards
     * through a long chain — pendingSoundDelayTicks is itself counted down once per virtual tick
     * in stepOnce(), so both sides of this math need to live in the same (virtual) tick unit.
     */
    private void scheduleActivationSound(Minecraft mc) {
        if (priority != NotificationPriority.NEW_SPECIES) {
            playSound(mc, activationSound());
            return;
        }

        // The catch celebration announces a discovery at its reveal, seconds before this banner
        // arrives. Play it once, at the moment it lands — not again when the banner catches up.
        // The banner itself still shows; only the duplicated fanfare is dropped, and the cooldown
        // below is deliberately left untouched since no sound was spent.
        if (QuestProgressNotificationManager.consumeDiscoveryFanfareClaim(questId())) {
            return;
        }

        long now = QuestProgressNotificationManager.getVirtualGameTime();
        int delay = (int) Math.max(0, Math.min(NEW_SPECIES_SOUND_COOLDOWN_TICKS, nextNewSpeciesSoundTick - now));
        nextNewSpeciesSoundTick = now + delay + NEW_SPECIES_SOUND_COOLDOWN_TICKS;

        if (delay <= 0) {
            playSound(mc, activationSound());
        } else {
            pendingSoundDelayTicks = delay;
        }
    }

    private SoundEvent activationSound() {
        return switch (priority) {
            case NEW_SPECIES -> FishtasticSounds.NEW_SPECIES_DISCOVERED.value();
            case OTHER -> FishtasticSounds.QUEST_PROGRESS.value();
            case COMPLETION_BRONZE, COMPLETION_SILVER, COMPLETION_GOLD -> completionSound(priority);
            case PROGRESS_BRONZE -> FishtasticSounds.QUEST_PROGRESS_BRONZE.value();
            case PROGRESS_SILVER -> FishtasticSounds.QUEST_PROGRESS_SILVER.value();
            case PROGRESS_GOLD -> FishtasticSounds.QUEST_PROGRESS_GOLD.value();
        };
    }

    private static SoundEvent completionSound(NotificationPriority completionTier) {
        return switch (completionTier) {
            case COMPLETION_SILVER -> FishtasticSounds.QUEST_COMPLETE_SILVER.value();
            case COMPLETION_GOLD -> FishtasticSounds.QUEST_COMPLETE_GOLD.value();
            default -> FishtasticSounds.QUEST_COMPLETE_BRONZE.value();
        };
    }

    private void playSound(Minecraft mc, SoundEvent sound) {
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, FishtasticClientConfig.getNotificationVolumeFraction()));
        }
    }
}
