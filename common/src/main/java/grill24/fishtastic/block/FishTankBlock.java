package grill24.fishtastic.block;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBlockTags;
import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticStructures;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.item.FishTankCosmeticItem;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.item.PileOfFishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
    protected ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state, boolean includeData) {
        // Materials + shape are copied on every pick-block (ctrl or not) — only contents
        // (fish/cosmetics, via includeData's vanilla block-entity-data path) are gated behind
        // ctrl. Connectivity (open faces/waxed) is never copied; see
        // FishTankBlockEntity#saveCustomOnly.
        ItemStack stack = super.getCloneItemStack(level, pos, state, includeData);
        if (level.getBlockEntity(pos) instanceof FishTankBlockEntity fishTank) {
            stack.set(grill24.fishtastic.FishtasticDataComponents.FISH_TANK_MATERIALS.value(), fishTank.getMaterials());
            stack.set(grill24.fishtastic.FishtasticDataComponents.FISH_TANK_SHAPE.value(), fishTank.getShape());
        }
        return stack;
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
        // A held Fish Tank must never become decorative content in another tank — always fall
        // through to PASS so vanilla's normal BlockItem placement (a new adjacent tank) runs,
        // exactly like it already does today via the shift-click path (which bypasses this whole
        // method — see tryShiftExtractFromTargetedTank's javadoc). This makes that placement
        // behavior the default for a held tank, not just a shift-click side effect.
        if (itemStack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof FishTankBlock) {
            return InteractionResult.PASS;
        }

        // Wax / unwax: honeycomb stops the tank opening NEW connections on any face (existing
        // connections are untouched — see FishTankBlockEntity#updateConnections); an axe clears
        // it. Mirrors vanilla's copper wax/scrape interaction, but as block-entity state rather
        // than a swap to a separate registered block, since the tank already carries a BE for its
        // materials/shape/contents. Must run before the generic "add held item as display
        // content" fallback below, which would otherwise swallow the honeycomb/axe as decor —
        // but only when there's an actual wax/unwax action to take, so a honeycomb on an
        // already-waxed tank (or an axe on an unwaxed one) still falls through to that fallback
        // like any other held item would.
        //
        // levelEvent/playSound take `null` rather than `player`: vanilla's HoneycombItem/AxeItem
        // pass the acting player because Block#useItemOn runs on both sides (the client predicts
        // its own local sound/particle, so the server's broadcast deliberately excludes that
        // player to avoid doubling it up). This block only runs server-side, so there's no client
        // prediction to avoid doubling — passing the player would just make the actor unable to
        // hear or see their own action.
        if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND) {
            BlockEntity waxBe = level.getBlockEntity(blockPos);
            if (waxBe instanceof FishTankBlockEntity fishTank) {
                if (itemStack.getItem() instanceof HoneycombItem && !fishTank.isWaxed()) {
                    fishTank.setWaxed(true);
                    itemStack.shrink(1);
                    level.levelEvent(null, 3003, blockPos, 0);
                    return InteractionResult.SUCCESS;
                }
                if (itemStack.getItem() instanceof AxeItem && fishTank.isWaxed()) {
                    fishTank.setWaxed(false);
                    level.playSound(null, blockPos, SoundEvents.AXE_WAX_OFF, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.levelEvent(null, 3004, blockPos, 0);
                    itemStack.hurtAndBreak(1, player, hand);
                    return InteractionResult.SUCCESS;
                }
            }
        }

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

        // Structure cosmetic placement: custom FishTankStructureCosmeticItem spanning multiple cells.
        ResourceKey<CosmeticStructure> cosmeticStructureId = getCosmeticStructureId(itemStack);
        if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND && cosmeticStructureId != null) {
            BlockEntity be = level.getBlockEntity(blockPos);
            if (be instanceof FishTankBlockEntity fishTank) {
                return placeStructureCosmetic(level, blockPos, player, itemStack, fishTank, cosmeticStructureId);
            }
        }

        // Edit mode: cosmetic removal with empty hand.
        if (!level.isClientSide() && FishTankEditModeManager.isInEditMode(player.getUUID())
                && hand == InteractionHand.MAIN_HAND && itemStack.isEmpty()) {
            BlockEntity be = level.getBlockEntity(blockPos);
            if (be instanceof FishTankBlockEntity fishTank) {
                CosmeticGridCell cell = findTargetedCell(player, blockPos, fishTank);
                CosmeticGridCell structureAnchor = cell != null ? fishTank.getStructureAnchor(cell) : null;
                if (structureAnchor != null) {
                    return removeStructureCosmetic(player, fishTank, structureAnchor);
                }
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

        // Pile-specific interaction takes priority over the generic "insert held item as display
        // content" fallback below: a plain click with a Pile of Fish in hand pops just its top
        // fish into the tank instead of inserting the whole pile as a single display item.
        //
        // The shift-click "pull topmost fish into hand" interaction is NOT handled here — vanilla
        // suppresses Block#useItemOn entirely whenever the player sneaks with a non-empty hand
        // (see ServerPlayerGameMode#useItemOn's suppressUsingBlock check; item.doesSneakBypassUse
        // defaults to false), so this method never even runs for that case. It's implemented
        // instead as an Item#use() override on PileOfFishItem/FishtasticFishItem, which does its
        // own raycast — see FishTankBlock#tryShiftExtractFromTargetedTank.
        if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND
                && itemStack.getItem() instanceof PileOfFishItem && !player.isShiftKeyDown()) {
            BlockEntity be = level.getBlockEntity(blockPos);
            if (be instanceof FishTankBlockEntity fishTank) {
                return popPileTopIntoTank(player, blockPos, itemStack, fishTank);
            }
        }

        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(blockPos);
            if (be instanceof FishTankBlockEntity fishTank) {
                if (!itemStack.isEmpty()) {
                    // Try to add the held item to the tank as display content.
                    ItemStack toAdd = itemStack.copy();
                    toAdd.setCount(1);

                    // Calculate the rotation based on player's position relative to the block
                    float rotation = calculateRotationTowardPlayer(player, blockPos);

                    if (fishTank.addItem(toAdd, rotation)) {
                        itemStack.shrink(1);
                        player.sendSystemMessage(
                            Component.literal("Added item to fish tank")
                        );
                        return InteractionResult.SUCCESS;
                    } else {
                        // addItem only fails when there's no room left (no mergeable stack, no empty slot)
                        player.sendSystemMessage(
                            Component.literal("Fish tank is full")
                        );
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

    /** Pops just the top fish off a held Pile of Fish and adds it to the tank as display content. */
    private InteractionResult popPileTopIntoTank(Player player, BlockPos blockPos, ItemStack itemStack, FishTankBlockEntity fishTank) {
        BundleContents.Mutable contents = new BundleContents.Mutable(
                itemStack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
        ItemStack popped = contents.removeOne();
        if (popped == null) {
            player.sendSystemMessage(Component.literal("Pile of Fish is empty"));
            return InteractionResult.FAIL;
        }
        float rotation = calculateRotationTowardPlayer(player, blockPos);
        if (!fishTank.addItem(popped, rotation)) {
            // Tank has no room — leave the pile untouched.
            player.sendSystemMessage(Component.literal("Fish tank is full"));
            return InteractionResult.FAIL;
        }
        itemStack.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
        player.sendSystemMessage(Component.literal("Added item to fish tank"));
        return InteractionResult.SUCCESS;
    }

    /**
     * Entry point for the shift-click "pull topmost fish into hand" interaction, called from
     * {@link PileOfFishItem#use} / {@link FishtasticFishItem#use}. It can't live in
     * {@link #useItemOn} because vanilla never calls that method for this case: sneaking with a
     * non-empty hand makes {@code ServerPlayerGameMode#useItemOn} skip {@code Block#useItemOn}
     * entirely and fall through to {@code Item#use} instead (see
     * {@code ItemStack#doesSneakBypassUse}, which defaults to false for ordinary items). So this
     * does its own raycast, mirroring what the suppressed block interaction would have targeted.
     *
     * @return {@code null} if the player isn't sneaking or isn't targeting a fish tank with an
     * eligible item, so the caller can fall back to its normal {@code use()} behavior.
     */
    @Nullable
    public static InteractionResult tryShiftExtractFromTargetedTank(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        boolean isPile = itemStack.getItem() instanceof PileOfFishItem;
        if (!player.isShiftKeyDown() || !(isPile || PileOfFishItem.canInsertInPile(itemStack))) {
            return null;
        }
        // Mirrors Item#getPlayerPOVHitResult (protected, not accessible from here) — this
        // reconstructs the block the player is looking at, since the suppressed block
        // interaction never gave us a BlockHitResult to work with.
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.calculateViewVector(player.getXRot(), player.getYRot()).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockEntity be = level.getBlockEntity(hit.getBlockPos());
        if (!(be instanceof FishTankBlockEntity fishTank)) {
            return null;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        return extractTopFishIntoHand(player, hand, itemStack, fishTank);
    }

    /**
     * Extracts the topmost fish from the tank into the held item. If a Pile of Fish is held, the
     * fish is added to it directly; if a single fish item is held instead, it's combined with the
     * extracted fish into a new pile (replacing the held stack, or split off into a new stack
     * alongside the remainder when more than one was held).
     */
    private static InteractionResult extractTopFishIntoHand(Player player, InteractionHand hand, ItemStack itemStack, FishTankBlockEntity fishTank) {
        ItemStack extracted = fishTank.extractItem();
        if (extracted.isEmpty()) {
            player.sendSystemMessage(Component.literal("Fish tank is empty"));
            return InteractionResult.FAIL;
        }
        if (itemStack.getItem() instanceof PileOfFishItem) {
            BundleContents.Mutable contents = new BundleContents.Mutable(
                    itemStack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
            contents.tryInsert(extracted);
            if (!extracted.isEmpty()) {
                // Pile is full — put the fish back in the tank rather than losing it.
                fishTank.addItem(extracted);
                player.sendSystemMessage(Component.literal("Pile of Fish is full"));
                return InteractionResult.FAIL;
            }
            itemStack.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
        } else {
            BundleContents.Mutable contents = new BundleContents.Mutable(BundleContents.EMPTY);
            contents.tryInsert(extracted);
            contents.tryInsert(itemStack.copyWithCount(1));
            ItemStack newPile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
            newPile.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
            if (itemStack.getCount() == 1) {
                player.setItemInHand(hand, newPile);
            } else {
                itemStack.shrink(1);
                if (!player.getInventory().add(newPile)) {
                    player.drop(newPile, false);
                }
            }
        }
        player.sendSystemMessage(Component.literal("Removed item from fish tank"));
        return InteractionResult.SUCCESS;
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

    /** Returns the structure to place from the held stack, or null if it isn't a structure cosmetic item. */
    @Nullable
    private static ResourceKey<CosmeticStructure> getCosmeticStructureId(ItemStack stack) {
        if (stack.getItem() instanceof FishTankStructureCosmeticItem custom) return custom.getStructureId();
        return null;
    }

    /**
     * Resolves the structure, rotates its footprint by the placing player's 4-way facing, validates
     * every resulting cell (in-bounds and unoccupied by either single-block or structure cosmetics),
     * and places it. Footprint cells are rotated before any validation runs, so the shape checked is
     * always the shape that gets rendered.
     */
    private InteractionResult placeStructureCosmetic(Level level, BlockPos blockPos, Player player, ItemStack itemStack,
                                                       FishTankBlockEntity fishTank, ResourceKey<CosmeticStructure> structureId) {
        Optional<CosmeticStructure> structure = level.registryAccess()
                .lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY)
                .getOptional(structureId);
        if (structure.isEmpty()) {
            grill24.fishtastic.Fishtastic.LOGGER.warn("[FishTankBlock] cosmetic structure lookup returned nothing for id={}", structureId);
            player.sendSystemMessage(Component.literal("That cosmetic is no longer available"));
            return InteractionResult.FAIL;
        }

        CosmeticGridCell anchor = findTargetedCell(player, blockPos, fishTank);
        if (anchor == null) {
            return InteractionResult.FAIL;
        }

        Rotation rotation = rotationFromPlayerFacing(player);

        List<CosmeticGridCell> footprintCells = new ArrayList<>(structure.get().footprintCells().size());
        for (CosmeticStructure.GridOffset offset : structure.get().footprintCells()) {
            CosmeticStructure.GridOffset rotated = CosmeticStructures.rotateFootprintCell(rotation, offset);
            int gx = anchor.gridX() + rotated.dx();
            int gz = anchor.gridZ() + rotated.dz();
            if (!CosmeticGridCell.isValid(gx, gz)) {
                player.sendSystemMessage(Component.literal("Not enough room to place that here"));
                return InteractionResult.FAIL;
            }
            CosmeticGridCell cell = new CosmeticGridCell(gx, gz);
            if (fishTank.getCosmetics().containsKey(cell) || fishTank.getStructureAnchor(cell) != null) {
                player.sendSystemMessage(Component.literal("That space is already occupied"));
                return InteractionResult.FAIL;
            }
            footprintCells.add(cell);
        }

        fishTank.setStructureCosmetic(anchor, new FishTankBlockEntity.PlacedStructureCosmetic(structureId, rotation), footprintCells);
        itemStack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    /** Maps the placing player's 4-way horizontal facing to the {@link Rotation} that turns a
     * structure authored facing south to face the same way. */
    private static Rotation rotationFromPlayerFacing(Player player) {
        Direction facing = player.getDirection();
        for (Rotation rotation : Rotation.values()) {
            if (rotation.rotate(Direction.SOUTH) == facing) {
                return rotation;
            }
        }
        return Rotation.NONE;
    }

    /** Removes the whole structure anchored at {@code anchor} and returns its item to the player. */
    private InteractionResult removeStructureCosmetic(Player player, FishTankBlockEntity fishTank, CosmeticGridCell anchor) {
        FishTankBlockEntity.PlacedStructureCosmetic placed = fishTank.getStructureCosmetics().get(anchor);
        fishTank.removeStructureCosmetic(anchor);
        if (placed != null) {
            FishTankStructureCosmeticItem returnItem = FishTankStructureCosmeticItem.forStructure(placed.structureId());
            if (returnItem != null) {
                ItemStack returnStack = new ItemStack(returnItem);
                if (!player.getInventory().add(returnStack)) {
                    player.drop(returnStack, false);
                }
            }
        }
        return InteractionResult.SUCCESS;
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

    /** Fixed hit-box height for structure footprint cells — independent of the actual part model,
     * since a footprint cell may host parts of any height; generous enough to be easy to target. */
    private static final float STRUCTURE_HIT_HEIGHT = 0.5f;

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
        // Structure cosmetics: test the union of every occupied footprint cell (not per-part), so
        // aiming anywhere within a placed structure's footprint hits it, matching single-cosmetic behavior.
        for (CosmeticGridCell cell : be.getStructureCellIndex().keySet()) {
            double wx = blockPos.getX() + cell.localX();
            double wy = blockPos.getY() + CosmeticGridCell.FLOOR_Y;
            double wz = blockPos.getZ() + cell.localZ();
            double half = CosmeticGridCell.CELL_WIDTH / 2.0;
            AABB box = new AABB(wx - half, wy, wz - half, wx + half, wy + STRUCTURE_HIT_HEIGHT, wz + half);
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
