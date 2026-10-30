package grill24.fishtastic.server;

import com.mojang.serialization.Lifecycle;
import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticDataComponents;
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
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
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
 *       registry is touched, and no test item is registered into the built-in registry itself.</li>
 *   <li>The mod's own data component types ({@code ITEM_SIZE}, {@code FISH_QUALITY}) are normally
 *       registered by the loader entrypoints, so their static holders are null in a bare JVM.
 *       {@link #ensureDataComponents()} registers them against {@code BuiltInRegistries
 *       .DATA_COMPONENT_TYPE} exactly the way {@code FabricRegistrationApi#registerDataComponent}
 *       does, so {@code FishQualityHelper}/{@code ItemSizeHelper} work headlessly.</li>
 * </ul>
 * Holders in the fixture's own registry get empty component maps bound ({@code ItemStack}
 * materialises them eagerly); the game binds the real ones from item properties at resource reload.
 *
 * <p><b>Global state warning:</b> this fixture mutates process-global registries and leaves them
 * that way for the rest of the test JVM — {@code BuiltInRegistries.ITEM} and
 * {@code BuiltInRegistries.DATA_COMPONENT_TYPE} are re-opened (see {@link #unfreeze}) and eight
 * Fishtastic data component types are registered into the built-in registry. There is no restore
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
    static final Identifier COD = id(NS_VANILLA, "cod");
    static final Identifier RIVER_DARTER = id(NS_MOD, "river_darter");
    static final Identifier SALMON = id(NS_VANILLA, "salmon");
    static final Identifier OCEAN_GLIDER = id(NS_MOD, "ocean_glider");
    static final Identifier EXOTIC_BLENNY = id(NS_MOD, "exotic_blenny");
    static final Identifier COLORFUL_CYPRINID = id(NS_MOD, "colorful_cyprinid");
    static final Identifier DIAMOND_ALERT_IAPSIS = id(NS_MOD, "diamond_alert_iapsis");
    static final Identifier PROFILESS_MINNOW = id(NS_MOD, "profileless_minnow");
    static final Identifier TROPICAL_FISH = id(NS_VANILLA, "tropical_fish");
    static final Identifier POOLFRY = id(NS_MOD, "poolfry");
    static final Identifier PUFFERFISH = id(NS_VANILLA, "pufferfish");

    static final List<Identifier> POOL_ORDER = List.of(COD, RIVER_DARTER, SALMON, OCEAN_GLIDER, EXOTIC_BLENNY,
            COLORFUL_CYPRINID, DIAMOND_ALERT_IAPSIS, PROFILESS_MINNOW, TROPICAL_FISH, POOLFRY, PUFFERFISH);

    // ---- non-fish carrier items (a rod stack and its slots), never tagged into FISHES ----
    static final Identifier TEST_ROD = id(NS_MOD, "test_rod");
    static final Identifier TEST_BAIT = id(NS_MOD, "test_bait");
    static final Identifier TEST_CHARM = id(NS_MOD, "test_charm");
    static final Identifier TEST_HOOK = id(NS_MOD, "test_hook");

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
        if (dataComponentsRegistered) {
            return;
        }
        bootstrap();
        try {
            FishtasticDataComponents.ITEM_SIZE = registerComponent("item_size",
                    DataComponentType.<ItemSize>builder()
                            .persistent(ItemSize.CODEC)
                            .networkSynchronized(ItemSize.STREAM_CODEC)
                            .build());
            FishtasticDataComponents.FISH_QUALITY = registerComponent("fish_quality",
                    DataComponentType.<FishQuality>builder()
                            .persistent(FishQuality.CODEC)
                            .networkSynchronized(FishQuality.STREAM_CODEC)
                            .build());
            FishtasticDataComponents.ROD_BAIT_CONTENTS = registerComponent("rod_bait_contents",
                    DataComponentType.<RodBaitContents>builder()
                            .persistent(RodBaitContents.CODEC)
                            .networkSynchronized(RodBaitContents.STREAM_CODEC)
                            .build());
            FishtasticDataComponents.ROD_HOOK_CONTENTS = registerComponent("rod_hook_contents",
                    DataComponentType.<RodHookContents>builder()
                            .persistent(RodHookContents.CODEC)
                            .networkSynchronized(RodHookContents.STREAM_CODEC)
                            .build());
            FishtasticDataComponents.ROD_CHARM_CONTENTS = registerComponent("rod_charm_contents",
                    DataComponentType.<RodCharmContents>builder()
                            .persistent(RodCharmContents.CODEC)
                            .networkSynchronized(RodCharmContents.STREAM_CODEC)
                            .build());
            FishtasticDataComponents.BAIT_EFFECT = registerComponent("bait_effect",
                    DataComponentType.<BaitEffect>builder()
                            .persistent(BaitEffect.CODEC)
                            .networkSynchronized(BaitEffect.STREAM_CODEC)
                            .build());
            FishtasticDataComponents.HOOK_EFFECT = registerComponent("hook_effect",
                    DataComponentType.<HookEffect>builder()
                            .persistent(HookEffect.CODEC)
                            .networkSynchronized(HookEffect.STREAM_CODEC)
                            .build());
            FishtasticDataComponents.CHARM_EFFECT = registerComponent("charm_effect",
                    DataComponentType.<CharmEffect>builder()
                            .persistent(CharmEffect.CODEC)
                            .networkSynchronized(CharmEffect.STREAM_CODEC)
                            .build());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not register fishtastic data components for tests", e);
        }
        dataComponentsRegistered = true;
    }

    /**
     * A real rod stack carrying whatever gear the caller puts in its slots, so rod-reading code can
     * be exercised through the same {@code CopperFishingRod.getBait}/{@code getHook}/{@code getCharm}
     * accessors the server uses.
     */
    static ItemStack rodWith(ItemStack bait, ItemStack hook, ItemStack charm) {
        ItemStack rod = new ItemStack(items().getOrThrow(itemKey(TEST_ROD)));
        if (!bait.isEmpty()) CopperFishingRod.setBait(rod, bait);
        if (!hook.isEmpty()) CopperFishingRod.setHook(rod, hook);
        if (!charm.isEmpty()) CopperFishingRod.setCharm(rod, charm);
        return rod;
    }

    /** A fresh stack of one of the fixture's non-fish carrier items (bait/charm/hook carriers). */
    static ItemStack carrier(Identifier id) {
        return new ItemStack(items().getOrThrow(itemKey(id)));
    }

    @SuppressWarnings("unchecked")
    private static <T> Holder<DataComponentType<T>> registerComponent(String name, DataComponentType<T> type)
            throws ReflectiveOperationException {
        unfreeze(BuiltInRegistries.DATA_COMPONENT_TYPE, true);
        BuiltInRegistries.DATA_COMPONENT_TYPE.createIntrusiveHolder(type);
        ResourceKey<DataComponentType<?>> key =
                ResourceKey.create(BuiltInRegistries.DATA_COMPONENT_TYPE.key(), id(NS_MOD, name));
        return (Holder<DataComponentType<T>>) (Holder<?>)
                Registry.registerForHolder(BuiltInRegistries.DATA_COMPONENT_TYPE, key, type);
    }

    static Identifier id(String namespace, String path) {
        return Ids.of(namespace, path);
    }

    static ResourceKey<Item> itemKey(Identifier id) {
        return ResourceKey.create(Registries.ITEM, id);
    }

    static ResourceKey<FishProfile> profileKey(Identifier id) {
        return ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, id);
    }

    /** Item registry: real vanilla Items under their own keys plus synthetic Fishtastic fish. */
    static synchronized MappedRegistry<Item> items() {
        if (items != null) {
            return items;
        }
        bootstrap();
        MappedRegistry<Item> registry = new MappedRegistry<>(Registries.ITEM, Lifecycle.stable());
        for (Identifier species : POOL_ORDER) {
            registry.register(itemKey(species), create(species), RegistrationInfo.BUILT_IN)
                    .bindComponents(DataComponentMap.EMPTY);
        }
        registry.register(itemKey(TEST_ROD),
                        new CopperFishingRod(new Item.Properties().setId(itemKey(TEST_ROD))), RegistrationInfo.BUILT_IN)
                .bindComponents(DataComponentMap.EMPTY);
        for (Identifier carrier : List.of(TEST_BAIT, TEST_CHARM, TEST_HOOK)) {
            registry.register(itemKey(carrier),
                            new Item(new Item.Properties().setId(itemKey(carrier))), RegistrationInfo.BUILT_IN)
                    .bindComponents(DataComponentMap.EMPTY);
        }
        Map<TagKey<Item>, List<Holder<Item>>> tagMap = new HashMap<>();
        tagMap.put(ItemTags.FISHES,
                POOL_ORDER.stream().<Holder<Item>>map(species -> registry.getOrThrow(itemKey(species))).toList());
        tagMap.put(FishtasticItemTags.EXOTIC_FISH,
                List.of(registry.getOrThrow(itemKey(EXOTIC_BLENNY))));
        tagMap.put(FishtasticItemTags.SMALL_FISH,
                List.of(registry.getOrThrow(itemKey(OCEAN_GLIDER)), registry.getOrThrow(itemKey(COLORFUL_CYPRINID))));
        tagMap.put(FishtasticItemTags.COLOR_YELLOW,
                List.of(registry.getOrThrow(itemKey(COLORFUL_CYPRINID))));
        registry.bindTags(tagMap);
        registry.freeze();
        items = registry;
        return registry;
    }

    private static Item create(Identifier species) {
        if (species.equals(COD)) return Items.COD;
        if (species.equals(SALMON)) return Items.SALMON;
        if (species.equals(TROPICAL_FISH)) return Items.TROPICAL_FISH;
        if (species.equals(PUFFERFISH)) return Items.PUFFERFISH;
        Item.Properties properties = new Item.Properties().setId(itemKey(species));
        if (species.equals(DIAMOND_ALERT_IAPSIS)) return new AcuteIapsisItem(properties);
        return new FishtasticFishItem(properties);
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
    static MappedRegistry<FishProfile> profilesWithBaseWeight(Identifier species, int baseWeight) {
        bootstrap();
        return buildProfiles(Map.of(species, baseWeight));
    }

    private static MappedRegistry<FishProfile> buildProfiles(Map<Identifier, Integer> baseWeightOverrides) {
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
                List.of()), RegistrationInfo.BUILT_IN);
        registry.register(profileKey(OCEAN_GLIDER), profile(baseWeightOverrides.getOrDefault(OCEAN_GLIDER, 30),
                List.of(FishProfile.Zone.OCEAN),
                List.of(),
                List.of(),
                List.of(),
                List.of(new FishProfile.MoonWeight(FishMoonPhase.FULL_MOON, 3.0f,
                        FishProfile.ConditionTier.PRIMARY))), RegistrationInfo.BUILT_IN);
        registry.register(profileKey(EXOTIC_BLENNY), profile(baseWeightOverrides.getOrDefault(EXOTIC_BLENNY, 12),
                List.of(FishProfile.Zone.RIVER), List.of(), List.of(), List.of(), List.of()),
                RegistrationInfo.BUILT_IN);
        registry.register(profileKey(COLORFUL_CYPRINID), profile(baseWeightOverrides.getOrDefault(COLORFUL_CYPRINID, 8),
                List.of(FishProfile.Zone.RIVER), List.of(), List.of(), List.of(), List.of()),
                RegistrationInfo.BUILT_IN);
        registry.register(profileKey(DIAMOND_ALERT_IAPSIS), profile(baseWeightOverrides.getOrDefault(DIAMOND_ALERT_IAPSIS, 6),
                List.of(FishProfile.Zone.RIVER, FishProfile.Zone.OCEAN), List.of(), List.of(), List.of(), List.of()),
                RegistrationInfo.BUILT_IN);
        registry.register(profileKey(POOLFRY), profile(baseWeightOverrides.getOrDefault(POOLFRY, 4),
                List.of(FishProfile.Zone.RIVER),
                List.of(new FishProfile.BiomeWeight(HolderSet.direct(riverBiome()), 2.0f,
                        FishProfile.ConditionTier.PRIMARY)),
                List.of(), List.of(), List.of()), RegistrationInfo.BUILT_IN);
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
                FishProfile.CompanionDrift.BELL, Optional.empty());
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
                    .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3F76E4).build())
                    .mobSpawnSettings(MobSpawnSettings.EMPTY)
                    .generationSettings(BiomeGenerationSettings.EMPTY)
                    .build();
            // Registered under a key rather than Holder.direct so the resolver's fingerprint hashes
            // a stable biome id instead of System.identityHashCode — the exact-value fingerprint
            // pin in FishingPoolResolverTest depends on that id being the same every JVM run.
            MappedRegistry<Biome> biomes = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
            riverBiome = biomes.register(ResourceKey.create(Registries.BIOME, id(NS_MOD, "test_river")),
                    biome, RegistrationInfo.BUILT_IN);
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
        return holder.unwrapKey().map(key -> key.identifier().toString()).orElse("<unknown>");
    }
}
