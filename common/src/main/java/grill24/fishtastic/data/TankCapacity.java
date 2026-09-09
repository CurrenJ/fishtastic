package grill24.fishtastic.data;

import grill24.FishtasticRegistries;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.TankGroups;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Governs how many fish a tank can hold based on their individual size, rather than a flat slot
 * count. Each fish spends a share of the tank's fixed budget proportional to its actual rolled
 * length (see {@link ItemSizeHelper}), so a tank can hold either a handful of large fish or a
 * crowd of tiny ones. This is the gameplay capacity gate for {@code FishTankBlockEntity#addItem};
 * {@link SwarmConfig#count()} remains a separate, render-side draw-count ceiling.
 */
public final class TankCapacity {
    private TankCapacity() {}

    /**
     * Total budget a tank has to spend on occupants. Tuned so exactly 5 reference-length (50cm)
     * fish fill it, matching the old flat 5-fish cap.
     */
    public static final float BASE_BUDGET = 5.0f;

    private static final float REFERENCE_LENGTH = FishProfile.DEFAULT_MEAN_SIZE;
    private static final float MIN_COST = 0.2f;
    private static final float MAX_COST = 3.0f;

    /**
     * Budget cost of housing one fish: its actual rolled size relative to the 50cm reference
     * length, clamped so no single tiny fish is free and no single giant fish blocks the tank
     * outright. Falls back to the species' mean size if the stack has no rolled size.
     */
    public static float costOf(ItemStack stack, Level level) {
        if (stack.isEmpty()) return 0f;

        float size = ItemSizeHelper.getSize(stack);
        if (size <= 0f) {
            size = resolveMeanSize(stack, level);
        }

        float ratio = size / REFERENCE_LENGTH;
        return Math.max(MIN_COST, Math.min(MAX_COST, ratio));
    }

    /** Whether {@code incoming} can join {@code occupants} without exceeding the tank's budget. */
    public static boolean canAdd(Iterable<ItemStack> occupants, ItemStack incoming, Level level) {
        float used = 0f;
        for (ItemStack occupant : occupants) {
            used += costOf(occupant, level);
        }
        return used + costOf(incoming, level) <= BASE_BUDGET;
    }

    /**
     * Finds a member of {@code group} with room for {@code incoming}, checked in group order
     * (the clicked segment should be first in that order, so it's preferred when it has room).
     * Returns null if every member's own budget is already spent — storage stays strictly
     * per-segment (see docs/fish-tank-interaction-redesign.md §3a), this just spreads a rejected
     * insert across the connected group instead of failing outright.
     */
    @Nullable
    public static FishTankBlockEntity findSegmentWithRoom(TankGroups.Group group, ItemStack incoming, Level level) {
        for (BlockPos pos : group.members()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof FishTankBlockEntity fishTank)) continue;
            List<ItemStack> occupants = new ArrayList<>();
            for (int i = 0; i < FishTankBlockEntity.CONTAINER_SIZE; i++) {
                ItemStack existing = fishTank.getItem(i);
                if (!existing.isEmpty()) occupants.add(existing);
            }
            if (canAdd(occupants, incoming, level)) {
                return fishTank;
            }
        }
        return null;
    }

    private static float resolveMeanSize(ItemStack stack, Level level) {
        var itemKey = BuiltInRegistries.ITEM.getResourceKey(stack.getItem());
        if (itemKey.isEmpty()) return REFERENCE_LENGTH;

        ResourceKey<FishProfile> profileKey = ResourceKey.create(
                FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, itemKey.get().identifier());

        return level.registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                .getOptional(profileKey)
                .map(profile -> profile.size().mean())
                .orElse(REFERENCE_LENGTH);
    }
}
