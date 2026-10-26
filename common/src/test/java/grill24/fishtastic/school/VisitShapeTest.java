package grill24.fishtastic.school;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.WaterDomain;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Visit shapes (docs/in-world-fish-encounters-design-spec.md §3): sizes, the fallback, and the sim they run. */
class VisitShapeTest {

    private static final BlockPos ORIGIN = new BlockPos(100, -63, 200);
    private static final float SURFACE = 8f / 9f;

    /** A square pond {@code size} blocks across and {@code depth} deep, centred on the origin. */
    private static List<WaterDomain.Column> pond(int size, int depth) {
        List<WaterDomain.Column> cols = new ArrayList<>();
        int half = size / 2;
        for (int x = -half; x < size - half; x++) {
            for (int z = -half; z < size - half; z++) {
                cols.add(new WaterDomain.Column(ORIGIN.getX() + x, ORIGIN.getZ() + z,
                        new float[]{ORIGIN.getY(), ORIGIN.getY() + depth - 1 + SURFACE}, false));
            }
        }
        return cols;
    }

    @Test
    void fallbackFollowsRarity() {
        assertEquals(VisitShape.SCHOOL, VisitShape.fallback(75));
        assertEquals(VisitShape.SCHOOL, VisitShape.fallback(25));
        assertEquals(VisitShape.LOOSE_GROUP, VisitShape.fallback(24));
        assertEquals(VisitShape.LOOSE_GROUP, VisitShape.fallback(8));
        assertEquals(VisitShape.SOLITARY, VisitShape.fallback(7));
        assertEquals(VisitShape.SOLITARY, VisitShape.fallback(1));
    }

    @Test
    void onlyFreeSwimmersSchool() {
        assertEquals(VisitShape.SCHOOL, VisitShape.allowedFor(VisitShape.SCHOOL, Locomotion.FREE_SWIM));
        for (Locomotion other : new Locomotion[]{Locomotion.BENTHIC, Locomotion.ANCHORED, Locomotion.DRIFT, Locomotion.GLIDE}) {
            assertEquals(VisitShape.LOOSE_GROUP, VisitShape.allowedFor(VisitShape.SCHOOL, other), other.name());
            assertEquals(VisitShape.PAIR, VisitShape.allowedFor(VisitShape.PAIR, other), other.name());
        }
    }

    @Test
    void sizesMatchTheSpec() {
        assertEquals(1, VisitShape.SOLITARY.minMembers());
        assertEquals(1, VisitShape.SOLITARY.maxMembers());
        assertEquals(2, VisitShape.PAIR.minMembers());
        assertEquals(2, VisitShape.PAIR.maxMembers());
        assertEquals(2, VisitShape.LOOSE_GROUP.minMembers());
        assertEquals(4, VisitShape.LOOSE_GROUP.maxMembers());
        assertEquals(3, VisitShape.SCHOOL.minMembers());
        assertEquals(6, VisitShape.SCHOOL.maxMembers());
    }

    @Test
    void schoolsKeepTheParametersSchoolsAlwaysRan() {
        assertSame(Tunables.GROUP, VisitShape.SCHOOL.tunables());
        assertSame(Tunables.GROUP, VisitShape.SOLITARY.tunables());
    }

    @Test
    void looseGroupsAndPairsFlockLessTightlyThanSchools() {
        Tunables school = VisitShape.SCHOOL.tunables();
        Tunables loose = VisitShape.LOOSE_GROUP.tunables();
        Tunables pair = VisitShape.PAIR.tunables();
        assertEquals(0f, loose.alignHeadingWeight());
        assertEquals(0f, loose.alignmentWeight());
        assertTrue(loose.cohesionSpeed() < school.cohesionSpeed());
        assertTrue(pair.alignHeadingWeight() < school.alignHeadingWeight());
        // Both look further for company than a school does: fewer fish, spread wider.
        assertTrue(loose.neighborRange() > school.neighborRange());
        assertTrue(pair.neighborRange() > school.neighborRange());
    }

    @Test
    void aLoneFishSwimsAndStaysInItsWater() {
        UUID only = new UUID(7, 7);
        FishSchoolSim sim = FishSchoolSim.create(new FishSchoolSim.Setup(ORIGIN, pond(9, 3),
                FishSchoolSim.spec(0.4f, Locomotion.FREE_SWIM, ShelterUse.VISITOR), 42L,
                List.of(new FishSchoolSim.Placement(only, 0.5f, 1.5f, 0.5f)), VisitShape.SOLITARY));
        assertNotNull(sim);
        float startL = sim.engine().posL()[0], startD = sim.engine().posD()[0];
        for (int i = 0; i < 400; i++) sim.step(null);
        float l = sim.engine().posL()[0], y = sim.engine().posY()[0], d = sim.engine().posD()[0];
        assertTrue(Float.isFinite(l) && Float.isFinite(y) && Float.isFinite(d), "position must stay finite");
        assertTrue(Math.abs(l) < 6 && Math.abs(d) < 6, "a lone fish must stay inside its pond: " + l + ", " + d);
        assertTrue(Math.abs(l - startL) + Math.abs(d - startD) > 0.2f, "a lone fish must actually swim");
    }

