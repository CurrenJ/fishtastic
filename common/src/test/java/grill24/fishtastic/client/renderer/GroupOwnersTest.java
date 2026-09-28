package grill24.fishtastic.client.renderer;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.Locomotion;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The partition deciding which member tank draws which of a group's fish (docs/fish-tank-group-scaling.md
 * §9.8). It replaces an anchor that drew every fish in the build, and the two ways it can go wrong are
 * both silent: a fish owned by nobody is simply never drawn, and a fish owned by two tanks is drawn
 * twice — neither shows up in any cheaper check, because both produce a frame that looks plausible.
 *
 * <p>What makes it correct in game is that the owner is the <b>nearest</b> member: a block entity
 * renderer only runs while its own chunk section is on screen, so a fish has to be owned by a tank
 * standing where the fish is, not merely by some tank in the same group.
 */
class GroupOwnersTest {

    /**
     * Three tanks in an L at y=64 — (0,0), (1,0), (0,1) in xz. The fourth cell of the 2×2 box is
     * bare, giving the grid a hole to own from a distance rather than from itself.
     */
    private static final List<BlockPos> L_SHAPE = List.of(
            new BlockPos(0, 64, 0),
            new BlockPos(1, 64, 0),
            new BlockPos(0, 64, 1));
    private static final BlockPos MIN = new BlockPos(0, 64, 0);

    private static GroupOwners lShape() {
        return GroupOwners.build(L_SHAPE, MIN, 2, 1, 2);
    }

    @Test
    @DisplayName("a fish inside a tank is owned by that tank, never by a neighbour")
    void aFishInsideATankIsOwnedByThatTank() {
        GroupOwners owners = lShape();
        for (int i = 0; i < L_SHAPE.size(); i++) {
            BlockPos member = L_SHAPE.get(i);
            // Sampled across the block, including its far corners, which is where a floor/ceil slip
            // would hand a fish to the tank next door.
            for (float dx : new float[] {0.05f, 0.5f, 0.95f}) {
                for (float dz : new float[] {0.05f, 0.5f, 0.95f}) {
                    assertEquals(i, owners.ownerOf(member.getX() + dx, member.getY() + 0.5f, member.getZ() + dz),
                            "cell " + (member.getX() + dx) + "," + (member.getZ() + dz));
                }
            }
        }
    }

    @Test
    @DisplayName("every cell of the group's box has an owner, so no fish can be drawn by nobody")
    void everyCellHasAnOwner() {
        GroupOwners owners = lShape();
        for (int x = 0; x < 2; x++) {
            for (int z = 0; z < 2; z++) {
                int owner = owners.ownerOf(x + 0.5f, 64.5f, z + 0.5f);
                assertTrue(owner >= 0 && owner < L_SHAPE.size(),
                        "cell " + x + "," + z + " owned by " + owner);
            }
        }
    }

    @Test
    @DisplayName("the bare cell of the box is owned by the nearest tank, not by a far one")
    void aBareCellGoesToTheNearestTank() {
        GroupOwners owners = lShape();
        int owner = owners.ownerOf(1.5f, 64.5f, 1.5f);
        assertEquals(1, distance(L_SHAPE.get(owner), 1, 64, 1),
                "the tank owning the bare cell (1,64,1) must be one step away from it");
    }

    @Test
    @DisplayName("ownership is always the nearest member, sampled across and around the box")
    void ownershipIsAlwaysTheNearestMember() {
        GroupOwners owners = lShape();
        // A cell of margin on every side, because a fish can be pushed just outside the box by
        // separation and scatter, and has to land on the member nearest to it either way.
        for (float x = -1.5f; x <= 2.5f; x += 0.25f) {
            for (float z = -1.5f; z <= 2.5f; z += 0.25f) {
                int cellX = (int) Math.floor(x);
                int cellZ = (int) Math.floor(z);
                int owner = owners.ownerOf(x, 64.5f, z);
                int ownerDistance = distance(L_SHAPE.get(owner), cellX, 64, cellZ);
                int nearest = Integer.MAX_VALUE;
                for (BlockPos member : L_SHAPE) {
                    nearest = Math.min(nearest, distance(member, cellX, 64, cellZ));
                }
                assertEquals(nearest, ownerDistance,
                        "fish at " + x + "," + z + " went to a tank " + ownerDistance + " cells away,"
                                + " but one stands " + nearest + " away");
            }
        }
    }

