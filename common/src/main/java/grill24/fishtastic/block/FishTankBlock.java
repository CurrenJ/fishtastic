package grill24.fishtastic.block;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBlockTags;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.data.TankCapacity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticStructures;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.fishtank.CosmeticPlacement;
import grill24.fishtastic.fishtank.TankGroups;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import grill24.fishtastic.fishtank.TankDiagonal;
import grill24.fishtastic.fishtank.TankEdgeDiagonal;
import grill24.fishtastic.item.FishTankCosmeticItem;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.item.PileOfFishItem;
import grill24.fishtastic.server.QuestTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
            FishtasticItemData.set(stack, grill24.fishtastic.FishtasticDataComponents.FISH_TANK_MATERIALS, fishTank.getMaterials());
            FishtasticItemData.set(stack, grill24.fishtastic.FishtasticDataComponents.FISH_TANK_SHAPE, fishTank.getShape());
        }
        return stack;
    }

    @Override
    protected void onPlace(BlockState blockState, Level level, BlockPos blockPos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(blockState, level, blockPos, oldState, movedByPiston);

        if (!level.isClientSide()) {
            // Update connections for this tank
            updateConnections(level, blockPos);

            // Update connections for all orthogonally adjacent tanks
            for (Direction direction : Direction.values()) {
                BlockPos adjacentPos = blockPos.relative(direction);
                if (level.getBlockEntity(adjacentPos) instanceof FishTankBlockEntity) {
                    updateConnections(level, adjacentPos);
                }
            }

            // Update filledDiagonals for all diagonally adjacent tanks too — vanilla neighbor
            // notifications only ever reach the 6 orthogonal neighbors, so a tank whose diagonal
            // cell is filled by this placement would otherwise never recompute (its filledDiagonals
            // stays stale until something else — an orthogonal neighbor changing — happens to force
            // a recompute), leaving a phantom corner post rendered in the interior of a tank cube.
            for (TankDiagonal diagonal : TankDiagonal.values()) {
                BlockPos diagonalPos = blockPos.relative(diagonal.first()).relative(diagonal.second());
                if (level.getBlockEntity(diagonalPos) instanceof FishTankBlockEntity) {
                    updateConnections(level, diagonalPos);
                }
            }

            // Same reasoning as the diagonal loop above, for edge-diagonal (horizontal×vertical)
            // neighbors — vanilla neighbor notifications never reach these either, so a tank whose
            // edge-diagonal cell is filled by this placement would otherwise never recompute,
            // leaving a phantom frame beam rendered where this new tank now covers the gap.
            for (TankEdgeDiagonal edgeDiagonal : TankEdgeDiagonal.values()) {
                BlockPos edgeDiagonalPos = blockPos.relative(edgeDiagonal.horizontal()).relative(edgeDiagonal.vertical());
                if (level.getBlockEntity(edgeDiagonalPos) instanceof FishTankBlockEntity) {
                    updateConnections(level, edgeDiagonalPos);
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
        // Update connections for all orthogonally adjacent tanks
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.relative(direction);
            if (level.getBlockEntity(adjacentPos) instanceof FishTankBlockEntity) {
                updateConnections(level, adjacentPos);
            }
        }

        // Update filledDiagonals for all diagonally adjacent tanks too — see the matching note in
        // onPlace; a tank whose diagonal cell just emptied out needs the same forced recompute.
        for (TankDiagonal diagonal : TankDiagonal.values()) {
            BlockPos diagonalPos = pos.relative(diagonal.first()).relative(diagonal.second());
            if (level.getBlockEntity(diagonalPos) instanceof FishTankBlockEntity) {
                updateConnections(level, diagonalPos);
            }
        }

        // Update filledEdgeDiagonals for all edge-diagonally adjacent tanks too — see the matching
        // note in onPlace; a tank whose edge-diagonal cell just emptied out needs the same forced
        // recompute.
        for (TankEdgeDiagonal edgeDiagonal : TankEdgeDiagonal.values()) {
            BlockPos edgeDiagonalPos = pos.relative(edgeDiagonal.horizontal()).relative(edgeDiagonal.vertical());
            if (level.getBlockEntity(edgeDiagonalPos) instanceof FishTankBlockEntity) {
                updateConnections(level, edgeDiagonalPos);
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
                // Empty-hand click opens the browser GUI (lists every fish/cosmetic across the
                // whole connected tank group and lets the player remove any of them) instead of
                // blindly popping the last-placed fish — see docs/fish-tank-interaction-redesign.md.
                player.openMenu(fishTank);
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

        // Cosmetic placement: single-cell floor cosmetics, kelp, hanging cosmetics and structures.
        // CosmeticPlacement decides where the held cosmetic goes and whether it fits — the same
        // plan the client previews as a highlighted cell (CosmeticPlacementPreview).
        if (!level.isClientSide() && hand == InteractionHand.MAIN_HAND
                && level.getBlockEntity(blockPos) instanceof FishTankBlockEntity clicked) {
            CosmeticPlacement.Plan plan = CosmeticPlacement.plan(player, clicked, itemStack);
            if (plan != null) {
                if (!plan.valid()) {
                    if (plan.failMessage() != null) player.sendSystemMessage(Component.literal(plan.failMessage()));
                    return InteractionResult.FAIL;
                }
                plan.apply().run();
                itemStack.shrink(1);
                return InteractionResult.SUCCESS;
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
        // (see ServerPlayerGameMode#useItemOn's suppressUsingBlock check), so this method never
        // even runs for that case. It's implemented instead as an Item#use() override on
        // PileOfFishItem/FishtasticFishItem, which does its own raycast — see
        // FishTankBlock#tryShiftExtractFromTargetedTank. The other shift-click interaction, the
        // forced cosmetic placement, hooks ItemStack#useOn (ItemStackUseOnMixin) instead, because
        // it needs the real hit result and must cover vanilla cosmetic items too.
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

                    ItemStack placedStack = toAdd.copy();
                    FishTankBlockEntity target = addToGroupWithFallback(level, fishTank, toAdd, rotation);
                    if (target != null) {
                        itemStack.shrink(1);
                        checkTankQuests(player, target, placedStack);
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
                FishtasticItemData.bundleContentsOrEmpty(itemStack));
        ItemStack popped = contents.removeOne();
        if (popped == null) {
            player.sendSystemMessage(Component.literal("Pile of Fish is empty"));
            return InteractionResult.FAIL;
        }
        float rotation = calculateRotationTowardPlayer(player, blockPos);
        ItemStack placedStack = popped.copy();
        Level level = fishTank.getLevel();
        FishTankBlockEntity target = level != null ? addToGroupWithFallback(level, fishTank, popped, rotation) : null;
        if (target == null) {
            // Tank has no room anywhere in its connected group — leave the pile untouched.
            player.sendSystemMessage(Component.literal("Fish tank is full"));
            return InteractionResult.FAIL;
        }
        FishtasticItemData.setBundleContents(itemStack, contents.toImmutable());
        checkTankQuests(player, target, placedStack);
        return InteractionResult.SUCCESS;
    }

    /**
     * Adds {@code toAdd} to {@code clicked} if it has room; otherwise tries the rest of the
     * clicked tank's connected group in group order, so a full segment doesn't block insertion
     * into a tank the player can plainly see swims as one continuous space — see
     * docs/fish-tank-interaction-redesign.md §4.1. Returns the tank the item actually landed in,
     * or null if every member's budget is spent. Storage stays strictly per-segment; this only
     * spreads a rejected insert across the group instead of failing outright.
     */
    @Nullable
    private static FishTankBlockEntity addToGroupWithFallback(Level level, FishTankBlockEntity clicked, ItemStack toAdd, float rotation) {
        if (clicked.addItem(toAdd, rotation)) {
            return clicked;
        }
        TankGroups.Group group = TankGroups.of(clicked, level, TankGroups.GAMEPLAY_MAX_GROUP_SIZE);
        if (!group.isMultiTank()) {
            return null;
        }
        FishTankBlockEntity fallback = TankCapacity.findSegmentWithRoom(group, toAdd, level);
        if (fallback == null || !fallback.addItem(toAdd, rotation)) {
            return null;
        }
        return fallback;
    }

    /** Re-checks tank-composition quests after a fish is inserted; no-op off the server thread. */
    private static void checkTankQuests(Player player, FishTankBlockEntity fishTank, ItemStack placedStack) {
        if (player instanceof ServerPlayer serverPlayer) {
            QuestTracker.onTankChanged(serverPlayer.level().getServer(), serverPlayer, fishTank, placedStack);
        }
    }

    /**
     * Entry point for the shift-click "pull topmost fish into hand" interaction, called from
     * {@link PileOfFishItem#use} / {@link FishtasticFishItem#use}. It can't live in
     * {@link #useItemOn} because vanilla never calls that method for this case: sneaking with a
     * non-empty hand makes {@code ServerPlayerGameMode#useItemOn} skip {@code Block#useItemOn}
     * entirely (its {@code suppressUsingBlock} check) and call {@code ItemStack#useOn} with the
     * click's hit result instead. A pile's {@code useOn} is the default PASS, so the client falls
     * through to {@code Item#use} from {@code Minecraft#startUseItem} and the server does the same
     * when the follow-up UseItem packet arrives — which is where this hook lives. So this does its
     * own raycast, mirroring what the suppressed block interaction would have targeted.
     *
     * <p>(Shift-clicking a tank <i>cosmetic</i> is the other use of this same suppressed path, but
     * it needs the real hit result, so it hooks {@code ItemStack#useOn} directly via
     * {@code ItemStackUseOnMixin} → {@link grill24.fishtastic.fishtank.CosmeticPlacement#tryForcePlace}.)
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
        PileOfFishItem.combineExtractedFish(player, hand, itemStack, extracted);
        if (!extracted.isEmpty()) {
            // Pile is full — put the fish back in the tank rather than losing it.
            fishTank.addItem(extracted);
            player.sendSystemMessage(Component.literal("Pile of Fish is full"));
            return InteractionResult.FAIL;
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Calculate the Y-axis rotation angle for an item to face toward the player.
     * Returns angle in degrees.
     */
    public static float calculateRotationTowardPlayer(Player player, BlockPos blockPos) {
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
