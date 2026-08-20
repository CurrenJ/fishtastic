package grill24.fishtastic.block;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import grill24.fishtastic.item.PileOfFishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A placeable stack of up to {@link FishPileBlockEntity#MAX_FISH} fish, decanted one at a time
 * from a {@link PileOfFishItem} or a lone fish item. Plain right-click adds one fish — either onto
 * the top of a solid, non-tank, non-pile surface (starting a new pile) or directly onto an existing
 * pile; shift-right-click always removes one fish from a targeted pile. Rendered entirely by
 * {@link grill24.fishtastic.client.renderer.FishPileBlockEntityRenderer} — this block's own model
 * contributes no geometry.
 */
public class FishPileBlock extends Block implements EntityBlock {
    /** Mirrors the block entity's actual fish count, so {@link #getShape} can size the hitbox to
     * the real stack height without a block-entity lookup (which vanilla shape queries — entity
     * collision, raycasts — run far too often for that to be cheap). Kept in sync by every
     * mutation via {@link #syncFishCount}. */
    public static final IntegerProperty FISH_COUNT = IntegerProperty.create("fish_count", 1, FishPileBlockEntity.MAX_FISH);

    // One flush-stacked shape per possible fish count, precomputed from the same render geometry
    // FishPileBlockEntityRenderer draws with (see FishPileBlockEntity.RENDER_BASE_Y/RENDER_LAYER_HEIGHT)
    // so the hitbox top always lands exactly on top of the visible stack.
    private static final VoxelShape[] SHAPES_BY_FISH_COUNT = buildShapes();

    private static VoxelShape[] buildShapes() {
        int max = FishPileBlockEntity.MAX_FISH;
        VoxelShape[] shapes = new VoxelShape[max + 1]; // index 0 is never used — FISH_COUNT's minimum is 1
        for (int count = 1; count <= max; count++) {
            float topY = FishPileBlockEntity.RENDER_BASE_Y + count * FishPileBlockEntity.RENDER_LAYER_HEIGHT;
            double topPx = Math.min(16.0, topY * 16.0);
            shapes[count] = Block.box(0, 0, 0, 16, topPx, 16);
        }
        return shapes;
    }

    public FishPileBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FISH_COUNT, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FISH_COUNT);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES_BY_FISH_COUNT[state.getValue(FISH_COUNT)];
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FishPileBlockEntity(pos, state);
    }

    /** Updates {@link #FISH_COUNT} to match the pile's current contents and notifies clients. */
    private static void syncFishCount(Level level, BlockPos pos, BlockState state, FishPileBlockEntity pile) {
        int count = Mth.clamp(pile.getFish().size(), 1, FishPileBlockEntity.MAX_FISH);
        level.setBlock(pos, state.setValue(FISH_COUNT, count), Block.UPDATE_CLIENTS);
    }

    // --- Adding one fish to a placed pile (plain right-click, directly targeted) ---

    @Override
    protected InteractionResult useItemOn(ItemStack itemStack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (itemStack.isEmpty()) {
            // In MC 26.1.2, useWithoutItem is never automatically called — useItemOn fires even
            // with an empty hand. Delegate pickup here when the hand is empty (see FishTankBlock
            // for the same pattern/quirk).
            if (hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            return useWithoutItem(state, level, pos, player, hit);
        }

        // Vanilla suppresses this whole method while sneaking with a non-empty hand (see
        // FishTankBlock#tryShiftExtractFromTargetedTank's javadoc), so shift-click never reaches
        // here — only reachable with plain right-click, which is all "add one fish" needs.
        boolean isPileItem = itemStack.getItem() instanceof PileOfFishItem;
        boolean isSingleFish = !isPileItem && PileOfFishItem.canInsertInPile(itemStack);
        if (player.isShiftKeyDown() || !(isPileItem || isSingleFish)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(level.getBlockEntity(pos) instanceof FishPileBlockEntity pile) || pile.isFull()) {
            return InteractionResult.PASS;
        }

        if (isPileItem) {
            if (!takeOneFromPileItem(player, hand, itemStack, pile)) {
                return InteractionResult.PASS;
            }
        } else {
            if (!pile.insertSingle(itemStack)) {
                return InteractionResult.PASS;
            }
            itemStack.shrink(1);
        }

        syncFishCount(level, pos, state, pile);
        level.playSound(null, pos, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS_SERVER;
    }

    // --- Removing one fish (shift always; empty-hand plain click is a no-op) ---

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof FishPileBlockEntity pile) || pile.isEmptyPile()) {
            return InteractionResult.PASS;
        }

        ItemStack top = pile.removeTopFish();
        if (top.isEmpty()) {
            return InteractionResult.PASS;
        }
        giveOrDrop(player, top);
        if (pile.isEmptyPile()) {
            level.removeBlock(pos, false);
        } else {
            syncFishCount(level, pos, state, pile);
        }
        level.playSound(null, pos, SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS_SERVER;
    }

    // --- Breaking ---

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // A player breaking the block removes it (and its BlockEntity) before spawnAfterBreak ever
        // runs, so the contents have to be dropped here while the entity still exists.
        dropFishIfPresent(level, pos);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        // Non-player destruction (pistons, explosions, fire) drops resources via this hook before
        // the block is removed, unlike the player-break path above — so the BlockEntity is still
        // live here.
        dropFishIfPresent(level, pos);
    }

    private static void dropFishIfPresent(Level level, BlockPos pos) {
        if (level.isClientSide()) return;
        if (!(level.getBlockEntity(pos) instanceof FishPileBlockEntity pile)) return;
        for (ItemStack stack : pile.getFish()) {
            if (!stack.isEmpty()) {
                Block.popResource(level, pos, stack.copy());
            }
        }
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    // --- Item-side fallback, triggered from PileOfFishItem#use / FishtasticFishItem#use ---

    /**
     * Entry point for the two interactions that can't go through {@link #useItemOn}/
     * {@link #useWithoutItem} directly:
     * <ul>
     *   <li>Plain right-click on bare ground to start a <em>new</em> pile — no fishtastic block is
     *   involved in that click, so nothing on this class ever gets asked.</li>
     *   <li>Shift-right-click on an existing pile to extract one fish — vanilla suppresses
     *   {@link #useItemOn} entirely while sneaking with a non-empty hand (see
     *   {@code FishTankBlock#tryShiftExtractFromTargetedTank}'s javadoc), so a held fish item never
     *   reaches this class's own handlers that way.</li>
     * </ul>
     * Both cases reach the calling item's {@code use()} instead, as vanilla's client-side fallback
     * once the primary block/item interaction chain returns non-consuming — so this does its own
     * raycast rather than relying on a {@link BlockHitResult}.
     *
     * @return {@code null} if nothing here applies, so the caller can fall back to its normal
     * {@code use()} behavior.
     */
    @Nullable
    public static InteractionResult tryHandleTargetedInteraction(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        boolean shift = player.isShiftKeyDown();
        boolean isPileItem = itemStack.getItem() instanceof PileOfFishItem;
        boolean isSingleFish = !isPileItem && PileOfFishItem.canInsertInPile(itemStack);

        // Mirrors Item#getPlayerPOVHitResult (protected, not accessible from here) — this
        // reconstructs the block the player is looking at, since the suppressed block interaction
        // never gave us a BlockHitResult to work with.
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.calculateViewVector(player.getXRot(), player.getYRot()).scale(player.blockInteractionRange()));
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockPos hitPos = hit.getBlockPos();
        BlockState hitState = level.getBlockState(hitPos);

        if (shift) {
            return tryExtractOne(level, player, hand, itemStack, hitPos, hitState);
        }
        if (!(isPileItem || isSingleFish) || hit.getDirection() != Direction.UP) {
            return null;
        }
        return tryPlaceOneOnNewPile(level, player, hand, itemStack, isPileItem, hitPos, hitState);
    }

    /**
     * Shift-right-click branch: take one fish out of the directly targeted pile, combining it into
     * the held item via {@link PileOfFishItem#combineExtractedFish} — the same helper
     * {@code FishTankBlock#extractTopFishIntoHand} uses. A held item that isn't pile-eligible at
     * all (or an empty hand — though that's actually routed through {@link #useWithoutItem}
     * instead, since this item-side raycast is never reached with an empty hand) just gets the
     * fish given/dropped loose.
     */
    @Nullable
    private static InteractionResult tryExtractOne(Level level, Player player, InteractionHand hand,
            ItemStack itemStack, BlockPos hitPos, BlockState hitState) {
        if (!(hitState.getBlock() instanceof FishPileBlock)) {
            return null;
        }
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(hitPos) instanceof FishPileBlockEntity pile) || pile.isEmptyPile()) {
            return null;
        }

        ItemStack top = pile.removeTopFish();
        if (top.isEmpty()) {
            return null;
        }

        PileOfFishItem.combineExtractedFish(player, hand, itemStack, top);
        if (!top.isEmpty()) {
            if (itemStack.getItem() instanceof PileOfFishItem) {
                // Held pile is full — put the fish back rather than losing it.
                pile.insertSingle(top);
                return InteractionResult.FAIL;
            }
            // Held item isn't pile-eligible at all — just give/drop the extracted fish loose.
            giveOrDrop(player, top);
        }

        if (pile.isEmptyPile()) {
            level.removeBlock(hitPos, false);
        } else {
            syncFishCount(level, hitPos, hitState, pile);
        }
        level.playSound(null, hitPos, SoundEvents.BUNDLE_REMOVE_ONE, SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Plain right-click branch: start a new pile with one fish on a valid bare surface. */
    @Nullable
    private static InteractionResult tryPlaceOneOnNewPile(Level level, Player player, InteractionHand hand,
            ItemStack itemStack, boolean isPileItem, BlockPos surfacePos, BlockState surfaceState) {
        if (surfaceState.getBlock() instanceof FishTankBlock
                || surfaceState.getBlock() instanceof FishPileBlock
                || !surfaceState.isFaceSturdy(level, surfacePos, Direction.UP)) {
            return null;
        }
        BlockPos targetPos = surfacePos.above();
        if (!level.getBlockState(targetPos).canBeReplaced()) {
            return null;
        }
        if (isPileItem) {
            BundleContents contents = itemStack.get(DataComponents.BUNDLE_CONTENTS);
            if (contents == null || contents.items().isEmpty()) {
                return null;
            }
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!level.setBlockAndUpdate(targetPos, FishtasticBlocks.FISH_PILE.value().defaultBlockState())) {
            return InteractionResult.FAIL;
        }
        if (!(level.getBlockEntity(targetPos) instanceof FishPileBlockEntity pile)) {
            return InteractionResult.FAIL;
        }

        boolean took = isPileItem ? takeOneFromPileItem(player, hand, itemStack, pile) : pile.insertSingle(itemStack);
        if (!took) {
            // Shouldn't happen — a freshly placed pile is always empty — but leave no ghost block.
            level.removeBlock(targetPos, false);
            return InteractionResult.FAIL;
        }
        if (!isPileItem) {
            itemStack.shrink(1);
        }
        level.playSound(null, targetPos, SoundEvents.BUNDLE_INSERT, SoundSource.BLOCKS, 0.8f, 1.0f);
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Removes one fish from {@code itemStack}'s bundle contents and inserts it into {@code pile},
     * writing the remainder back onto the held stack (decomposing to a single item if only one is
     * left). Returns whether a fish was actually moved.
     */
    private static boolean takeOneFromPileItem(Player player, InteractionHand hand, ItemStack itemStack, FishPileBlockEntity pile) {
        BundleContents contents = itemStack.get(DataComponents.BUNDLE_CONTENTS);
        if (contents == null || contents.items().isEmpty()) {
            return false;
        }
        BundleContents.Mutable mutable = new BundleContents.Mutable(contents);
        ItemStack oneFish = mutable.removeOne();
        if (oneFish == null || !pile.insertSingle(oneFish)) {
            return false;
        }

        BundleContents remaining = mutable.toImmutable();
        if (remaining.isEmpty()) {
            itemStack.shrink(1);
        } else if (remaining.items().size() == 1) {
            player.setItemInHand(hand, remaining.items().getFirst().create());
        } else {
            itemStack.set(DataComponents.BUNDLE_CONTENTS, remaining);
        }
        return true;
    }
}
