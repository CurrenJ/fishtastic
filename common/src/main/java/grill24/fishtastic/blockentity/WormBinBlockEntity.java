package grill24.fishtastic.blockentity;

import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.block.WormBinBlock;
import grill24.fishtastic.block.WormBinPhase;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.util.FishQualityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.ArrayList;
import java.util.List;

public class WormBinBlockEntity extends BlockEntity {
    private static final int MAX_FISH_SLOTS = 5;
    private static final int MAX_AERATION_TURNS = 5;
    private static final int BASE_CONVERSION_TICKS = 6000;
    private static final int AERATION_TICK_REDUCTION = 240;

    private final List<ItemStack> depositedFish = new ArrayList<>();
    private int conversionTicks = 0;
    private int aerationTurns = 0;
    private int pendingWorms = 0;

    public WormBinBlockEntity(BlockPos pos, BlockState state) {
        super(FishtasticBlockEntityTypes.WORM_BIN.value(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WormBinBlockEntity bin) {
        if (state.getValue(WormBinBlock.PHASE) != WormBinPhase.CONVERTING) return;

        bin.conversionTicks++;
        int maxTicks = BASE_CONVERSION_TICKS - bin.aerationTurns * AERATION_TICK_REDUCTION;
        if (bin.conversionTicks >= maxTicks) {
            bin.pendingWorms = bin.computeYield();
            bin.depositedFish.clear();
            bin.conversionTicks = 0;
            level.setBlockAndUpdate(pos, state.setValue(WormBinBlock.PHASE, WormBinPhase.READY));
            bin.setChanged();
        }
    }

    private int computeYield() {
        int total = 0;
        for (ItemStack fish : depositedFish) {
            FishQuality.Quality quality = FishQualityHelper.getQuality(fish);
            if (quality == null) quality = FishQuality.Quality.COMMON;
            total += switch (quality) {
                case COMMON -> 1;
                case UNCOMMON -> 3;
                case RARE -> 6;
                case EPIC -> 12;
                case LEGENDARY -> 25;
            };
        }
        total += aerationTurns;
        return Math.max(1, total);
    }

    public boolean canDeposit() {
        return depositedFish.size() < MAX_FISH_SLOTS;
    }

    public boolean canAerate() {
        return aerationTurns < MAX_AERATION_TURNS;
    }

    public void depositFish(ItemStack fish) {
        if (canDeposit()) depositedFish.add(fish.copy());
    }

    public void aerate() {
        if (canAerate()) aerationTurns++;
    }

    public int getPendingWorms() {
        return pendingWorms;
    }

    public List<ItemStack> getDepositedFish() {
        return List.copyOf(depositedFish);
    }

    public void reset() {
        depositedFish.clear();
        conversionTicks = 0;
        aerationTurns = 0;
        pendingWorms = 0;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("conversion_ticks", conversionTicks);
        output.putInt("aeration_turns", aerationTurns);
        output.putInt("pending_worms", pendingWorms);
        ValueOutput.ValueOutputList fishList = output.childrenList("deposited_fish");
        for (ItemStack fish : depositedFish) {
            fishList.addChild().store("stack", ItemStack.CODEC, fish);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        conversionTicks = input.getIntOr("conversion_ticks", 0);
        aerationTurns = input.getIntOr("aeration_turns", 0);
        pendingWorms = input.getIntOr("pending_worms", 0);
        depositedFish.clear();
        input.childrenListOrEmpty("deposited_fish").forEach(e ->
                e.read("stack", ItemStack.CODEC).ifPresent(depositedFish::add));
    }
}
