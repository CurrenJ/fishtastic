package grill24.fishtastic.server;

import grill24.fishtastic.network.QuestSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;

/**
 * Tracks contributions to the shared, server-wide "Clean Up the Waters" goal.
 * Unlike {@link QuestTracker}, progress here is global rather than per-player —
 * every trash catch by any player advances the same counter, and payouts are
 * split across all contributors once a threshold is crossed.
 */
public class CleanupGoalTracker {

    /** Process trash caught in a single minigame session and broadcast a milestone banner if reached. */
    public static void onTrashCatch(MinecraftServer server, ServerPlayer player, int amount) {
        FishCatchSavedData catchData = FishCatchSavedData.getOrCreate(server);
        List<Integer> crossed = catchData.recordTrashContribution(player, amount);
        if (crossed.isEmpty()) return;

        int latestMilestone = crossed.get(crossed.size() - 1);
        // Announce to every online player — this is a shared goal, not a personal one.
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            QuestSyncPacket.sendToPlayer(online, catchData, Map.of(), latestMilestone);
        }
    }
}
