package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.data.SwarmConfig;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * Server-side game tests for FishTankBlockEntity's Container implementation and cosmetic
 * placement bookkeeping. Mirrors WormBinGameTests's setBlock + getBlockEntity pattern.
 * Rendering/composite-model code and the world-adjacency part of updateConnections are
 * out of scope — only inventory and face/cosmetic state bookkeeping are covered here.
 */
public final class FishTankGameTests {

    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private static final BlockPos TANK_POS = new BlockPos(1, 1, 1);

    private FishTankGameTests() {}

    private static FishTankBlockEntity placeFishTank(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(TANK_POS, FishtasticBlocks.FISH_TANK.value());
        return helper.getBlockEntity(TANK_POS, FishTankBlockEntity.class);
    }

    // -------------------------------------------------------------------------
    // Container: addItem
    // -------------------------------------------------------------------------

    /**
     * addItem into an empty tank succeeds; isEmpty()/hasItems() flip accordingly;
     * getFirstItem() returns the inserted stack.
     */
    public static void addItemIntoEmptyTankSucceeds(GameTestHelper helper) {
        FishTankBlockEntity tank = placeFishTank(helper);
        helper.assertTrue(tank.isEmpty() && !tank.hasItems(), "Fresh tank must start empty");

        ItemStack stack = new ItemStack(Items.DIAMOND, 5);
        helper.assertTrue(tank.addItem(stack), "Insert into an empty tank must succeed");
        helper.assertTrue(!tank.isEmpty() && tank.hasItems(), "Tank must report non-empty after an insert");

        ItemStack first = tank.getFirstItem();
        helper.assertTrue(first.getItem() == Items.DIAMOND && first.getCount() == 5,
            "getFirstItem must return the inserted stack, got " + first);
        helper.succeed();
    }

    /**
     * addItem merges into an existing matching stack up to getMaxStackSize() before
     * spilling the remainder into a new slot.
     */
    public static void addItemMergesIntoExistingStackBeforeNewSlot(GameTestHelper helper) {
        FishTankBlockEntity tank = placeFishTank(helper);
        helper.assertTrue(tank.addItem(new ItemStack(Items.DIAMOND, 10)), "First insert must succeed");

        ItemStack second = new ItemStack(Items.DIAMOND, 5);
        helper.assertTrue(tank.addItem(second), "Second insert of the same item must succeed via merge");
        helper.assertTrue(second.isEmpty(), "Fully merged input stack must be drained to empty");
        helper.assertTrue(tank.getItem(0).getCount() == 15,
            "Existing slot must grow by the merged amount, got " + tank.getItem(0).getCount());

        ItemStack overflowing = new ItemStack(Items.DIAMOND, 60);
        helper.assertTrue(tank.addItem(overflowing), "Overflowing insert must still succeed by spilling into a new slot");
        helper.assertTrue(tank.getItem(0).getCount() == 64,
            "First slot must be topped up to max stack size, got " + tank.getItem(0).getCount());
        helper.assertTrue(tank.getItem(1).getCount() == 11,
            "Remainder must spill into the next empty slot, got " + tank.getItem(1).getCount());
        helper.succeed();
    }

