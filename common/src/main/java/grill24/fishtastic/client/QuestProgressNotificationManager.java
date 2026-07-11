package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Singleton that manages a queue of QuestProgressNotifications.
 * One notification is displayed at a time; additional events queue behind it.
 * Ticks and renders the active notification, advancing the queue on completion.
 *
 * Usage:
 *   1. Call install() once during client init to wire the QuestClientCache listener.
 *   2. Register this manager's render method on the platform HUD pipeline.
 *   3. Call tick() every client tick.
 */
public class QuestProgressNotificationManager {

    private static final QuestProgressNotificationManager INSTANCE = new QuestProgressNotificationManager();
    private static final int MAX_QUEUE_SIZE = 5;

    private QuestProgressNotification active;
    private final Deque<QuestProgressEvent> pending = new ArrayDeque<>();
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
        if (active != null && !active.isDone() && active.questId().equals(event.questId())) {
            active.updateProgress(event);
            return;
        }

        // If same quest is already pending, replace the pending entry
        for (var it = pending.iterator(); it.hasNext(); ) {
            QuestProgressEvent p = it.next();
            if (p.questId().equals(event.questId())) {
                it.remove();
                break;
            }
        }

        // If nothing active, start immediately
        if (active == null || active.isDone()) {
            active = new QuestProgressNotification(event);
            return;
        }

        // Otherwise queue
        pending.addLast(event);
        while (pending.size() > MAX_QUEUE_SIZE) {
            pending.removeFirst();
        }
    }

    /** Call once per client tick. Drives the active notification's lifecycle. */
    public void tick() {
        if (active != null) {
            active.tick();
            if (active.isDone()) {
                // Advance to next pending
                active = null;
                if (!pending.isEmpty()) {
                    active = new QuestProgressNotification(pending.removeFirst());
                }
            }
        }
    }

    /** Render the active notification, if any. Called from the platform HUD pipeline. */
    public void render(GuiGraphicsExtractor graphics, float partialTick) {
        if (active != null && !active.isDone()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                active.render(mc, graphics, partialTick);
            }
        }
    }
}
