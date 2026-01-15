package grill24.fishtastic.examples;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

import java.util.EnumSet;
import java.util.Set;

/**
 * Example code demonstrating how to use the Fish Tank multi-block connection system.
 *
 * NOTE: As of the automatic connection update, fish tanks now automatically detect
 * and connect to adjacent fish tanks when placed. Manual connection is still supported
 * for custom scenarios.
 */
public class FishTankConnectionExample {

    /**
     * Example: Place fish tanks - they automatically connect!
     * When you place a fish tank next to another fish tank, they automatically
     * detect each other and open the connecting faces.
     */
    public void automaticConnection() {
        // Just place fish tanks adjacent to each other in-game
        // They will automatically connect!
        //
        // Example: Place tank A, then place tank B to the east
        // - Tank A will automatically open its EAST face
        // - Tank B will automatically open its WEST face
        // - They form a connected 2x1 tank
    }

    /**
     * Example: Create a standalone fish tank (all faces closed)
     * This still works for setting initial properties.
     */
    public void createStandaloneTank(FishTankBlockEntity blockEntity) {
        // Set frame and sand blocks
        blockEntity.setFrameBlock(Blocks.OAK_PLANKS);
        blockEntity.setSandBlock(Blocks.SAND);
        blockEntity.setGlassBlock(Blocks.BLUE_STAINED_GLASS);

        // Note: Open faces are automatically managed based on adjacent tanks
        // Manual setting will be overridden on next block update
    }

    /**
     * Example: Manual connection (for custom scenarios)
     * You can still manually control connections if needed.
     */
    public void manualConnection(FishTankBlockEntity northTank, FishTankBlockEntity southTank) {
        // Manually open faces (useful for testing or special cases)
        northTank.setFaceOpen(Direction.SOUTH, true);
        southTank.setFaceOpen(Direction.NORTH, true);

        // Note: These manual settings will be overridden if neighboring blocks change
        // Use this for custom scenarios where automatic detection isn't desired
    }

    /**
     * Example: Create a vertical column of tanks
     */
    public void createVerticalColumn(FishTankBlockEntity bottomTank, FishTankBlockEntity middleTank, FishTankBlockEntity topTank) {
        // Bottom tank - open top
        bottomTank.setFaceOpen(Direction.UP, true);

        // Middle tank - open both top and bottom
        Set<Direction> middleOpenFaces = EnumSet.of(Direction.UP, Direction.DOWN);
        middleTank.setOpenFaces(middleOpenFaces);

        // Top tank - open bottom
        topTank.setFaceOpen(Direction.DOWN, true);
    }

    /**
     * Example: Create a 2x2x2 cube of connected tanks (center tank)
     */
    public void createCubeCenterTank(FishTankBlockEntity centerTank) {
        // Center tank of a cube has all 6 faces open
        Set<Direction> allFaces = EnumSet.allOf(Direction.class);
        centerTank.setOpenFaces(allFaces);

        // This will use permutation 63 (all faces open)
        // The model will only show corner supports, no walls
    }

    /**
     * Example: Get the permutation index for a tank
     */
    public void checkPermutation(FishTankBlockEntity blockEntity) {
        Set<Direction> openFaces = blockEntity.getOpenFaces();

        // Calculate permutation index manually
        int index = 0;
        for (Direction dir : Direction.values()) {
            if (openFaces.contains(dir)) {
                index |= (1 << dir.ordinal());
            }
        }

        System.out.println("Tank is using frame model permutation: " + index);
        System.out.println("Open faces: " + openFaces);
    }

    /**
     * Example: Progressive connection - start with standalone, then connect
     */
    public void progressiveConnection(FishTankBlockEntity tank) {
        // Start as standalone (permutation 0)
        tank.setOpenFaces(EnumSet.noneOf(Direction.class));

        // Connect to east neighbor (permutation 32 - bit 5 set)
        tank.setFaceOpen(Direction.EAST, true);

        // Also connect to south neighbor (permutation 32 + 8 = 40)
        tank.setFaceOpen(Direction.SOUTH, true);

        // Model will automatically update to show open faces
    }

    /**
     * Example: Change frame material after connection
     */
    public void changeFrameMaterial(FishTankBlockEntity blockEntity) {
        // Set initial frame
        blockEntity.setFrameBlock(Blocks.OAK_PLANKS);

        // Connect to neighbor
        blockEntity.setFaceOpen(Direction.NORTH, true);

        // Change to different frame material
        // The model system will regenerate with new texture
        blockEntity.setFrameBlock(Blocks.SPRUCE_PLANKS);
    }

    /**
     * Example: Permutation index breakdown
     */
    public void permutationBreakdown() {
        // Bit mapping (Direction.ordinal() values):
        // Bit 0 (value 1):  DOWN
        // Bit 1 (value 2):  UP
        // Bit 2 (value 4):  NORTH
        // Bit 3 (value 8):  SOUTH
        // Bit 4 (value 16): WEST
        // Bit 5 (value 32): EAST

        // Example permutations:
        // 0: No faces open (standalone tank)
        // 1: Only DOWN open
        // 2: Only UP open
        // 3: UP and DOWN open (vertical tube)
        // 4: Only NORTH open
        // 12: NORTH and SOUTH open (horizontal tube)
        // 48: EAST and WEST open (horizontal tube)
        // 60: All horizontal faces open, top/bottom closed (ring)
        // 63: All faces open (center of cube, only corner supports)
    }
}

