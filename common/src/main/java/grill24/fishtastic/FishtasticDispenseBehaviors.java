package grill24.fishtastic;

import grill24.fishtastic.block.MarineCompostBlock;
import grill24.fishtastic.block.MarineCompostPhase;
import grill24.fishtastic.blockentity.MarineCompostBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.OptionalDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.DirectionalPlaceContext;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FishtasticDispenseBehaviors {
    private static final Logger LOGGER = LoggerFactory.getLogger(FishtasticDispenseBehaviors.class);

    private FishtasticDispenseBehaviors() {}

    public static void registerDispenseBehaviors() {
        OptionalDispenseItemBehavior flipMarineCompost = new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack dispensed) {
                this.setSuccess(false);

                ServerLevel level = source.level();
                Direction facing = source.state().getValue(DispenserBlock.FACING);
                BlockPos targetPos = source.pos().relative(facing);
                BlockState targetState = level.getBlockState(targetPos);

                if (targetState.is(FishtasticBlocks.MARINE_COMPOST.value())
                        && targetState.getValue(MarineCompostBlock.PHASE) == MarineCompostPhase.DRY
                        && level.getBlockEntity(targetPos) instanceof MarineCompostBlockEntity bin
                        && bin.canAerate(level.getGameTime())) {
                    bin.aerate(level.getGameTime());
                    bin.setChanged();
                    level.setBlockAndUpdate(targetPos, targetState.setValue(MarineCompostBlock.PHASE, MarineCompostPhase.WET));
                    dispensed.hurtAndBreak(1, level, null, item -> {});
                    this.setSuccess(true);
                }

                return dispensed;
            }
        };

        DispenserBlock.registerBehavior(Items.WOODEN_SHOVEL, flipMarineCompost);
        DispenserBlock.registerBehavior(Items.STONE_SHOVEL, flipMarineCompost);
        DispenserBlock.registerBehavior(Items.IRON_SHOVEL, flipMarineCompost);
        DispenserBlock.registerBehavior(Items.GOLDEN_SHOVEL, flipMarineCompost);
        DispenserBlock.registerBehavior(Items.DIAMOND_SHOVEL, flipMarineCompost);
        DispenserBlock.registerBehavior(Items.NETHERITE_SHOVEL, flipMarineCompost);

        DispenserBlock.registerBehavior(FishtasticBlocks.MARINE_COMPOST.value(), new OptionalDispenseItemBehavior() {
            @Override
            protected ItemStack execute(BlockSource source, ItemStack dispensed) {
                this.setSuccess(false);

                Item item = dispensed.getItem();
                if (item instanceof BlockItem blockItem) {
                    Direction facing = source.state().getValue(DispenserBlock.FACING);
                    BlockPos targetPos = source.pos().relative(facing);
                    Direction clickedFace = source.level().isEmptyBlock(targetPos.below()) ? facing : Direction.UP;

                    try {
                        this.setSuccess(blockItem.place(new DirectionalPlaceContext(source.level(), targetPos, facing, dispensed, clickedFace)).consumesAction());
                    } catch (Exception e) {
                        LOGGER.error("Error trying to place marine compost at {}", targetPos, e);
                    }
                }

                return dispensed;
            }
        });
    }
}
