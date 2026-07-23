package grill24.fishtastic.block;

import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.blockentity.MarineCompostBlockEntity;
import grill24.fishtastic.util.FishQualityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class MarineCompostBlock extends Block implements EntityBlock {
    public static final EnumProperty<MarineCompostPhase> PHASE = EnumProperty.create("phase", MarineCompostPhase.class);

    public MarineCompostBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(PHASE, MarineCompostPhase.DRY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MarineCompostBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        BlockEntityType<?> expected = FishtasticBlockEntityTypes.MARINE_COMPOST.value();
        return type == expected ? (lvl, p, s, be) -> MarineCompostBlockEntity.tick(lvl, p, s, (MarineCompostBlockEntity) be) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.isClientSide()) return;
        if (!(level.getBlockEntity(pos) instanceof MarineCompostBlockEntity bin)) return;

        bin.initialize(FishQualityHelper.getQuality(stack));

        MarineCompostPhase initialPhase = bin.canAerate(level.getGameTime()) ? MarineCompostPhase.DRY : MarineCompostPhase.WET;
        level.setBlockAndUpdate(pos, state.setValue(PHASE, initialPhase));
        bin.setChanged();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        MarineCompostPhase phase = state.getValue(PHASE);
        boolean willAerate = phase == MarineCompostPhase.DRY;

        if (level.isClientSide()) return willAerate ? InteractionResult.SUCCESS : InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof MarineCompostBlockEntity bin)) return InteractionResult.PASS;

        if (willAerate && bin.canAerate(level.getGameTime())) {
            bin.aerate(level.getGameTime());
            bin.setChanged();
            level.setBlockAndUpdate(pos, state.setValue(PHASE, MarineCompostPhase.WET));
            return InteractionResult.SUCCESS_SERVER;
        }

        return InteractionResult.PASS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        // A player breaking the block removes it (and its BlockEntity) before spawnAfterBreak/playerDestroy
        // ever runs, so the pending worm count has to be read here instead, while the entity still exists.
        dropWormsIfReady(level, pos, state);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        // Non-player destruction (pistons, explosions, fire) drops resources via this hook *before* the
        // block is removed, unlike the player-break path above - so the BlockEntity is still live here.
        dropWormsIfReady(level, pos, state);
    }

    private static void dropWormsIfReady(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide() || state.getValue(PHASE) != MarineCompostPhase.READY) return;
        if (!(level.getBlockEntity(pos) instanceof MarineCompostBlockEntity bin)) return;

        int worms = bin.getPendingWorms();
        if (worms > 0) {
            Block.popResource(level, pos, new ItemStack(FishtasticItems.WORMS.value(), worms));
        }
    }
}
