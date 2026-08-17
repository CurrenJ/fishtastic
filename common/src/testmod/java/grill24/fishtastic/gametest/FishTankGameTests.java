package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.data.TankCapacity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * Server-side game tests for FishTankBlockEntity's Container implementation, cosmetic
 * placement bookkeeping, and shape-based connection gating. Mirrors WormBinGameTests's
 * setBlock + getBlockEntity pattern. Rendering/composite-model code is out of scope — only
 * block-entity state (inventory, face/cosmetic bookkeeping, real world-adjacency
 * updateConnections) is covered here.
 */
public final class FishTankGameTests {

    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private static final BlockPos TANK_POS = new BlockPos(1, 1, 1);
    private static final BlockPos TANK_POS_EAST = new BlockPos(2, 1, 1);

    private FishTankGameTests() {}

    private static FishTankBlockEntity placeFishTank(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(TANK_POS, FishtasticBlocks.FISH_TANK.value());
        return helper.getBlockEntity(TANK_POS, FishTankBlockEntity.class);
    }

    /** Places a second tank immediately east of {@link #TANK_POS}, for connection-gating tests. */
    private static FishTankBlockEntity placeEastNeighborFishTank(GameTestHelper helper) {
        helper.setBlock(TANK_POS_EAST, FishtasticBlocks.FISH_TANK.value());
        return helper.getBlockEntity(TANK_POS_EAST, FishTankBlockEntity.class);
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
     * addItem returns false and leaves the tank unchanged once TankCapacity's size-based budget
     * is spent — items beyond that would be stored but never rendered. Items.DIAMOND has no
     * fish_profile entry, so it falls back to the 50cm reference length, costing exactly 1.0 of
     * TankCapacity.BASE_BUDGET (5.0) per stack; see FishTankBlockEntity#addItem.
     */
    public static void addItemFailsOnceSwarmCapReached(GameTestHelper helper) {
        FishTankBlockEntity tank = placeFishTank(helper);
        float costPerDiamond = TankCapacity.costOf(new ItemStack(Items.DIAMOND, 64), helper.getLevel());
        int cap = (int) (TankCapacity.BASE_BUDGET / costPerDiamond);
        for (int i = 0; i < cap; i++) {
            helper.assertTrue(tank.addItem(new ItemStack(Items.DIAMOND, 64)),
                "Slot " + i + " insert must succeed while under the tank capacity budget (" + cap + ")");
        }

        ItemStack overflow = new ItemStack(Items.DIAMOND, 64);
        boolean result = tank.addItem(overflow);
        helper.assertTrue(!result, "addItem must fail once the tank capacity budget (" + cap + ") is spent");
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

    // -------------------------------------------------------------------------
    // Shape-based connection gating (real world-adjacency updateConnections)
    // -------------------------------------------------------------------------

    /**
     * Two adjacent tanks sharing a shape (and therefore a connectionCollection) open the
     * faces between them — the actual world-adjacency path in updateConnections, not just the
     * face-state bookkeeping covered by {@link #openFacesRoundTrip}.
     */
    public static void sameShapeNeighborsConnect(GameTestHelper helper) {
        FishTankBlockEntity west = placeFishTank(helper);
        FishTankBlockEntity east = placeEastNeighborFishTank(helper);
        west.setShape(FishTankShape.TRIMMED);
        east.setShape(FishTankShape.TRIMMED);

        west.updateConnections(helper.getLevel(), west.getBlockPos());
        east.updateConnections(helper.getLevel(), east.getBlockPos());

        helper.assertTrue(west.getOpenFaces().contains(Direction.EAST),
            "TRIMMED tank must open its EAST face toward a same-shape TRIMMED neighbor");
        helper.assertTrue(east.getOpenFaces().contains(Direction.WEST),
            "TRIMMED tank must open its WEST face toward a same-shape TRIMMED neighbor");
        helper.succeed();
    }

    /**
     * TRIMMED and REINFORCED are different shapes but deliberately curated into the same
     * connectionCollection (both share STANDARD's) — by design, per-request, all three shipped
     * shapes are meant to freely interconnect. This is the "curated family" case the
     * connectionCollection mechanism exists to support, as opposed to the every-shape-isolated
     * default.
     */
    public static void crossShapeNeighborsInSameFamilyConnect(GameTestHelper helper) {
        FishTankBlockEntity west = placeFishTank(helper);
        FishTankBlockEntity east = placeEastNeighborFishTank(helper);
        west.setShape(FishTankShape.TRIMMED);
        east.setShape(FishTankShape.REINFORCED);

        west.updateConnections(helper.getLevel(), west.getBlockPos());
        east.updateConnections(helper.getLevel(), east.getBlockPos());

        helper.assertTrue(west.getOpenFaces().contains(Direction.EAST),
            "TRIMMED tank must open its EAST face toward a same-family REINFORCED neighbor");
        helper.assertTrue(east.getOpenFaces().contains(Direction.WEST),
            "REINFORCED tank must open its WEST face toward a same-family TRIMMED neighbor");
        helper.succeed();
    }

    /**
     * A STANDARD tank (today's default shape) and a REINFORCED tank connect too — same curated
     * family as {@link #crossShapeNeighborsInSameFamilyConnect}, confirming STANDARD wasn't left
     * out of the shared collection.
     */
    public static void standardAndReinforcedNeighborsConnect(GameTestHelper helper) {
        FishTankBlockEntity west = placeFishTank(helper);
        FishTankBlockEntity east = placeEastNeighborFishTank(helper);
        // west stays at its default shape: STANDARD
        east.setShape(FishTankShape.REINFORCED);

        west.updateConnections(helper.getLevel(), west.getBlockPos());
        east.updateConnections(helper.getLevel(), east.getBlockPos());

        helper.assertTrue(west.getOpenFaces().contains(Direction.EAST),
            "STANDARD tank must open its EAST face toward a same-family REINFORCED neighbor");
        helper.assertTrue(east.getOpenFaces().contains(Direction.WEST),
            "REINFORCED tank must open its WEST face toward a same-family STANDARD neighbor");
        helper.succeed();
    }

    /**
     * All shipped shapes share one connectionCollection, so every new shape connects to a
     * STANDARD tank (and, by the same mechanism, to every other shape). Confirms the
     * "all shapes freely interconnect" decision from the shape-variants plan.
     */
    public static void newShapesConnectToStandard(GameTestHelper helper) {
        for (FishTankShape shape : new FishTankShape[]{FishTankShape.STURDY, FishTankShape.FACETED, FishTankShape.BASTION,
                FishTankShape.HONED, FishTankShape.RAMPART, FishTankShape.ORNATE, FishTankShape.SHAGGY, FishTankShape.BRAMBLE,
                FishTankShape.TOOTH, FishTankShape.FILM}) {
            FishTankBlockEntity west = placeFishTank(helper);
            FishTankBlockEntity east = placeEastNeighborFishTank(helper);
            west.setShape(shape);
            // east stays STANDARD
            west.updateConnections(helper.getLevel(), west.getBlockPos());
            east.updateConnections(helper.getLevel(), east.getBlockPos());
            helper.assertTrue(west.getOpenFaces().contains(Direction.EAST),
                shape + " must open its EAST face toward a STANDARD neighbor (shared collection)");
            helper.assertTrue(east.getOpenFaces().contains(Direction.WEST),
                "STANDARD must open its WEST face toward a " + shape + " neighbor (shared collection)");
        }
        helper.succeed();
    }

    /**
     * The new shapes connect to each other too — two different non-STANDARD shapes are in the same
     * shared collection, the cross-shape case the connectionCollection mechanism exists to support.
     */
    public static void newShapesConnectToEachOther(GameTestHelper helper) {
        FishTankBlockEntity west = placeFishTank(helper);
        FishTankBlockEntity east = placeEastNeighborFishTank(helper);
        west.setShape(FishTankShape.FACETED);
        east.setShape(FishTankShape.BASTION);
        west.updateConnections(helper.getLevel(), west.getBlockPos());
        east.updateConnections(helper.getLevel(), east.getBlockPos());
        helper.assertTrue(west.getOpenFaces().contains(Direction.EAST),
            "FACETED must open its EAST face toward a BASTION neighbor (shared collection)");
        helper.assertTrue(east.getOpenFaces().contains(Direction.WEST),
            "BASTION must open its WEST face toward a FACETED neighbor (shared collection)");
        helper.succeed();
    }
}
