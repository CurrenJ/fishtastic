package grill24.fishtastic.block;

import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.blockentity.WormBinBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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

public class WormBinBlock extends Block implements EntityBlock {
    public static final EnumProperty<WormBinPhase> PHASE = EnumProperty.create("phase", WormBinPhase.class);

    public WormBinBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any().setValue(PHASE, WormBinPhase.EMPTY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PHASE);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WormBinBlockEntity(pos, state);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        BlockEntityType<?> expected = FishtasticBlockEntityTypes.WORM_BIN.value();
        return type == expected ? (lvl, p, s, be) -> WormBinBlockEntity.tick(lvl, p, s, (WormBinBlockEntity) be) : null;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof WormBinBlockEntity bin)) return InteractionResult.TRY_WITH_EMPTY_HAND;

        WormBinPhase phase = state.getValue(PHASE);

        if (!stack.isEmpty() && stack.is(ItemTags.FISHES)) {
            if ((phase == WormBinPhase.EMPTY || phase == WormBinPhase.CONVERTING) && bin.canDeposit()) {
                if (!player.isCreative()) stack.shrink(1);
                bin.depositFish(stack.copyWithCount(1));
                if (phase == WormBinPhase.EMPTY) {
                    level.setBlockAndUpdate(pos, state.setValue(PHASE, WormBinPhase.CONVERTING));
                }
                level.sendBlockUpdated(pos, state, state, 3);
                bin.setChanged();
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof WormBinBlockEntity bin)) return InteractionResult.PASS;

        WormBinPhase phase = state.getValue(PHASE);

        if (phase == WormBinPhase.CONVERTING && bin.canAerate(level.getGameTime())) {
            bin.aerate(level.getGameTime());
            bin.setChanged();
            level.sendBlockUpdated(pos, state, state, 3);
            return InteractionResult.SUCCESS_SERVER;
        }

        if (phase == WormBinPhase.READY) {
            int worms = bin.getPendingWorms();
            if (worms > 0) {
                ItemStack wormsStack = new ItemStack(FishtasticItems.WORMS.value(), worms);
                if (!player.getInventory().add(wormsStack)) {
                    Block.popResource(level, pos, wormsStack);
                }
            }
            bin.reset();
            level.setBlockAndUpdate(pos, state.setValue(PHASE, WormBinPhase.EMPTY));
            bin.setChanged();
            return InteractionResult.SUCCESS_SERVER;
        }

        return InteractionResult.PASS;
    }

    @Override
    protected void spawnAfterBreak(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean dropExperience) {
        super.spawnAfterBreak(state, level, pos, tool, dropExperience);
        if (level.getBlockEntity(pos) instanceof WormBinBlockEntity bin) {
            for (ItemStack fish : bin.getDepositedFish()) {
                if (!fish.isEmpty()) Block.popResource(level, pos, fish);
            }
        }
    }
}
