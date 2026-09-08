package grill24.fishtastic.server;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.component.HookEffect;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.data.PhaseRule;
import grill24.fishtastic.data.Temperament;
import grill24.fishtastic.server.QuestTracker;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.item.PileOfFishItem;
import grill24.fishtastic.item.StormCharmItem;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.network.StartFishingMinigamePacket;
import grill24.fishtastic.util.FishingTarget;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.IFishingHookExtension;
import grill24.fishtastic.util.MathUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Server-side manager for fishing minigame sessions (TRUST-BASED).
 * Server generates loot and caches it. Client plays the minigame locally and reports results.
 * Server validates and awards only the items that were actually in the session.
 */
public class FishingMinigameManager {
    private static final Map<ServerLevel, FishingMinigameManager> INSTANCES = new WeakHashMap<>();

    // Debug: per-player forced temperament override, applied to all fish targets in a session.
    private static final Map<UUID, net.minecraft.resources.ResourceKey<Temperament>> FORCED_TEMPERAMENTS = new HashMap<>();

    public static void setForcedTemperament(UUID playerId, net.minecraft.resources.ResourceKey<Temperament> key) {
        FORCED_TEMPERAMENTS.put(playerId, key);
    }

    public static void clearForcedTemperament(UUID playerId) {
        FORCED_TEMPERAMENTS.remove(playerId);
    }

    public static Optional<net.minecraft.resources.ResourceKey<Temperament>> getForcedTemperament(UUID playerId) {
        return Optional.ofNullable(FORCED_TEMPERAMENTS.get(playerId));
    }

    // Debug: per-player forced exact difficulty override, bypassing temperament sampling
    // and the quality difficulty boost so a session is fully reproducible for testing.
    private static final Map<UUID, Float> FORCED_DIFFICULTIES = new HashMap<>();

    public static void setForcedDifficulty(UUID playerId, float difficulty) {
        FORCED_DIFFICULTIES.put(playerId, difficulty);
    }

    public static void clearForcedDifficulty(UUID playerId) {
        FORCED_DIFFICULTIES.remove(playerId);
    }

    public static Optional<Float> getForcedDifficulty(UUID playerId) {
        return Optional.ofNullable(FORCED_DIFFICULTIES.get(playerId));
    }

    // Debug: per-player forced quality override, stamped onto every fish reward in a session after
    // the loot roll. Exists so the rare-catch celebration sequences can be seen in a real cast
    // rather than only in isolation — the odds of rolling a Legendary on demand are the whole
    // problem. Applied before the quality difficulty boost, so a forced Legendary also fights like
    // one instead of being a common fish wearing a gold tooltip.
    private static final Map<UUID, FishQuality.Quality> FORCED_QUALITIES = new HashMap<>();

    public static void setForcedQuality(UUID playerId, FishQuality.Quality quality) {
        FORCED_QUALITIES.put(playerId, quality);
    }

    public static void clearForcedQuality(UUID playerId) {
        FORCED_QUALITIES.remove(playerId);
    }

    public static Optional<FishQuality.Quality> getForcedQuality(UUID playerId) {
        return Optional.ofNullable(FORCED_QUALITIES.get(playerId));
    }

    private final Map<UUID, ActiveSession> activeSessions = new HashMap<>();
    private final AtomicInteger sessionIdGenerator = new AtomicInteger(0);

    private static final int SESSION_TIMEOUT_TICKS = 6000;
    private static final int MAX_TARGETS = 4;
    private static final float DEFAULT_TREASURE_CHANCE = 1.0f / 6.0f;
    private static final float DEFAULT_TRASH_CHANCE = 0.12f;
    // How strongly a fish's rolled quality pushes its minigame difficulty toward the max,
    // independent of its species' base temperament. 0 = no effect, 1 = quality alone can
    // drive difficulty all the way to 1.0 regardless of species.
    private static final float QUALITY_DIFFICULTY_BOOST_STRENGTH = 0.6f;
    private static final int DEFAULT_TARGET_COUNT_MEAN = 1;

    /**
     * Data-driven treasure loot table (under {@code data/fishtastic/loot_table/gameplay/fishing/})
     * per rolled {@link FishQuality.Quality} tier. Legendary has no entry here — it's a dedicated
     * code path, see {@link #generateLegendaryTreasureTank}, since it composes three independently
     * rolled blocks plus a shape into one {@code FishTankMaterials} component rather than picking
     * a single loot-table item.
     */
    private static final Map<FishQuality.Quality, String> TREASURE_LOOT_TABLE_NAMES = Map.of(
            FishQuality.Quality.COMMON, "gameplay/fishing/treasure_common",
            FishQuality.Quality.UNCOMMON, "gameplay/fishing/treasure_uncommon",
            FishQuality.Quality.RARE, "gameplay/fishing/treasure_rare",
            FishQuality.Quality.EPIC, "gameplay/fishing/treasure_epic"
    );

    /** Reward count for a Legendary treasure hit's fish tank — matches the shop-tank bulk convention. */
    private static final int LEGENDARY_TANK_COUNT = 8;

