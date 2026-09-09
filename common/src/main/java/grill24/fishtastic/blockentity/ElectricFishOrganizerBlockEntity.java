package grill24.fishtastic.blockentity;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.item.PileOfFishItem;
import grill24.fishtastic.menu.ElectricFishOrganizerMenu;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A 54-slot container that only accepts sized fish and {@link PileOfFishItem} stacks, and keeps
 * itself in a canonical sorted state: every fish it holds — whether dropped in loose or already
 * inside a pile someone hands it — ends up folded into exactly one (or, past a pile's weight cap,
 * a few) {@link PileOfFishItem} stack per species, slots ordered alphabetically by species, and
 * each pile's own contents ordered by {@link FishQuality} descending then size descending.
 *
 * <p>{@link #setChanged()} is the single hook for this: every mutation path (GUI clicks,
 * shift-clicks, hoppers) ends by calling it on this block entity, so re-sorting there catches all
 * of them uniformly instead of needing to be threaded through the menu as well.
 */
public class ElectricFishOrganizerBlockEntity extends BlockEntity implements Container, MenuProvider {
    public static final int CONTAINER_SIZE = 54;

    private static final Comparator<ItemStack> FISH_ORDER = Comparator
            .comparingInt(ElectricFishOrganizerBlockEntity::qualityRank).reversed()
            .thenComparing(Comparator.comparingDouble((ItemStack stack) -> (double) ItemSizeHelper.getSize(stack)).reversed());

    private final NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    /** Guards re-entrancy: {@link #normalize()} itself calls {@link #setItem} in a loop. */
    private boolean normalizing = false;

    /** How piles are grouped, and in which direction the groups are ordered across the slots. */
    private OrganizerSortMode sortMode = OrganizerSortMode.SPECIES;
    private boolean sortAscending = true;

