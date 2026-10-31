package grill24.fishtastic.server;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.item.FishtasticFishItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * One side-effect-free fishing pool resolver (Fish in World, Phase 3).
 *
 * <p>Answers a single question: <em>given this environment and this equipment, which species are in
 * the pool right now and with what final raw weight?</em> Real fishing
 * ({@link FishingMinigameManager#generateTargets}), the Angler's Almanac preview and future ambient
 * snapshots all consume this same answer, so the three can never drift apart.
 *
 * <h2>What "side-effect-free" means here</h2>
 * <ul>
 *   <li>Never touches {@code player.getRandom()} or any other RNG — resolving a pool consumes no
 *       random numbers, so a preview cannot shift the catch RNG sequence.</li>
 *   <li>Never casts, summons weather or consumes items. In particular a Storm Charm sitting on a
 *       rod is <em>not</em> fired here: the real cast path fires it before resolving (see
 *       {@code FishingMinigameManager#startSession}).</li>
 *   <li>Never reads world state the caller did not pass in: environment comes in as an
 *       {@link EnvironmentContext}, equipment as {@link Inputs}.</li>
 * </ul>
 *
 * <h2>Explicit inputs, including the hook-dependent ones</h2>
 * {@link AcuteIapsisItem} historically read the hook owner's inventory out of the loot params to
 * vary its own weight. That is an explicit {@link PersonalEquipment} input here; the real cast path
 * passes {@link PersonalEquipment#fromHookOwner} (the hook's owner, which is the caster), and the
 * the baseline modes pass what they actually know (see {@link Inputs}). No
 * {@code LootParams} are needed by the resolver.
 *
 * <h2>Species-affecting versus not</h2>
 * Only the inputs below may influence the candidate list or its weights, so the {@link
 * SpeciesContext#fingerprint()} covers exactly these and nothing else:
 * <ul>
 *   <li>environment: biome, time-of-day, weather, moon phase, zones — all five;</li>
 *   <li>bait: exclusive fish pool, mod-fish multiplier, pool rarity flattening, group affinities
 *       (group tag, member multiplier, non-member multiplier, rarity exponent);</li>
 *   <li>charm: night multiplier bonus, group affinities — note {@code forceNightFishing} is
 *       covered indirectly, because it only matters through the environment's time-of-day;</li>
 *   <li>personal equipment: inventory diamond count;</li>
 *   <li>pool membership: the ordered candidate ids and their final weights (which absorb profile
 *       datapack content).</li>
 * </ul>
 * Deliberately <em>not</em> species-affecting, so deliberately absent from {@link Inputs}: bait
 * luck/treasure/trash/target-count/quality-bias/small-bobber, charm input-force/outline/trash/
 * treasure/auto-pile/bait-save/sunset-extension, every {@code HookEffect} field (quality bias,
 * trash delta, treasure delta), player luck, forced temperament/difficulty/quality and target count.
 * Quality, difficulty, treasure and size are rolled downstream of species selection and must not
 * invalidate a cached species context — that is the whole point of the fingerprint.
 *
 * <h2>Ordering guarantees</h2>
 * {@link SpeciesContext#candidates()} is in pool order: the order of the {@code #minecraft:fishes}
 * tag, filtered — never re-sorted. {@link FishtasticFishItem#sampleRandomFish} and
 * {@link #topWeighted} both depend on it; ties in the ranking keep pool order (stable sort).
 */
public final class FishingPoolResolver {

    private FishingPoolResolver() {
    }

    /**
     * Everything about the world that can change which species are in the pool or how heavily they
     * weigh. Gathered once per resolution at the relevant position (hook, or the player for a
     * preview) by {@link #resolveEnvironment}.
     */
    public record EnvironmentContext(
            Holder<Biome> biome,
            FishProfile.TimeOfDay timeOfDay,
            FishProfile.WeatherCondition weather,
            FishMoonPhase moonPhase,
            Set<FishProfile.Zone> zones) {
    }

    /**
     * Equipment-derived modifiers that follow the player rather than the rod. Today that is only
     * the diamond count that {@link grill24.fishtastic.item.AcuteIapsisItem} weighs its own
     * catch-odds by; {@link #NEUTRAL} is the neutral baseline (no rod held, no live hook).
     */
    public record PersonalEquipment(int inventoryDiamonds) {

        /** Neutral personal equipment — zero diamonds, regardless of what the player is carrying. */
        public static final PersonalEquipment NEUTRAL = new PersonalEquipment(0);

        /** The player's own carried diamonds (main inventory; equipment slots don't count, as before). */
        public static PersonalEquipment fromInventory(Player player) {
            int diamonds = player.getInventory().items.stream()
                    .filter(stack -> stack.is(Items.DIAMOND))
                    .mapToInt(ItemStack::getCount)
                    .sum();
            return new PersonalEquipment(diamonds);
        }

        /**
         * The live hook owner's carried diamonds, or {@link #NEUTRAL} when there is no hook/owner —
         * exactly the lookup the pre-Phase-3 weight method did through
         * {@code LootContextParams.THIS_ENTITY}.
         */
        public static PersonalEquipment fromHookOwner(@Nullable FishingHook hook) {
            if (hook != null && hook.getOwner() instanceof Player owner) {
                return fromInventory(owner);
            }
            return NEUTRAL;
        }
    }

    /** One candidate species with its final raw weight, in pool order. */
    public record WeightedFish(Holder<Item> item, int weight) {
    }

    /**
     * The explicit input set of one resolution. The fishing modes expressible here:
     * <ul>
     *   <li><b>standard-worms baseline</b> (no rod held, or a rod with no bait): {@link
     *       #standardWormsBaseline()} — the registered Worms definition plus neutral personal
     *       equipment, so an inventory full of diamonds changes nothing.</li>
     *   <li><b>active-session snapshot</b>: not an input mode at all — a session stores the
     *       {@link SpeciesContext} it already resolved (see {@link SessionSnapshot}) rather than
     *       re-deriving one from later equipment.</li>
     * </ul>
     */
    public record Inputs(
            @Nullable BaitEffect baitEffect,
            @Nullable CharmEffect charmEffect,
            PersonalEquipment personalEquipment) {

        public Inputs {
            Objects.requireNonNull(personalEquipment,
                    "personal equipment is always explicit — use PersonalEquipment.NEUTRAL for the neutral baseline");
        }

        public static Inputs of(@Nullable BaitEffect baitEffect, @Nullable CharmEffect charmEffect,
                                PersonalEquipment personalEquipment) {
            return new Inputs(baitEffect, charmEffect, personalEquipment);
        }

        /**
         * No rod held: the registered standard Worms bait definition under local environment
         * conditions, with neutral personal equipment — zero diamonds no matter what the player
         * carries. Neither owns nor consumes worms.
         */
        public static Inputs standardWormsBaseline() {
            return new Inputs(BaitEffect.WORMS, null, PersonalEquipment.NEUTRAL);
        }

        /** Vanilla fish stay in the pool only for the bare hook — see {@link FishingPoolResolver#vanillaAllowed(BaitEffect)}. */
        public boolean vanillaAllowed() {
            return FishingPoolResolver.vanillaAllowed(baitEffect);
        }
    }

    /**
     * Vanilla fish stay in the pool only for the bare hook ({@code null} or {@link BaitEffect#NO_BAIT}).
     * Single definition shared by {@link Inputs#vanillaAllowed()} and {@link #candidatePool}.
     */
    public static boolean vanillaAllowed(@Nullable BaitEffect baitEffect) {
        return baitEffect == null || baitEffect.equals(BaitEffect.NO_BAIT);
    }

    /**
     * The full answer of one resolution: the environment it was resolved for, the candidate species
     * with their final raw weights in pool order, and a fingerprint of every species-affecting input
     * (see the class doc for the exact list). Cached/invalidated by fingerprint, never by resolving
     * twice.
     */
    public record SpeciesContext(EnvironmentContext environment, List<WeightedFish> candidates, long fingerprint) {
    }

    /**
     * What an active fishing session retains for its hook patch: where the cast resolved, which rod
     * was used, the inputs it resolved with, and the resulting species context. Captured once at
     * target-generation time; never reconstructed from later equipment. {@code rodId} is recorded for
     * diagnostics only and is deliberately not part of the fingerprint.
     */
    public record SessionSnapshot(Vec3 origin, @Nullable ResourceLocation rodId, Inputs inputs, SpeciesContext species) {
    }

    /**
     * Gathers the environment context at {@code pos}. {@code charmEffect} matters only for
     * {@code forceNightFishing} (Luna Charm), which pins time-of-day to night.
     */
    public static EnvironmentContext resolveEnvironment(Level level, BlockPos pos, @Nullable CharmEffect charmEffect) {
        Holder<Biome> biome = level.getBiome(pos);
        FishProfile.TimeOfDay timeOfDay = charmEffect != null && charmEffect.forceNightFishing()
                ? FishProfile.TimeOfDay.NIGHT
                : FishProfile.TimeOfDay.fromGameTime(level.getDayTime());
        FishProfile.WeatherCondition weather = FishProfile.WeatherCondition.fromLevel(level, pos);
        FishMoonPhase moonPhase = FishMoonPhase.at(level, pos);
        return grill24.fishtastic.ecology.NaturalPool.environment(biome, timeOfDay, weather, moonPhase,
                pos.getY(), level.getSeaLevel());
    }

    /**
     * The species eligible to be caught right now, in tag order. Applies, in this order: the bait's
     * exclusive pool narrowing, the vanilla exclusion (vanilla fish only survive a bare hook), and
     * the zone gate. No weights, no RNG.
     */
    public static List<Holder<Item>> candidatePool(Registry<Item> itemRegistry,
                                                   Registry<FishProfile> fishProfileRegistry,
                                                   EnvironmentContext environment,
                                                   @Nullable BaitEffect baitEffect) {
        Optional<TagKey<Item>> exclusivePool = baitEffect != null ? baitEffect.exclusiveFishPool() : Optional.empty();
        boolean allowVanilla = vanillaAllowed(baitEffect);

        List<Holder<Item>> pool = new ArrayList<>();
        for (Holder<Item> holder : itemRegistry.getTagOrEmpty(ItemTags.FISHES)) {
            if (exclusivePool.isPresent() && !holder.is(exclusivePool.get())) {
                continue;
            }
            if (!allowVanilla && !(holder.value() instanceof FishtasticFishItem)) {
                continue;
            }
            if (!FishtasticFishItem.isZoneEligible(holder, fishProfileRegistry, environment.zones())) {
                continue;
            }
            pool.add(holder);
        }
        return List.copyOf(pool);
    }

    /**
     * Resolves the pool for the given environment and equipment. Pure: the same inputs always
     * produce the same candidates, weights and fingerprint, and no random number is consumed.
     */
    public static SpeciesContext resolve(Registry<Item> itemRegistry,
                                         Registry<FishProfile> fishProfileRegistry,
                                         EnvironmentContext environment,
                                         Inputs inputs) {
        List<Holder<Item>> pool = candidatePool(itemRegistry, fishProfileRegistry, environment, inputs.baitEffect());

        List<WeightedFish> candidates = new ArrayList<>(pool.size());
        for (Holder<Item> item : pool) {
            int weight = FishtasticFishItem.getFishingLootWeight(item, fishProfileRegistry, environment,
                    inputs.baitEffect(), inputs.charmEffect(), inputs.personalEquipment().inventoryDiamonds());
            candidates.add(new WeightedFish(item, weight));
        }
        List<WeightedFish> ordered = List.copyOf(candidates);
        return new SpeciesContext(environment, ordered, fingerprint(environment, inputs, ordered));
    }

    /**
     * The {@code count} highest-weighted candidates, ranked descending — what a weighted draw is
     * most likely to pick right now. Ties keep pool order (stable sort). Only candidates with a
     * positive weight participate; every weight the mod produces today is at least 1, so in practice
     * this is "the whole pool, sorted".
     */
    public static List<WeightedFish> topWeighted(SpeciesContext context, int count) {
        if (count <= 0) {
            return List.of();
        }
        List<WeightedFish> ranked = new ArrayList<>(context.candidates());
        ranked.removeIf(candidate -> candidate.weight() <= 0);
        ranked.sort(Comparator.comparingInt(WeightedFish::weight).reversed());
        return List.copyOf(ranked.subList(0, Math.min(count, ranked.size())));
    }

    /**
     * Fingerprint over species-affecting inputs only (see the class doc for the exact list).
     *
     * <p>Limitation: a datapack/profile edit is detected through the weights it changes and through
     * tag membership, but an edit that alters neither zone eligibility nor any weight (a size
     * distribution tweak, say) leaves the fingerprint untouched — which is correct, since such an
     * edit cannot change which species are in the pool. Biome {@code Holder}s without a registry key
     * (test fixtures, direct holders) hash by identity instead of id, so a fingerprint is only
     * comparable within a world session.
     */
    private static long fingerprint(EnvironmentContext environment, Inputs inputs, List<WeightedFish> candidates) {
        StringBuilder canonical = new StringBuilder(256);
        canonical.append("biome=")
                .append(environment.biome().unwrapKey().map(key -> key.location().toString())
                        .orElseGet(() -> "<direct:" + System.identityHashCode(environment.biome()) + ">"));
        canonical.append("|time=").append(environment.timeOfDay());
        canonical.append("|weather=").append(environment.weather());
        canonical.append("|moon=").append(environment.moonPhase());
        canonical.append("|zones=");
        environment.zones().stream().map(Enum::name).sorted().forEach(zone -> canonical.append(zone).append(','));
        canonical.append("|bait=").append(baitFields(inputs.baitEffect()));
        canonical.append("|charm=").append(charmFields(inputs.charmEffect()));
        canonical.append("|diamonds=").append(inputs.personalEquipment().inventoryDiamonds());
        canonical.append("|pool=");
        for (WeightedFish candidate : candidates) {
            canonical.append(candidate.item().unwrapKey().map(key -> key.location().toString()).orElse("<unknown>"))
                    .append(':').append(candidate.weight()).append(',');
        }
        return fnv1a64(canonical);
    }

    private static String baitFields(@Nullable BaitEffect bait) {
        if (bait == null) {
            return "<null>";
        }
        StringBuilder fields = new StringBuilder(128);
        fields.append(bait.exclusiveFishPool().map(TagKey::location).map(Object::toString).orElse("-"));
        appendFloat(fields, bait.modFishMultiplier());
        appendFloat(fields, bait.rarityFlattening());
        appendAffinities(fields, bait.fishGroupAffinities());
        return fields.toString();
    }

    private static String charmFields(@Nullable CharmEffect charm) {
        if (charm == null) {
            return "<null>";
        }
        StringBuilder fields = new StringBuilder(128);
        appendFloat(fields, charm.nightMultiplierBonus());
        appendAffinities(fields, charm.fishGroupAffinities());
        return fields.toString();
    }

    private static void appendAffinities(StringBuilder out, List<BaitEffect.FishGroupAffinity> affinities) {
        for (BaitEffect.FishGroupAffinity affinity : affinities) {
            out.append(';').append(affinity.group().location());
            appendFloat(out, affinity.multiplier());
            appendFloat(out, affinity.nonMemberMultiplier());
            appendFloat(out, affinity.rarityExponent());
        }
    }

    private static void appendFloat(StringBuilder out, float value) {
        out.append(';').append(Integer.toHexString(Float.floatToIntBits(value)));
    }

    private static long fnv1a64(CharSequence input) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < input.length(); i++) {
            hash ^= input.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