    // Legendary treasure: frame/sand/glass/shape are each rolled independently from these preset
    // lists (see the tiered treasure pool design doc) rather than a fixed preset combo, so a
    // legendary pull is always a surprise. Frame is deliberately stocked with materials little/no
    // existing shop/quest tank uses.
    private static final List<Identifier> LEGENDARY_TANK_FRAMES = List.of(
            Identifier.withDefaultNamespace("emerald_block"),
            Identifier.withDefaultNamespace("netherite_block"),
            Identifier.withDefaultNamespace("copper_block"),
            Identifier.withDefaultNamespace("crying_obsidian"),
            Identifier.withDefaultNamespace("sculk"),
            Identifier.withDefaultNamespace("end_stone_bricks"),
            Identifier.withDefaultNamespace("warped_planks"),
            Identifier.withDefaultNamespace("crimson_planks"),
            Identifier.withDefaultNamespace("lodestone"),
            Identifier.withDefaultNamespace("sea_lantern"),
            Identifier.withDefaultNamespace("obsidian"),
            Identifier.withDefaultNamespace("ancient_debris"),
            Identifier.withDefaultNamespace("respawn_anchor"),
            Identifier.withDefaultNamespace("reinforced_deepslate"),
            Identifier.withDefaultNamespace("chiseled_nether_bricks"),
            Identifier.withDefaultNamespace("purpur_pillar"),
            Identifier.withDefaultNamespace("exposed_copper"),
            Identifier.withDefaultNamespace("chiseled_polished_blackstone"),
            Identifier.withDefaultNamespace("budding_amethyst"),
            Identifier.withDefaultNamespace("dripstone_block"),
            Identifier.withDefaultNamespace("glowstone"),
            Identifier.withDefaultNamespace("shroomlight"),
            Fishtastic.id("cyan_clear_stained_glass"),
            Fishtastic.id("pink_clear_stained_glass"),
            Fishtastic.id("lime_clear_stained_glass")
    );
    private static final List<Identifier> LEGENDARY_TANK_SANDS = List.of(
            Identifier.withDefaultNamespace("sand"),
            Identifier.withDefaultNamespace("red_sand"),
            Identifier.withDefaultNamespace("gravel"),
            Identifier.withDefaultNamespace("soul_sand"),
            Identifier.withDefaultNamespace("soul_soil"),
            Identifier.withDefaultNamespace("snow_block"),
            Identifier.withDefaultNamespace("glowstone"),
            Identifier.withDefaultNamespace("shroomlight"),
            Fishtastic.id("cyan_clear_stained_glass"),
            Fishtastic.id("pink_clear_stained_glass"),
            Fishtastic.id("lime_clear_stained_glass")
    );
    private static final List<Identifier> LEGENDARY_TANK_GLASS = List.of(
            Fishtastic.id("white_clear_stained_glass"),
            Fishtastic.id("light_gray_clear_stained_glass"),
            Fishtastic.id("gray_clear_stained_glass"),
            Fishtastic.id("black_clear_stained_glass"),
            Fishtastic.id("brown_clear_stained_glass"),
            Fishtastic.id("red_clear_stained_glass"),
            Fishtastic.id("orange_clear_stained_glass"),
            Fishtastic.id("yellow_clear_stained_glass"),
            Fishtastic.id("lime_clear_stained_glass"),
            Fishtastic.id("green_clear_stained_glass"),
            Fishtastic.id("cyan_clear_stained_glass"),
            Fishtastic.id("light_blue_clear_stained_glass"),
            Fishtastic.id("blue_clear_stained_glass"),
            Fishtastic.id("purple_clear_stained_glass"),
            Fishtastic.id("magenta_clear_stained_glass"),
            Fishtastic.id("pink_clear_stained_glass")
    );

    private final ServerLevel level;

    private FishingMinigameManager(ServerLevel level) {
        this.level = level;
    }