    @Test
    void eachShapeBuildsAndStepsDeterministically() {
        for (VisitShape shape : VisitShape.values()) {
            List<FishSchoolSim.Placement> placements = new ArrayList<>();
            for (int i = 0; i < shape.minMembers(); i++) {
                placements.add(new FishSchoolSim.Placement(new UUID(1, i), 0.5f + 2 * i, 1.5f, 0.5f));
            }
            FishSchoolSim.Setup setup = new FishSchoolSim.Setup(ORIGIN, pond(11, 3),
                    FishSchoolSim.spec(0.267f, Locomotion.FREE_SWIM, ShelterUse.VISITOR), 9L, placements, shape);
            FishSchoolSim a = FishSchoolSim.create(setup), b = FishSchoolSim.create(setup);
            assertNotNull(a);
            assertNotNull(b);
            for (int i = 0; i < 200; i++) {
                a.step(null);
                b.step(null);
            }
            for (int i = 0; i < placements.size(); i++) {
                assertEquals(a.engine().posL()[i], b.engine().posL()[i], shape + " copies must agree");
                assertEquals(a.engine().posD()[i], b.engine().posD()[i], shape + " copies must agree");
            }
        }
    }

    @Test
    void bigFishNeedsWaterBigEnoughToSwimIn() {
        // A 1.6-block fish (a large sunfish) cannot swim a 3-block puddle; a 0.27-block bluegill can.
        var big = FishSchoolSim.spec(1.6f, Locomotion.FREE_SWIM, ShelterUse.VISITOR);
        var small = FishSchoolSim.spec(0.267f, Locomotion.FREE_SWIM, ShelterUse.VISITOR);
        assertFalse(FishSchoolSim.hosts(ORIGIN, pond(3, 2), big, VisitShape.SOLITARY));
        assertTrue(FishSchoolSim.hosts(ORIGIN, pond(3, 2), small, VisitShape.SCHOOL));
        assertTrue(FishSchoolSim.hosts(ORIGIN, pond(16, 4), big, VisitShape.SOLITARY));
    }

    @Test
    void aBigFishPlacedAtTheSurfaceIsMovedToRoomAndSwimsToABobber() {
        // A longnose gar (0.48 blocks) on a block centre in the top layer pokes out of the water.
        // Placed there as-is it never moved and never bit (found in game, 2026-10-26).
        UUID gar = new UUID(5, 5), hook = new UUID(9, 9);
        FishSchoolSim sim = FishSchoolSim.create(new FishSchoolSim.Setup(ORIGIN, pond(13, 2),
                FishSchoolSim.spec(0.484f, Locomotion.FREE_SWIM, ShelterUse.LURKER), 7L,
                List.of(new FishSchoolSim.Placement(gar, 0.5f, 1.5f, 0.5f)), VisitShape.SOLITARY));
        assertNotNull(sim);
        assertTrue(sim.bodyFits(sim.engine().posL()[0], sim.engine().posY()[0], sim.engine().posD()[0]),
                "a placement with no room for the body is moved to where it fits");
        float[] goal = sim.goalFor(gar, 4.5f, 1.85f, 4.5f);
        assertNotNull(goal);
        assertTrue(sim.apply(new FishSchoolSim.Approach(hook, gar, goal[0], goal[1], goal[2], 2.5f, 3f)));
        boolean[] touched = {false};
        for (int s = 0; s < 600 && !touched[0]; s++) {
            sim.step(e -> touched[0] |= e.touch() && e.anchor().equals(hook));
        }
        assertTrue(touched[0], "the fish must swim to the bobber and touch it");
    }

    @Test
    void waterTooShallowForTheBodyHostsNoVisit() {
        var gar = FishSchoolSim.spec(0.484f, Locomotion.FREE_SWIM, ShelterUse.LURKER);
        assertFalse(FishSchoolSim.hosts(ORIGIN, pond(13, 1), gar, VisitShape.SOLITARY),
                "one block of water is too shallow for a 0.48-block fish anywhere");
        assertTrue(FishSchoolSim.hosts(ORIGIN, pond(13, 2), gar, VisitShape.SOLITARY));
    }
}
