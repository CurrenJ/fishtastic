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

    /** D13: every free swimmer in the party joins one school, whatever its species. */
    @Test
    void freeSwimmersOfAnySpeciesFormOneMixedShoal() {
        List<PartyGrouping.Group> g = PartyGrouping.group(List.of(
                sized("bluegill", Locomotion.FREE_SWIM, 0.30f, 0), sized("clownfish", Locomotion.FREE_SWIM, 0.10f, 1),
                sized("tang", Locomotion.FREE_SWIM, 0.20f, 2)));
        assertEquals(1, g.size());
        assertEquals(PartyGrouping.Kind.SCHOOL, g.get(0).kind());
        // The smallest leads whatever its species (D9).
        assertArrayEquals(new int[]{1, 2, 0}, g.get(0).slots());
        assertEquals("clownfish", g.get(0).leader().species());
        // A ground benthic of another species stays out; two free swimmers still shoal.
        List<PartyGrouping.Group> h = PartyGrouping.group(List.of(m("a", Locomotion.FREE_SWIM, 0),
                m("crab", Locomotion.BENTHIC, 1), m("b", Locomotion.FREE_SWIM, 2)));
        assertEquals(2, h.size());
        assertEquals(PartyGrouping.Kind.SCHOOL, h.get(0).kind());
        assertArrayEquals(new int[]{0, 2}, h.get(0).slots());
        assertEquals(PartyGrouping.Kind.SOLO, h.get(1).kind());
    }

    /** With the mixed shoal off (the A/B switch), schools are one species, as before D13. */
    @Test
    void aPredatorSwimsApartFromItsPreyShoal() {
        // D21: pike + two prey. The prey shoal; the pike is a solo.
        List<PartyGrouping.Group> g = PartyGrouping.group(List.of(
                predator("pike", 0), m("bluegill", Locomotion.FREE_SWIM, 1), m("perch", Locomotion.FREE_SWIM, 2)));
        assertEquals(2, g.size());
        assertEquals(PartyGrouping.Kind.SOLO, g.get(0).kind());
        assertEquals("pike", g.get(0).leader().species());
        assertEquals(PartyGrouping.Kind.SCHOOL, g.get(1).kind());
        assertArrayEquals(new int[]{1, 2}, g.get(1).slots());
        // Predators of different species still shoal together, and a predator's duplicates school.
        List<PartyGrouping.Group> h = PartyGrouping.group(List.of(
                predator("pike", 0), m("bluegill", Locomotion.FREE_SWIM, 1), predator("gar", 2)));
        assertEquals(2, h.size());
        assertArrayEquals(new int[]{0, 2}, h.get(0).slots());
        assertEquals(PartyGrouping.Kind.SOLO, h.get(1).kind());
        List<PartyGrouping.Group> k = PartyGrouping.group(List.of(predator("pike", 0), predator("pike", 1)));
        assertEquals(1, k.size());
        assertEquals(PartyGrouping.Kind.SCHOOL, k.get(0).kind());
    }

    private static PartyGrouping.Member predator(String species, int slot) {
        return new PartyGrouping.Member(species, Locomotion.FREE_SWIM, null, null, 0.25f, 100 + slot, slot, true);
    }

    @Test
    void mixedShoalOffKeepsSchoolsToOneSpecies() {
        List<PartyGrouping.Group> g = PartyGrouping.group(List.of(
                m("bluegill", Locomotion.FREE_SWIM, 0), m("clownfish", Locomotion.FREE_SWIM, 1),
                m("bluegill", Locomotion.FREE_SWIM, 2)), false);
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

    /** Only free swimmers mix: other classes of different species never group. */
    @Test
    void differentSpeciesOfOtherClassesNeverGroup() {
        List<PartyGrouping.Group> g = PartyGrouping.group(List.of(m("crab", Locomotion.BENTHIC, 0),
                m("star", Locomotion.BENTHIC, 1), m("eel", Locomotion.ANCHORED, 2)));
        assertEquals(3, g.size());
        for (PartyGrouping.Group group : g) assertEquals(PartyGrouping.Kind.SOLO, group.kind());
        List<PartyGrouping.Group> off = PartyGrouping.group(List.of(m("a", Locomotion.FREE_SWIM, 0),
                m("b", Locomotion.FREE_SWIM, 1), m("c", Locomotion.BENTHIC, 2)), false);
        assertEquals(3, off.size());
        for (PartyGrouping.Group group : off) assertEquals(PartyGrouping.Kind.SOLO, group.kind());
    }

    /** Equal sizes: the leader is the member in the lowest slot, whatever order the party arrives in; groups come out by lowest slot. */
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

    private static PartyGrouping.Member sized(String species, Locomotion l, float length, int slot) {
        GroundGait gait = l == Locomotion.BENTHIC ? GroundGait.CRAWL : null;
        return new PartyGrouping.Member(species, l, gait, null, length, 100 + slot, slot);
    }

    /** D9: the smallest member leads, the rest follow smallest first; size, never the player, picks it. */
    @Test
    void smallestLeadsAndFollowersComeSmallestFirst() {
        List<PartyGrouping.Group> g = PartyGrouping.group(List.of(
                sized("bluegill", Locomotion.FREE_SWIM, 0.24f, 0), sized("bluegill", Locomotion.FREE_SWIM, 0.09f, 1),
                sized("bluegill", Locomotion.FREE_SWIM, 0.16f, 2)));
        assertEquals(1, g.size());
        assertEquals(1, g.get(0).leaderSlot());
        assertEquals(0, g.get(0).lowestSlot());
        assertArrayEquals(new int[]{1, 2, 0}, g.get(0).slots());
        assertArrayEquals(new float[]{0.09f, 0.16f, 0.24f}, g.get(0).lengths());
        // The whole group's randomness is the leader's: its slot's member seed.
        assertEquals(PartyGrouping.memberSeed(101, 1), g.get(0).seed());
        // Ground formations follow the same rule.
        List<PartyGrouping.Group> line = PartyGrouping.group(List.of(
                sized("starfish", Locomotion.BENTHIC, 0.5f, 0), sized("starfish", Locomotion.BENTHIC, 0.3f, 2)));
        assertArrayEquals(new int[]{2, 0}, line.get(0).slots());
    }

    /** D9 ties go to the lower slot, so identical members keep today's leader; groups keep lowest-slot order. */
    @Test
    void sizeTiesGoToTheLowerSlotAndGroupsStayInLowestSlotOrder() {
        List<PartyGrouping.Group> g = PartyGrouping.group(List.of(
                sized("eel", Locomotion.ANCHORED, 0.5f, 1), sized("bluegill", Locomotion.FREE_SWIM, 0.2f, 2),
                sized("bluegill", Locomotion.FREE_SWIM, 0.2f, 0)));
        assertEquals(2, g.size());
        assertEquals(PartyGrouping.Kind.SCHOOL, g.get(0).kind());
        assertArrayEquals(new int[]{0, 2}, g.get(0).slots());
        assertEquals(1, g.get(1).leaderSlot());
        // A school led from a higher slot still sits where its lowest slot puts it.
        List<PartyGrouping.Group> h = PartyGrouping.group(List.of(
                sized("bluegill", Locomotion.FREE_SWIM, 0.3f, 0), sized("eel", Locomotion.ANCHORED, 0.5f, 1),
                sized("bluegill", Locomotion.FREE_SWIM, 0.1f, 2)));
        assertEquals(PartyGrouping.Kind.SCHOOL, h.get(0).kind());
        assertEquals(2, h.get(0).leaderSlot());
        assertEquals(PartyGrouping.Kind.SOLO, h.get(1).kind());
    }

    /**
     * D10's explicit cap (opt-in, {@link CompanionTunables#groupPaceCap}): a ground leader bigger than
     * a member walks at that member's pace; identical members and smallest-first groups are uncapped.
     */
    @Test
    void groundLeaderIsPacedToItsSlowestMember() {
        CompanionWorld world = OwnerScript.named("walk_flat", 1).nextWorld();
        CompanionTunables t = CompanionTunables.defaults();
        t.groupPaceCap = true;
        BenthicGroup big = new BenthicGroup(t, 1, new float[]{0.5f, 0.4f, 0.3f}, Temperament.BASELINE,
                GroundGait.CRAWL, BenthicGroup.Formation.LINE, world);
        assertEquals(big.member(2).groundPace(), big.leader().groundPaceCap(), 1e-12);
        BenthicGroup small = new BenthicGroup(t, 1, new float[]{0.3f, 0.4f, 0.5f}, Temperament.BASELINE,
                GroundGait.CRAWL, BenthicGroup.Formation.LINE, world);
        assertEquals(Double.POSITIVE_INFINITY, small.leader().groundPaceCap());
        BenthicGroup same = new BenthicGroup(t, 1, 0.4f, Temperament.BASELINE, GroundGait.CRAWL, 3,
                BenthicGroup.Formation.LINE, world);
        assertEquals(Double.POSITIVE_INFINITY, same.leader().groundPaceCap());
        CompanionTunables off = CompanionTunables.defaults();
        assertFalse(off.groupPaceCap, "the cap is opt-in: D9 already makes the leader the slowest walker");
        BenthicGroup uncapped = new BenthicGroup(off, 1, new float[]{0.5f, 0.4f, 0.3f}, Temperament.BASELINE,
                GroundGait.CRAWL, BenthicGroup.Formation.LINE, world);
        assertEquals(Double.POSITIVE_INFINITY, uncapped.leader().groundPaceCap());
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
