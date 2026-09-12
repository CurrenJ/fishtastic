package grill24.fishtastic.server;

import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;

/**
 * Elongates the dawn and dusk windows (overworld clock ticks [{@link FishProfile.TimeOfDay#DAWN_START_TICK},
 * {@link FishProfile.TimeOfDay#DAWN_END_TICK}) and [{@link FishProfile.TimeOfDay#DUSK_START_TICK},
 * {@link FishProfile.TimeOfDay#DUSK_END_TICK})) based on how many online players are carrying a
 * Sunset Postcard Charm ({@link CharmEffect#sunsetExtensionSeconds()}) anywhere in their inventory
 * — passive, like the Little Fish Box/Angler's Almanac, so it doesn't need to be rod-slotted (see
 * {@link FishingMinigameManager#sumCharmEffectInInventory}). The window bounds are read directly
 * from {@link FishProfile.TimeOfDay} — the same boundaries the fishing minigame's quest time
 * conditions use — rather than duplicated, so the two can't drift apart.
 * <p>
 * Each player's contribution is capped at {@link #PLAYER_CAP_SECONDS}, then summed linearly
 * across every online player (regardless of which dimension they're in — {@code
 * getOverworldClockTime()} is the single shared time source the rest of the mod already reads
 * everywhere, see {@code FishProfile.TimeOfDay}), and applied as a slowed rate on the overworld
 * {@link net.minecraft.world.clock.ServerClockManager} clock so the active window takes longer to
 * pass in real time. The rate is global — it isn't possible for one player to see a different
 * day-speed than another sharing the same world.
 */
public class SunsetExtensionHandler {

    /**
     * Max seconds any single player can add to the active window's duration. This is a separate,
     * hard-coded ceiling from {@link CharmEffect#sunsetExtensionSeconds()} — a charm authored
     * above this value still only contributes this much per player. The {@link #RATE_FLOOR} caps
     * how far multiple players stacking can push it further.
     */
    private static final float PLAYER_CAP_SECONDS = 120.0f;

    /** Hard floor on the applied clock rate, regardless of how many players are active. */
    private static final float RATE_FLOOR = 0.2f;

    /** Only re-broadcast setRate when the target rate actually changes, to avoid spamming clients. */
    private static final float RATE_CHANGE_THRESHOLD = 0.001f;

    private static Float lastAppliedRate = null;
    private static boolean resetOnStartup = true;

    public static void tick(MinecraftServer server) {
        Holder<WorldClock> overworldClock = server.registryAccess().getOrThrow(WorldClocks.OVERWORLD);

        // Defensively clear any rate left over from a crash/unclean shutdown mid-slowdown.
        if (resetOnStartup) {
            resetOnStartup = false;
            server.clockManager().setRate(overworldClock, 1.0f);
            lastAppliedRate = 1.0f;
        }

        long dayTime = server.overworld().getOverworldClockTime() % 24000L;
        long windowStartTick = -1L;
        long windowEndTick = -1L;
        if (dayTime >= FishProfile.TimeOfDay.DAWN_START_TICK && dayTime < FishProfile.TimeOfDay.DAWN_END_TICK) {
            windowStartTick = FishProfile.TimeOfDay.DAWN_START_TICK;
            windowEndTick = FishProfile.TimeOfDay.DAWN_END_TICK;
        } else if (dayTime >= FishProfile.TimeOfDay.DUSK_START_TICK && dayTime < FishProfile.TimeOfDay.DUSK_END_TICK) {
            windowStartTick = FishProfile.TimeOfDay.DUSK_START_TICK;
            windowEndTick = FishProfile.TimeOfDay.DUSK_END_TICK;
        }

        float targetRate = 1.0f;
        if (windowStartTick >= 0L) {
            // At rate 1.0, this window normally takes this many real seconds to pass.
            float windowSeconds = (windowEndTick - windowStartTick) / 20.0f;
            float totalBonusSeconds = 0.0f;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                totalBonusSeconds += FishingMinigameManager.sumCharmEffectInInventory(
                        player, CharmEffect::sunsetExtensionSeconds, PLAYER_CAP_SECONDS);
            }
            targetRate = Math.max(RATE_FLOOR, windowSeconds / (windowSeconds + totalBonusSeconds));
        }

        if (lastAppliedRate == null || Math.abs(lastAppliedRate - targetRate) > RATE_CHANGE_THRESHOLD) {
            server.clockManager().setRate(overworldClock, targetRate);
            lastAppliedRate = targetRate;
        }
    }
}
