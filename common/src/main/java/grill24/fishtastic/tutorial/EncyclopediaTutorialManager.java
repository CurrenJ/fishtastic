package grill24.fishtastic.tutorial;

import grill24.fishtastic.network.EncyclopediaTutorialSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.server.level.ServerPlayer;

/**
 * Small, independent FTUE for the Fish Encyclopedia screen.
 * <p>
 * Deliberately separate from {@link TutorialManager}/{@link TutorialStep} rather than a few
 * extra values spliced into that chain: the encyclopedia's content is only worth explaining
 * once a player actually opens it (its payoff — species grid, zone info, claimable rewards —
 * is mostly empty right after the very first catch, so teaching it during the first-catch
 * tutorial would explain it at its least useful moment), and it's triggered by opening the
 * screen, not by fishing progress. Keeping it a distinct enum/savedata field also means it
 * shares nothing with {@link TutorialStep}'s ordinal layout, so it can't be affected by changes
 * to that chain.
 */
public class EncyclopediaTutorialManager {

    /** Call every time RequestFishEncyclopediaPacket is handled, i.e. every time the screen opens. */
    public static void onEncyclopediaOpened(ServerPlayer player) {
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(player.level().getServer());
        EncyclopediaTutorialStep step = data.getEncyclopediaTutorialStep(player);
        if (step == EncyclopediaTutorialStep.NOT_STARTED) {
            step = EncyclopediaTutorialStep.INTRO;
            data.setEncyclopediaTutorialStep(player, step);
        }
        if (step.hasOverlay()) {
            EncyclopediaTutorialSyncPacket.sendToPlayer(player, step);
        }
    }

    /** Called by EncyclopediaTutorialAdvancePacket — client-initiated step advance. */
    public static void advanceStep(ServerPlayer player, EncyclopediaTutorialStep fromStep) {
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(player.level().getServer());
        if (data.getEncyclopediaTutorialStep(player) != fromStep) return;

        EncyclopediaTutorialStep next = switch (fromStep) {
            case INTRO -> EncyclopediaTutorialStep.ZONES_AND_REWARDS;
            case ZONES_AND_REWARDS -> EncyclopediaTutorialStep.COMPLETE;
            default -> null;
        };
        if (next != null) {
            data.setEncyclopediaTutorialStep(player, next);
            EncyclopediaTutorialSyncPacket.sendToPlayer(player, next);
        }
    }
}