    /**
     * addItem returns false and leaves the tank unchanged once the active swarm cap is reached —
     * items beyond that would be stored but never rendered (FishTankBlockEntityRenderer only
     * draws up to swarm.count() items). Items.DIAMOND has no fish_profile entry, so it resolves
     * to SwarmConfig.DEFAULT rather than CONTAINER_SIZE (27); see FishTankBlockEntity#addItem.
     */
    public static void addItemFailsOnceSwarmCapReached(GameTestHelper helper) {
        FishTankBlockEntity tank = placeFishTank(helper);
        int cap = SwarmConfig.DEFAULT.count();
        for (int i = 0; i < cap; i++) {
            helper.assertTrue(tank.addItem(new ItemStack(Items.DIAMOND, 64)),
                "Slot " + i + " insert must succeed while under the swarm cap (" + cap + ")");
        }

        ItemStack overflow = new ItemStack(Items.DIAMOND, 64);
        boolean result = tank.addItem(overflow);
        helper.assertTrue(!result, "addItem must fail once the swarm cap (" + cap + ") is reached");
        helper.assertTrue(overflow.getCount() == 64, "Rejected stack must be left unchanged");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Container: extractItem (LIFO)
    // -------------------------------------------------------------------------

    /**
     * extractItem removes the last-filled slot first (LIFO) and returns EMPTY when the tank is empty.
     */
    public static void extractItemRemovesLastSlotLifoOrder(GameTestHelper helper) {
        FishTankBlockEntity tank = placeFishTank(helper);
        helper.assertTrue(tank.extractItem().isEmpty(), "extractItem on an empty tank must return EMPTY");

        tank.addItem(new ItemStack(Items.DIAMOND, 1));
        tank.addItem(new ItemStack(Items.EMERALD, 1));
        tank.addItem(new ItemStack(Items.GOLD_INGOT, 1));

        ItemStack extracted = tank.extractItem();
        helper.assertTrue(extracted.getItem() == Items.GOLD_INGOT,
            "extractItem must remove the most-recently-filled slot first, got " + extracted.getItem());

        ItemStack extractedNext = tank.extractItem();
        helper.assertTrue(extractedNext.getItem() == Items.EMERALD,
            "Second extract must return the next-most-recent slot, got " + extractedNext.getItem());
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // First-item rotation
    // -------------------------------------------------------------------------

    /**
     * getFirstItemRotation reflects the rotation passed when slot 0 was filled, and is
     * unaffected by later additions to other slots.
     */
    public static void firstItemRotationReflectsSlotZeroInsert(GameTestHelper helper) {
        FishTankBlockEntity tank = placeFishTank(helper);
        tank.addItem(new ItemStack(Items.DIAMOND, 1), 90f);
        helper.assertTrue(tank.getFirstItemRotation() == 90f,
            "Rotation passed for slot 0 must be stored, got " + tank.getFirstItemRotation());

        tank.addItem(new ItemStack(Items.EMERALD, 1), 270f);
        helper.assertTrue(tank.getFirstItemRotation() == 90f,
            "Rotation must remain unaffected by inserts into later slots, got " + tank.getFirstItemRotation());
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Cosmetics
    // -------------------------------------------------------------------------

    /**
     * setCosmetic/getCosmetics/removeCosmetic round-trip for a CosmeticGridCell key.
     */
    public static void cosmeticsRoundTrip(GameTestHelper helper) {
        FishTankBlockEntity tank = placeFishTank(helper);
        helper.assertTrue(tank.getCosmetics().isEmpty(), "Fresh tank must have no cosmetics");

        CosmeticGridCell cell = new CosmeticGridCell(1, 2);
        PlacedCosmetic cosmetic = new PlacedCosmetic(Blocks.KELP.defaultBlockState(), 3);
        tank.setCosmetic(cell, cosmetic);
        helper.assertTrue(cosmetic.equals(tank.getCosmetics().get(cell)),
            "getCosmetics must reflect the placed cosmetic at its cell");

        tank.removeCosmetic(cell);
        helper.assertTrue(tank.getCosmetics().isEmpty(), "Cosmetic must be gone after removeCosmetic");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Open faces
    // -------------------------------------------------------------------------

    /**
     * getOpenFaces/setFaceOpen/setOpenFaces round-trip the face-state bookkeeping itself
     * (the world-adjacency detection in updateConnections is out of scope).
     */
    public static void openFacesRoundTrip(GameTestHelper helper) {
        FishTankBlockEntity tank = placeFishTank(helper);
        helper.assertTrue(tank.getOpenFaces().isEmpty(), "Fresh tank must have no open faces");

        tank.setFaceOpen(Direction.NORTH, true);
        helper.assertTrue(tank.getOpenFaces().contains(Direction.NORTH), "setFaceOpen(true) must add the face");

        tank.setFaceOpen(Direction.NORTH, false);
        helper.assertTrue(!tank.getOpenFaces().contains(Direction.NORTH), "setFaceOpen(false) must remove the face");

        tank.setOpenFaces(Set.of(Direction.UP, Direction.DOWN));
        Set<Direction> faces = tank.getOpenFaces();
        helper.assertTrue(faces.size() == 2 && faces.contains(Direction.UP) && faces.contains(Direction.DOWN),
            "setOpenFaces must replace the entire face set, got " + faces);
        helper.succeed();
    }
}