    public static FishingMinigameManager get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, FishingMinigameManager::new);
    }

    public int startSession(ServerPlayer player, float difficultyModifier, boolean cancelExistingSession) {
        UUID playerId = player.getUUID();

        if (activeSessions.containsKey(playerId)) {
            if (cancelExistingSession) {
                cancelSession(player);
            } else {
                return -1;
            }
        }

        int sessionId = sessionIdGenerator.incrementAndGet();

        ItemStack rod = findFishtasticRod(player);
        ItemStack bait = CopperFishingRod.getBait(rod);
        BaitEffect baitEffect = bait.isEmpty() ? BaitEffect.NO_BAIT : BaitEffect.fromStack(bait);
        ItemStack hookStack = CopperFishingRod.getHook(rod);
        HookEffect hookEffect = hookStack.isEmpty() ? null : hookStack.get(FishtasticDataComponents.HOOK_EFFECT.value());
        ItemStack charmStack = CopperFishingRod.getCharm(rod);

        // A Storm Charm sitting in the charm slot fires on the cast and is consumed. It carries no
        // CharmEffect, so it would otherwise be inert in a slot players reach for instinctively.
        // Done here — ahead of generateTargets and the environment capture below — so the weather
        // is already thundering when this cast resolves its fish pool and quest conditions.
        if (!charmStack.isEmpty() && charmStack.getItem() instanceof StormCharmItem
                && StormCharmItem.trySummonStorm(level, player)) {
            charmStack.shrink(1);
            CopperFishingRod.setCharm(rod, charmStack.isEmpty() ? ItemStack.EMPTY : charmStack);
            charmStack = CopperFishingRod.getCharm(rod);
        }

        CharmEffect charmEffect = charmStack.isEmpty() ? null : charmStack.get(FishtasticDataComponents.CHARM_EFFECT.value());

        List<ServerFishingTarget> targets = generateTargets(player, difficultyModifier, baitEffect, hookEffect, charmEffect);

        boolean revealTopWeightedFish = (charmEffect != null && charmEffect.showTopWeightedFish())
                || hasCharmEffectInInventory(player, CharmEffect::showTopWeightedFish);
        List<ItemStack> topWeightedFishPreview = revealTopWeightedFish
                ? computeTopWeightedFish(player, baitEffect, charmEffect)
                : List.of();

        // Capture environment context at hook position for quest tracking
        FishingHook sessionHook = player.fishing;
        BlockPos sessionPos = sessionHook != null
                ? BlockPos.containing(sessionHook.position())
                : player.blockPosition();
        Holder<Biome> sessionBiome = level.getBiome(sessionPos);
        FishProfile.TimeOfDay sessionTimeOfDay = charmEffect != null && charmEffect.forceNightFishing()
                ? FishProfile.TimeOfDay.NIGHT
                : FishProfile.TimeOfDay.fromGameTime(level.getOverworldClockTime());
        FishProfile.WeatherCondition sessionWeather = FishProfile.WeatherCondition.fromLevel(level, sessionPos);
        Set<FishProfile.Zone> sessionZones = FishProfile.Zone.resolve(sessionBiome, sessionPos.getY(), level.getSeaLevel());

        ActiveSession session = new ActiveSession(sessionId, playerId, targets, level.getGameTime(),
                sessionBiome, sessionTimeOfDay, sessionWeather, sessionZones);
        activeSessions.put(playerId, session);

        List<StartFishingMinigamePacket.TargetData> targetData = new ArrayList<>();
        for (ServerFishingTarget target : targets) {
            targetData.add(new StartFishingMinigamePacket.TargetData(
                    target.rewardStacks(),
                    target.category(),
                    target.initialPosition(),
                    target.difficulty(),
                    target.phases()
            ));
        }

        Set<Identifier> undiscovered = computeUndiscoveredSpecies(player, targets, topWeightedFishPreview);

        sendToPlayer(player, new StartFishingMinigamePacket(
                sessionId, targetData, false, topWeightedFishPreview, sessionZones, undiscovered));
        TutorialManager.onMinigameStarted(player);

        Fishtastic.LOGGER.info("Started fishing minigame session {} for player {} with {} targets",
                sessionId, player.getName().getString(), targets.size());

        return sessionId;
    }

    /** Starts a simplified tutorial session: one slow-moving Bluegill, no difficulty. */
    public int startTutorialSession(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (activeSessions.containsKey(playerId)) {
            cancelSession(player);
        }

        int sessionId = sessionIdGenerator.incrementAndGet();

        StartFishingMinigamePacket.TargetData tutorialTarget = TutorialManager.buildTutorialTarget(player);
        List<ServerFishingTarget> targets = List.of(new ServerFishingTarget(
                tutorialTarget.rewardStacks(),
                tutorialTarget.category(),
                tutorialTarget.difficulty(),
                tutorialTarget.initialPosition(),
                tutorialTarget.phases()
        ));

        FishingHook hook = player.fishing;
        BlockPos tutorialPos = hook != null
                ? BlockPos.containing(hook.position())
                : player.blockPosition();
        Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(tutorialPos);
        FishProfile.TimeOfDay timeOfDay = FishProfile.TimeOfDay.fromGameTime(level.getOverworldClockTime());
        FishProfile.WeatherCondition weather = FishProfile.WeatherCondition.fromLevel(level, tutorialPos);
        Set<FishProfile.Zone> tutorialZones = FishProfile.Zone.resolve(biome, tutorialPos.getY(), level.getSeaLevel());

        ActiveSession session = new ActiveSession(sessionId, playerId, targets,
                level.getGameTime(), biome, timeOfDay, weather, tutorialZones);
        activeSessions.put(playerId, session);

        // The tutorial hands out a scripted fish; it must never be dressed up as a discovery, so
        // the undiscovered set is deliberately empty regardless of the player's catch history.
        sendToPlayer(player, new StartFishingMinigamePacket(
                sessionId, List.of(tutorialTarget), true, List.of(), tutorialZones, Set.of()));
        TutorialManager.onMinigameStarted(player);

        Fishtastic.LOGGER.info("Started TUTORIAL minigame session {} for player {}", sessionId, player.getName().getString());
        return sessionId;
    }

    public void handleMinigameComplete(ServerPlayer player, int sessionId, List<Integer> caughtTargetIndices) {
        UUID playerId = player.getUUID();
        ActiveSession session = activeSessions.get(playerId);

        if (session == null || session.sessionId != sessionId) {
            Fishtastic.LOGGER.warn("Received results for invalid session {} from player {}",
                    sessionId, player.getName().getString());
            return;
        }

        long timeTaken = level.getGameTime() - session.startTime;

        if (timeTaken < 20) {
            Fishtastic.LOGGER.warn("Player {} completed fishing too quickly ({} ticks)",
                    player.getName().getString(), timeTaken);
        }

        List<ItemStack> rewards = new ArrayList<>();
        List<ItemStack> questStacks = new ArrayList<>();
        List<ItemStack> firstCatchItems = new ArrayList<>();
        int trashCaught = 0;
        int xpAwarded = 0;
        FishCatchSavedData catchDb = FishCatchSavedData.getOrCreate(level.getServer());
        Registry<FishProfile> xpFishProfiles = level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);

        ItemStack deliveryCharmStack = CopperFishingRod.getCharm(findFishtasticRod(player));
        CharmEffect deliveryCharmEffect = deliveryCharmStack.isEmpty() ? null : deliveryCharmStack.get(FishtasticDataComponents.CHARM_EFFECT.value());
        boolean autoPileFish = (deliveryCharmEffect != null && deliveryCharmEffect.autoPileFish())
                || hasCharmEffectInInventory(player, CharmEffect::autoPileFish);
        // De-dupe indices — a client re-reporting the same target index must not award its reward twice.
        for (Integer index : new LinkedHashSet<>(caughtTargetIndices)) {
            if (index >= 0 && index < session.targets.size()) {
                ServerFishingTarget target = session.targets.get(index);
                for (ItemStack rewardStack : target.rewardStacks()) {
                    ItemStack reward = rewardStack.copy();
                    if (!reward.isEmpty()) {
                        if (catchDb.recordCatch(catchDb.resolvePlayerKey(player), player.getName().getString(), reward)) {
                            firstCatchItems.add(reward.copy());
                        }
                        questStacks.add(reward.copy()); // copy — inventory.add() mutates the stack in-place
                        // Snapshot count/tag before inventory.add() mutates reward down to its leftover
                        // (usually 0) — reading these after the call under-counts trash almost every time.
                        boolean isTrash = reward.is(FishtasticItemTags.TRASH);
                        int caughtCount = reward.getCount();
                        // Scored before inventory.add() mutates the stack's count down to its leftover.
                        xpAwarded += FishingXpAward.forRewardStack(reward, xpFishProfiles);
                        if (autoPileFish && PileOfFishItem.canInsertInPile(reward)) {
                            addToFishPiles(player, reward);
                        } else {
                            player.getInventory().add(reward);
                        }
                        if (!reward.isEmpty()) {
                            // inventory.add() / addToFishPiles() leaves any leftover count in reward when full/partially full
                            player.drop(reward, false);
                        }
                        rewards.add(reward);
                        if (isTrash) {
                            trashCaught += caughtCount;
                        }
                    }
                }
            } else {
                Fishtastic.LOGGER.warn("Player {} reported invalid target index {}",
                        player.getName().getString(), index);
            }
        }

        // Fishtastic rods bypass vanilla's retrieve loot branch entirely (FishingHookMixin), so
        // this is the only xp a minigame catch ever grants. Orbs spawn at the player rather than
        // the bobber — the hook is already gone by the time the client reports results.
        if (xpAwarded > 0) {
            ExperienceOrb.award(level, player.position(), xpAwarded);
        }

        // Record trash contributions first so the quest sync packet below (which snapshots
        // the cleanup goal total) reflects this session's catches instead of a stale total.
        if (trashCaught > 0) {
            CleanupGoalTracker.onTrashCatch(level.getServer(), player, trashCaught);
        }

        // Batch quest tracking — only one sync packet for all catches in this session
        if (!questStacks.isEmpty()) {
            QuestTracker.onCatchBatch(level.getServer(), player, questStacks,
                    session.hookBiome, session.hookTimeOfDay, session.hookWeather, session.hookZones);
        }

        TutorialManager.onMinigameComplete(player);

        ItemStack baitDepletedItem = ItemStack.EMPTY;
        if (!rewards.isEmpty()) {
            float baitSaveChance = Math.max(
                    deliveryCharmEffect != null ? deliveryCharmEffect.baitSaveChance() : 0.0f,
                    inventoryBaitSaveChance(player));
            if (baitSaveChance <= 0.0f || player.getRandom().nextFloat() >= baitSaveChance) {
                baitDepletedItem = consumeBait(player);
            }
            damageUpgrades(player);
        }

        // Extra lightweight sync just for the one-shot banners above — QuestTracker's own
        // sync (if it fired) predates bait consumption/first-catch detection, so it can't
        // carry these fields.
        if (!baitDepletedItem.isEmpty() || !firstCatchItems.isEmpty()) {
            QuestSyncPacket.sendToPlayer(player, catchDb, Map.of(), 0, baitDepletedItem, firstCatchItems);
        }

        activeSessions.remove(playerId);

        Fishtastic.LOGGER.info("Awarded {} items to player {} for session {} (took {} ticks)",
                rewards.size(), player.getName().getString(), sessionId, timeTaken);
    }

    public void tick() {
        long currentTime = level.getGameTime();
        activeSessions.entrySet().removeIf(entry -> {
            ActiveSession session = entry.getValue();
            if (currentTime - session.startTime > SESSION_TIMEOUT_TICKS) {
                Fishtastic.LOGGER.info("Session {} timed out", session.sessionId);
                return true;
            }
            return false;
        });
    }

    public void cancelSession(net.minecraft.world.entity.player.Player player) {
        ActiveSession session = activeSessions.remove(player.getUUID());
        if (session != null) {
            Fishtastic.LOGGER.info("Cancelled session {} for player {}",
                    session.sessionId, player.getName().getString());
        }
    }

    private List<ServerFishingTarget> generateTargets(ServerPlayer player, float difficultyModifier, @Nullable BaitEffect baitEffect, @Nullable HookEffect hookEffect, @Nullable CharmEffect charmEffect) {
        List<ServerFishingTarget> targets = new ArrayList<>();
        RandomSource randomSource = player.getRandom();

        FishingHook hook = player.fishing;
        IFishingHookExtension hookExt = (IFishingHookExtension) hook;
        if (hook == null) return targets;

        float luckBonus = baitEffect != null ? baitEffect.luckBonus() : 0.0f;
        LootParams lootparams = new LootParams.Builder(player.level())
                .withParameter(LootContextParams.ORIGIN, hook.position())
                .withParameter(LootContextParams.TOOL, player.getUseItem())
                .withParameter(LootContextParams.THIS_ENTITY, hook)
                .withLuck(hookExt.getLuck() + player.getLuck() + luckBonus)
                .create(LootContextParamSets.FISHING);

        // Resolve environment context at hook position
        BlockPos hookPos = BlockPos.containing(hook.position());
        EnvironmentContext env = resolveEnvironment(hookPos, charmEffect);
        Holder<Biome> biome = env.biome();
        FishProfile.TimeOfDay timeOfDay = env.timeOfDay();
        FishProfile.WeatherCondition weather = env.weather();
        net.minecraft.world.level.MoonPhase moonPhase = env.moonPhase();

        Registry<FishProfile> fishProfileRegistry = level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        float qualityBias = (baitEffect != null ? baitEffect.qualityBias() : 0.0f)
                + (hookEffect != null ? hookEffect.qualityBias() : 0.0f);

        boolean vanillaAllowed = baitEffect == null || baitEffect.equals(BaitEffect.NO_BAIT);
        List<Holder<Item>> fishPool = getFishPool(player, baitEffect).stream()
                .filter(h -> vanillaAllowed || h.value() instanceof FishtasticFishItem)
                .filter(h -> FishtasticFishItem.isZoneEligible(h, fishProfileRegistry, env.zone()))
                .toList();
        List<Holder<Item>> trashPool = getTrashPool(player);

        float treasureChance = Math.max(0f, (baitEffect != null ? baitEffect.treasureChance() : DEFAULT_TREASURE_CHANCE)
                + (hookEffect != null ? hookEffect.treasureChanceDelta() : 0.0f)
                + (charmEffect != null ? charmEffect.treasureChanceDelta() : 0.0f));
        float trashChance = Math.max(0f, (baitEffect != null ? baitEffect.trashChance() : DEFAULT_TRASH_CHANCE)
                + (hookEffect != null ? hookEffect.trashChanceDelta() : 0.0f)
                + (charmEffect != null ? charmEffect.trashChanceDelta() : 0.0f));
        int targetCountMean = DEFAULT_TARGET_COUNT_MEAN + (baitEffect != null ? baitEffect.targetCountBonus() : 0);
        int targetCount = (int) Math.clamp(MathUtil.randomGaussian(randomSource, targetCountMean, 1), 1, MAX_TARGETS);

        Registry<Temperament> temperamentRegistry = level.registryAccess().lookupOrThrow(FishtasticRegistries.TEMPERAMENT_REGISTRY_KEY);
        float[] baseDifficulties = {0.3f, 0.4f, 0.5f, 0.6f, 0.8f, 0.9f, 0.7f};

        Temperament forcedTemperament = getForcedTemperament(player.getUUID())
                .flatMap(key -> temperamentRegistry.getOptional(key))
                .orElse(null);
        if (forcedTemperament != null) {
            Fishtastic.LOGGER.info("Applying forced temperament override for player {}", player.getName().getString());
        }
        Float forcedDifficulty = getForcedDifficulty(player.getUUID()).orElse(null);
        if (forcedDifficulty != null) {
            Fishtastic.LOGGER.info("Applying forced difficulty override ({}) for player {}", forcedDifficulty, player.getName().getString());
        }
        FishQuality.Quality forcedQuality = getForcedQuality(player.getUUID()).orElse(null);
        if (forcedQuality != null) {
            Fishtastic.LOGGER.info("Applying forced quality override ({}) for player {}", forcedQuality, player.getName().getString());
        }

        for (int i = 0; i < targetCount; i++) {
            float roll = randomSource.nextFloat();
            boolean isTrash = roll < trashChance;
            boolean isTreasure = !isTrash && roll < trashChance + treasureChance;
            boolean isFishReward = !isTrash && !isTreasure;
            int numRewards = isFishReward ? 1 : MathUtil.clamp((int) MathUtil.randomGaussian(randomSource, 1, 1), 1, 3);

            List<ItemStack> rewardStacks;
            if (isFishReward) {
                rewardStacks = generateFishRewards(randomSource, lootparams, fishPool,
                        fishProfileRegistry, biome, timeOfDay, weather, moonPhase, qualityBias, baitEffect, charmEffect, numRewards);
                if (forcedQuality != null) {
                    // Restamped after the roll rather than forced during it, so species selection
                    // stays completely natural and only the quality is pinned.
                    for (ItemStack rewardStack : rewardStacks) {
                        FishtasticFishItem.applyQualityAndSize(rewardStack, forcedQuality, randomSource, fishProfileRegistry);
                    }
                }
            } else if (isTreasure) {
                rewardStacks = generateTreasureRewards(randomSource, lootparams, qualityBias, numRewards, forcedQuality);
            } else {
                rewardStacks = generateTrashRewards(randomSource, trashPool, numRewards);
            }

            if (rewardStacks.isEmpty()) continue;

            ItemStack reward = rewardStacks.getFirst();
            // Driven by which roll produced this target, not the reward's own tags — Common-tier
            // treasure can itself roll bulk trash items (#fishtastic:trash), which would otherwise
            // misclassify as a TRASH target (generic-fish icon) instead of TREASURE (chest icon).
            FishingTarget.TargetCategory category = isTreasure
                    ? FishingTarget.TargetCategory.TREASURE
                    : isTrash
                            ? FishingTarget.TargetCategory.TRASH
                            : FishingTarget.TargetCategory.FISH;

            float difficulty;
            List<PhaseRule> phases = null;

            Temperament temperament = isFishReward
                    ? (forcedTemperament != null ? forcedTemperament : resolveTemperament(reward, fishProfileRegistry, temperamentRegistry))
                    : null;

            if (temperament != null) {
                phases = temperament.resolvedPhases();
            }

            if (forcedDifficulty != null) {
                difficulty = forcedDifficulty;
            } else if (temperament != null) {
                difficulty = temperament.sampleDifficulty(randomSource) * difficultyModifier;
            } else {
                difficulty = baseDifficulties[randomSource.nextInt(baseDifficulties.length)] * difficultyModifier;
            }

            if (isFishReward && forcedDifficulty == null) {
                difficulty = applyQualityDifficultyBoost(difficulty, reward);
            }

            if (temperament == null) {
                FishingTarget.MovementPattern pattern = FishingTarget.pickRandom(difficulty, randomSource.nextFloat());
                phases = List.of(new PhaseRule(0f, List.of(pattern), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            }

            float initialPosition = randomSource.nextFloat();
            targets.add(new ServerFishingTarget(rewardStacks, category, difficulty, initialPosition, phases));
        }

        return targets;
    }

    /**
     * Resolved environment context for a fishing cast: everything downstream weight/zone
     * computation needs, gathered once at the hook position. {@code zone} is the hard
     * location gate ({@link FishProfile.Zone#resolve}); the rest feed the soft flavor
     * multipliers in {@link FishProfile#computeEnvironmentMultiplier}.
     */
    private record EnvironmentContext(Holder<Biome> biome, FishProfile.TimeOfDay timeOfDay,
            FishProfile.WeatherCondition weather, net.minecraft.world.level.MoonPhase moonPhase,
            Set<FishProfile.Zone> zone) {}

    private EnvironmentContext resolveEnvironment(BlockPos hookPos, @Nullable CharmEffect charmEffect) {
        Holder<Biome> biome = level.getBiome(hookPos);
        FishProfile.TimeOfDay timeOfDay = charmEffect != null && charmEffect.forceNightFishing()
                ? FishProfile.TimeOfDay.NIGHT
                : FishProfile.TimeOfDay.fromGameTime(level.getOverworldClockTime());
        FishProfile.WeatherCondition weather = FishProfile.WeatherCondition.fromLevel(level, hookPos);
        net.minecraft.world.level.MoonPhase moonPhase = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.MOON_PHASE, hookPos);
        Set<FishProfile.Zone> zone = FishProfile.Zone.resolve(biome, hookPos.getY(), level.getSeaLevel());
        return new EnvironmentContext(biome, timeOfDay, weather, moonPhase, zone);
    }

    // Number of top-weighted species the Angler's Almanac reveals, ranked highest-weight first.
    private static final int TOP_WEIGHTED_FISH_PREVIEW_COUNT = 3;

    /**
     * Scans the fish pool under the current hook's environment context and returns the
     * {@link #TOP_WEIGHTED_FISH_PREVIEW_COUNT} highest-weighted species, ranked descending —
     * i.e. what {@link FishtasticFishItem#sampleRandomFish} is most likely to roll right now.
     * Mirrors the environment/pool resolution in {@link #generateTargets} but ranks by weight
     * instead of drawing a single weighted-random pick. Only called when the equipped charm
     * (Angler's Almanac) reveals this.
     */
    private List<ItemStack> computeTopWeightedFish(ServerPlayer player, @Nullable BaitEffect baitEffect, @Nullable CharmEffect charmEffect) {
        FishingHook hook = player.fishing;
        if (hook == null) return List.of();
        IFishingHookExtension hookExt = (IFishingHookExtension) hook;

        float luckBonus = baitEffect != null ? baitEffect.luckBonus() : 0.0f;
        LootParams lootparams = new LootParams.Builder(player.level())
                .withParameter(LootContextParams.ORIGIN, hook.position())
                .withParameter(LootContextParams.TOOL, player.getUseItem())
                .withParameter(LootContextParams.THIS_ENTITY, hook)
                .withLuck(hookExt.getLuck() + player.getLuck() + luckBonus)
                .create(LootContextParamSets.FISHING);

        BlockPos hookPos = BlockPos.containing(hook.position());
        EnvironmentContext env = resolveEnvironment(hookPos, charmEffect);

        Registry<FishProfile> fishProfileRegistry = level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        boolean vanillaAllowed = baitEffect == null || baitEffect.equals(BaitEffect.NO_BAIT);
        List<Holder<Item>> fishPool = getFishPool(player, baitEffect).stream()
                .filter(h -> vanillaAllowed || h.value() instanceof FishtasticFishItem)
                .filter(h -> FishtasticFishItem.isZoneEligible(h, fishProfileRegistry, env.zone()))
                .toList();

        record WeightedFish(Holder<Item> holder, int weight) {}
        List<WeightedFish> ranked = new ArrayList<>();
        for (Holder<Item> candidate : fishPool) {
            int weight = FishtasticFishItem.getFishingLootWeight(
                    candidate, lootparams, fishProfileRegistry, env.biome(), env.timeOfDay(), env.weather(), env.moonPhase(), baitEffect, charmEffect);
            if (weight > 0) {
                ranked.add(new WeightedFish(candidate, weight));
            }
        }
        ranked.sort(Comparator.comparingInt(WeightedFish::weight).reversed());

        List<ItemStack> top = new ArrayList<>(TOP_WEIGHTED_FISH_PREVIEW_COUNT);
        for (int i = 0; i < ranked.size() && i < TOP_WEIGHTED_FISH_PREVIEW_COUNT; i++) {
            top.add(new ItemStack(ranked.get(i).holder()));
        }
        return top;
    }

    /**
     * Quality is rolled independently of species/temperament, so without this a Legendary
     * catch of an easy species (e.g. cod) would play through the same trivial minigame as
     * a Common one. Push difficulty toward the max proportionally to quality, scaled down
     * as the species' own difficulty approaches 1 so it never exceeds the [0,1] range.
     */
    private static float applyQualityDifficultyBoost(float difficulty, ItemStack reward) {
        FishQuality.Quality quality = FishQualityHelper.getQuality(reward);
        if (quality == null) return difficulty;
        float intensity = new FishQuality(quality).getEffectIntensity();
        return difficulty + intensity * (1f - difficulty) * QUALITY_DIFFICULTY_BOOST_STRENGTH;
    }

    @Nullable
    private Temperament resolveTemperament(ItemStack stack, Registry<FishProfile> fishProfileRegistry, Registry<Temperament> temperamentRegistry) {
        var itemKey = BuiltInRegistries.ITEM.getResourceKey(stack.getItem());
        if (itemKey.isEmpty()) return null;
        var profileKey = net.minecraft.resources.ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, itemKey.get().identifier());
        FishProfile profile = fishProfileRegistry.getOptional(profileKey).orElse(null);
        if (profile == null || profile.temperament().isEmpty()) return null;
        return temperamentRegistry.getOptional(profile.temperament().get()).orElse(null);
    }

    /**
     * Rolls a {@link FishQuality.Quality} tier for this treasure target (same call/bias as fish
     * quality — see {@link FishtasticFishItem#sampleRandomQuality}) and generates {@code numRewards}
     * reward stacks from that tier, each stamped with the rolled quality so Crystal Ball Charm's
     * rarity outline works on treasure the same way it does on fish.
     *
     * <p>The tier is rolled once for the whole target rather than once per reward — a treasure hit
     * is one themed pull that may surface 1-3 items of that same rarity, not a grab-bag of mixed
     * tiers. Legendary short-circuits entirely: it isn't a loot table, it's a dedicated fish-tank
     * builder (see {@link #generateLegendaryTreasureTank}) that always returns exactly one stack
     * regardless of {@code numRewards}, since "8 tanks" is the whole jackpot, not a per-roll unit.
     *
     * @param forcedQuality when non-null (the {@code /fishtastic forcequality} debug override),
     *                      skips the roll entirely and uses this tier instead — mirrors how the
     *                      fish path restamps quality after a forced override, but here it's simpler
     *                      to just skip the roll since treasure quality has no species to preserve.
     */
    private List<ItemStack> generateTreasureRewards(RandomSource randomSource, LootParams lootParams, float qualityBias, int numRewards, @Nullable FishQuality.Quality forcedQuality) {
        FishQuality.Quality quality = forcedQuality != null ? forcedQuality : FishtasticFishItem.sampleRandomQuality(randomSource, qualityBias);

        if (quality == FishQuality.Quality.LEGENDARY) {
            return List.of(generateLegendaryTreasureTank(randomSource));
        }

        String tableName = TREASURE_LOOT_TABLE_NAMES.get(quality);
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(
                net.minecraft.resources.ResourceKey.create(Registries.LOOT_TABLE, Fishtastic.id(tableName)));

        List<ItemStack> rewardStacks = new ArrayList<>();
        for (int n = 0; n < numRewards; n++) {
            for (ItemStack stack : lootTable.getRandomItems(lootParams)) {
                ItemStack reward = stack.copy();
                reward.set(FishtasticDataComponents.FISH_QUALITY.value(), new FishQuality(quality));
                rewardStacks.add(reward);
            }
        }
        return rewardStacks;
    }

    /**
     * Legendary treasure: a {@code fishtastic:fish_tank} whose frame/sand/glass and shape are each
     * rolled independently from the preset lists above, rather than a fixed preset combo like the
     * shop/quest tanks — every legendary pull is a fresh combination. Shape rolls uniformly across
     * every {@link FishTankShape}, including ones normally locked behind quest unlocks — same
     * precedent as Epic/Legendary-tier charms bypassing their shop/quest gates as a rare RNG bonus.
     */
    private static ItemStack generateLegendaryTreasureTank(RandomSource randomSource) {
        Block frame = BuiltInRegistries.BLOCK.getValue(LEGENDARY_TANK_FRAMES.get(randomSource.nextInt(LEGENDARY_TANK_FRAMES.size())));
        Block sand = BuiltInRegistries.BLOCK.getValue(LEGENDARY_TANK_SANDS.get(randomSource.nextInt(LEGENDARY_TANK_SANDS.size())));
        Block glass = BuiltInRegistries.BLOCK.getValue(LEGENDARY_TANK_GLASS.get(randomSource.nextInt(LEGENDARY_TANK_GLASS.size())));
        FishTankShape[] shapes = FishTankShape.values();
        FishTankShape shape = shapes[randomSource.nextInt(shapes.length)];

        ItemStack tank = new ItemStack(FishtasticBlocks.FISH_TANK.value(), LEGENDARY_TANK_COUNT);
        tank.set(FishtasticDataComponents.FISH_TANK_MATERIALS.value(), new FishTankMaterials(frame, sand, glass));
        tank.set(FishtasticDataComponents.FISH_TANK_SHAPE.value(), shape);
        tank.set(FishtasticDataComponents.FISH_QUALITY.value(), new FishQuality(FishQuality.Quality.LEGENDARY));
        return tank;
    }

    private static @NotNull List<Holder<Item>> getTrashPool(ServerPlayer player) {
        List<Holder<Item>> result = new ArrayList<>();
        for (Holder<Item> holder : player.registryAccess().lookupOrThrow(Registries.ITEM).getTagOrEmpty(FishtasticItemTags.TRASH)) {
            result.add(holder);
        }
        return result;
    }

    private static List<ItemStack> generateTrashRewards(RandomSource randomSource, List<Holder<Item>> trashPool, int numRewards) {
        List<ItemStack> rewardStacks = new ArrayList<>();
        if (trashPool.isEmpty()) return rewardStacks;
        for (int n = 0; n < numRewards; n++) {
            Holder<Item> trash = trashPool.get(randomSource.nextInt(trashPool.size()));
            rewardStacks.add(new ItemStack(trash));
        }
        return rewardStacks;
    }

    /**
     * Item ids in this session that the player has never caught, across both the hidden rewards and
     * the almanac preview.
     *
     * <p>Answered here rather than on the client because the client keeps no standing record:
     * {@code FishEncyclopediaClientCache} is filled only while the encyclopedia screen is open and
     * is empty the rest of the time, which would make every catch look like a first discovery.
     * Evaluated at session start, before any of these catches are recorded, so the reading is the
     * state the player had when they cast.
     */
    private static Set<Identifier> computeUndiscoveredSpecies(
            ServerPlayer player, List<ServerFishingTarget> targets, List<ItemStack> previews) {
        FishCatchSavedData catchDb = FishCatchSavedData.getOrCreate(player.level().getServer());
        UUID playerKey = catchDb.resolvePlayerKey(player);

        Set<Identifier> undiscovered = new HashSet<>();
        for (ServerFishingTarget target : targets) {
            if (target.category() != FishingTarget.TargetCategory.FISH) continue;
            for (ItemStack stack : target.rewardStacks()) {
                addIfUndiscovered(catchDb, playerKey, stack, undiscovered);
            }
        }
        for (ItemStack stack : previews) {
            addIfUndiscovered(catchDb, playerKey, stack, undiscovered);
        }
        return undiscovered;
    }

    private static void addIfUndiscovered(FishCatchSavedData catchDb, UUID playerKey,
                                          ItemStack stack, Set<Identifier> out) {
        if (stack.isEmpty()) return;
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (catchDb.getCatchCount(playerKey, id) == 0) out.add(id);
    }

    private static @NotNull List<Holder<Item>> getFishPool(ServerPlayer player, @Nullable BaitEffect baitEffect) {
        Optional<net.minecraft.tags.TagKey<Item>> exclusivePool = baitEffect != null
                ? baitEffect.exclusiveFishPool()
                : Optional.empty();

        List<Holder<Item>> result = new ArrayList<>();
        for (Holder<Item> holder : player.registryAccess().lookupOrThrow(Registries.ITEM).getTagOrEmpty(ItemTags.FISHES)) {
            if (exclusivePool.isEmpty() || holder.is(exclusivePool.get())) {
                result.add(holder);
            }
        }
        return result;
    }

    private static List<ItemStack> generateFishRewards(
            RandomSource randomSource,
            LootParams lootParams,
            List<Holder<Item>> fishPool,
            Registry<FishProfile> fishProfileRegistry,
            Holder<Biome> biome,
            FishProfile.TimeOfDay timeOfDay,
            FishProfile.WeatherCondition weather,
            net.minecraft.world.level.MoonPhase moonPhase,
            float qualityBias,
            @Nullable BaitEffect baitEffect,
            @Nullable CharmEffect charmEffect,
            int numRewards
    ) {
        List<ItemStack> rewardStacks = new ArrayList<>();
        for (int n = 0; n < numRewards; n++) {
            ItemStack reward = FishtasticFishItem.sampleRandomFish(
                    randomSource, lootParams, fishPool,
                    fishProfileRegistry, biome, timeOfDay, weather, moonPhase, qualityBias, baitEffect, charmEffect);
            if (!reward.isEmpty()) rewardStacks.add(reward);
        }
        return rewardStacks;
    }

    private static ItemStack findFishtasticRod(ServerPlayer player) {
        if (player.getMainHandItem().is(FishtasticItemTags.FISHING_RODS)) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(FishtasticItemTags.FISHING_RODS)) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    /**
     * The Little Fish Box's auto-pile effect and the Angler's Almanac's top-weighted-fish reveal
     * are passive: unlike every other charm effect, they work from anywhere in the player's
     * inventory, not just the rod's charm slot — so either can be carried alongside another charm
     * that's actually equipped.
     */
    private static boolean hasCharmEffectInInventory(ServerPlayer player, Predicate<CharmEffect> predicate) {
        return findCharmSlotInInventory(player, predicate) >= 0;
    }

    /** @return the first inventory slot holding a charm matching {@code predicate}, or -1. */
    private static int findCharmSlotInInventory(ServerPlayer player, Predicate<CharmEffect> predicate) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            CharmEffect effect = inventory.getItem(i).get(FishtasticDataComponents.CHARM_EFFECT.value());
            if (effect != null && predicate.test(effect)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Delivery effect for the Little Fish Box charm: fills existing Pile of Fish stacks in the
     * player's inventory first, then creates new piles for any remainder. Mirrors the manual
     * click-driven insertion in {@link PileOfFishItem} but goes straight through
     * {@link BundleContents.Mutable} since there's no slot/click to route through here.
     * <p>
     * When a new pile has to be created, any other loose fish/sized items already sitting in
     * the inventory are swept into it too. This isn't just a convenience — {@link PileOfFishItem}
     * auto-unpacks any pile that drops to exactly 1 item back into a loose stack on the next
     * inventory tick (its single-item safety net). Since most catches are a single fish, a
     * freshly-created pile with just that one fish would otherwise get unpacked again before
     * the next cast, so single catches would never actually accumulate. Combining with a loose
     * item up front (or, failing that, letting the *next* catch's sweep find this one after it
     * unpacks) keeps the pile at 2+ items so it survives.
     */
    private static void addToFishPiles(ServerPlayer player, ItemStack reward) {
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize() && !reward.isEmpty(); i++) {
            ItemStack slotStack = inventory.getItem(i);
            if (slotStack.is(FishtasticItems.PILE_OF_FISH.value())) {
                BundleContents.Mutable contents = new BundleContents.Mutable(
                        slotStack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY));
                contents.tryInsert(reward);
                slotStack.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
            }
        }
        while (!reward.isEmpty()) {
            BundleContents.Mutable contents = new BundleContents.Mutable(BundleContents.EMPTY);
            int beforeCount = reward.getCount();
            contents.tryInsert(reward);
            if (reward.getCount() == beforeCount) {
                break; // a fresh, empty pile couldn't accept anything — avoid spinning forever
            }

            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack slotStack = inventory.getItem(i);
                if (!slotStack.isEmpty() && !slotStack.is(FishtasticItems.PILE_OF_FISH.value())
                        && PileOfFishItem.canInsertInPile(slotStack)) {
                    contents.tryInsert(slotStack);
                }
            }

            ItemStack newPile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
            newPile.set(DataComponents.BUNDLE_CONTENTS, contents.toImmutable());
            inventory.add(newPile);
            if (!newPile.isEmpty()) {
                player.drop(newPile, false);
            }
        }
    }

    /** @return a single copy of the bait item if this consumption emptied the stack, else EMPTY. */
    private static ItemStack consumeBait(ServerPlayer player) {
        ItemStack rod = findFishtasticRod(player);
        if (rod.isEmpty()) return ItemStack.EMPTY;
        ItemStack bait = CopperFishingRod.getBait(rod);
        if (bait.isEmpty()) return ItemStack.EMPTY;
        ItemStack depletedIcon = bait.getCount() == 1 ? bait.copyWithCount(1) : ItemStack.EMPTY;
        bait.shrink(1);
        CopperFishingRod.setBait(rod, bait);
        return depletedIcon;
    }

    private static void damageUpgrades(ServerPlayer player) {
        ItemStack rod = findFishtasticRod(player);
        if (!rod.isEmpty()) {
            ItemStack hook = CopperFishingRod.getHook(rod);
            if (!hook.isEmpty()) {
                hook.setDamageValue(hook.getDamageValue() + 1);
                boolean broken = hook.getDamageValue() >= hook.getMaxDamage();
                if (broken) playBreakEffect(player, hook);
                CopperFishingRod.setHook(rod, broken ? ItemStack.EMPTY : hook);
            }

            ItemStack charm = CopperFishingRod.getCharm(rod);
            if (!charm.isEmpty()) {
                charm.setDamageValue(charm.getDamageValue() + 1);
                boolean broken = charm.getDamageValue() >= charm.getMaxDamage();
                if (broken) playBreakEffect(player, charm);
                CopperFishingRod.setCharm(rod, broken ? ItemStack.EMPTY : charm);
            }
        }

        // Little Fish Box and Angler's Almanac working from the inventory (see
        // hasCharmEffectInInventory) still cost durability, same as every other charm effect —
        // they're real ItemStacks in real slots here, so vanilla hurtAndBreak handles the
        // damage/break sound/shrink-on-break itself.
        damagePassiveCharmInInventory(player, CharmEffect::autoPileFish);
        damagePassiveCharmInInventory(player, CharmEffect::showTopWeightedFish);
        damagePassiveCharmInInventory(player, effect -> effect.baitSaveChance() > 0.0f);
    }

    /**
     * Bait Buddy's save-chance is passive, like {@link CharmEffect#autoPileFish()}: it works
     * from anywhere in the inventory, not just the rod's charm slot.
     */
    private static float inventoryBaitSaveChance(ServerPlayer player) {
        int slot = findCharmSlotInInventory(player, effect -> effect.baitSaveChance() > 0.0f);
        if (slot < 0) return 0.0f;
        CharmEffect effect = player.getInventory().getItem(slot).get(FishtasticDataComponents.CHARM_EFFECT.value());
        return effect == null ? 0.0f : effect.baitSaveChance();
    }

    private static void damagePassiveCharmInInventory(ServerPlayer player, Predicate<CharmEffect> predicate) {
        int slot = findCharmSlotInInventory(player, predicate);
        if (slot >= 0) {
            player.getInventory().getItem(slot).hurtAndBreak(1, (ServerLevel) player.level(), player, item -> {});
        }
    }

    /**
     * Plays the vanilla tool-break sound and item particles at the player's position,
     * mirroring {@code LivingEntity#breakItem} — used when a hook or charm's durability
     * (tracked as an ItemStack sub-component on the rod, not equipment) is exhausted.
     */
    private static void playBreakEffect(ServerPlayer player, ItemStack stack) {
        ServerLevel serverLevel = (ServerLevel) player.level();
        Holder<SoundEvent> breakSound = stack.get(DataComponents.BREAK_SOUND);
        if (breakSound != null) {
            serverLevel.playSound(null, player.blockPosition(), breakSound.value(), SoundSource.PLAYERS,
                    0.8F, 0.8F + serverLevel.getRandom().nextFloat() * 0.4F);
        }
        serverLevel.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, ItemStackTemplate.fromNonEmptyStack(stack)),
                player.getX(), player.getEyeY() - 0.3, player.getZ(), 5, 0.15, 0.1, 0.15, 0.05);
    }

    private void sendToPlayer(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(payload));
    }

    public boolean isPlayerInActiveSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    /**
     * Test-only seam: seeds a deterministic active session for {@code player}, bypassing the
     * RNG-driven, registry-dependent {@link #generateTargets} so {@link #handleMinigameComplete}'s
     * validation logic — the actual trust boundary — can be tested directly with known reward
     * contents instead of real loot/fish-profile data.
     */
    public int seedSessionForTest(ServerPlayer player, List<List<ItemStack>> targetRewardStacks) {
        UUID playerId = player.getUUID();
        int sessionId = sessionIdGenerator.incrementAndGet();

        List<ServerFishingTarget> targets = new ArrayList<>();
        for (List<ItemStack> rewards : targetRewardStacks) {
            targets.add(new ServerFishingTarget(rewards, FishingTarget.TargetCategory.FISH, 0.5f, 0f, List.of()));
        }

        Holder<Biome> biome = level.getBiome(player.blockPosition());
        Set<FishProfile.Zone> zones = FishProfile.Zone.resolve(biome, player.blockPosition().getY(), level.getSeaLevel());
        activeSessions.put(playerId, new ActiveSession(sessionId, playerId, targets, level.getGameTime(),
                biome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR, zones));
        return sessionId;
    }

    private static class ActiveSession {
        final int sessionId;
        final UUID playerId;
        final List<ServerFishingTarget> targets;
        final long startTime;
        final Holder<Biome> hookBiome;
        final FishProfile.TimeOfDay hookTimeOfDay;
        final FishProfile.WeatherCondition hookWeather;
        final Set<FishProfile.Zone> hookZones;

        ActiveSession(int sessionId, UUID playerId, List<ServerFishingTarget> targets, long startTime,
                Holder<Biome> hookBiome, FishProfile.TimeOfDay hookTimeOfDay, FishProfile.WeatherCondition hookWeather,
                Set<FishProfile.Zone> hookZones) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.targets = targets;
            this.startTime = startTime;
            this.hookBiome = hookBiome;
            this.hookTimeOfDay = hookTimeOfDay;
            this.hookWeather = hookWeather;
            this.hookZones = hookZones;
        }
    }

    private record ServerFishingTarget(
            List<ItemStack> rewardStacks,
            grill24.fishtastic.util.FishingTarget.TargetCategory category,
            float difficulty,
            float initialPosition,
            List<PhaseRule> phases
    ) {}
}
