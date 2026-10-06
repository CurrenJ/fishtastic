package grill24.fishtastic.client.renderer;

import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.TankGroups;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A group's fish budget for one rebuild: per locomotion class, how many fish each member tank may
 * contribute to the shared engine — see {@link TankGroups#perTankFishCap}, which does the
 * arithmetic. Built from the group's live contents, so both halves of the swim/hover split
 * ({@code TankGroupFlock.rebuild} and {@code TankFlockAdapter.rebuildGroupMode}) read the same
 * numbers and cannot disagree about a slot.
 *
 * <p>Counting fish needs both worlds — the item, its resolved animation config — so this lives
 * beside the adapter rather than in {@code TankGroups} with the rule it applies.
 */
final class TankFishBudget {

    /** {@link Integer#MAX_VALUE} means the group's total fits the budget: no cap for that class. */
    private final int[] perClassCap;

    private TankFishBudget(int[] perClassCap) {
        this.perClassCap = perClassCap;
    }

    /** Every class uncapped — what a lone tank's engine needs, where there is no budget at all. */
    static TankFishBudget unlimited() {
        int[] caps = new int[Locomotion.values().length];
        java.util.Arrays.fill(caps, Integer.MAX_VALUE);
        return new TankFishBudget(caps);
    }

    /** The same cap for every class — how {@code GroupSplitTest} drives the split rule directly. */
    static TankFishBudget uniformCap(int cap) {
        int[] caps = new int[Locomotion.values().length];
        java.util.Arrays.fill(caps, cap);
        return new TankFishBudget(caps);
    }

    /**
     * Counts the group's fish per member per class and turns those counts into caps. Members whose
     * block entity is not loaded count as empty, exactly as the collection passes treat them.
     */
    static TankFishBudget forGroup(TankGroups.Group group, Level level) {
        Locomotion[] classes = Locomotion.values();
        java.util.List<BlockPos> members = group.members();
        int[][] fishPerClassPerMember = new int[classes.length][members.size()];
        for (int member = 0; member < members.size(); member++) {
            if (!(level.getBlockEntity(members.get(member)) instanceof FishTankBlockEntity tank)) continue;
            for (int slot = 0; slot < FishTankBlockEntity.CONTAINER_SIZE; slot++) {
                ItemStack stack = tank.getItem(slot);
                if (stack.isEmpty()) continue;
                Locomotion locomotion = TankFlockAdapter.locomotionOf(
                        FishTankBlockEntityRenderer.resolveFishRender(stack, level).animation());
                fishPerClassPerMember[locomotion.ordinal()][member]++;
            }
        }
        int[] caps = new int[classes.length];
        for (int cls = 0; cls < classes.length; cls++) {
            caps[cls] = TankGroups.perTankFishCap(fishPerClassPerMember[cls]);
        }
        return new TankFishBudget(caps);
    }

    /** How many of this tank's fish of {@code locomotion} may join the group, or MAX_VALUE for "all". */
    int perTankCap(Locomotion locomotion) {
        return perClassCap[locomotion.ordinal()];
    }
}
