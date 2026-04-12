package grill24.fishtastic.block;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
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
        String side = level.isClientSide() ? "CLIENT" : "SERVER";
        Fishtastic.LOGGER.info("[FishTankBlock.onPlace][{}] pos={}, oldState={}", side, blockPos, oldState);

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
        String side = level.isClientSide() ? "CLIENT" : "SERVER";
        Fishtastic.LOGGER.info("[FishTankBlock.updateShape][{}] pos={}, direction={}, neighborPos={}, neighborState={}",
                side, blockPos, direction, neighborPos, neighborState.getBlock());
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
        String side = level.isClientSide() ? "CLIENT" : "SERVER";
        Fishtastic.LOGGER.info("[FishTankBlock.useWithoutItem][{}] pos={}", side, blockPos);
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
        String side = level.isClientSide() ? "CLIENT" : "SERVER";
        Fishtastic.LOGGER.info("[FishTankBlock.useItemOn][{}] Called at pos={}, item={}, hand={}",
                side, blockPos, itemStack.getItem(), hand);

        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(blockPos);
            Fishtastic.LOGGER.info("[FishTankBlock.useItemOn][SERVER] BlockEntity at pos={}: type={}, class={}",
                    blockPos,
                    be != null ? be.getType() : "null",
                    be != null ? be.getClass().getSimpleName() : "null");
            if (be instanceof FishTankBlockEntity fishTank) {
                // Check if the player is holding a block item
                if (itemStack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
                    Block heldBlock = blockItem.getBlock();
                    Fishtastic.LOGGER.info("[FishTankBlock.useItemOn][SERVER] Player holding BlockItem: block={}",
                            BuiltInRegistries.BLOCK.getKey(heldBlock));

                    // Get the player's current customization mode
                    FishTankCustomizationMode mode = FishTankCustomizationModeManager.getMode(player.getUUID());
                    Fishtastic.LOGGER.info("[FishTankBlock.useItemOn][SERVER] Customization mode={}", mode);

                    // Check if the block is blacklisted for this part
                    String partName = mode.name().toLowerCase();
                    if (RegistrationApiSided.getInstance().isBlockBlacklisted(heldBlock, partName)) {
                        Fishtastic.LOGGER.info("[FishTankBlock.useItemOn][SERVER] Block is BLACKLISTED for part={}, returning FAIL", partName);
                        player.sendSystemMessage(
                            Component.literal("§cThis block cannot be used for fish tank " + partName + " (blacklisted in config)")
                        );
                        return InteractionResult.FAIL;
                    }

                    // Log current state BEFORE change
                    Fishtastic.LOGGER.info("[FishTankBlock.useItemOn][SERVER] BEFORE change: frame={}, sand={}, glass={}, openFaces={}",
                            BuiltInRegistries.BLOCK.getKey(fishTank.getFrameBlock()),
                            BuiltInRegistries.BLOCK.getKey(fishTank.getSandBlock()),
                            BuiltInRegistries.BLOCK.getKey(fishTank.getGlassBlock()),
                            fishTank.getOpenFaces());

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

                    // Log state AFTER change
                    Fishtastic.LOGGER.info("[FishTankBlock.useItemOn][SERVER] AFTER change: frame={}, sand={}, glass={}, openFaces={}",
                            BuiltInRegistries.BLOCK.getKey(fishTank.getFrameBlock()),
                            BuiltInRegistries.BLOCK.getKey(fishTank.getSandBlock()),
                            BuiltInRegistries.BLOCK.getKey(fishTank.getGlassBlock()),
                            fishTank.getOpenFaces());

                    Fishtastic.LOGGER.info("[FishTankBlock.useItemOn][SERVER] Returning SUCCESS for block customization");
                    return InteractionResult.SUCCESS;
                } else if (!itemStack.isEmpty()) {
                    // Non-block item - try to add it to the tank
                    Fishtastic.LOGGER.info("[FishTankBlock.useItemOn][SERVER] Non-block item, attempting to add to tank");
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
                Fishtastic.LOGGER.info("[FishTankBlock.useItemOn][CLIENT] Returning SUCCESS to consume interaction (prevents BlockItem placement)");
                return InteractionResult.SUCCESS;
            }
        }

        Fishtastic.LOGGER.info("[FishTankBlock.useItemOn][{}] Returning PASS (end of method)", side);
        return InteractionResult.PASS;
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
