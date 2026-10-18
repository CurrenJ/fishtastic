package grill24.fishtastic.server;

import com.mojang.serialization.Lifecycle;
import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.HookEffect;
import grill24.fishtastic.component.ItemSize;
import grill24.fishtastic.component.RodBaitContents;
import grill24.fishtastic.component.RodCharmContents;
import grill24.fishtastic.component.RodHookContents;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.SwarmConfig;
import grill24.fishtastic.item.AcuteIapsisItem;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.util.Ids;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Headless fixture for the fishing pool resolver: builds small synthetic {@code ITEM} and
 * {@code FISH_PROFILE} registries so pool filtering, weighting, ordering and the seeded reward
 * draw can be pinned in plain JUnit without a running server.
 *
 * <p>The species set and tag order below are fixed on purpose: tests pin the exact weights this
 * produces, so any behaviour change in the resolver or the item weight math shows up as a failure.
 * Real vanilla {@code Items} are registered under their own keys so the vanilla (no-bait) branch is
 * exercised for real rather than approximated.
 *
 * <h2>Bootstrap mechanics</h2>
 * Two pieces of game setup are unavailable outside a running game, so this fixture reproduces the
 * minimum:
 * <ul>
 *   <li>{@code Bootstrap.bootStrap()} freezes the built-in registries, and {@code Item}'s
 *       constructor asks {@code BuiltInRegistries.ITEM} for an intrusive holder — which a frozen
 *       registry refuses. In a real game the platform registers mod items before that freeze (or
 *       re-opens the registry); here {@link #unfreeze} clears the two relevant {@code MappedRegistry}
 *       fields reflectively so items can be constructed after bootstrapping. Nothing else about the
 *       registry is touched; only the three rod-slot carrier items are registered into it (see {@link #items}, needed for NBT round-trips).</li>
 *   <li>On 1.20.1 the mod's item data ({@code ITEM_SIZE}, {@code FISH_QUALITY}, rod slots, ...) are
 *       NBT-keyed {@code ComponentKey}s, so there is nothing to register;
 *       {@link #ensureDataComponents()} only bootstraps.</li>
 * </ul>
 * Holders in the fixture's own registry get empty component maps bound ({@code ItemStack}
 * materialises them eagerly); the game binds the real ones from item properties at resource reload.
 *
 * <p><b>Global state warning:</b> this fixture mutates process-global registries and leaves them
 * that way for the rest of the test JVM — {@code BuiltInRegistries.ITEM} is re-opened (see {@link #unfreeze}). There is no restore
 * hook: un-freezing a shared registry cannot be undone safely mid-JVM, and re-freezing it from an
 * {@code @AfterAll} could break whichever suite runs next. Anything that asserts on "every
 * registered data component type" or on registry frozenness must therefore either run before this
 * fixture bootstraps or account for these entries.
 */
public final class FishingPoolFixture {

    static final String NS_MOD = "fishtastic";
    static final String NS_VANILLA = "minecraft";

    private static boolean bootstrapCalled;
    private static boolean dataComponentsRegistered;
    private static MappedRegistry<Item> items;
    private static MappedRegistry<FishProfile> profiles;
    private static Holder<Biome> riverBiome;

    // ---- species keys (pool order == ItemTags.FISHES tag order, vanilla interleaved) ----
    static final ResourceLocation COD = id(NS_VANILLA, "cod");
    static final ResourceLocation RIVER_DARTER = id(NS_MOD, "river_darter");
    static final ResourceLocation SALMON = id(NS_VANILLA, "salmon");
    static final ResourceLocation OCEAN_GLIDER = id(NS_MOD, "ocean_glider");
    static final ResourceLocation EXOTIC_BLENNY = id(NS_MOD, "exotic_blenny");
    static final ResourceLocation COLORFUL_CYPRINID = id(NS_MOD, "colorful_cyprinid");
    static final ResourceLocation DIAMOND_ALERT_IAPSIS = id(NS_MOD, "diamond_alert_iapsis");
    static final ResourceLocation PROFILESS_MINNOW = id(NS_MOD, "profileless_minnow");
    static final ResourceLocation TROPICAL_FISH = id(NS_VANILLA, "tropical_fish");
    static final ResourceLocation POOLFRY = id(NS_MOD, "poolfry");
    static final ResourceLocation PUFFERFISH = id(NS_VANILLA, "pufferfish");

    static final List<ResourceLocation> POOL_ORDER = List.of(COD, RIVER_DARTER, SALMON, OCEAN_GLIDER, EXOTIC_BLENNY,
            COLORFUL_CYPRINID, DIAMOND_ALERT_IAPSIS, PROFILESS_MINNOW, TROPICAL_FISH, POOLFRY, PUFFERFISH);

    // ---- non-fish carrier items (a rod stack and its slots), never tagged into FISHES ----
    static final ResourceLocation TEST_ROD = id(NS_MOD, "test_rod");
    static final ResourceLocation TEST_BAIT = id(NS_MOD, "test_bait");
    static final ResourceLocation TEST_CHARM = id(NS_MOD, "test_charm");
    static final ResourceLocation TEST_HOOK = id(NS_MOD, "test_hook");

    // ---- environment zone sets ----
    static final Set<FishProfile.Zone> RIVER_ZONES = Set.copyOf(EnumSet.of(FishProfile.Zone.RIVER));
    static final Set<FishProfile.Zone> OCEAN_ZONES = Set.copyOf(EnumSet.of(FishProfile.Zone.OCEAN));
    static final Set<FishProfile.Zone> HIGH_ALTITUDE_ZONES =
            Set.copyOf(EnumSet.of(FishProfile.Zone.RIVER, FishProfile.Zone.HIGH_ALTITUDE));

    private FishingPoolFixture() {
    }

    public static void bootstrap() {
        if (bootstrapCalled) {
            return;
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bootstrapCalled = true;
        try {
            unfreeze(BuiltInRegistries.ITEM, true);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not re-open BuiltInRegistries.ITEM for test items", e);
        }
    }

    /**
     * Clears the two fields {@code MappedRegistry.freeze()} writes: the frozen flag (so writes are
     * accepted again) and — for registries created with intrusive-holder support — the intrusive
     * holder map that holds a holder for a value until it is actually registered.
     */
    private static void unfreeze(Registry<?> registry, boolean allowIntrusiveHolders)
            throws ReflectiveOperationException {
        Field frozen = MappedRegistry.class.getDeclaredField("frozen");
        frozen.setAccessible(true);
        frozen.setBoolean(registry, false);
        Field intrusive = MappedRegistry.class.getDeclaredField("unregisteredIntrusiveHolders");
        intrusive.setAccessible(true);
        intrusive.set(registry, allowIntrusiveHolders ? new IdentityHashMap<>() : null);
    }

    /**
     * Registers the mod's stack-data component types the way the loader entrypoints do. Idempotent;
     * needed before any {@code FishQualityHelper}/{@code ItemSizeHelper} call, and before a rod
     * stack can carry bait/hook/charm slots (see {@link #rodWith}).
     */
    static synchronized void ensureDataComponents() {
        // 1.20.1: item data are NBT-key ComponentKeys, nothing to register.
        bootstrap();
        dataComponentsRegistered = true;
    }

    /**
     * A real rod stack carrying whatever gear the caller puts in its slots, so
     * {@link FishingPoolResolver.Inputs#heldRodPreview} can be exercised through the same
     * {@code CopperFishingRod.getBait}/{@code getHook}/{@code getCharm} accessors the server uses.
     */
    static ItemStack rodWith(ItemStack bait, ItemStack hook, ItemStack charm) {
        ItemStack rod = new ItemStack(items().getHolderOrThrow(itemKey(TEST_ROD)).value());
        if (!bait.isEmpty()) CopperFishingRod.setBait(rod, bait);
        if (!hook.isEmpty()) CopperFishingRod.setHook(rod, hook);
        if (!charm.isEmpty()) CopperFishingRod.setCharm(rod, charm);
        return rod;
    }

    /** A fresh stack of one of the fixture's non-fish carrier items (bait/charm/hook carriers). */
    static ItemStack carrier(ResourceLocation id) {
        return new ItemStack(items().getHolderOrThrow(itemKey(id)).value());
    }

    static ResourceLocation id(String namespace, String path) {
        return Ids.of(namespace, path);
    }

    static ResourceKey<Item> itemKey(ResourceLocation id) {
        return ResourceKey.create(Registries.ITEM, id);
    }

    static ResourceKey<FishProfile> profileKey(ResourceLocation id) {
        return ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, id);
    }

    /** Item registry: real vanilla Items under their own keys plus synthetic Fishtastic fish. */
    static synchronized MappedRegistry<Item> items() {
        if (items != null) {
            return items;
        }
        bootstrap();
        MappedRegistry<Item> registry = new MappedRegistry<>(Registries.ITEM, Lifecycle.stable());
        for (ResourceLocation species : POOL_ORDER) {
            registry.register(itemKey(species), create(species), Lifecycle.stable());
        }
        registry.register(itemKey(TEST_ROD),
                        new CopperFishingRod(new Item.Properties()), Lifecycle.stable());
        for (ResourceLocation carrier : List.of(TEST_BAIT, TEST_CHARM, TEST_HOOK)) {
            Item carrierItem = new Item(new Item.Properties());
            registry.register(itemKey(carrier), carrierItem, Lifecycle.stable());
            // 1.20.1 stores rod slots as NBT through ItemStack.CODEC, which writes the item's id
            // via BuiltInRegistries.ITEM; the carriers must be known there to round-trip.
            Registry.register(BuiltInRegistries.ITEM, carrier, carrierItem);
        }
        Map<TagKey<Item>, List<Holder<Item>>> tagMap = new HashMap<>();
        tagMap.put(ItemTags.FISHES,
                POOL_ORDER.stream().<Holder<Item>>map(species -> registry.getHolderOrThrow(itemKey(species))).toList());
        tagMap.put(FishtasticItemTags.EXOTIC_FISH,
                List.of(registry.getHolderOrThrow(itemKey(EXOTIC_BLENNY))));
        tagMap.put(FishtasticItemTags.SMALL_FISH,
                List.of(registry.getHolderOrThrow(itemKey(OCEAN_GLIDER)), registry.getHolderOrThrow(itemKey(COLORFUL_CYPRINID))));
        tagMap.put(FishtasticItemTags.COLOR_YELLOW,
                List.of(registry.getHolderOrThrow(itemKey(COLORFUL_CYPRINID))));
        registry.bindTags(tagMap);
        registry.freeze();
        items = registry;
        return registry;
    }

    private static Item create(ResourceLocation species) {
        if (species.equals(COD)) return Items.COD;
        if (species.equals(SALMON)) return Items.SALMON;
        if (species.equals(TROPICAL_FISH)) return Items.TROPICAL_FISH;
        if (species.equals(PUFFERFISH)) return Items.PUFFERFISH;
        Item.Properties properties = new Item.Properties();
        Item item = species.equals(DIAMOND_ALERT_IAPSIS) ? new AcuteIapsisItem(properties)
                : new FishtasticFishItem(properties);
        bindIntrusiveHolder(item, itemKey(species));
        return item;
    }

    /**
     * On 1.20.1 {@code ItemStack.getItemHolder()} resolves through the item's own intrusive
     * holder, not the holder a test registry hands out, so a synthetic item's key must be bound
     * there too or species-keyed lookups (size profile) see an unregistered item.
     */
    private static void bindIntrusiveHolder(Item item, ResourceKey<Item> key) {
        try {
            Holder.Reference<Item> holder = item.builtInRegistryHolder();
            Method bindKey = Holder.Reference.class.getDeclaredMethod("bindKey", ResourceKey.class);
            bindKey.setAccessible(true);
            bindKey.invoke(holder, key);
            Method bindValue = Holder.Reference.class.getDeclaredMethod("bindValue", Object.class);
            bindValue.setAccessible(true);
            bindValue.invoke(holder, item);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not bind the intrusive holder for " + key, e);
        }
    }

    /**
     * Profiles for the fixture's mod fish. Only profile-relevant axes are set; every other
     * component uses the record's default. {@code profileless_minnow} deliberately has none, to
     * keep the fail-open zone rule covered.
     */
    static synchronized MappedRegistry<FishProfile> profiles() {
        if (profiles != null) {
            return profiles;
        }
        bootstrap();
        profiles = buildProfiles(Map.of());
        return profiles;
    }

    /**
     * A one-off profile registry identical to {@link #profiles()} except that {@code species} has
     * {@code baseWeight} instead of its usual one. Used to prove the fingerprint hashes the
     * resolved pool itself, without disturbing the shared registry every other test resolves
     * against.
     */
    static MappedRegistry<FishProfile> profilesWithBaseWeight(ResourceLocation species, int baseWeight) {
        bootstrap();
        return buildProfiles(Map.of(species, baseWeight));
    }

    private static MappedRegistry<FishProfile> buildProfiles(Map<ResourceLocation, Integer> baseWeightOverrides) {
        MappedRegistry<FishProfile> registry =
                new MappedRegistry<>(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, Lifecycle.stable());
        registry.register(profileKey(RIVER_DARTER), profile(baseWeightOverrides.getOrDefault(RIVER_DARTER, 20),
                List.of(FishProfile.Zone.RIVER),
                List.of(),
                List.of(
                        new FishProfile.TimeWeight(FishProfile.TimeOfDay.DUSK, 2.0f, FishProfile.ConditionTier.PRIMARY),
                        new FishProfile.TimeWeight(FishProfile.TimeOfDay.NIGHT, 0.5f, FishProfile.ConditionTier.PRIMARY)),
                List.of(new FishProfile.WeatherWeight(FishProfile.WeatherCondition.RAIN, 2.0f,
                        FishProfile.ConditionTier.PRIMARY)),
                List.of()), Lifecycle.stable());
        registry.register(profileKey(OCEAN_GLIDER), profile(baseWeightOverrides.getOrDefault(OCEAN_GLIDER, 30),
                List.of(FishProfile.Zone.OCEAN),
                List.of(),
                List.of(),
                List.of(),
                List.of(new FishProfile.MoonWeight(FishMoonPhase.FULL_MOON, 3.0f,
                        FishProfile.ConditionTier.PRIMARY))), Lifecycle.stable());
        registry.register(profileKey(EXOTIC_BLENNY), profile(baseWeightOverrides.getOrDefault(EXOTIC_BLENNY, 12),
                List.of(FishProfile.Zone.RIVER), List.of(), List.of(), List.of(), List.of()),
                Lifecycle.stable());
        registry.register(profileKey(COLORFUL_CYPRINID), profile(baseWeightOverrides.getOrDefault(COLORFUL_CYPRINID, 8),
                List.of(FishProfile.Zone.RIVER), List.of(), List.of(), List.of(), List.of()),
                Lifecycle.stable());
        registry.register(profileKey(DIAMOND_ALERT_IAPSIS), profile(baseWeightOverrides.getOrDefault(DIAMOND_ALERT_IAPSIS, 6),
                List.of(FishProfile.Zone.RIVER, FishProfile.Zone.OCEAN), List.of(), List.of(), List.of(), List.of()),
                Lifecycle.stable());
        registry.register(profileKey(POOLFRY), profile(baseWeightOverrides.getOrDefault(POOLFRY, 4),
                List.of(FishProfile.Zone.RIVER),
                List.of(new FishProfile.BiomeWeight(FishProfile.BiomeSelector.ofKeys(List.of(riverBiome().unwrapKey().orElseThrow())), 2.0f,
                        FishProfile.ConditionTier.PRIMARY)),
                List.of(), List.of(), List.of()), Lifecycle.stable());
        registry.freeze();
        return registry;
    }

    private static FishProfile profile(int baseWeight, List<FishProfile.Zone> zones,
                                       List<FishProfile.BiomeWeight> biomeWeights,
                                       List<FishProfile.TimeWeight> timeWeights,
                                       List<FishProfile.WeatherWeight> weatherWeights,
                                       List<FishProfile.MoonWeight> moonWeights) {
        return new FishProfile(baseWeight, FishProfile.SizeParams.DEFAULT, biomeWeights, timeWeights,
                weatherWeights, moonWeights, zones, Optional.empty(), Optional.empty(), SwarmConfig.DEFAULT,
                FishProfile.DEFAULT_RENDER_CALIBRATION, Optional.empty(), FishProfile.CompanionGait.CRAWL,
                FishProfile.CompanionDrift.BELL);
    }

    /**
     * Single shared biome holder — biome weights match on this exact instance ({@code HolderSet
     * .Direct.contains} is identity-based, and {@code Holder} overrides neither equals nor
     * hashCode), so every test environment must use this instance or the poolfry biome weight
     * silently stops matching.
     */
    static synchronized Holder<Biome> riverBiome() {
        if (riverBiome == null) {
            bootstrap();
            Biome biome = new Biome.BiomeBuilder()
                    .temperature(0.5f)
                    .downfall(0.5f)
                    .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3F76E4).waterFogColor(0x050533)
                            .fogColor(0xC0D8FF).skyColor(0x78A7FF).build())
                    .mobSpawnSettings(MobSpawnSettings.EMPTY)
                    .generationSettings(BiomeGenerationSettings.EMPTY)
                    .build();
            // Registered under a key rather than Holder.direct so the resolver's fingerprint hashes
            // a stable biome id instead of System.identityHashCode — the exact-value fingerprint
            // pin in FishingPoolResolverTest depends on that id being the same every JVM run.
            MappedRegistry<Biome> biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
            riverBiome = biomes.register(ResourceKey.create(Registries.BIOME, id(NS_MOD, "test_river")),
                    biome, Lifecycle.stable());
            biomes.freeze();
        }
        return riverBiome;
    }

    /** Index of a sampled stack's item within {@code pool}, by identity (fixture items are singletons). */
    static int indexOf(List<Holder<Item>> pool, Item item) {
        for (int i = 0; i < pool.size(); i++) {
            if (pool.get(i).value() == item) {
                return i;
            }
        }
        return -1;
    }

    static String idOf(Holder<Item> holder) {
        return holder.unwrapKey().map(key -> key.location().toString()).orElse("<unknown>");
    }
}
