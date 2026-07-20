package grill24.fishtastic.server;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.component.HookEffect;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.PhaseRule;
import grill24.fishtastic.data.Temperament;
import grill24.fishtastic.server.QuestTracker;
import grill24.fishtastic.tutorial.TutorialManager;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.item.FishtasticFishItem;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;

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

        ItemStack rod = findCopperRod(player);
        ItemStack bait = CopperFishingRod.getBait(rod);
        BaitEffect baitEffect = bait.isEmpty() ? BaitEffect.NO_BAIT : BaitEffect.fromStack(bait);
        ItemStack hookStack = CopperFishingRod.getHook(rod);
        HookEffect hookEffect = hookStack.isEmpty() ? null : hookStack.get(FishtasticDataComponents.HOOK_EFFECT.value());
        ItemStack charmStack = CopperFishingRod.getCharm(rod);
        CharmEffect charmEffect = charmStack.isEmpty() ? null : charmStack.get(FishtasticDataComponents.CHARM_EFFECT.value());

        List<ServerFishingTarget> targets = generateTargets(player, difficultyModifier, baitEffect, hookEffect, charmEffect);

        ItemStack topWeightedFishPreview = (charmEffect != null && charmEffect.showTopWeightedFish())
                ? computeTopWeightedFish(player, baitEffect, charmEffect)
                : ItemStack.EMPTY;

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

        ActiveSession session = new ActiveSession(sessionId, playerId, targets, level.getGameTime(),
                sessionBiome, sessionTimeOfDay, sessionWeather);
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

        sendToPlayer(player, new StartFishingMinigamePacket(sessionId, targetData, false, topWeightedFishPreview));
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

        ActiveSession session = new ActiveSession(sessionId, playerId, targets,
                level.getGameTime(), biome, timeOfDay, weather);
        activeSessions.put(playerId, session);

        sendToPlayer(player, new StartFishingMinigamePacket(sessionId, List.of(tutorialTarget), true, ItemStack.EMPTY));
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
        FishCatchSavedData catchDb = FishCatchSavedData.getOrCreate(level.getServer());
        // De-dupe indices — a client re-reporting the same target index must not award its reward twice.
        for (Integer index : new LinkedHashSet<>(caughtTargetIndices)) {
            if (index >= 0 && index < session.targets.size()) {
                ServerFishingTarget target = session.targets.get(index);
                for (ItemStack rewardStack : target.rewardStacks()) {
                    ItemStack reward = rewardStack.copy();
                    if (!reward.isEmpty()) {
                        if (catchDb.recordCatch(player.getUUID(), player.getName().getString(), reward)) {
                            firstCatchItems.add(reward.copy());
                        }
                        questStacks.add(reward.copy()); // copy — inventory.add() mutates the stack in-place
                        // Snapshot count/tag before inventory.add() mutates reward down to its leftover
                        // (usually 0) — reading these after the call under-counts trash almost every time.
                        boolean isTrash = reward.is(FishtasticItemTags.TRASH);
                        int caughtCount = reward.getCount();
                        player.getInventory().add(reward);
                        if (!reward.isEmpty()) {
                            // inventory.add() leaves any leftover count in reward when full/partially full
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

        // Record trash contributions first so the quest sync packet below (which snapshots
        // the cleanup goal total) reflects this session's catches instead of a stale total.
        if (trashCaught > 0) {
            CleanupGoalTracker.onTrashCatch(level.getServer(), player, trashCaught);
        }

        // Batch quest tracking — only one sync packet for all catches in this session
        if (!questStacks.isEmpty()) {
            QuestTracker.onCatchBatch(level.getServer(), player, questStacks,
                    session.hookBiome, session.hookTimeOfDay, session.hookWeather);
        }

        TutorialManager.onMinigameComplete(player);

        ItemStack baitDepletedItem = ItemStack.EMPTY;
        if (!rewards.isEmpty()) {
            baitDepletedItem = consumeBait(player);
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
        Holder<Biome> biome = level.getBiome(hookPos);
        FishProfile.TimeOfDay timeOfDay = charmEffect != null && charmEffect.forceNightFishing()
                ? FishProfile.TimeOfDay.NIGHT
                : FishProfile.TimeOfDay.fromGameTime(level.getOverworldClockTime());
        FishProfile.WeatherCondition weather = FishProfile.WeatherCondition.fromLevel(level, hookPos);
        net.minecraft.world.level.MoonPhase moonPhase = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.MOON_PHASE, hookPos);
        FishProfile.Elevation elevation = FishProfile.Elevation.fromY(hookPos.getY(), level.getSeaLevel());

        Registry<FishProfile> fishProfileRegistry = level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        float qualityBias = (baitEffect != null ? baitEffect.qualityBias() : 0.0f)
                + (hookEffect != null ? hookEffect.qualityBias() : 0.0f);

        List<ItemStack> treasureRewards = getTreasureRewards(lootparams);
        List<Holder<Item>> fishPool = getFishPool(player, baitEffect);
        List<Holder<Item>> trashPool = getTrashPool(player);

        float treasureChance = Math.max(0f, (baitEffect != null ? baitEffect.treasureChance() : DEFAULT_TREASURE_CHANCE)
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

        for (int i = 0; i < targetCount; i++) {
            float roll = randomSource.nextFloat();
            boolean isTrash = roll < trashChance;
            boolean isTreasure = !isTrash && roll < trashChance + treasureChance;
            boolean isFishReward = !isTrash && !isTreasure;
            int numRewards = isFishReward ? 1 : MathUtil.clamp((int) MathUtil.randomGaussian(randomSource, 1, 1), 1, 3);

            List<ItemStack> rewardStacks;
            if (isFishReward) {
                rewardStacks = generateFishRewards(randomSource, lootparams, fishPool,
                        fishProfileRegistry, biome, timeOfDay, weather, moonPhase, elevation, qualityBias, baitEffect, charmEffect, numRewards);
            } else if (isTreasure) {
                rewardStacks = generateTreasureRewards(randomSource, treasureRewards, numRewards);
            } else {
                rewardStacks = generateTrashRewards(randomSource, trashPool, numRewards);
            }

            if (rewardStacks.isEmpty()) continue;

            ItemStack reward = rewardStacks.getFirst();
            FishingTarget.TargetCategory category = reward.is(ItemTags.FISHES)
                    ? FishingTarget.TargetCategory.FISH
                    : reward.is(FishtasticItemTags.TRASH)
                            ? FishingTarget.TargetCategory.TRASH
                            : FishingTarget.TargetCategory.TREASURE;

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
     * Scans the fish pool under the current hook's environment context and returns the single
     * highest-weighted species — i.e. what {@link FishtasticFishItem#sampleRandomFish} is most
     * likely to roll right now. Mirrors the environment/pool resolution in
     * {@link #generateTargets} but picks the max instead of a weighted random draw. Only called
     * when the equipped charm (Angler's Almanac) reveals this.
     */
    private ItemStack computeTopWeightedFish(ServerPlayer player, @Nullable BaitEffect baitEffect, @Nullable CharmEffect charmEffect) {
        FishingHook hook = player.fishing;
        if (hook == null) return ItemStack.EMPTY;
        IFishingHookExtension hookExt = (IFishingHookExtension) hook;

        float luckBonus = baitEffect != null ? baitEffect.luckBonus() : 0.0f;
        LootParams lootparams = new LootParams.Builder(player.level())
                .withParameter(LootContextParams.ORIGIN, hook.position())
                .withParameter(LootContextParams.TOOL, player.getUseItem())
                .withParameter(LootContextParams.THIS_ENTITY, hook)
                .withLuck(hookExt.getLuck() + player.getLuck() + luckBonus)
                .create(LootContextParamSets.FISHING);

        BlockPos hookPos = BlockPos.containing(hook.position());
        Holder<Biome> biome = level.getBiome(hookPos);
        FishProfile.TimeOfDay timeOfDay = charmEffect != null && charmEffect.forceNightFishing()
                ? FishProfile.TimeOfDay.NIGHT
                : FishProfile.TimeOfDay.fromGameTime(level.getOverworldClockTime());
        FishProfile.WeatherCondition weather = FishProfile.WeatherCondition.fromLevel(level, hookPos);
        net.minecraft.world.level.MoonPhase moonPhase = level.environmentAttributes().getValue(net.minecraft.world.attribute.EnvironmentAttributes.MOON_PHASE, hookPos);
        FishProfile.Elevation elevation = FishProfile.Elevation.fromY(hookPos.getY(), level.getSeaLevel());

        Registry<FishProfile> fishProfileRegistry = level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        List<Holder<Item>> fishPool = getFishPool(player, baitEffect);

        Holder<Item> best = null;
        int bestWeight = 0;
        for (Holder<Item> candidate : fishPool) {
            int weight = FishtasticFishItem.getFishingLootWeight(
                    candidate, lootparams, fishProfileRegistry, biome, timeOfDay, weather, moonPhase, elevation, baitEffect, charmEffect);
            if (weight > bestWeight) {
                bestWeight = weight;
                best = candidate;
            }
        }
        return best != null ? new ItemStack(best) : ItemStack.EMPTY;
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

    private @NotNull List<ItemStack> getTreasureRewards(LootParams lootparams) {
        List<ItemStack> treasureRewards = new ArrayList<>();
        LootTable lootTable = level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING_TREASURE);
        for (int i = 0; i < 8; i++) {
            treasureRewards.addAll(lootTable.getRandomItems(lootparams));
        }
        return treasureRewards;
    }

    private static List<ItemStack> generateTreasureRewards(RandomSource randomSource, List<ItemStack> possibleTreasures, int numRewards) {
        List<ItemStack> rewardStacks = new ArrayList<>();
        for (int n = 0; n < numRewards; n++) {
            if (possibleTreasures.isEmpty()) break;
            rewardStacks.add(possibleTreasures.get(randomSource.nextInt(possibleTreasures.size())).copy());
        }
        return rewardStacks;
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
            FishProfile.Elevation elevation,
            float qualityBias,
            @Nullable BaitEffect baitEffect,
            @Nullable CharmEffect charmEffect,
            int numRewards
    ) {
        List<ItemStack> rewardStacks = new ArrayList<>();
        for (int n = 0; n < numRewards; n++) {
            ItemStack reward = FishtasticFishItem.sampleRandomFish(
                    randomSource, lootParams, fishPool,
                    fishProfileRegistry, biome, timeOfDay, weather, moonPhase, elevation, qualityBias, baitEffect, charmEffect);
            if (!reward.isEmpty()) rewardStacks.add(reward);
        }
        return rewardStacks;
    }

    private static ItemStack findCopperRod(ServerPlayer player) {
        if (player.getMainHandItem().is(FishtasticItems.COPPER_FISHING_ROD)) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().is(FishtasticItems.COPPER_FISHING_ROD)) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    /** @return a single copy of the bait item if this consumption emptied the stack, else EMPTY. */
    private static ItemStack consumeBait(ServerPlayer player) {
        ItemStack rod = findCopperRod(player);
        if (rod.isEmpty()) return ItemStack.EMPTY;
        ItemStack bait = CopperFishingRod.getBait(rod);
        if (bait.isEmpty()) return ItemStack.EMPTY;
        ItemStack depletedIcon = bait.getCount() == 1 ? bait.copyWithCount(1) : ItemStack.EMPTY;
        bait.shrink(1);
        CopperFishingRod.setBait(rod, bait);
        return depletedIcon;
    }

    private static void damageUpgrades(ServerPlayer player) {
        ItemStack rod = findCopperRod(player);
        if (rod.isEmpty()) return;

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
        activeSessions.put(playerId, new ActiveSession(sessionId, playerId, targets, level.getGameTime(),
                biome, FishProfile.TimeOfDay.DAY, FishProfile.WeatherCondition.CLEAR));
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

        ActiveSession(int sessionId, UUID playerId, List<ServerFishingTarget> targets, long startTime,
                Holder<Biome> hookBiome, FishProfile.TimeOfDay hookTimeOfDay, FishProfile.WeatherCondition hookWeather) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.targets = targets;
            this.startTime = startTime;
            this.hookBiome = hookBiome;
            this.hookTimeOfDay = hookTimeOfDay;
            this.hookWeather = hookWeather;
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
