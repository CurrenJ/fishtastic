// PORT STUB: deleted in A4
package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import net.minecraft.resources.ResourceLocation;

/** Stand-in for the quest toast manager (A4, GUI): the ids kept code compares against, and a no-op queue. */
public final class QuestProgressNotificationManager {
    private static final QuestProgressNotificationManager INSTANCE = new QuestProgressNotificationManager();

    public static final ResourceLocation CLEANUP_GOAL_MILESTONE_ID = Fishtastic.id("cleanup_goal");
    public static final ResourceLocation OUT_OF_BAIT_ID = Fishtastic.id("out_of_bait");
    public static final String FIRST_CATCH_ID_PREFIX = "first_catch/";

    private QuestProgressNotificationManager() {}

    public static QuestProgressNotificationManager getInstance() {
        return INSTANCE;
    }

    public void enqueue(QuestProgressEvent event) {}
}
