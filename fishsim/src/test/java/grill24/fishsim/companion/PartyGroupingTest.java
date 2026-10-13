package grill24.fishsim.companion;

import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.companion.OwnerScript;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** {@link PartyGrouping}: which party members group up (docs/fish-companions-party.md §3, D7, D8). */
class PartyGroupingTest {

    private static PartyGrouping.Member m(String species, Locomotion l, int slot) {
        GroundGait gait = l == Locomotion.BENTHIC ? GroundGait.CRAWL : null;
        return new PartyGrouping.Member(species, l, gait, null, 0.25f, 100 + slot, slot);
    }

    private static PartyGrouping.Member percher(String species, int slot) {
        return new PartyGrouping.Member(species, Locomotion.BENTHIC, null, null, 0.25f, 100 + slot, slot);
    }

    @Test
    void threeOfOneFreeSwimmerAreOneSchool() {
        List<PartyGrouping.Group> g = PartyGrouping.group(List.of(
                m("bluegill", Locomotion.FREE_SWIM, 0), m("bluegill", Locomotion.FREE_SWIM, 1),
                m("bluegill", Locomotion.FREE_SWIM, 2)));
        assertEquals(1, g.size());
        assertEquals(PartyGrouping.Kind.SCHOOL, g.get(0).kind());
        assertArrayEquals(new int[]{0, 1, 2}, g.get(0).slots());
    }

    @Test
    void twoOfASpeciesPairAndTheOtherIsSolo() {
        List<PartyGrouping.Group> g = PartyGrouping.group(List.of(
                m("bluegill", Locomotion.FREE_SWIM, 0), m("clownfish", Locomotion.FREE_SWIM, 1),
                m("bluegill", Locomotion.FREE_SWIM, 2)));
        assertEquals(2, g.size());
        assertEquals(PartyGrouping.Kind.SCHOOL, g.get(0).kind());
        assertArrayEquals(new int[]{0, 2}, g.get(0).slots());
        assertEquals(PartyGrouping.Kind.SOLO, g.get(1).kind());
        assertArrayEquals(new int[]{1}, g.get(1).slots());
    }

    @Test
    void groundBenthicDuplicatesFormAGroundGroupWithTheirStyle() {
        var a = new PartyGrouping.Member("starfish", Locomotion.BENTHIC, GroundGait.CRAWL,
                BenthicGroup.Formation.LINE, 0.3f, 7, 1);
        var b = new PartyGrouping.Member("starfish", Locomotion.BENTHIC, GroundGait.CRAWL,
                BenthicGroup.Formation.LINE, 0.45f, 8, 2);
        List<PartyGrouping.Group> g = PartyGrouping.group(List.of(b, a));
        assertEquals(1, g.size());
        assertEquals(PartyGrouping.Kind.GROUND, g.get(0).kind());
        assertEquals(BenthicGroup.Formation.LINE, g.get(0).formation());
        assertArrayEquals(new float[]{0.3f, 0.45f}, g.get(0).lengths());
        // A species that names no formation walks in a line.
        List<PartyGrouping.Group> crabs = PartyGrouping.group(List.of(m("crab", Locomotion.BENTHIC, 0),
                m("crab", Locomotion.BENTHIC, 1)));
        assertEquals(BenthicGroup.Formation.LINE, crabs.get(0).formation());
    }

    /** D7: duplicates of the non-schooling classes stay separate members. */
    @Test
    void nonSchoolingDuplicatesStaySeparate() {
        for (Locomotion l : List.of(Locomotion.GLIDE, Locomotion.DRIFT, Locomotion.ANCHORED)) {
            List<PartyGrouping.Group> g = PartyGrouping.group(List.of(m("x", l, 0), m("x", l, 1), m("x", l, 2)));
            assertEquals(3, g.size(), l.name());
            for (PartyGrouping.Group group : g) assertEquals(PartyGrouping.Kind.SOLO, group.kind(), l.name());
        }
        List<PartyGrouping.Group> perchers = PartyGrouping.group(List.of(percher("crab", 0), percher("crab", 1)));
        assertEquals(2, perchers.size());
        for (PartyGrouping.Group group : perchers) assertEquals(PartyGrouping.Kind.SOLO, group.kind());
    }

    @Test
    void differentSpeciesNeverGroupEvenOfOneClass() {
        List<PartyGrouping.Group> g = PartyGrouping.group(List.of(m("a", Locomotion.FREE_SWIM, 0),
                m("b", Locomotion.FREE_SWIM, 1), m("c", Locomotion.BENTHIC, 2)));
        assertEquals(3, g.size());
        for (PartyGrouping.Group group : g) assertEquals(PartyGrouping.Kind.SOLO, group.kind());
    }

    /** The leader is the member in the lowest slot, whatever order the party arrives in; groups come out by lowest slot. */
    @Test
    void leaderIsTheLowestSlotAndGroupsAreInSlotOrder() {
        List<PartyGrouping.Group> g = PartyGrouping.group(List.of(
                m("bluegill", Locomotion.FREE_SWIM, 2), m("eel", Locomotion.ANCHORED, 0),
                m("bluegill", Locomotion.FREE_SWIM, 1)));
        assertEquals(2, g.size());
        assertEquals(0, g.get(0).leader().slot());
        assertEquals(PartyGrouping.Kind.SCHOOL, g.get(1).kind());
        assertEquals(1, g.get(1).leader().slot());
        assertArrayEquals(new int[]{1, 2}, g.get(1).slots());
        assertEquals(g.get(1).leader().seed(), g.get(1).seed());
    }

