package grill24.fishtastic.server;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.PhaseRule;
import grill24.fishtastic.data.Temperament;
import grill24.fishtastic.server.QuestTracker;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.network.StartFishingMinigamePacket;
import grill24.fishtastic.util.FishingTarget;
import grill24.fishtastic.util.IFishingHookExtension;
import grill24.fishtastic.util.MathUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    private final Map<UUID, ActiveSession> activeSessions = new HashMap<>();
    private final AtomicInteger sessionIdGenerator = new AtomicInteger(0);

    private static final int SESSION_TIMEOUT_TICKS = 6000;
    private static final int MAX_TARGETS = 4;
    private static final float DEFAULT_TREASURE_CHANCE = 1.0f / 6.0f;
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
        BaitEffect baitEffect = bait.isEmpty() ? BaitEffect.NO_BAIT : bait.get(FishtasticDataComponents.BAIT_EFFECT.value());

        List<ServerFishingTarget> targets = generateTargets(player, difficultyModifier, baitEffect);

        // Capture environment context at hook position for quest tracking
        FishingHook sessionHook = player.fishing;
        Holder<Biome> sessionBiome = sessionHook != null
                ? level.getBiome(BlockPos.containing(sessionHook.position()))
                : level.getBiome(player.blockPosition());
        FishProfile.TimeOfDay sessionTimeOfDay = FishProfile.TimeOfDay.fromGameTime(level.getOverworldClockTime());
        FishProfile.WeatherCondition sessionWeather = level.isThundering() ? FishProfile.WeatherCondition.THUNDER
                : level.isRaining() ? FishProfile.WeatherCondition.RAIN
                : FishProfile.WeatherCondition.CLEAR;

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

        sendToPlayer(player, new StartFishingMinigamePacket(sessionId, targetData));

        Fishtastic.LOGGER.info("Started fishing minigame session {} for player {} with {} targets",
                sessionId, player.getName().getString(), targets.size());

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
        FishCatchSavedData catchDb = FishCatchSavedData.getOrCreate(level.getServer());
        for (Integer index : caughtTargetIndices) {
            if (index >= 0 && index < session.targets.size()) {
                ServerFishingTarget target = session.targets.get(index);
                for (ItemStack rewardStack : target.rewardStacks()) {
                    ItemStack reward = rewardStack.copy();
                    if (!reward.isEmpty()) {
                        catchDb.recordCatch(player.getUUID(), player.getName().getString(), reward);
                        QuestTracker.onCatch(level.getServer(), player, reward,
                                session.hookBiome, session.hookTimeOfDay, session.hookWeather);
                        player.getInventory().add(reward);
                        rewards.add(reward);
                    }
                }
            } else {
                Fishtastic.LOGGER.warn("Player {} reported invalid target index {}",
                        player.getName().getString(), index);
            }
        }

        if (!rewards.isEmpty()) {
            consumeBait(player);
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

    private List<ServerFishingTarget> generateTargets(ServerPlayer player, float difficultyModifier, @Nullable BaitEffect baitEffect) {
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
        FishProfile.TimeOfDay timeOfDay = FishProfile.TimeOfDay.fromGameTime(level.getOverworldClockTime());
        FishProfile.WeatherCondition weather = level.isThundering() ? FishProfile.WeatherCondition.THUNDER
                : level.isRaining() ? FishProfile.WeatherCondition.RAIN
                : FishProfile.WeatherCondition.CLEAR;

        Registry<FishProfile> fishProfileRegistry = level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        float qualityBias = baitEffect != null ? baitEffect.qualityBias() : 0.0f;

        List<ItemStack> treasureRewards = getTreasureRewards(lootparams);
        List<Holder<Item>> fishPool = getFishPool(player, baitEffect);

        float treasureChance = baitEffect != null ? baitEffect.treasureChance() : DEFAULT_TREASURE_CHANCE;
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

        for (int i = 0; i < targetCount; i++) {
            boolean isFishReward = randomSource.nextFloat() >= treasureChance;
            int numRewards = isFishReward ? 1 : MathUtil.clamp((int) MathUtil.randomGaussian(randomSource, 1, 1), 1, 3);

            List<ItemStack> rewardStacks;
            if (isFishReward) {
                rewardStacks = generateFishRewards(randomSource, lootparams, fishPool,
                        fishProfileRegistry, biome, timeOfDay, weather, qualityBias, baitEffect, numRewards);
            } else {
                rewardStacks = generateTreasureRewards(randomSource, treasureRewards, numRewards);
            }

            if (rewardStacks.isEmpty()) continue;

            ItemStack reward = rewardStacks.getFirst();
            FishingTarget.TargetCategory category = reward.is(ItemTags.FISHES)
                    ? FishingTarget.TargetCategory.FISH
                    : FishingTarget.TargetCategory.TREASURE;

            float difficulty;
            List<PhaseRule> phases;

            Temperament temperament = isFishReward
                    ? (forcedTemperament != null ? forcedTemperament : resolveTemperament(reward, fishProfileRegistry, temperamentRegistry))
                    : null;

            if (temperament != null) {
                difficulty = temperament.sampleDifficulty(randomSource) * difficultyModifier;
                phases = temperament.resolvedPhases();
            } else {
                difficulty = baseDifficulties[randomSource.nextInt(baseDifficulties.length)] * difficultyModifier;
                FishingTarget.MovementPattern pattern = FishingTarget.pickRandom(difficulty, randomSource.nextFloat());
                phases = List.of(new PhaseRule(0f, List.of(pattern), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()));
            }

            float initialPosition = randomSource.nextFloat();
            targets.add(new ServerFishingTarget(rewardStacks, category, difficulty, initialPosition, phases));
        }

        return targets;
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
            float qualityBias,
            @Nullable BaitEffect baitEffect,
            int numRewards
    ) {
        List<ItemStack> rewardStacks = new ArrayList<>();
        for (int n = 0; n < numRewards; n++) {
            ItemStack reward = FishtasticFishItem.sampleRandomFish(
                    randomSource, lootParams, fishPool,
                    fishProfileRegistry, biome, timeOfDay, weather, qualityBias, baitEffect);
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

    private static void consumeBait(ServerPlayer player) {
        ItemStack rod = findCopperRod(player);
        if (rod.isEmpty()) return;
        ItemStack bait = CopperFishingRod.getBait(rod);
        if (!bait.isEmpty()) {
            bait.shrink(1);
            CopperFishingRod.setBait(rod, bait);
        }
    }

    private void sendToPlayer(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(payload));
    }

    public boolean isPlayerInActiveSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
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