    @Test
    @DisplayName("a member's local origin rebases the group origin exactly onto that member")
    void localOffsetRebasesOntoEachMember() {
        // The draw loop works in the drawing tank's model space, so the group's anchor-relative
        // origin has to be rebased onto whichever member is drawing. A sign error here puts fish in
        // the wrong tanks, at exactly the offset between the two — plausible-looking, and silent.
        TankGroupFlock group = new TankGroupFlock();
        group.anchorPos = new BlockPos(0, 64, 0);
        group.offsetX = 1.5f;
        group.offsetY = 0.5f;
        group.offsetZ = 2.25f;

        float renderX = 0.37f;
        float renderY = -0.11f;
        float renderZ = 0.83f;
        for (BlockPos member : L_SHAPE) {
            assertEquals(group.anchorPos.getX() + group.offsetX + renderX,
                    member.getX() + group.localOffsetX(member) + renderX, 1e-4f,
                    "x rebased onto " + member);
            assertEquals(group.anchorPos.getY() + group.offsetY + renderY,
                    member.getY() + group.localOffsetY(member) + renderY, 1e-4f,
                    "y rebased onto " + member);
            assertEquals(group.anchorPos.getZ() + group.offsetZ + renderZ,
                    member.getZ() + group.localOffsetZ(member) + renderZ, 1e-4f,
                    "z rebased onto " + member);
        }
    }

    @Test
    @DisplayName("every fish lands in exactly one tank's bucket")
    void everyFishLandsInExactlyOneBucket() {
        // The grid above says who owns a position; this says the bucketing agrees with it, which is
        // what the renderer actually iterates. A fish in no bucket is never drawn; a fish in two is
        // submitted twice, with the second submission overwriting the first's render state — the
        // double draw the shared render states depend on not happening.
        TankGroupFlock group = new TankGroupFlock();
        // Two tanks in a row on X. The group origin sits at the bounding box centre, one block along
        // from the anchor, and fish positions are measured from there.
        group.setGroupGeometry(L_SHAPE.subList(0, 2), MIN, 2, 1, 1,
                MIN, 1.0f, 0.5f, 0.5f);
        group.engine().rebuild(new FishSpec[] {
                new FishSpec(0.5f, Locomotion.FREE_SWIM, false, 1),
                new FishSpec(0.5f, Locomotion.FREE_SWIM, false, 1),
                new FishSpec(0.5f, Locomotion.FREE_SWIM, false, 1),
                new FishSpec(0.5f, Locomotion.FREE_SWIM, false, 1)}, 1234L, 0f, 1, 0f, 0f, 0f);

        // Fish 0 and 1 into the anchor's cell (world x in [0,1)), 2 and 3 into its neighbour's.
        // Three in one bucket also walks the growth path, which starts empty.
        float[] renderX = group.engine().renderX;
        renderX[0] = -0.75f;
        renderX[1] = -0.25f;
        renderX[2] = 0.25f;
        renderX[3] = 0.75f;
        group.refreshBuckets();

        int seen = 0;
        for (int member = 0; member < 2; member++) {
            int[] bucket = group.bucket(member);
            for (int k = 0; k < group.bucketSize(member); k++) {
                int fish = bucket[k];
                assertTrue(fish >= 0 && fish < 4, "bucket " + member + " holds fish " + fish);
                // The member holding the fish must be the tank the fish's own position is in.
                boolean inAnchor = renderX[fish] < 0f;
                assertEquals(inAnchor ? 0 : 1, member, "fish " + fish + " bucketed to tank " + member);
                seen++;
            }
        }
        assertEquals(4, seen, "every fish must be drawn exactly once");
    }

    private static int distance(BlockPos member, int x, int y, int z) {
        return Math.abs(member.getX() - x) + Math.abs(member.getY() - y) + Math.abs(member.getZ() - z);
    }
}
