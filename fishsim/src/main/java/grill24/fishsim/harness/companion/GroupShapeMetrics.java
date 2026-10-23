package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.CompanionAgent;
import grill24.fishsim.companion.Vec;

/**
 * Formation measures for a group of party members (docs/fish-companions.md, §8 last row),
 * each a level paired with a rate, so a school can't buy calm spacing with a twitching one:
 *
 * <ul>
 *   <li><b>Spacing</b>: each follower's distance to its nearest other member, in that pair's body
 *       lengths (mean, min), and how fast it changes (mean |Δ|, blocks/s).</li>
 *   <li><b>Overtakes</b>: while the leader travels, the share of follower ticks spent ahead of it
 *       along its heading, and behind-to-ahead crossings per travelling minute.</li>
 *   <li><b>Lag</b>: follower distance from the leader (mean, max, blocks), its rate of change
 *       (mean |Δ|, blocks/s), and the longest straggle (beyond {@link CompanionRun#STRAGGLE}).</li>
 *   <li><b>Contact</b>: ticks two members were closer than their radii combined (spheres through
 *       each other).</li>
 * </ul>
 *
 * Only members marked active (fully appeared, not riding the owner) are measured on a tick.
 */
public final class GroupShapeMetrics {
    /** The leader counts as travelling above this horizontal speed, blocks/s. */
    static final double TRAVEL_SPEED = 0.5;

    private final float[] lengths;
    private final double[] radii;
    private final int n;

    private int ticks, contactTicks, measuredTicks;
    private double spacingSum, spacingMin = Double.MAX_VALUE, spacingRateSum;
    private int spacingN, spacingRateN;
    private int travelFollowerTicks, aheadTicks, overtakes, travelTicks;
    private double lagSum, lagMax, lagRateSum;
    private int lagN, lagRateN;
    private int longestStraggle;
    private final double[] prevNearest, prevLag;
    private final boolean[] prevAhead, prevActive;
    private final int[] straggle;

    /** {@code lengths} and {@code radii} per member, leader first. */
    public GroupShapeMetrics(float[] lengths, double[] radii) {
        if (lengths.length != radii.length) throw new IllegalArgumentException("one length and radius per member");
        this.lengths = lengths.clone();
        this.radii = radii.clone();
        this.n = lengths.length;
        prevNearest = new double[n];
        prevLag = new double[n];
        prevAhead = new boolean[n];
        prevActive = new boolean[n];
        straggle = new int[n];
    }

    /** One tick: every member's position (leader first), the leader's velocity, and who is measured. */
    public void tick(Vec[] pos, Vec leaderVel, boolean[] active) {
        ticks++;
        double dt = CompanionAgent.DT;
        boolean travelling = leaderVel.horizontalLength() > TRAVEL_SPEED;
        Vec heading = travelling ? leaderVel.horizontal().normalize() : null;
        if (travelling) travelTicks++;
        boolean contact = false, any = false;
        for (int a = 0; a < n; a++) {
            if (!active[a]) continue;
            for (int b = a + 1; b < n; b++) {
                if (!active[b]) continue;
                any = true;
                if (pos[a].distanceTo(pos[b]) < radii[a] + radii[b]) contact = true;
            }
        }
        if (any) measuredTicks++;
        if (contact) contactTicks++;
        for (int i = 1; i < n; i++) {
            boolean on = active[i] && active[0];
            if (!on) {
                prevActive[i] = false;
                straggle[i] = 0;
                continue;
            }
            // Spacing: to the nearest other active member, in that pair's body lengths.
            double nearest = Double.MAX_VALUE, nearestLengths = Double.MAX_VALUE;
            for (int j = 0; j < n; j++) {
                if (j == i || !active[j]) continue;
                double d = pos[i].distanceTo(pos[j]);
                if (d < nearest) {
                    nearest = d;
                    nearestLengths = d / (((double) lengths[i] + lengths[j]) * 0.5);
                }
            }
            spacingSum += nearestLengths;
            spacingMin = Math.min(spacingMin, nearestLengths);
            spacingN++;
            double lag = pos[i].distanceTo(pos[0]);
            lagSum += lag;
            lagMax = Math.max(lagMax, lag);
            lagN++;
            if (prevActive[i]) {
                spacingRateSum += Math.abs(nearest - prevNearest[i]) / dt;
                spacingRateN++;
                lagRateSum += Math.abs(lag - prevLag[i]) / dt;
                lagRateN++;
            }
            boolean ahead = false;
            if (travelling) {
                ahead = pos[i].sub(pos[0]).horizontal().dot(heading) > 0;
                travelFollowerTicks++;
                if (ahead) aheadTicks++;
                if (ahead && prevActive[i] && !prevAhead[i]) overtakes++;
            }
            straggle[i] = lag > CompanionRun.STRAGGLE ? straggle[i] + 1 : 0;
            longestStraggle = Math.max(longestStraggle, straggle[i]);
            prevNearest[i] = nearest;
            prevLag[i] = lag;
            prevAhead[i] = ahead;
            prevActive[i] = true;
        }
    }

    public record Result(double meanSpacingLengths, double minSpacingLengths, double spacingRate,
                         double aheadShare, double overtakesPerTravelMinute,
                         double meanLag, double maxLag, double lagRate, double longestStraggleSeconds,
                         double contactShare, int contactTicks) {
        public String summary() {
            return String.format(java.util.Locale.ROOT,
                    "spacing %.2f L (min %.2f) rate %.2f b/s | ahead %.1f%% overtakes %.1f/min | lag %.2f b (max %.2f) rate %.2f b/s straggle %.1fs | contact %.1f%% (%d)",
                    meanSpacingLengths, minSpacingLengths, spacingRate, 100 * aheadShare, overtakesPerTravelMinute,
                    meanLag, maxLag, lagRate, longestStraggleSeconds, 100 * contactShare, contactTicks);
        }
    }

    public Result result() {
        double dt = CompanionAgent.DT;
        double travelMinutes = travelTicks * dt / 60.0;
        return new Result(
                spacingN == 0 ? 0 : spacingSum / spacingN,
                spacingMin == Double.MAX_VALUE ? -1 : spacingMin,
                spacingRateN == 0 ? 0 : spacingRateSum / spacingRateN,
                travelFollowerTicks == 0 ? 0 : aheadTicks / (double) travelFollowerTicks,
                travelMinutes == 0 ? 0 : overtakes / travelMinutes,
                lagN == 0 ? 0 : lagSum / lagN, lagMax,
                lagRateN == 0 ? 0 : lagRateSum / lagRateN,
                longestStraggle * dt,
                measuredTicks == 0 ? 0 : contactTicks / (double) measuredTicks, contactTicks);
    }
}
