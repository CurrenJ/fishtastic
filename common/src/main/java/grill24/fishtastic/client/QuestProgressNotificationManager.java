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
import java.util.Deque;
import java.util.List;

/**
 * Singleton that manages a queue of QuestProgressNotifications.
 * Up to MAX_ACTIVE notifications are displayed simultaneously, stacked vertically;
 * additional events queue behind them. When several notifications are activated in
 * quick succession, each one's slide-in is staggered by STAGGER_DELAY_TICKS so they
 * don't all animate in at once.
 * Ticks and renders the active notifications, advancing the queue as slots free up.
 *
 * Usage:
 *   1. Call install() once during client init to wire the QuestClientCache listener.
 *   2. Register this manager's render method on the platform HUD pipeline.
 *   3. Call tick() every client tick.
 */
public class QuestProgressNotificationManager {

    private static final QuestProgressNotificationManager INSTANCE = new QuestProgressNotificationManager();
    private static final int MAX_QUEUE_SIZE = 5;
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
            enqueue(new QuestProgressEvent(Fishtastic.id(FIRST_CATCH_ID_PREFIX + fishId.getPath()),
                    0, 1, 1, true, fishItem));
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

        // If there's a free slot, activate immediately (staggered against any
        // notifications that were just activated this same burst)
        if (active.size() < MAX_ACTIVE) {
            activate(event);
            return;
        }

        // Otherwise queue
        pending.addLast(event);
        while (pending.size() > MAX_QUEUE_SIZE) {
            pending.removeFirst();
        }
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

        while (active.size() < MAX_ACTIVE && !pending.isEmpty()) {
            activate(pending.removeFirst());
        }

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
