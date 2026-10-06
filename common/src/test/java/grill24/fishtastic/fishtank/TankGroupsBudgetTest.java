package grill24.fishtastic.fishtank;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The arithmetic that makes {@link TankGroups#RENDER_MAX_GROUP_SIZE} safe to raise.
 *
 * <p>The tank cap bounds nothing on its own — every measured cost scales with fish, and a tank
 * holds {@link FishTankBlockEntity#CONTAINER_SIZE} of them (docs/fish-tank-group-scaling.md §4).
 * What bounds the frame is {@link TankGroups#perTankFishCap}, so the properties worth locking down
 * are that no legal group can exceed the fish budget however it is shaped, and that a group
 * <em>under</em> the budget freezes nobody.
 */
class TankGroupsBudgetTest {

    @Test
    void noLegalGroupCanExceedTheFishBudget() {
        for (int members = 1; members <= TankGroups.RENDER_MAX_GROUP_SIZE; members++) {
            int[] full = new int[members];
            java.util.Arrays.fill(full, FishTankBlockEntity.CONTAINER_SIZE);
            long worstCase = simulated(full, TankGroups.perTankFishCap(full));
            assertTrue(worstCase <= TankGroups.RENDER_MAX_GROUP_FISH,
                    "a fully-stocked " + members + "-tank group would simulate " + worstCase
                            + " fish, over the " + TankGroups.RENDER_MAX_GROUP_FISH + " budget");
        }
    }

    @Test
    void everyTankMayShowAtLeastOneFish() {
        // A cap of zero would make a large build render as an empty aquarium, which reads as a
        // bug rather than as a budget.
        for (int members = 1; members <= TankGroups.RENDER_MAX_GROUP_SIZE; members++) {
            int[] counts = new int[members];
            java.util.Arrays.fill(counts, FishTankBlockEntity.CONTAINER_SIZE);
            assertTrue(TankGroups.perTankFishCap(counts) >= 1, "cap at " + members + " members");
        }
    }

    @Test
    void aGroupUnderTheBudgetFreezesNobody() {
        // The regression this rule replaced: a flat quota (budget / members) trimmed a busy tank
        // even when the group held a fraction of the budget — a 154-tank group with 87 fish froze
        // the tail of its one 19-fish tank to enforce a bound it was nowhere near.
        int[] members = new int[154];
        members[0] = 19;
        members[1] = 12;
        members[2] = 11;
        int total = 42;
        assertTrue(total <= TankGroups.RENDER_MAX_GROUP_FISH);
        assertEquals(Integer.MAX_VALUE, TankGroups.perTankFishCap(members),
                "a group holding " + total + " fish must not cap any tank");
    }

    @Test
    void anOverBudgetGroupTrimsOnlyTheTanksAboveTheCap() {
        // Only when the total really exceeds the budget does anything stay home — and then the
        // thin tanks lose nothing: the cap is chosen so the total fits, not handed out per tank.
        int[] members = new int[100];
        java.util.Arrays.fill(members, 27);
        int cap = TankGroups.perTankFishCap(members);
        assertTrue(cap < 27, "100 full tanks must be trimmed");
        assertTrue(simulated(members, cap) <= TankGroups.RENDER_MAX_GROUP_FISH);

        int[] uneven = new int[100];
        java.util.Arrays.fill(uneven, 0, 40, 27);   // 40 full tanks…
        java.util.Arrays.fill(uneven, 40, 100, 1);  // …and 60 with a single fish: 1140 total
        assertTrue(simulated(uneven, Integer.MAX_VALUE) > TankGroups.RENDER_MAX_GROUP_FISH);
        int unevenCap = TankGroups.perTankFishCap(uneven);
        assertTrue(unevenCap >= 1, "the spare tanks keep their fish");
        assertTrue(simulated(uneven, unevenCap) <= TankGroups.RENDER_MAX_GROUP_FISH);
    }

    @Test
    void capIsAPureFunctionOfTheGroupsContents() {
        // Load-bearing: the anchor decides who swims and each member separately decides who
        // hovers, in different block entities with no shared state. Both compute the cap from the
        // same member list and the same inventories, so they cannot disagree.
        int[] counts = {5, 27, 19, 1};
        assertEquals(TankGroups.perTankFishCap(counts), TankGroups.perTankFishCap(counts.clone()));
    }

    @Test
    void anEmptyGroupIsUncapped() {
        assertEquals(Integer.MAX_VALUE, TankGroups.perTankFishCap(new int[0]));
        assertEquals(Integer.MAX_VALUE, TankGroups.perTankFishCap(new int[]{0, 0, 0}));
    }

    /** What the group would actually simulate at this cap, over one class. */
    private static long simulated(int[] fishPerTank, int cap) {
        long sum = 0;
        for (int count : fishPerTank) sum += Math.min(count, cap);
        return sum;
    }
}
