package grill24.fishtastic.data;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticItemTags;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Derives every species' encyclopedia unlock thresholds from how rare it is where it can be caught,
 * so no entry authors its own (docs/fish-companions.md). Thresholds always count catches of the
 * one species; the pool share below only picks which tier a species is in.
 *
 * <p>A species' rarity is its best share of base weight across the location pools it can appear
 * in. A pool is one zone set {@link FishProfile.Zone#resolve} can actually hand back for a cast —
 * a deep ocean draws from OCEAN and DEEP_OCEAN together, a mountain river from RIVER and
 * HIGH_ALTITUDE — and a species is in the pool when its zones overlap the set, mirroring
 * {@code FishtasticFishItem#isZoneEligible}. {@code #fishtastic:unlisted_fish} species are left out
 * of the totals (they're one-off secrets, not part of anyone's normal pool); an unlisted species
 * still gets a tier for its own entry, measured against the listed pool plus itself.
 */
public final class EncyclopediaTiers {
    private EncyclopediaTiers() {}

    /**
     * Tiers by best pool share, rarest first. Full completion (lore) never asks for more than 50
     * catches of the species; rarer tiers ask for fewer so the expected casts to finish an entry
     * stay within a few hundred whatever the species. The name opens on the first catch in every
     * tier (world fish exploration P2: a catch always grants what spyglass observation grants).
     */
    public enum Tier {
        VERY_RARE(0.000f, new FishEncyclopediaEntry.UnlockThresholds(1, 1, 1, 1, 3)),
        RARE(0.015f, new FishEncyclopediaEntry.UnlockThresholds(1, 1, 2, 2, 6)),
        UNCOMMON(0.040f, new FishEncyclopediaEntry.UnlockThresholds(1, 2, 4, 3, 15)),
        COMMON(0.120f, new FishEncyclopediaEntry.UnlockThresholds(1, 3, 8, 4, 30)),
        ABUNDANT(0.300f, new FishEncyclopediaEntry.UnlockThresholds(1, 4, 12, 6, 50));

        /** Lowest best-pool share that lands a species in this tier. */
        public final float minShare;
        public final FishEncyclopediaEntry.UnlockThresholds thresholds;

        Tier(float minShare, FishEncyclopediaEntry.UnlockThresholds thresholds) {
            this.minShare = minShare;
            this.thresholds = thresholds;
        }

        public static Tier forShare(float share) {
            Tier result = VERY_RARE;
            for (Tier tier : values()) {
                if (share >= tier.minShare) result = tier;
            }
            return result;
        }
    }

    /** Used for a fish id with no profile at all; it can't be caught, so this only has to be sane. */
    public static final FishEncyclopediaEntry.UnlockThresholds FALLBACK = Tier.UNCOMMON.thresholds;

    /** Every zone set {@link FishProfile.Zone#resolve} returns for a real cast location. */
    public static final List<Set<FishProfile.Zone>> LOCATION_POOLS = List.of(
            EnumSet.of(FishProfile.Zone.NETHER),
            EnumSet.of(FishProfile.Zone.OCEAN),
            EnumSet.of(FishProfile.Zone.OCEAN, FishProfile.Zone.DEEP_OCEAN),
            EnumSet.of(FishProfile.Zone.RIVER),
            EnumSet.of(FishProfile.Zone.CAVE),
            EnumSet.of(FishProfile.Zone.RIVER, FishProfile.Zone.CAVE),
            EnumSet.of(FishProfile.Zone.RIVER, FishProfile.Zone.HIGH_ALTITUDE)
    );

    /** The inputs the derivation needs from one fish profile. */
    public record Species(Identifier id, int baseWeight, Collection<FishProfile.Zone> zones, boolean unlisted) {}

    /** One species' best pool and its share of it. {@code pool} is null when it fits no pool. */
    public record Rarity(float share, Set<FishProfile.Zone> pool, Tier tier) {}

    /** Pure derivation: each species' best pool share and the tier that share lands in. */
    public static Map<Identifier, Rarity> derive(Collection<Species> species) {
        int[] listedTotals = new int[LOCATION_POOLS.size()];
        for (Species s : species) {
            if (s.unlisted()) continue;
            for (int p = 0; p < LOCATION_POOLS.size(); p++) {
                if (inPool(s, LOCATION_POOLS.get(p))) listedTotals[p] += Math.max(0, s.baseWeight());
            }
        }

        Map<Identifier, Rarity> result = new HashMap<>();
        for (Species s : species) {
            int weight = Math.max(0, s.baseWeight());
            float best = 0f;
            Set<FishProfile.Zone> bestPool = null;
            for (int p = 0; p < LOCATION_POOLS.size(); p++) {
                Set<FishProfile.Zone> pool = LOCATION_POOLS.get(p);
                if (!inPool(s, pool)) continue;
                int total = listedTotals[p] + (s.unlisted() ? weight : 0);
                float share = total > 0 ? (float) weight / total : 0f;
                if (bestPool == null || share > best) {
                    best = share;
                    bestPool = pool;
                }
            }
            result.put(s.id(), new Rarity(best, bestPool, Tier.forShare(best)));
        }
        return result;
    }

    private static boolean inPool(Species s, Set<FishProfile.Zone> pool) {
        for (FishProfile.Zone zone : s.zones()) {
            if (pool.contains(zone)) return true;
        }
        return false;
    }

    // ----- Registry-backed lookup ----- //

    private record Cached(Set<Identifier> unlisted, Map<Identifier, Rarity> rarities) {}

    /**
     * Keyed by RegistryAccess: fish profiles are a datapack registry, so a new world or server
     * brings a new access and a fresh derivation. The unlisted tag can change on {@code /reload}
     * without a new access, so the cached unlisted set is rechecked on every lookup.
     */
    private static final Map<RegistryAccess, Cached> CACHE = new WeakHashMap<>();

    /** Thresholds for one fish, derived from the profile registry in {@code access}. */
    public static FishEncyclopediaEntry.UnlockThresholds thresholdsFor(RegistryAccess access, Identifier fishId) {
        Rarity rarity = rarities(access).get(fishId);
        return rarity != null ? rarity.tier().thresholds : FALLBACK;
    }

    public static FishEncyclopediaEntry.UnlockThresholds thresholdsFor(RegistryAccess access, ResourceKey<FishProfile> fishKey) {
        return thresholdsFor(access, fishKey.identifier());
    }

    /** Every species' rarity in {@code access}; empty if the profile registry isn't present. */
    public static synchronized Map<Identifier, Rarity> rarities(RegistryAccess access) {
        Registry<FishProfile> registry;
        try {
            registry = access.lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        } catch (Exception e) {
            return Map.of();
        }

        Set<Identifier> unlisted = new HashSet<>();
        for (Identifier id : registry.keySet()) {
            if (isUnlisted(id)) unlisted.add(id);
        }

        Cached cached = CACHE.get(access);
        if (cached != null && cached.unlisted().equals(unlisted)) return cached.rarities();

        List<Species> species = registry.entrySet().stream()
                .map(e -> new Species(e.getKey().identifier(), e.getValue().baseWeight(), e.getValue().zones(),
                        unlisted.contains(e.getKey().identifier())))
                .toList();
        Map<Identifier, Rarity> rarities = Map.copyOf(derive(species));
        CACHE.put(access, new Cached(unlisted, rarities));
        return rarities;
    }

    private static boolean isUnlisted(Identifier fishId) {
        return BuiltInRegistries.ITEM.getOptional(fishId)
                .map(item -> item.builtInRegistryHolder().is(FishtasticItemTags.UNLISTED_FISH))
                .orElse(false);
    }
}
