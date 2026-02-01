package grill24.fishtastic.block;

import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FishTankBlock extends Block implements EntityBlock {
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

        if (!level.isClientSide) {
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
    protected BlockState updateShape(BlockState blockState, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos blockPos, BlockPos neighborPos) {
        if (!level.isClientSide()) {
            // Update connections when a neighboring block changes
            updateConnections((Level) level, blockPos);
        }
        return super.updateShape(blockState, direction, neighborState, level, blockPos, neighborPos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        // If the block is being replaced by a different block (not just state change)
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                // Update connections for all adjacent tanks
                for (Direction direction : Direction.values()) {
                    BlockPos adjacentPos = pos.relative(direction);
                    if (level.getBlockEntity(adjacentPos) instanceof FishTankBlockEntity) {
                        updateConnections(level, adjacentPos);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
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
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(blockPos);
            if (be instanceof FishTankBlockEntity fishTank) {
                ItemStack extracted = fishTank.extractItem();
                if (!extracted.isEmpty()) {
                    // Give the item to the player or drop it
                    if (!player.getInventory().add(extracted)) {
                        player.drop(extracted, false);
                    }
                    player.displayClientMessage(
                        Component.literal("Removed item from fish tank"),
                        true
                    );
                    return InteractionResult.SUCCESS;
                } else {
                    player.displayClientMessage(
                        Component.literal("Fish tank is empty"),
                        true
                    );
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack itemStack, BlockState blockState, Level level, BlockPos blockPos, Player player, InteractionHand hand, BlockHitResult blockHitResult) {
        if (!level.isClientSide) {
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
                        player.displayClientMessage(
                            Component.literal("§cThis block cannot be used for fish tank " + partName + " (blacklisted in config)"),
                            true
                        );
                        return ItemInteractionResult.FAIL;
                    }

                    // Apply the block based on the current mode
                    switch (mode) {
                        case FRAME:
                            fishTank.setFrameBlock(heldBlock);
                            player.displayClientMessage(
                                Component.literal("Fish tank frame changed to: " +
                                    BuiltInRegistries.BLOCK.getKey(heldBlock)),
                                true
                            );
                            break;
                        case SAND:
                            fishTank.setSandBlock(heldBlock);
                            player.displayClientMessage(
                                Component.literal("Fish tank sand changed to: " +
                                    BuiltInRegistries.BLOCK.getKey(heldBlock)),
                                true
                            );
                            break;
                        case GLASS:
                            fishTank.setGlassBlock(heldBlock);
                            player.displayClientMessage(
                                Component.literal("Fish tank glass changed to: " +
                                    BuiltInRegistries.BLOCK.getKey(heldBlock)),
                                true
                            );
                            break;
                    }

                    return ItemInteractionResult.SUCCESS;
                } else if (!itemStack.isEmpty()) {
                    // Non-block item - try to add it to the tank
                    ItemStack toAdd = itemStack.copy();
                    toAdd.setCount(1);

                    // Calculate the rotation based on player's position relative to the block
                    float rotation = calculateRotationTowardPlayer(player, blockPos);

                    if (fishTank.addItem(toAdd, rotation, player)) {
                        itemStack.shrink(1);
                        player.displayClientMessage(
                            Component.literal("Added item to fish tank"),
                            true
                        );
                        return ItemInteractionResult.SUCCESS;
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
                                player.displayClientMessage(
                                    Component.literal("Fish tank is full"),
                                    true
                                );
                            }
                        }
                        return ItemInteractionResult.FAIL;
                    }
                }
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