    public ElectricFishOrganizerBlockEntity(BlockPos pos, BlockState state) {
        super(FishtasticBlockEntityTypes.ELECTRIC_FISH_ORGANIZER.value(), pos, state);
    }

    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack result = ContainerHelper.removeItem(items, slot, amount);
        if (!result.isEmpty()) setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    /** Only sized fish and Pile of Fish stacks are allowed in — gates GUI placement and hoppers alike. */
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return PileOfFishItem.canInsertInPile(stack) || stack.getItem() instanceof PileOfFishItem;
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (!normalizing && level != null && !level.isClientSide()) {
            normalize();
        }
    }

    public OrganizerSortMode getSortMode() {
        return sortMode;
    }

    public boolean isSortAscending() {
        return sortAscending;
    }

    /** Server-side: from {@link grill24.fishtastic.network.SetOrganizerSortPacket} via the menu. Re-triggers {@link #normalize()}. */
    public void setSortMode(OrganizerSortMode sortMode, boolean sortAscending) {
        if (this.sortMode == sortMode && this.sortAscending == sortAscending) return;
        this.sortMode = sortMode;
        this.sortAscending = sortAscending;
        setChanged();
    }

    /**
     * Rebuilds the container from scratch into its canonical sorted form. Splits every raw fish
     * stack and every existing pile (ours or handed in by a player, single-species or mixed) back
     * down to individual fish, regroups them by species, sorts each species' fish by quality then
     * size, and repacks them into one pile per species (spilling into an extra pile only once a
     * species' fish stop fitting one bundle's weight cap).
     *
     * <p>Refuses to touch anything if the result wouldn't fit back into the slots available to it
     * — an unsorted container is better than one that silently ate fish.
     *
     * <p>{@link #canPlaceItem} and the menu's slot gating are meant to keep anything that isn't a
     * fish or a pile out in the first place, but neither is airtight against every path something
     * could land here (a dispenser, another mod, a future bypass) — so any slot holding something
     * else entirely is left untouched rather than swept away by the rewrite below.
     */
    private void normalize() {
        List<Integer> availableSlots = new ArrayList<>();
        List<ItemStack> allFish = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (stack.isEmpty()) {
                availableSlots.add(i);
            } else if (stack.getItem() instanceof PileOfFishItem) {
                availableSlots.add(i);
                BundleContents contents = stack.get(DataComponents.BUNDLE_CONTENTS);
                if (contents != null) {
                    for (ItemStackTemplate template : contents.items()) {
                        allFish.add(template.create());
                    }
                }
            } else if (PileOfFishItem.canInsertInPile(stack)) {
                availableSlots.add(i);
                for (int c = 0; c < stack.getCount(); c++) {
                    allFish.add(stack.copyWithCount(1));
                }
            }
            // Anything else is foreign to the organizer — its slot is excluded from
            // availableSlots entirely, so the rewrite below can never touch it.
        }

        Registry<FishProfile> fishProfileRegistry = sortMode == OrganizerSortMode.ZONE
                ? level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                : null;

        Map<Object, List<ItemStack>> byGroup = new LinkedHashMap<>();
        for (ItemStack fish : allFish) {
            byGroup.computeIfAbsent(groupKey(fish, fishProfileRegistry), key -> new ArrayList<>()).add(fish);
        }

        List<Object> groupsSorted = new ArrayList<>(byGroup.keySet());
        Comparator<Object> groupOrder = groupOrder();
        groupsSorted.sort(sortAscending ? groupOrder : groupOrder.reversed());

        List<ItemStack> newPiles = new ArrayList<>();
        for (Object group : groupsSorted) {
            List<ItemStack> fish = byGroup.get(group);
            fish.sort(FISH_ORDER);

            BundleContents.Mutable pile = new BundleContents.Mutable(BundleContents.EMPTY);
            for (ItemStack single : fish) {
                ItemStack toInsert = single.copy();
                pile.tryInsert(toInsert);
                if (!toInsert.isEmpty()) {
                    // Current pile is full — flush it and start a new one for the overflow.
                    newPiles.add(pileFrom(pile));
                    pile = new BundleContents.Mutable(BundleContents.EMPTY);
                    pile.tryInsert(toInsert);
                }
            }
            if (!pile.toImmutable().isEmpty()) {
                newPiles.add(pileFrom(pile));
            }
        }

        if (newPiles.size() > availableSlots.size()) {
            // Refuse to lose fish: leave the container exactly as it is instead of truncating.
            return;
        }

        normalizing = true;
        try {
            for (int i = 0; i < availableSlots.size(); i++) {
                ItemStack newValue = i < newPiles.size() ? newPiles.get(i) : ItemStack.EMPTY;
                setItem(availableSlots.get(i), newValue);
            }
        } finally {
            normalizing = false;
        }
    }

    /** The value fish with equal keys are grouped under a single pile by, per {@link #sortMode}. */
    private Object groupKey(ItemStack fish, @Nullable Registry<FishProfile> fishProfileRegistry) {
        return switch (sortMode) {
            case SPECIES -> fish.getItem();
            case QUALITY -> {
                FishQuality.Quality quality = FishQualityHelper.getQuality(fish);
                yield quality != null ? quality : FishQuality.Quality.COMMON;
            }
            case SIZE -> sizeBucket(ItemSizeHelper.getSize(fish));
            case ZONE -> primaryZone(fish.getItem(), fishProfileRegistry);
        };
    }

    /**
     * The order {@link #groupKey} values sort in (before {@link #sortAscending} is applied).
     * Cast is safe: every key produced by {@link #groupKey} for the current {@link #sortMode}
     * is of the one type this compares.
     */
    @SuppressWarnings("unchecked")
    private Comparator<Object> groupOrder() {
        return switch (sortMode) {
            case SPECIES -> Comparator.<Object, String>comparing(key -> BuiltInRegistries.ITEM.getKey((Item) key).toString());
            case QUALITY -> Comparator.<Object>comparingInt(key -> ((FishQuality.Quality) key).ordinal());
            case SIZE -> Comparator.<Object>comparingInt(key -> (Integer) key);
            case ZONE -> Comparator.<Object>comparingInt(key -> ((Optional<FishProfile.Zone>) key).map(Enum::ordinal).orElse(Integer.MAX_VALUE));
        };
    }

    /**
     * First declared zone in the species' {@link FishProfile}, or empty for a species with no
     * profile (or a profile with no zones) — sorts last regardless of {@link #sortAscending}.
     */
    private static Optional<FishProfile.Zone> primaryZone(Item item, @Nullable Registry<FishProfile> fishProfileRegistry) {
        if (fishProfileRegistry == null) return Optional.empty();
        return FishtasticFishItem.getProfile(item.builtInRegistryHolder(), fishProfileRegistry)
                .flatMap(profile -> profile.zones().stream().findFirst());
    }

    /** 10cm-wide length buckets for "stack by size": 0 groups [0,10), 10 groups [10,20), etc. */
    private static int sizeBucket(float size) {
        return Math.max(0, (int) size / 10) * 10;
    }

    private static ItemStack pileFrom(BundleContents.Mutable contents) {
        ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
        pile.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
        return pile;
    }

    private static int qualityRank(ItemStack stack) {
        FishQuality.Quality quality = FishQualityHelper.getQuality(stack);
        return quality != null ? quality.ordinal() : -1;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("SortMode", sortMode.getSerializedName());
        output.putBoolean("SortAscending", sortAscending);
        ValueOutput.ValueOutputList itemsList = output.childrenList("Items");
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                ValueOutput child = itemsList.addChild();
                child.putInt("Slot", i);
                child.store("Stack", ItemStack.CODEC, stack);
            }
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        sortMode = OrganizerSortMode.bySerializedName(input.getStringOr("SortMode", OrganizerSortMode.SPECIES.getSerializedName()));
        sortAscending = input.getBooleanOr("SortAscending", true);
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            items.set(i, ItemStack.EMPTY);
        }
        input.childrenListOrEmpty("Items").forEach(child -> {
            int slot = child.getIntOr("Slot", -1);
            if (slot >= 0 && slot < CONTAINER_SIZE) {
                child.read("Stack", ItemStack.CODEC).ifPresent(stack -> items.set(slot, stack));
            }
        });
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ElectricFishOrganizerMenu(containerId, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.fishtastic.electric_fish_organizer");
    }
}
