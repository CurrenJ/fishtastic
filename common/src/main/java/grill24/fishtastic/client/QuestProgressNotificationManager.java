package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Singleton that manages a queue of QuestProgressNotifications.
 * Up to MAX_ACTIVE notifications are displayed simultaneously, stacked vertically;
 * additional events queue behind them. When several notifications are activated in
 * quick succession, each one's slide-in is staggered by STAGGER_DELAY_TICKS so they
 * don't all animate in at once.
 * Ticks and renders the active notifications, advancing the queue as slots free up.
 *
 * Active slots are filled strictly by {@link NotificationPriority} tier: only one tier
 * occupies the active set at a time. Once a tier is showing, lower-priority (higher
 * ordinal) pending events wait — even into free slots — until that tier has fully
 * drained (nothing left active or pending at that tier); higher-priority arrivals never
 * preempt a tier already on screen. See {@link #fillActiveSlots()}.
 *
 * Usage:
 *   1. Call install() once during client init to wire the QuestClientCache listener.
 *   2. Register this manager's render method on the platform HUD pipeline.
 *   3. Call tick() every client tick.
 */
public class QuestProgressNotificationManager {

    private static final QuestProgressNotificationManager INSTANCE = new QuestProgressNotificationManager();
    private static final int MAX_QUEUE_SIZE = 10;
    private static final int MAX_ACTIVE = 3;
    private static final int STAGGER_DELAY_TICKS = 6;

    private final List<QuestProgressNotification> active = new ArrayList<>(MAX_ACTIVE);
    private final Deque<QuestProgressEvent> pending = new ArrayDeque<>();
    /** Ticks until the next newly-activated notification is allowed to start its slide-in. */
    private int nextStaggerDelay;
    private boolean installed;

    public static QuestProgressNotificationManager getInstance() {
        return INSTANCE;
    }

    private QuestProgressNotificationManager() {}

    /** Synthetic "quest" id used to drive the global cleanup-goal milestone banner through this pipeline. */
    public static final Identifier CLEANUP_GOAL_MILESTONE_ID = Fishtastic.id("cleanup_goal");

    /** Synthetic "quest" id used to drive the out-of-bait banner through this pipeline. */
    public static final Identifier OUT_OF_BAIT_ID = Fishtastic.id("out_of_bait");

    /**
     * Prefix for synthetic "quest" ids driving first-catch banners, one per fish species
     * (rather than a single shared id) so catching several new species in one session
     * queues a separate banner for each instead of the later ones clobbering the earlier
     * pending entry — see the same-id replace/update-in-place logic in {@link #enqueue}.
     */
    public static final String FIRST_CATCH_ID_PREFIX = "first_catch/";

    /** The synthetic quest id used for a species' first-catch banner. */
    public static Identifier firstCatchQuestId(Identifier fishId) {
        return Fishtastic.id(FIRST_CATCH_ID_PREFIX + fishId.getPath());
    }

    /**
     * First-catch banners whose fanfare has already been played by something else — in practice the
     * catch celebration, which announces a discovery at its reveal, seconds before the banner
     * arrives. Keyed by banner quest id and consumed once.
     *
     * <p>The banner still shows; only its sound is dropped. The discovery is worth announcing once,
     * at the moment it lands, and the banner turning up later to replay the same fanfare is the
     * duplicate — the reveal is where the player is actually looking.
     */
    private static final Map<Identifier, Long> FANFARE_CLAIMS = new HashMap<>();

    /**
     * How long a claim stays valid. Long enough to cover the celebration plus the round trip that
     * records the catch and sends the banner back, short enough that a claim left behind by a catch
     * that never registered can't mute an unrelated banner later.
     */
    private static final int FANFARE_CLAIM_WINDOW_TICKS = 200;

    /** Called when something else has already played the discovery fanfare for this species. */
    public static void claimDiscoveryFanfare(Identifier fishId) {
        FANFARE_CLAIMS.put(firstCatchQuestId(fishId), gameTime() + FANFARE_CLAIM_WINDOW_TICKS);
    }

    /** True if this banner's fanfare was already played elsewhere; consumes the claim. */
    public static boolean consumeDiscoveryFanfareClaim(Identifier questId) {
        Long expiry = FANFARE_CLAIMS.remove(questId);
        if (expiry == null) return false;

        long now = gameTime();
        // Game time restarts per world, so a claim carried across worlds can look arbitrarily far
        // in the future. Treat anything beyond the window as stale rather than trusting it.
        return now <= expiry && expiry - now <= FANFARE_CLAIM_WINDOW_TICKS;
    }

    private static long gameTime() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null ? mc.level.getGameTime() : 0L;
    }

    /** Wire the QuestClientCache listener so progress events feed into this manager. */
    public void install() {
        if (installed) return;
        installed = true;
        QuestClientCache.setListener((questId, oldCount, newCount, targetCount, completed, triggeringItem) -> {
            enqueue(new QuestProgressEvent(questId, oldCount, newCount, targetCount, completed, triggeringItem));
        });
        QuestClientCache.setMilestoneListener((milestoneReached, threshold) -> {
            ItemStack icon = new ItemStack(FishtasticItems.OLD_TIRE.value());
            enqueue(new QuestProgressEvent(CLEANUP_GOAL_MILESTONE_ID, 0, threshold, threshold, true, icon));
        });
        QuestClientCache.setBaitDepletedListener(baitItem ->
                enqueue(new QuestProgressEvent(OUT_OF_BAIT_ID, 0, 1, 1, true, baitItem)));
        QuestClientCache.setFirstCatchListener(fishItem -> {
            Identifier fishId = BuiltInRegistries.ITEM.getKey(fishItem.getItem());
            enqueue(new QuestProgressEvent(firstCatchQuestId(fishId), 0, 1, 1, true, fishItem));
        });
    }

    /** Enqueue a progress event for display. */
    public void enqueue(QuestProgressEvent event) {
        // If same quest is already active, update in-place
        for (QuestProgressNotification n : active) {
            if (!n.isDone() && n.questId().equals(event.questId())) {
                n.updateProgress(event);
                return;
            }
        }

        // If same quest is already pending, replace the pending entry
        for (var it = pending.iterator(); it.hasNext(); ) {
            QuestProgressEvent p = it.next();
            if (p.questId().equals(event.questId())) {
                it.remove();
                break;
            }
        }

        pending.addLast(event);
        // Only fast-track into a free slot here if a tier is already locked in by the
        // active set — safe because that tier was already decided independently of
        // arrival order. If active is empty, deliberately do NOT activate here: several
        // enqueue() calls often land synchronously in the same burst (e.g. one sync
        // packet triggering several quests at once via QuestClientCache's listener
        // loop), and activating on the very first one would lock in whichever event
        // happened to arrive first as "the" tier, starving any higher-priority event
        // that arrives a moment later in the same burst. Leaving it queued lets tick()
        // pick the true minimum-priority tier once the whole burst has landed.
        if (!active.isEmpty()) {
            fillActiveSlots();
        }
        while (pending.size() > MAX_QUEUE_SIZE) {
            pending.removeFirst();
        }
    }

    /**
     * Fills free active slots from the pending queue, restricted to a single
     * {@link NotificationPriority} tier at a time: the tier already occupying the active
     * set (if any), or otherwise the highest-priority tier present in pending. Lower-
     * priority pending events are left waiting even when slots are free, so a tier is
     * always fully displayed and exits before the next tier gets a turn.
     */
    private void fillActiveSlots() {
        NotificationPriority currentTier = active.isEmpty()
                ? pending.stream().map(NotificationPriority::classify).min(Comparator.naturalOrder()).orElse(null)
                : NotificationPriority.classify(active.get(0).event());

        while (active.size() < MAX_ACTIVE && currentTier != null) {
            QuestProgressEvent next = removeFirstPendingOfTier(currentTier);
            if (next == null) break;
            activate(next);
        }
    }

    /** Removes and returns the first pending event matching the given tier, or null if none. */
    private QuestProgressEvent removeFirstPendingOfTier(NotificationPriority tier) {
        for (var it = pending.iterator(); it.hasNext(); ) {
            QuestProgressEvent event = it.next();
            if (NotificationPriority.classify(event) == tier) {
                it.remove();
                return event;
            }
        }
        return null;
    }

    /** Move an event into an active slot, staggering its slide-in start against recently-activated notifications. */
    private void activate(QuestProgressEvent event) {
        QuestProgressNotification notification = new QuestProgressNotification(event);
        notification.setStartDelay(nextStaggerDelay);
        nextStaggerDelay += STAGGER_DELAY_TICKS;
        active.add(notification);
        // Assign this one's slot immediately (it snaps on first assignment — see
        // setTargetY) so it's positioned correctly even if render() runs before the
        // next tick().
        layoutSlots();
    }

    /**
     * Assigns each active notification's target vertical slot based on current stack
     * order. Notifications already on screen ease toward a changed target rather than
     * snapping (see QuestProgressNotification#setTargetY), so when a banner above exits
     * and is removed, the rest of the stack slides smoothly up instead of popping.
     */
    private void layoutSlots() {
        int y = QuestProgressNotification.MARGIN;
        for (QuestProgressNotification n : active) {
            n.setTargetY(y);
            y += n.getScaledHeight() + QuestProgressNotification.STACK_GAP;
        }
    }

    /** Call once per client tick. Drives the active notifications' lifecycles. */
    public void tick() {
        if (nextStaggerDelay > 0) {
            nextStaggerDelay--;
        }

        for (QuestProgressNotification n : active) {
            n.tick();
        }
        active.removeIf(QuestProgressNotification::isDone);

        fillActiveSlots();

        // Re-layout in case a banner above left the stack without a promotion filling
        // its slot (activate() above already covers the promotion case).
        layoutSlots();
    }

    /** Render all active notifications, stacked vertically top to bottom. Called from the platform HUD pipeline. */
    public void render(GuiGraphicsExtractor graphics, float partialTick) {
        if (active.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        for (QuestProgressNotification n : active) {
            if (n.isDone()) continue;
            n.render(mc, graphics, partialTick);
        }
    }
}
