package grill24.fishtastic.tutorial;

import grill24.fishtastic.network.CompanionTutorialSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server side of the companion FTUE (docs/fish-companions.md §8.4, R7.7). Starts the first time any
 * bond unlocks — wherever that transition is detected (see
 * {@code CompanionBondUnlocks#refresh}) — and it never starts again: later bond unlocks get the
 * banner only, which is the pace the owner asked for.
 */
public final class CompanionTutorialManager {
    private CompanionTutorialManager() {}

    /** Call on the transition to "this player has at least one unlocked bond" (any number of times). */
    public static void onBondUnlocked(ServerPlayer player) {
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(player.level().getServer());
        if (data.getCompanionTutorialStep(player) == CompanionTutorialStep.NOT_STARTED) {
            data.setCompanionTutorialStep(player, CompanionTutorialStep.INTRO);
            CompanionTutorialSyncPacket.sendToPlayer(player, CompanionTutorialStep.INTRO);
        }
    }

    /** Call when the player first opens the Companions tab — the action the INTRO step teaches. */
    public static void onCompanionsTabOpened(ServerPlayer player) {
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(player.level().getServer());
        if (data.getCompanionTutorialStep(player) == CompanionTutorialStep.INTRO) {
            data.setCompanionTutorialStep(player, CompanionTutorialStep.COMPLETE);
            CompanionTutorialSyncPacket.sendToPlayer(player, CompanionTutorialStep.COMPLETE);
        }
    }

    /**
     * Call every time the encyclopedia opens (alongside the encyclopedia's own FTUE ping): the
     * INTRO panel belongs to that screen, so a player who was offline when the step started — or
     * who closed the screen before seeing it — gets it again here.
     */
    public static void onEncyclopediaOpened(ServerPlayer player) {
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(player.level().getServer());
        if (data.getCompanionTutorialStep(player) == CompanionTutorialStep.INTRO) {
            CompanionTutorialSyncPacket.sendToPlayer(player, CompanionTutorialStep.INTRO);
        }
    }
}
