package grill24.fishtastic.server;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.StartFishingMinigamePacket;
import grill24.fishtastic.util.FishingTarget;
import grill24.fishtastic.util.IFishingHookExtension;
import grill24.fishtastic.util.MathUtil;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Server-side manager for fishing minigame sessions (TRUST-BASED).
 * Server generates loot and caches it. Client plays the minigame locally and reports results.
 * Server validates and awards only the items that were actually in the session.
 */
public class FishingMinigameManager {
    private static final Map<ServerLevel, FishingMinigameManager> INSTANCES = new WeakHashMap<>();

    // Session management
    private final Map<UUID, ActiveSession> activeSessions = new HashMap<>();
    private final AtomicInteger sessionIdGenerator = new AtomicInteger(0);

    // Configuration
    private static final int SESSION_TIMEOUT_TICKS = 6000; // 5 minutes
    private static final int MAX_TARGETS = 5;

    private final ServerLevel level;

    private FishingMinigameManager(ServerLevel level) {
        this.level = level;
    }

    /**
     * Get or create the manager for a server level
     */
    public static FishingMinigameManager get(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, FishingMinigameManager::new);
    }

    /**
     * Start a new fishing minigame session for a player
     * @param player The player starting the minigame
     * @param difficultyModifier Modifier for target difficulty (0.5-2.0)
     * @return The session ID, or -1 if failed
     */
    public int startSession(ServerPlayer player, float difficultyModifier, boolean cancelExistingSession) {
        UUID playerId = player.getUUID();

        // Cancel existing session if any
        if (activeSessions.containsKey(playerId)) {
            if(cancelExistingSession) {
                cancelSession(player);
            } else {
                return -1;
            }
        }

        // Generate new session
        int sessionId = sessionIdGenerator.incrementAndGet();

        // Create targets with server-side loot determination
        List<ServerFishingTarget> targets = generateTargets(player, difficultyModifier);

        // Create session (no physics state needed - client handles that)
        ActiveSession session = new ActiveSession(
                sessionId,
                playerId,
                targets,
                level.getGameTime()
        );

        activeSessions.put(playerId, session);

        // Send start packet to client
        List<StartFishingMinigamePacket.TargetData> targetData = new ArrayList<>();
        for (ServerFishingTarget target : targets) {
            targetData.add(new StartFishingMinigamePacket.TargetData(
                    target.rewardStacks(),
                    target.category(),
                    target.initialPosition(),
                    target.difficulty()
            ));
        }

        sendToPlayer(player, new StartFishingMinigamePacket(sessionId, targetData));

        Fishtastic.LOGGER.info("Started fishing minigame session {} for player {} with {} targets",
                sessionId, player.getName().getString(), targets.size());

        return sessionId;
    }

    /**
     * Handle minigame completion on server
     */
    public void handleMinigameComplete(ServerPlayer player, int sessionId, List<Integer> caughtTargetIndices) {
        UUID playerId = player.getUUID();
        ActiveSession session = activeSessions.get(playerId);

        if (session == null || session.sessionId != sessionId) {
            Fishtastic.LOGGER.warn("Received results for invalid session {} from player {}",
                    sessionId, player.getName().getString());
            return;
        }

        long timeTaken = level.getGameTime() - session.startTime;

        // Basic validation - check for impossible completion times (optional anti-cheat)
        if (timeTaken < 20) { // Less than 1 second is suspicious
            Fishtastic.LOGGER.warn("Player {} completed fishing too quickly ({} ticks)",
                    player.getName().getString(), timeTaken);
            // Could reject or just log
        }

        // Validate caught indices and award items
        List<ItemStack> rewards = new ArrayList<>();
        for (Integer index : caughtTargetIndices) {
            if (index >= 0 && index < session.targets.size()) {
                ServerFishingTarget target = session.targets.get(index);
                // Award all reward stacks from this target
                for (ItemStack rewardStack : target.rewardStacks()) {
                    ItemStack reward = rewardStack.copy();
                    if (!reward.isEmpty()) {
                        player.getInventory().add(reward);
                        rewards.add(reward);
                    }
                }
            } else {
                Fishtastic.LOGGER.warn("Player {} reported invalid target index {}",
                        player.getName().getString(), index);
            }
        }

        // Clean up session
        activeSessions.remove(playerId);

        Fishtastic.LOGGER.info("Awarded {} items to player {} for session {} (took {} ticks)",
                rewards.size(), player.getName().getString(), sessionId, timeTaken);
    }

    /**
     * Tick all active sessions - just check for timeouts
     */
    public void tick() {
        long currentTime = level.getGameTime();

        // Clean up timed out sessions
        activeSessions.entrySet().removeIf(entry -> {
            ActiveSession session = entry.getValue();
            if (currentTime - session.startTime > SESSION_TIMEOUT_TICKS) {
                Fishtastic.LOGGER.info("Session {} timed out", session.sessionId);
                return true;
            }
            return false;
        });
    }

    /**
     * Cancel a player's active session
     */
    public void cancelSession(net.minecraft.world.entity.player.Player player) {
        ActiveSession session = activeSessions.remove(player.getUUID());
        if (session != null) {
            Fishtastic.LOGGER.info("Cancelled session {} for player {}",
                    session.sessionId, player.getName().getString());
        }
    }

    /**
     * Generate fishing targets based on loot tables or configured rules
     */
    private List<ServerFishingTarget> generateTargets(ServerPlayer player, float difficultyModifier) {
        List<ServerFishingTarget> targets = new ArrayList<>();
        RandomSource randomSource = player.getRandom();

        // TODO: Use loot tables for proper item selection
        // Sample fish items from fish tag
        List<ItemStack> fishRewards = player.registryAccess().registryOrThrow(Registries.ITEM).getOrCreateTag(ItemTags.FISHES).stream()
                .map(Holder::value)
                .map(ItemStack::new)
                .toList();

        // Sample treasure items from loot table
        List<ItemStack> treasureRewards = new ArrayList<>();
        FishingHook hook = player.fishing;
        IFishingHookExtension hookExt = (IFishingHookExtension) hook;
        if (hook != null) {
            LootTable lootTable =  level.getServer().reloadableRegistries().getLootTable(BuiltInLootTables.FISHING_TREASURE);
            for (int i = 0; i < 8; i++) {
                LootParams lootparams = new LootParams.Builder(player.serverLevel())
                        .withParameter(LootContextParams.ORIGIN, hook.position())
                        .withParameter(LootContextParams.TOOL, player.getUseItem())
                        .withParameter(LootContextParams.THIS_ENTITY, hook)
                        .withParameter(LootContextParams.ATTACKING_ENTITY, hook.getOwner())
                        .withLuck(hookExt.getLuck() + player.getLuck())
                        .create(LootContextParamSets.FISHING);
                List<ItemStack> drops = lootTable.getRandomItems(lootparams);
                treasureRewards.addAll(drops);
            }
        }

        float[] baseDifficulties = {0.3f, 0.4f, 0.5f, 0.6f, 0.8f, 0.9f, 0.7f};
        int targetCount = (int) Math.clamp(MathUtil.randomGaussian(randomSource, 2, 1), 1, MAX_TARGETS);
        for (int i = 0; i < targetCount; i++) {
            boolean isFish = randomSource.nextBoolean();
            List<ItemStack> possibleRewards = isFish ? fishRewards : treasureRewards;
            int numRewards = isFish ? 1 : MathUtil.clamp((int) MathUtil.randomGaussian(randomSource, 1, 1), 1, 3);
            List<ItemStack> rewardStacks = generateRewards(randomSource, possibleRewards, numRewards);

            int difficultyIndex = randomSource.nextInt(baseDifficulties.length);
            float difficulty = baseDifficulties[difficultyIndex] * difficultyModifier;
            float initialPosition = randomSource.nextFloat();

            // Determine category based on item type
            FishingTarget.TargetCategory category;
            ItemStack reward = rewardStacks.getFirst(); // Use first item to determine category
            if (reward.is(ItemTags.FISHES)) {
                category = FishingTarget.TargetCategory.FISH;
            } else {
                category = FishingTarget.TargetCategory.TREASURE;
            }

            targets.add(new ServerFishingTarget(
                    rewardStacks, // For now, single item per target
                    category,
                    difficulty,
                    initialPosition
            ));
        }

        return targets;
    }

    private static List<ItemStack> generateRewards(RandomSource randomSource, List<ItemStack> possibleRewards, int numRewards) {
        List<ItemStack> rewardStacks = new ArrayList<>();

        for(int n = 0; n < numRewards; n++) {
            int index = randomSource.nextInt(possibleRewards.size());
            rewardStacks.add(possibleRewards.get(index).copy());
        }
        return rewardStacks;
    }

    /**
     * Send a packet to a player (platform-agnostic wrapper)
     */
    private void sendToPlayer(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(payload));
    }

    public boolean isPlayerInActiveSession(UUID uuid) {
        return activeSessions.containsKey(uuid);
    }

    /**
     * Represents an active fishing session
     */
    private static class ActiveSession {
        final int sessionId;
        final UUID playerId;
        final List<ServerFishingTarget> targets;
        final long startTime;

        ActiveSession(int sessionId, UUID playerId, List<ServerFishingTarget> targets, long startTime) {
            this.sessionId = sessionId;
            this.playerId = playerId;
            this.targets = targets;
            this.startTime = startTime;
        }
    }

    /**
     * Server-side representation of a fishing target
     * Contains both the actual rewards and what the client sees
     */
    private record ServerFishingTarget(
            List<ItemStack> rewardStacks,      // Actual rewards given to player
            grill24.fishtastic.util.FishingTarget.TargetCategory category, // Category for display icon
            float difficulty,
            float initialPosition
    ) {}
}
