package grill24.fishtastic.client.companions;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.client.FishEncyclopediaClientCache;
import grill24.fishtastic.client.QuestClientCache;
import grill24.fishtastic.companion.CompanionBondUnlocks;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the companions screen's fish list shows (docs/companion-ui-redesign/layout-spec.md §3.4): every
 * fish the player carries, loose or inside a Pile of Fish, grouped by species, then by identical fish
 * (every component equal) and where it is. Read-only and client-side: an Equip click only
 * <i>describes</i> the chosen fish; the server finds an identical one itself.
 */
public final class CompanionFishCatalog {
    private CompanionFishCatalog() {}

    public enum Source { INVENTORY, PILE }

    /** Identical fish in one place; {@code fish} has count 1 and describes them all. */
    public record Individual(ItemStack fish, int count, Source source) {
        public FishQuality.Quality quality() {
            FishQuality.Quality q = FishQualityHelper.getQuality(fish);
            return q == null ? FishQuality.Quality.COMMON : q;
        }

        public float sizeCm() {
            return ItemSizeHelper.getSize(fish);
        }
    }

    /** The unlock state the list and the Unlocks view show for a species. */
    public enum Gate { UNLOCKED, CATCHES, QUEST }

    public record Unlock(Gate gate, int caught, int required) {
        public int remaining() { return Math.max(0, required - caught); }
        public float progress() { return required <= 0 ? 1f : Math.min(1f, caught / (float) required); }
    }

    public record Species(Item item, ResourceLocation id, String name, List<Individual> fish, int total, Unlock unlock) {}

    /** The species' companion unlock state; mirrors the Fishopedia bond strip's arithmetic. */
    public static Unlock unlockOf(RegistryAccess access, ResourceLocation species) {
        int caught = FishEncyclopediaClientCache.getCatchCount(species);
        if (QuestClientCache.isBondUnlocked(species)) return new Unlock(Gate.UNLOCKED, caught, 0);
        if (CompanionBondUnlocks.isUnlisted(species)) return new Unlock(Gate.QUEST, caught, 0);
        var thresholds = EncyclopediaTiers.thresholdsFor(access, species);
        int required = 0;
        for (EncyclopediaRewardSection section : EncyclopediaRewardSection.values()) {
            required = Math.max(required, section.threshold(thresholds));
        }
        return new Unlock(Gate.CATCHES, caught, required);
    }

    /** Builds the list: unlocked species alphabetically, then locked ones closest first, quest-gated last. */
    public static List<Species> build(Inventory inventory, RegistryAccess access) {
        Map<Item, List<Individual>> bySpecies = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(FishtasticItemTags.FISH)) {
                add(bySpecies, stack, stack.getCount(), Source.INVENTORY);
            } else if (stack.is(FishtasticItems.PILE_OF_FISH.value())) {
                for (ItemStack template : FishtasticItemData.bundleContentsOrEmpty(stack).items()) {
                    ItemStack fish = template.copy();
                    if (fish.is(FishtasticItemTags.FISH)) add(bySpecies, fish, fish.getCount(), Source.PILE);
                }
            }
        }
        List<Species> out = new ArrayList<>();
        for (var entry : bySpecies.entrySet()) {
            List<Individual> fish = entry.getValue();
            fish.sort(Comparator.<Individual>comparingInt(f -> f.quality().ordinal()).reversed()
                    .thenComparing(Comparator.comparingDouble(Individual::sizeCm).reversed())
                    .thenComparing(Individual::source));
            int total = fish.stream().mapToInt(Individual::count).sum();
            Item item = entry.getKey();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            out.add(new Species(item, id, new ItemStack(item).getHoverName().getString(), fish, total, unlockOf(access, id)));
        }
        out.sort(ORDER);
        return out;
    }

    /** Unlocked A–Z, then catch-gated by fewest catches left, then quest-gated A–Z. */
    public static final Comparator<Species> ORDER = Comparator
            .<Species>comparingInt(s -> s.unlock().gate().ordinal())
            .thenComparingInt(s -> s.unlock().gate() == Gate.CATCHES ? s.unlock().remaining() : 0)
            .thenComparing(s -> s.name().toLowerCase(Locale.ROOT));

    private static void add(Map<Item, List<Individual>> bySpecies, ItemStack stack, int count, Source source) {
        List<Individual> fish = bySpecies.computeIfAbsent(stack.getItem(), k -> new ArrayList<>());
        for (int i = 0; i < fish.size(); i++) {
            Individual existing = fish.get(i);
            if (existing.source() == source && FishtasticItemData.isSameItemSameData(existing.fish(), stack)) {
                fish.set(i, new Individual(existing.fish(), existing.count() + count, source));
                return;
            }
        }
        fish.add(new Individual(stack.copyWithCount(1), count, source));
    }

    /** Case-insensitive species-name match for the search box; blank matches everything. */
    public static boolean matches(Species species, String query) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        return q.isEmpty() || species.name().toLowerCase(Locale.ROOT).contains(q);
    }
}
