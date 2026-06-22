package grill24.fishtastic.block;

import grill24.fishtastic.FishtasticBlockTags;
import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import grill24.fishtastic.item.FishTankCosmeticItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

public class FishTankBlock extends Block implements EntityBlock {
    /** At scale 0.25 each kelp segment is 0.25 blocks tall; 3 segments fills the tank interior exactly. */
    private static final int MAX_KELP_HEIGHT = 3;

    public FishTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return RegistrationApiSided.getInstance().createFishTankBlockEntity(blockPos, blockState);
    }

    @Override
    protected void onPlace(BlockState blockState, Level level, BlockPos blockPos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(blockState, level, blockPos, oldState, movedByPiston);

        if (!level.isClientSide()) {
            // Update connections for this tank
            updateConnections(level, blockPos);

            // Update connections for all adjacent tanks
            for (Direction direction : Direction.values()) {
                BlockPos adjacentPos = blockPos.relative(direction);
                if (level.getBlockEntity(adjacentPos) instanceof FishTankBlockEntity) {
                    updateConnections(level, adjacentPos);
                }
            }
        }
    }

    @Override
    protected BlockState updateShape(BlockState blockState, LevelReader level, ScheduledTickAccess ticks,
                                     BlockPos blockPos, Direction direction, BlockPos neighborPos,
                                     BlockState neighborState, RandomSource random) {
        if (!level.isClientSide()) {
            // Update connections when a neighboring block changes
            if (level instanceof Level worldLevel) {
                updateConnections(worldLevel, blockPos);
            }
        }
        return super.updateShape(blockState, level, ticks, blockPos, direction, neighborPos, neighborState, random);
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        // Update connections for all adjacent tanks
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.relative(direction);
            if (level.getBlockEntity(adjacentPos) instanceof FishTankBlockEntity) {
                updateConnections(level, adjacentPos);
            }
        }
    }

    /**
     * Update connections for a fish tank by detecting adjacent fish tanks.
     */
    private void updateConnections(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof FishTankBlockEntity fishTank) {
            fishTank.updateConnections(level, pos);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState blockState, Level level, BlockPos blockPos, Player player, BlockHitResult blockHitResult) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(blockPos);
            if (be instanceof FishTankBlockEntity fishTank) {
                ItemStack extracted = fishTank.extractItem();
                if (!extracted.isEmpty()) {
                    // Give the item to the player or drop it
                    if (!player.getInventory().add(extracted)) {
                        player.drop(extracted, false);
                    }
                    player.sendSystemMessage(
                        Component.literal("Removed item from fish tank")
                    );
                    return InteractionResult.SUCCESS;
                } else {
                    player.sendSystemMessage(
                        Component.literal("Fish tank is empty")
                    );
                }
            }
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand hand, BlockHitResult blockHitResult) {
        // Cosmetic placement: custom FishTankCosmeticItem or any vanilla item in the #tank_cosmetics tag.
        Block cosmeticBlock = getCosmeticBlock(itemStack);
        if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && cosmeticBlock != null) {
            BlockEntity be = level.getBlockEntity(blockPos);
            if (be instanceof FishTankBlockEntity fishTank) {
                CosmeticGridCell cell = findTargetedCell(player, blockPos, fishTank);
                if (cell != null) {
                    PlacedCosmetic existing = fishTank.getCosmetics().get(cell);
                    // Sea pickle: right-clicking an occupied sea pickle with another sea pickle adds one more pickle.
                    if (existing != null
                            && existing.block() instanceof SeaPickleBlock
                            && cosmeticBlock instanceof SeaPickleBlock) {
                        int current = existing.blockState().getValue(BlockStateProperties.PICKLES);
                        if (current < SeaPickleBlock.MAX_PICKLES) {
                            BlockState newState = existing.blockState()
                                    .setValue(BlockStateProperties.PICKLES, current + 1);
                            fishTank.setCosmetic(cell, new PlacedCosmetic(newState));
                            itemStack.shrink(1);
                            return InteractionResult.SUCCESS;
                        }
                        return InteractionResult.FAIL;
                    }
                    // Kelp: right-clicking an existing kelp cosmetic with another kelp extends it upward.
                    if (existing != null
                            && existing.block() == Blocks.KELP
                            && cosmeticBlock == Blocks.KELP) {
                        if (existing.height() < MAX_KELP_HEIGHT) {
                            fishTank.setCosmetic(cell, new PlacedCosmetic(existing.blockState(), existing.height() + 1));
                            itemStack.shrink(1);
                            return InteractionResult.SUCCESS;
                        }
                        return InteractionResult.FAIL;
                    }
                    // Normal placement into an empty cell.
                    if (existing == null) {
                        BlockState placedState = cosmeticBlock.defaultBlockState();
                        // Any cosmetic with a horizontal-facing property (e.g. the treasure chest)
                        // orients toward the placing player, mirroring firstItemRotation for fish.
                        if (placedState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                            float rotation = calculateRotationTowardPlayer(player, blockPos);
                            placedState = placedState.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.fromYRot(rotation));
                        }
                        fishTank.setCosmetic(cell, new PlacedCosmetic(placedState));
                        itemStack.shrink(1);
                        return InteractionResult.SUCCESS;
                    }
                }
                return InteractionResult.FAIL;
            }
        }

        // Edit mode: cosmetic removal with empty hand.
        if (!level.isClientSide() && FishTankEditModeManager.isInEditMode(player.getUUID())
                && hand == InteractionHand.MAIN_HAND && itemStack.isEmpty()) {
            BlockEntity be = level.getBlockEntity(blockPos);
            if (be instanceof FishTankBlockEntity fishTank) {
                CosmeticGridCell cell = findTargetedCell(player, blockPos, fishTank);
                if (cell != null && fishTank.getCosmetics().containsKey(cell)) {
                    PlacedCosmetic existing = fishTank.getCosmetics().get(cell);
                    // Custom cosmetic item takes priority; fall back to the vanilla block item.
                    Item returnItem = FishTankCosmeticItem.forBlock(existing.block());
                    if (returnItem == null) returnItem = existing.block().asItem();
                    // Sea pickle: decrement one pickle at a time; remove when the last pickle is taken.
                    // Kelp: remove one segment at a time from the top; remove when the last segment is taken.
                    if (existing.block() instanceof SeaPickleBlock) {
                        int current = existing.blockState().getValue(BlockStateProperties.PICKLES);
                        if (current > 1) {
                            fishTank.setCosmetic(cell, new PlacedCosmetic(existing.blockState().setValue(BlockStateProperties.PICKLES, current - 1)));
                        } else {
                            fishTank.removeCosmetic(cell);
                        }
                    } else if (existing.block() == Blocks.KELP && existing.height() > 1) {
                        fishTank.setCosmetic(cell, new PlacedCosmetic(existing.blockState(), existing.height() - 1));
                    } else {
                        fishTank.removeCosmetic(cell);
                    }
                    if (returnItem != Items.AIR) {
                        ItemStack returnStack = new ItemStack(returnItem);
                        if (!player.getInventory().add(returnStack)) {
                            player.drop(returnStack, false);
                        }
                    }
                    return InteractionResult.SUCCESS;
                }
                // No cosmetic targeted in edit mode — consume to prevent display-item extraction.
                return InteractionResult.SUCCESS_SERVER;
            }
        }

        // In MC 26.1.2, useWithoutItem is never automatically called — useItemOn fires
        // even with an empty hand. Delegate withdrawal here when the hand is empty.
        if (itemStack.isEmpty()) {
            // Only act on the main hand to avoid double-firing with the offhand.
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                // Return SUCCESS so the hand swings and the interaction is consumed.
                BlockEntity be = level.getBlockEntity(blockPos);
                if (be instanceof FishTankBlockEntity) {
                    return InteractionResult.SUCCESS;
                }
                return InteractionResult.PASS;
            }
            // Server: delegate to withdrawal logic.
            return useWithoutItem(blockState, level, blockPos, player, blockHitResult);
        }

        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(blockPos);
            if (be instanceof FishTankBlockEntity fishTank) {
                // Check if the player is holding a block item
                if (itemStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                    Block heldBlock = blockItem.getBlock();

                    // Get the player's current customization mode
                    FishTankCustomizationMode mode = FishTankCustomizationModeManager.getMode(player.getUUID());

                    // Check if the block is blacklisted for this part
                    String partName = mode.name().toLowerCase();
                    if (RegistrationApiSided.getInstance().isBlockBlacklisted(heldBlock, partName)) {
                        player.sendSystemMessage(
                            Component.literal("§cThis block cannot be used for fish tank " + partName + " (blacklisted in config)")
                        );
                        return InteractionResult.FAIL;
                    }

                    // Apply the block based on the current mode
                    switch (mode) {
                        case FRAME:
                            fishTank.setFrameBlock(heldBlock);
                            player.sendSystemMessage(
                                Component.literal("Fish tank frame changed to: " +
                                    BuiltInRegistries.BLOCK.getKey(heldBlock))
                            );
                            break;
                        case SAND:
                            fishTank.setSandBlock(heldBlock);
                            player.sendSystemMessage(
                                Component.literal("Fish tank sand changed to: " +
                                    BuiltInRegistries.BLOCK.getKey(heldBlock))
                            );
                            break;
                        case GLASS:
                            fishTank.setGlassBlock(heldBlock);
                            player.sendSystemMessage(
                                Component.literal("Fish tank glass changed to: " +
                                    BuiltInRegistries.BLOCK.getKey(heldBlock))
                            );
                            break;
                    }

                    return InteractionResult.SUCCESS;
                } else if (!itemStack.isEmpty()) {
                    // Non-block item - try to add it to the tank
                    ItemStack toAdd = itemStack.copy();
                    toAdd.setCount(1);

                    // Calculate the rotation based on player's position relative to the block
                    float rotation = calculateRotationTowardPlayer(player, blockPos);

                    if (fishTank.addItem(toAdd, rotation, player)) {
                        itemStack.shrink(1);
                        player.sendSystemMessage(
                            Component.literal("Added item to fish tank")
                        );
                        return InteractionResult.SUCCESS;
                    } else {
                        // Error message is already displayed by canInsertItem if it's a size issue
                        // Only show "tank is full" if the item could have fit
                        if (fishTank.hasItems() && fishTank.getContainerSize() > 0) {
                            // Check if tank is actually full by trying to find an empty slot
                            boolean isFull = true;
                            for (int i = 0; i < fishTank.getContainerSize(); i++) {
                                if (fishTank.getItem(i).isEmpty()) {
                                    isFull = false;
                                    break;
                                }
                            }
                            if (isFull) {
                                player.sendSystemMessage(
                                    Component.literal("Fish tank is full")
                                );
                            }
                        }
                        return InteractionResult.FAIL;
                    }
                }
            }
        }

        // CLIENT SIDE: Must return SUCCESS when holding an item and targeting a fish tank.
        // If we return PASS, Minecraft will proceed to call BlockItem.useOn(), which
        // speculatively places the held block adjacent to the tank, causing cascading
        // chunk rebuilds that race with the server's block entity update and prevent
        // the customization texture from appearing.
        if (level.isClientSide() && !itemStack.isEmpty()) {
            BlockEntity be = level.getBlockEntity(blockPos);
            if (be instanceof FishTankBlockEntity) {
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }

    /**
     * Returns the block to use as a cosmetic from the held stack, or null if the item
     * cannot be used as a cosmetic. Accepts custom {@link FishTankCosmeticItem}s and any
     * vanilla {@link BlockItem} whose block is in the {@code #fishtastic:tank_cosmetics} tag.
     */
    @Nullable
    private static Block getCosmeticBlock(ItemStack stack) {
        if (stack.getItem() instanceof FishTankCosmeticItem custom) return custom.getRenderBlock();
        if (stack.getItem() instanceof BlockItem bi
                && bi.getBlock().defaultBlockState().is(FishtasticBlockTags.TANK_COSMETICS)) return bi.getBlock();
        return null;
    }

    /** Returns the grid cell the player is targeting in the given tank, or null. */
    @Nullable
    private CosmeticGridCell findTargetedCell(Player player, BlockPos blockPos, FishTankBlockEntity tankBE) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = player.blockInteractionRange();
        Vec3 end = eye.add(look.scale(reach));

        // Check existing cosmetic AABBs first (priority for removal)
        CosmeticGridCell fromCosmetic = findTargetedCosmetic(tankBE, eye, end, blockPos);
        if (fromCosmetic != null) return fromCosmetic;

        // Fall back to floor-plane intersection (for placement)
        return findFloorCell(eye, look, blockPos, reach);
    }

    @Nullable
    private CosmeticGridCell findTargetedCosmetic(FishTankBlockEntity be, Vec3 eye, Vec3 end, BlockPos blockPos) {
        CosmeticGridCell closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : be.getCosmetics().entrySet()) {
            CosmeticGridCell cell = entry.getKey();
            CosmeticTransforms.Transform t = CosmeticTransforms.get(entry.getValue().block());
            double wx = blockPos.getX() + cell.localX() + t.offsetX();
            double wy = blockPos.getY() + CosmeticGridCell.FLOOR_Y + t.offsetY();
            double wz = blockPos.getZ() + cell.localZ() + t.offsetZ();
            float half = t.scale() / 2f;
            AABB box = new AABB(wx - half, wy, wz - half, wx + half, wy + t.scale(), wz + half);
            Optional<Vec3> hit = box.clip(eye, end);
            if (hit.isPresent()) {
                double dist = hit.get().distanceToSqr(eye);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = cell;
                }
            }
        }
        return closest;
    }

    @Nullable
    private CosmeticGridCell findFloorCell(Vec3 eye, Vec3 look, BlockPos blockPos, double reach) {
        if (Math.abs(look.y) < 0.001) return null;
        double t = (blockPos.getY() + CosmeticGridCell.FLOOR_Y - eye.y) / look.y;
        if (t < 0 || t > reach) return null;
        double localX = eye.x + t * look.x - blockPos.getX();
        double localZ = eye.z + t * look.z - blockPos.getZ();
        if (localX < 0 || localX >= 1 || localZ < 0 || localZ >= 1) return null;
        int gx = Math.min(CosmeticGridCell.GRID_SIZE - 1, (int)(localX * CosmeticGridCell.GRID_SIZE));
        int gz = Math.min(CosmeticGridCell.GRID_SIZE - 1, (int)(localZ * CosmeticGridCell.GRID_SIZE));
        return new CosmeticGridCell(gx, gz);
    }

    /**
     * Calculate the Y-axis rotation angle for an item to face toward the player.
     * Returns angle in degrees.
     */
    private float calculateRotationTowardPlayer(Player player, BlockPos blockPos) {
        // Get the center of the block
        double blockCenterX = blockPos.getX() + 0.5;
        double blockCenterZ = blockPos.getZ() + 0.5;

        // Get player position
        double playerX = player.getX();
        double playerZ = player.getZ();

        // Calculate direction vector from block to player
        double dx = playerX - blockCenterX;
        double dz = playerZ - blockCenterZ;

        // Calculate angle in radians, then convert to degrees
        // atan2 gives us the angle from the positive X axis
        // We need to adjust because Minecraft's rotation is different
        double angleRadians = Math.atan2(dz, dx);
        float angleDegrees = (float) Math.toDegrees(angleRadians);

        // Adjust to face the player (add 90 degrees because of Minecraft's coordinate system)
        // In Minecraft, 0 degrees is south (+Z), 90 is west (-X), 180 is north (-Z), 270 is east (+X)
        angleDegrees = -angleDegrees + 90f;

        return angleDegrees;
    }
}
