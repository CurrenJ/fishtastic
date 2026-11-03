package grill24.fishtastic.client.ambient;

import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.school.VisitShape;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ambient group sizing and identity (docs/ambient-fish-schools-design-spec.md §4.1). */
class AmbientGroupsTest {

    @Test
    void sizesStayInTheShapeRangeAndSchoolsReachTwelve() {
        Set<Integer> school = new HashSet<>();
        for (long id = 1; id < 5_000; id++) {
            for (VisitShape shape : VisitShape.values()) {
                int n = AmbientGroups.size(id * 0x9E3779B97F4A7C15L, shape, 64);
                assertTrue(n >= AmbientGroups.minMembers(shape) && n <= AmbientGroups.maxMembers(shape), shape + " " + n);
                if (shape == VisitShape.SCHOOL) school.add(n);
            }
        }
        assertEquals(Set.of(6, 7, 8, 9, 10, 11, 12), school, "D1: every school size from 6 to 12 occurs");
    }

    @Test
    void theChunkAllowanceCapsAGroup() {
        assertEquals(2, AmbientGroups.size(42L, VisitShape.SCHOOL, 2));
        assertEquals(1, AmbientGroups.size(42L, VisitShape.PAIR, 0));
    }

    @Test
    void memberZeroIsTheGroupAndMembersAreDistinctAndStable() {
        long group = 0x1234_5678_9ABCL;
        assertEquals(group, AmbientGroups.memberId(group, 0), "the server checks observations by the roster's ID");
        Set<Long> ids = new HashSet<>();
        for (int i = 0; i < 12; i++) ids.add(AmbientGroups.memberId(group, i));
        assertEquals(12, ids.size());
        assertEquals(AmbientGroups.memberId(group, 7), AmbientGroups.memberId(group, 7));
    }

    @Test
    void onlyFreeSwimmersSchool() {
        assertEquals(VisitShape.SCHOOL, AmbientGroups.shapeFor(VisitShape.SCHOOL, Locomotion.FREE_SWIM));
        assertEquals(VisitShape.LOOSE_GROUP, AmbientGroups.shapeFor(VisitShape.SCHOOL, Locomotion.GLIDE));
        assertEquals(VisitShape.PAIR, AmbientGroups.shapeFor(VisitShape.PAIR, Locomotion.BENTHIC));
    }

    @Test
    void groupSlotsDivideTheOldFishSlots() {
        assertEquals(0, AmbientGroups.groupSlots(0));
        assertEquals(1, AmbientGroups.groupSlots(1));
        assertEquals(2, AmbientGroups.groupSlots(2));
        assertEquals(3, AmbientGroups.groupSlots(6));
        assertEquals(8, AmbientGroups.groupSlots(16));
    }
}
