package grill24.fishtastic.fabric.compat.coolcam;

import grill24.fishtastic.client.util.ClientTankFlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A {@link FollowablePosition} that continuously re-picks whichever fish in a tank is closest to
 * the player, so the camera follows "the nearest fish" as a live, moving target rather than one
 * fixed index. Optionally restricted to one species, so the camera only ever locks onto that kind
 * of fish rather than whatever happens to swim nearest.
 *
 * <p>Re-picking is rate-limited by {@link #retargetCooldownNanos} so the camera doesn't
 * re-evaluate every frame, and a retarget doesn't cut the camera straight to the new fish's
 * position — it blends over {@link #TRANSITION_SECONDS} from wherever it was tracking, using a
 * smoothstep curve (zero velocity at both ends, so it eases in as well as out).
 */
public final class ClosestFishFollowTarget implements FollowablePosition {
    /** Default re-pick rate when the command doesn't specify one. */
    public static final float DEFAULT_RETARGET_COOLDOWN_SECONDS = 1.5f;
    private static final double TRANSITION_SECONDS = 1.2;

    private final BlockPos tankPos;
    private final Level level;
    /** Species id to restrict candidates to (see {@code ClientTankFlocks.speciesIdOf}), or -1 for any. */
    private final int speciesFilter;
    private final long retargetCooldownNanos;

    private int activeIndex = -1;
    private long lastRetargetNanos;

    private boolean transitioning;
    private Vec3 transitionFrom = Vec3.ZERO;
    private long transitionStartNanos;
    /**
     * Wherever {@link #position} last reported the camera as looking, mid-blend or not. A retarget
     * that lands while a previous blend is still running (more likely now that
     * {@link #TRANSITION_SECONDS} can exceed the retarget cooldown) starts its own blend from here,
     * not from the outgoing fish's raw coordinates — which could be well ahead of where the eased
     * curve had actually gotten to, and would otherwise show up as a visible snap.
     */
    private Vec3 lastReportedPosition = Vec3.ZERO;

    public ClosestFishFollowTarget(BlockPos tankPos, Level level) {
        this(tankPos, level, DEFAULT_RETARGET_COOLDOWN_SECONDS, -1);
    }

    /**
     * @param retargetCooldownSeconds minimum time between re-picking the closest fish
     * @param speciesFilter species id to restrict candidates to (see
     *                      {@code ClientTankFlocks.speciesIdOf}), or -1 to consider every fish
     */
    public ClosestFishFollowTarget(BlockPos tankPos, Level level, float retargetCooldownSeconds, int speciesFilter) {
        this.tankPos = tankPos;
        this.level = level;
        this.retargetCooldownNanos = (long) (retargetCooldownSeconds * 1_000_000_000L);
        this.speciesFilter = speciesFilter;
        retarget();
    }

    @Override
    public Vec3 position(float partialTick) {
        retarget();

        float gameTimeTicks = (float) (level.getGameTime() + partialTick);
        Vec3 target = ClientTankFlocks.worldPositionOf(level, tankPos, activeIndex, gameTimeTicks);
        if (target == null) {
            return lastReportedPosition = transitioning ? transitionFrom : Vec3.atCenterOf(tankPos);
        }
        if (!transitioning) {
            return lastReportedPosition = target;
        }

        double t = (System.nanoTime() - transitionStartNanos) / 1e9 / TRANSITION_SECONDS;
        if (t >= 1.0) {
            transitioning = false;
            return lastReportedPosition = target;
        }
        double eased = t * t * (3 - 2 * t); // smoothstep: eases in and out
        return lastReportedPosition = transitionFrom.lerp(target, eased);
    }

    @Override
    public boolean isValid() {
        return Minecraft.getInstance().level == level && ClientTankFlocks.fishCount(level, tankPos) > 0;
    }

    /** Re-evaluates the closest fish, subject to the retarget cooldown, and starts a blend if it changed. */
    private void retarget() {
        long now = System.nanoTime();
        if (activeIndex >= 0 && now - lastRetargetNanos < retargetCooldownNanos) return;
        lastRetargetNanos = now;

        int best = pickClosest();
        if (best < 0 || best == activeIndex) return;

        if (activeIndex >= 0) {
            transitionFrom = lastReportedPosition;
            transitionStartNanos = now;
            transitioning = true;
        }
        activeIndex = best;
    }

    private int pickClosest() {
        Player player = Minecraft.getInstance().player;
        Vec3 ref = player != null ? player.getEyePosition() : Vec3.atCenterOf(tankPos);
        float gameTimeTicks = level.getGameTime();

        int best = -1;
        double bestDistSq = Double.MAX_VALUE;
        int count = ClientTankFlocks.fishCount(level, tankPos);
        for (int i = 0; i < count; i++) {
            if (speciesFilter >= 0 && ClientTankFlocks.speciesIdOf(level, tankPos, i) != speciesFilter) continue;
            Vec3 p = ClientTankFlocks.worldPositionOf(level, tankPos, i, gameTimeTicks);
            if (p == null) continue;
            double d = p.distanceToSqr(ref);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = i;
            }
        }
        return best;
    }
}
