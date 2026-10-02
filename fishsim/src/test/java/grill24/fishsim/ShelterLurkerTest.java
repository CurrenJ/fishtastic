package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.harness.Scenarios;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lurkers (docs/fish-shelters.md §5.5): a claimed home that stays claimed, one lurker to a
 * shelter, a mouth pose that holds its place and faces out, and a home that costs everyone else
 * exactly one place.
 */
class ShelterLurkerTest {

    private static final String DOMAIN = "4x1x2+log";

    private static FlockEngine tank(VoxelDomain domain, FishSpec[] specs, long seed) {
        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, seed, 0f, 0f, domain);
        return engine;
    }

    private static int[] identity(int n) {
        int[] carry = new int[n];
        for (int i = 0; i < n; i++) carry[i] = i;
        return carry;
    }

    @Test
    void aClaimSurvivesRebuildsAndALateLurkerNeverTakesItOver() {
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FishSpec[] specs = Scenarios.cast("lurker", 6, 12345L);
        FlockEngine engine = tank(domain, specs, 12345L);
        engine.step();
        assertEquals(0, engine.shelterClaim(0), "the lurker should claim the only log");
        for (int t = 0; t < 2_000; t++) engine.step();

        // A rebuild that carries everyone (a fish added to the tank, say) keeps the claim.
        engine.rebuildPreserving(specs, identity(specs.length), 12345L, 0f, 0f, domain);
        engine.step();
        assertEquals(0, engine.shelterClaim(0), "a rebuild dropped the claim");

        // A second lurker joins, nearer the log than the first: it finds the home taken.
        FishSpec[] more = new FishSpec[specs.length + 1];
        System.arraycopy(specs, 0, more, 0, specs.length);
        more[specs.length] = new FishSpec(0.30f, Locomotion.FREE_SWIM, false, 2, ShelterUse.LURKER);
        int[] carry = new int[more.length];
        System.arraycopy(identity(specs.length), 0, carry, 0, specs.length);
        carry[specs.length] = -1;
        engine.rebuildPreserving(more, carry, 12345L, 0f, 0f, domain);
        for (int t = 0; t < 600; t++) {
            engine.step();
            assertEquals(0, engine.shelterClaim(0), "the first lurker lost its home at tick " + t);
            assertEquals(-1, engine.shelterClaim(specs.length), "the late lurker took a claimed home");
        }
    }

    @Test
    void oneLurkerPerShelterAndTheOtherVisits() {
        VoxelDomain domain = Scenarios.domain(DOMAIN);
        FishSpec[] specs = Scenarios.cast("lurker", 6, 5L);
        specs[1] = new FishSpec(0.30f, Locomotion.FREE_SWIM, false, 2, ShelterUse.LURKER);
        FlockEngine engine = tank(domain, specs, 5L);
        engine.step();
        int claims = 0;
        for (int i = 0; i < engine.count(); i++) if (engine.shelterClaim(i) >= 0) claims++;
        assertEquals(1, claims, "two lurkers share one log");
    }

    /**
     * The mouth pose: once settled, the lurker holds its rest point to within its sway and faces
     * straight out of the mouth, through every rest of a ten-minute run.
     */
    @Test
    void theMouthPoseHoldsItsPlaceAndFacesOut() {
        for (String dom : new String[]{"3x1x1+log", DOMAIN, "L+log", "2x2slab+log"}) {
            VoxelDomain domain = Scenarios.domain(dom);
            FlockEngine engine = tank(domain, Scenarios.cast("lurker", 6, 31337L), 31337L);
            Shelter log = domain.shelters().get(0);
            int restTicks = 0, rests = 0, prev = FlockEngine.SHELTER_ROAMING;
            float worstOffset = 0f, worstFacing = 1f;
            for (int t = 0; t < 12_000; t++) {
                engine.step();
                int st = engine.shelterState(0);
                if (st == FlockEngine.SHELTER_RESTING && prev != FlockEngine.SHELTER_RESTING) rests++;
                prev = st;
                restTicks = st == FlockEngine.SHELTER_RESTING ? restTicks + 1 : 0;
                if (restTicks <= 40) continue; // two seconds to come to rest
                Shelter.Mouth m = log.mouths().get(engine.shelterMouth(0));
                float rl = engine.posL()[0] - m.centerL(), ry = engine.posY()[0] - m.centerY(), rd = engine.posD()[0] - m.centerD();
                float along = rl * m.normalL() + ry * m.normalY() + rd * m.normalD();
                float pl = rl - along * m.normalL(), py = ry - along * m.normalY(), pd = rd - along * m.normalD();
                float offset = Math.max(Math.abs(along - 0.45f * engine.lengths[0]),
                        (float) Math.sqrt(pl * pl + py * py + pd * pd));
                worstOffset = Math.max(worstOffset, offset);
                float yr = (float) Math.toRadians(engine.yawDeg[0]);
                float facing = -((float) Math.cos(yr) * m.normalL() - (float) Math.sin(yr) * m.normalD());
                worstFacing = Math.min(worstFacing, facing);
            }
            assertTrue(rests >= 3, dom + ": the lurker came home only " + rests + " times in ten minutes");
            assertTrue(worstOffset <= 0.025f, dom + ": the rest pose wandered " + worstOffset + " from its point");
            assertTrue(worstFacing >= 0.98f, dom + ": the resting lurker turned from the mouth (cos " + worstFacing + ")");
            assertEquals(0, engine.backstopEngagements(), dom + ": hard backstop engagements");
        }
    }

    /** A claimed home costs everyone else one place, home or not (§5.5). */
    @Test
    void aClaimedShelterHasOnePlaceFewerForEveryoneElse() {
        VoxelDomain domain = Scenarios.domain("2x2slab+log");
        FlockEngine engine = tank(domain, Scenarios.cast("lurker", 10, 77L), 77L);
        int capacity = domain.shelters().get(0).capacity();
        for (int t = 0; t < 12_000; t++) {
            engine.step();
            int others = 0;
            for (int i = 1; i < engine.count(); i++) {
                int st = engine.shelterState(i);
                if (engine.shelterIndex(i) == 0 && (st == FlockEngine.SHELTER_APPROACH
                        || st == FlockEngine.SHELTER_ENTER || st == FlockEngine.SHELTER_INSIDE)) {
                    others++;
                }
            }
            assertTrue(others <= capacity - 1, others + " visitors in a claimed log of capacity " + capacity + ", tick " + t);
        }
    }
}
