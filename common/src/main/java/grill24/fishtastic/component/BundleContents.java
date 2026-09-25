package grill24.fishtastic.component;

import com.google.common.collect.Lists;
import com.mojang.serialization.Codec;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.math.Fraction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 1.20.1 has no {@code net.minecraft.world.item.component.BundleContents} (added 1.21). Ported
 * from 1.21.1's class (docs/backport-pass2/track-b-1.20.1.md B2.2) with the same public surface —
 * only the import at each of the 11 call sites changes. {@code BundleItem} itself keeps its own
 * separate int-based ("out of 64") weight system; this class is Fishtastic's own bundle-style
 * container (the pile-of-fish item) and isn't wired into vanilla's {@code BundleItem}.
 */
public final class BundleContents {
    public static final BundleContents EMPTY = new BundleContents(List.of());
    public static final Codec<BundleContents> CODEC = ItemStack.CODEC.listOf().xmap(BundleContents::new, bundleContents -> bundleContents.items);
    public static final BufCodec<BundleContents> STREAM_CODEC = BufCodecs.fromCodec(CODEC);

    private static final Fraction BUNDLE_IN_BUNDLE_WEIGHT = Fraction.getFraction(1, 16);

    final List<ItemStack> items;
    final Fraction weight;

    BundleContents(List<ItemStack> items, Fraction weight) {
        this.items = items;
        this.weight = weight;
    }

    public BundleContents(List<ItemStack> items) {
        this(items, computeContentWeight(items));
    }

    private static Fraction computeContentWeight(List<ItemStack> items) {
        Fraction total = Fraction.ZERO;
        for (ItemStack stack : items) {
            total = total.add(getWeight(stack).multiplyBy(Fraction.getFraction(stack.getCount(), 1)));
        }
        return total;
    }

    static Fraction getWeight(ItemStack stack) {
        BundleContents nested = FishtasticItemData.bundleContents(stack);
        if (nested != null) {
            return BUNDLE_IN_BUNDLE_WEIGHT.add(nested.weight());
        }
        CompoundTag beehive = stack.getTagElement("BlockEntityTag");
        if (beehive != null && !beehive.getList("Bees", 10).isEmpty()) {
            return Fraction.ONE;
        }
        return Fraction.getFraction(1, stack.getMaxStackSize());
    }

    public ItemStack getItemUnsafe(int index) {
        return items.get(index);
    }

    public Stream<ItemStack> itemCopyStream() {
        return items.stream().map(ItemStack::copy);
    }

    public Iterable<ItemStack> items() {
        return items;
    }

    public Iterable<ItemStack> itemsCopy() {
        return Lists.transform(items, ItemStack::copy);
    }

    public int size() {
        return items.size();
    }

    public Fraction weight() {
        return weight;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BundleContents other)) return false;
        if (!weight.equals(other.weight) || items.size() != other.items.size()) return false;
        for (int i = 0; i < items.size(); i++) {
            if (!ItemStack.matches(items.get(i), other.items.get(i))) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = weight.hashCode();
        for (ItemStack stack : items) {
            hash = 31 * hash + (stack.isEmpty() ? 0 : stack.hashCode());
        }
        return hash;
    }

    @Override
    public String toString() {
        return "BundleContents" + items;
    }

    public static final class Mutable {
        private final List<ItemStack> items;
        private Fraction weight;

        public Mutable(BundleContents contents) {
            this.items = new ArrayList<>(contents.items);
            this.weight = contents.weight;
        }

        public Mutable clearItems() {
            items.clear();
            weight = Fraction.ZERO;
            return this;
        }

        private int findStackIndex(ItemStack stack) {
            if (!stack.isStackable()) return -1;
            for (int i = 0; i < items.size(); i++) {
                if (ItemStack.isSameItemSameTags(items.get(i), stack)) return i;
            }
            return -1;
        }

        private int getMaxAmountToAdd(ItemStack stack) {
            Fraction remaining = Fraction.ONE.subtract(weight);
            return Math.max(remaining.divideBy(BundleContents.getWeight(stack)).intValue(), 0);
        }

        public int tryInsert(ItemStack stack) {
            if (stack.isEmpty() || !stack.getItem().canFitInsideContainerItems()) return 0;
            int amount = Math.min(stack.getCount(), getMaxAmountToAdd(stack));
            if (amount == 0) return 0;

            weight = weight.add(BundleContents.getWeight(stack).multiplyBy(Fraction.getFraction(amount, 1)));
            int existingIndex = findStackIndex(stack);
            if (existingIndex != -1) {
                ItemStack existing = items.remove(existingIndex);
                ItemStack merged = existing.copyWithCount(existing.getCount() + amount);
                stack.shrink(amount);
                items.add(0, merged);
            } else {
                items.add(0, stack.split(amount));
            }
            return amount;
        }

        public int tryTransfer(Slot slot, Player player) {
            ItemStack stack = slot.getItem();
            int max = getMaxAmountToAdd(stack);
            return tryInsert(slot.safeTake(stack.getCount(), max, player));
        }

        @Nullable
        public ItemStack removeOne() {
            if (items.isEmpty()) return null;
            ItemStack removed = items.remove(0).copy();
            weight = weight.subtract(BundleContents.getWeight(removed).multiplyBy(Fraction.getFraction(removed.getCount(), 1)));
            return removed;
        }

        public Fraction weight() {
            return weight;
        }

        public BundleContents toImmutable() {
            return new BundleContents(List.copyOf(items), weight);
        }
    }
}
