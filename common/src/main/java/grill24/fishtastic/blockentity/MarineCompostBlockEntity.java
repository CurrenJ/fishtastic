package grill24.fishtastic.blockentity;

import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.block.MarineCompostBlock;
import grill24.fishtastic.block.MarineCompostPhase;
import grill24.fishtastic.component.FishQuality;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

public class MarineCompostBlockEntity extends BlockEntity {
    private static final int MAX_AERATION_TURNS = 5;
    private static final int BASE_CONVERSION_TICKS = 6000;
    private static final int AERATION_TICK_REDUCTION = 240;
    static final int AERATION_COOLDOWN_TICKS = 1200;

    @Nullable
    private FishQuality.Quality quality;
    private int conversionTicks = 0;
    private int aerationTurns = 0;
    private int pendingWorms = 0;
    private long lastAerationTick = -AERATION_COOLDOWN_TICKS;

    public MarineCompostBlockEntity(BlockPos pos, BlockState state) {
        super(FishtasticBlockEntityTypes.MARINE_COMPOST.value(), pos, state);
    }

    /**
     * Seeds this compost with the quality of the fish it was crafted from. Called once, right after placement.
     */
    public void initialize(@Nullable FishQuality.Quality quality) {
        this.quality = quality;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MarineCompostBlockEntity bin) {
        MarineCompostPhase phase = state.getValue(MarineCompostBlock.PHASE);
        if (phase != MarineCompostPhase.DRY && phase != MarineCompostPhase.WET) return;

        bin.conversionTicks++;
        int maxTicks = BASE_CONVERSION_TICKS - bin.aerationTurns * AERATION_TICK_REDUCTION;
        if (bin.conversionTicks >= maxTicks) {
            bin.pendingWorms = bin.computeYield();
            bin.conversionTicks = 0;
            level.setBlockAndUpdate(pos, state.setValue(MarineCompostBlock.PHASE, MarineCompostPhase.READY));
            bin.setChanged();
            return;
        }

        MarineCompostPhase correctPhase = bin.canAerate(level.getGameTime()) ? MarineCompostPhase.DRY : MarineCompostPhase.WET;
        if (phase != correctPhase) {
            level.setBlockAndUpdate(pos, state.setValue(MarineCompostBlock.PHASE, correctPhase));
        }
    }

    private int computeYield() {
        FishQuality.Quality effectiveQuality = quality != null ? quality : FishQuality.Quality.COMMON;
        int base = switch (effectiveQuality) {
            case COMMON -> 1;
            case UNCOMMON -> 3;
            case RARE -> 6;
            case EPIC -> 12;
            case LEGENDARY -> 25;
        };
        return Math.max(1, base + aerationTurns);
    }

    public boolean canAerate(long currentTick) {
        return aerationTurns < MAX_AERATION_TURNS
            && currentTick - lastAerationTick >= AERATION_COOLDOWN_TICKS;
    }

    public void aerate(long currentTick) {
        if (canAerate(currentTick)) {
            aerationTurns++;
            lastAerationTick = currentTick;
        }
    }

    public int getPendingWorms() {
        return pendingWorms;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (quality != null) output.store("quality", FishQuality.Quality.CODEC, quality);
        output.putInt("conversion_ticks", conversionTicks);
        output.putInt("aeration_turns", aerationTurns);
        output.putInt("pending_worms", pendingWorms);
        output.putLong("last_aeration_tick", lastAerationTick);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        quality = input.read("quality", FishQuality.Quality.CODEC).orElse(null);
        conversionTicks = input.getIntOr("conversion_ticks", 0);
        aerationTurns = input.getIntOr("aeration_turns", 0);
        pendingWorms = input.getIntOr("pending_worms", 0);
        lastAerationTick = input.getLongOr("last_aeration_tick", -AERATION_COOLDOWN_TICKS);
    }
}
