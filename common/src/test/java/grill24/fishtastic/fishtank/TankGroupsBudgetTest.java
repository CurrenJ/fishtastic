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
 * What bounds the frame is {@link TankGroups#perTankFishQuota}, so the property worth locking down
 * is that no legal group can exceed the fish budget however it is shaped.
 */
class TankGroupsBudgetTest {

    @Test
    void noLegalGroupCanExceedTheFishBudget() {
        for (int members = 1; members <= TankGroups.RENDER_MAX_GROUP_SIZE; members++) {
            long worstCase = (long) members * TankGroups.perTankFishQuota(members);
            assertTrue(worstCase <= TankGroups.RENDER_MAX_GROUP_FISH,
                    "a fully-stocked " + members + "-tank group would simulate " + worstCase
                            + " fish, over the " + TankGroups.RENDER_MAX_GROUP_FISH + " budget");
        }
    }

    @Test
    void everyTankMayShowAtLeastOneFish() {
        // A quota of zero would make a large build render as an empty aquarium, which reads as a
        // bug rather than as a budget.
        for (int members = 1; members <= TankGroups.RENDER_MAX_GROUP_SIZE; members++) {
            assertTrue(TankGroups.perTankFishQuota(members) >= 1, "quota at " + members + " members");
        }
    }

    @Test
    void smallGroupsAreUnaffectedByTheBudget() {
        // Below this size the quota exceeds a tank's capacity, so the budget cannot bind and
        // existing builds behave exactly as they did before it existed.
        int unconstrained = TankGroups.RENDER_MAX_GROUP_FISH / FishTankBlockEntity.CONTAINER_SIZE;
        for (int members = 1; members <= unconstrained; members++) {
            assertTrue(TankGroups.perTankFishQuota(members) >= FishTankBlockEntity.CONTAINER_SIZE,
                    "budget must not bind at " + members + " members");
        }
    }

    @Test
    void quotaIsAPureFunctionOfMemberCount() {
        // Load-bearing: the anchor decides who swims and each member separately decides who
        // hovers, in different block entities with no shared state. If those two could disagree a
        // fish would render twice or not at all.
        assertEquals(TankGroups.perTankFishQuota(100), TankGroups.perTankFishQuota(100));
        assertEquals(TankGroups.RENDER_MAX_GROUP_FISH, TankGroups.perTankFishQuota(1));
    }

    @Test
    void degenerateMemberCountDoesNotDivideByZero() {
        assertEquals(TankGroups.RENDER_MAX_GROUP_FISH, TankGroups.perTankFishQuota(0));
    }
}