    @Test
    void emptySlotsAreSimplyAbsent() {
        assertTrue(PartyGrouping.group(List.of()).isEmpty());
        List<PartyGrouping.Group> g = PartyGrouping.group(List.of(m("bluegill", Locomotion.FREE_SWIM, 2)));
        assertEquals(1, g.size());
        assertEquals(PartyGrouping.Kind.SOLO, g.get(0).kind());
    }

    @Test
    void rejectsBadParties() {
        assertThrows(IllegalArgumentException.class, () -> PartyGrouping.group(List.of(
                m("a", Locomotion.FREE_SWIM, 0), m("b", Locomotion.FREE_SWIM, 0))));
        assertThrows(IllegalArgumentException.class, () -> m("a", Locomotion.FREE_SWIM, 3));
        assertThrows(IllegalArgumentException.class, () -> PartyGrouping.group(List.of(
                m("a", Locomotion.FREE_SWIM, 0), m("a", Locomotion.FREE_SWIM, 1),
                m("a", Locomotion.FREE_SWIM, 2), m("a", Locomotion.FREE_SWIM, 2))));
    }

    /** Slot 0 keeps today's seed exactly; other slots of the same stack get distinct seeds. */
    @Test
    void slotZeroKeepsTheStackSeed() {
        Set<Long> seen = new HashSet<>();
        for (long stack : new long[]{0, 1, -1, 42, Long.MAX_VALUE, Long.MIN_VALUE, 0x5DEECE66DL}) {
            assertEquals(stack, PartyGrouping.memberSeed(stack, 0));
            long s1 = PartyGrouping.memberSeed(stack, 1), s2 = PartyGrouping.memberSeed(stack, 2);
            assertNotEquals(stack, s1);
            assertNotEquals(stack, s2);
            assertNotEquals(s1, s2);
            assertTrue(seen.add(s1) && seen.add(s2), "seeds collide across stacks");
        }
    }

    /** Three identical members from slots 0–2 build exactly today's trio and benthic group. */
    @Test
    void identicalPartyIsTodaysGroup() {
        float l = 0.18f;
        long stack = 5;
        List<PartyGrouping.Member> trio = List.of(
                new PartyGrouping.Member("bluegill", Locomotion.FREE_SWIM, null, null, l, stack, 0),
                new PartyGrouping.Member("bluegill", Locomotion.FREE_SWIM, null, null, l, stack, 1),
                new PartyGrouping.Member("bluegill", Locomotion.FREE_SWIM, null, null, l, stack, 2));
        OwnerScript sa = OwnerScript.named("walk_turns", 2), sb = OwnerScript.named("walk_turns", 2);
        CompanionWorld wa = sa.nextWorld(), wb = sb.nextWorld();
        CompanionGroup party = PartyGrouping.group(trio).get(0).school(CompanionTunables.defaults(), Temperament.BASELINE, wa);
        CompanionGroup today = new CompanionGroup(CompanionTunables.defaults(), stack, l, Temperament.BASELINE, wb);
        for (int tick = 0; tick < 400; tick++) {
            if (tick > 0) { wa = sa.nextWorld(); wb = sb.nextWorld(); }
            party.step(wa);
            today.step(wb);
            assertEquals(today.leader().position(), party.leader().position(), "leader t=" + tick);
            for (int i = 0; i < 2; i++) assertEquals(today.follower(i).position(), party.follower(i).position(), "f" + i + " t=" + tick);
        }

        float g = 0.4f;
        List<PartyGrouping.Member> line = List.of(
                new PartyGrouping.Member("starfish", Locomotion.BENTHIC, GroundGait.CRAWL, BenthicGroup.Formation.LINE, g, stack, 0),
                new PartyGrouping.Member("starfish", Locomotion.BENTHIC, GroundGait.CRAWL, BenthicGroup.Formation.LINE, g, stack, 1),
                new PartyGrouping.Member("starfish", Locomotion.BENTHIC, GroundGait.CRAWL, BenthicGroup.Formation.LINE, g, stack, 2));
        OwnerScript ga = OwnerScript.named("stairs_up_down", 2), gb = OwnerScript.named("stairs_up_down", 2);
        wa = ga.nextWorld();
        wb = gb.nextWorld();
        BenthicGroup partyLine = PartyGrouping.group(line).get(0).ground(CompanionTunables.defaults(), Temperament.BASELINE, wa);
        BenthicGroup todayLine = new BenthicGroup(CompanionTunables.defaults(), stack, g, Temperament.BASELINE,
                GroundGait.CRAWL, 3, BenthicGroup.Formation.LINE, wb);
        for (int tick = 0; tick < 400; tick++) {
            if (tick > 0) { wa = ga.nextWorld(); wb = gb.nextWorld(); }
            partyLine.step(wa);
            todayLine.step(wb);
            for (int i = 0; i < 3; i++) assertEquals(todayLine.member(i).position(), partyLine.member(i).position(), "m" + i + " t=" + tick);
        }
    }

    @Test
    void schoolsAndGroupsTakeTwoOrThreeMembers() {
        CompanionWorld world = OwnerScript.named("walk", 1).nextWorld();
        assertThrows(IllegalArgumentException.class, () -> new CompanionGroup(CompanionTunables.defaults(), 1,
                new float[]{0.2f}, Temperament.BASELINE, world));
        assertThrows(IllegalArgumentException.class, () -> new CompanionGroup(CompanionTunables.defaults(), 1,
                new float[]{0.2f, 0.2f, 0.2f, 0.2f}, Temperament.BASELINE, world));
        CompanionGroup pair = new CompanionGroup(CompanionTunables.defaults(), 1, new float[]{0.16f, 0.24f},
                Temperament.BASELINE, world);
        assertEquals(1, pair.followerCount());
        assertEquals(0.24f, pair.follower(0).length());
        assertEquals(0.16f, pair.leaderLength());
    }
}
