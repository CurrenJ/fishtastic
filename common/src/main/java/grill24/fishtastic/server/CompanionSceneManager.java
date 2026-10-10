package grill24.fishtastic.server;

import grill24.FishtasticRegistries;
import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.companion.CompanionSceneType;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.network.CompanionScenePacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Schedules pet-to-pet scenes (docs/fish-companions.md §10): the server — never the clients —
 * decides when two companions near each other put on a show, and sends exactly one
 * {@link CompanionScenePacket} per scene to the players near them.
 *
 * <p>The rules, all of them:
 * <ul>
 *   <li>a companion is in at most one scene at a time, and every scene puts both participants on
 *       a per-companion cooldown — the whole memory of the system (R5.2; there is deliberately no
 *       per-pair history, R5.4);</li>
 *   <li>no scene starts while either owner is in the fishing minigame or has a line out (R5.5 —
 *       their companion belongs at the bobber then, §5);</li>
 *   <li>a fixed priority order picks the scene (R5.6): same species (G1) beats two free swimmers
 *       (G2). Later scenes (predator/prey, wily, class pairing) slot into {@link #choose} in that
 *       order without touching the rest;</li>
 *   <li>the greeting on approach (R5.3) falls out of the cooldown: walking up to a companion you
 *       haven't seen in a while almost always starts a scene, and standing together repeats at
 *       roughly the cooldown's pace.</li>
 * </ul>
 *
 * <p>Everything here is in memory only — nothing is saved, exactly like the spec says. Scenes are
 * purely cosmetic; the state is two numbers per player, so losing it on restart is free.
 */
public final class CompanionSceneManager {
    private CompanionSceneManager() {}

    /** How close two companions must be for a scene (the ideas doc's "~6 blocks"). */
    public static final double RANGE = 6.0;
    /** How long after a scene (which already ends the participants' involvement) a companion sits out. */
    public static final int COOLDOWN_TICKS = 1200;
    /** How far a scene packet is worth sending, blocks — comfortably past where a companion is visible. */
    private static final double REACH = 128.0;

    /** Owner UUID → the tick their involvement in the current scene (and its cooldown) ends at. */
    private static final Map<UUID, Long> BUSY_UNTIL = new HashMap<>();

    /**
     * Dev/test seam: when set, scene packets go here instead of to the players' connections, so a
     * game test can assert exactly who was told what. Null in production.
     */
    @Nullable
    public static BiConsumer<ServerPlayer, CompanionScenePacket> packetSinkForTest;

    /** Called from the server tick (see {@link ServerTickHandler}). */
    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            try {
                tickLevel(level, level.getGameTime());
            } catch (Exception e) {
                Fishtastic.LOGGER.error("Error ticking companion scene manager for level {}",
                        level.dimension().identifier(), e);
            }
        }
    }

    /**
     * The whole scheduler against an explicit clock — public so the game tests can step time
     * deterministically (game tests can't advance a level's game time).
     */
    public static void tickLevel(ServerLevel level, long now) {
        BUSY_UNTIL.values().removeIf(until -> until < now);

        List<ServerPlayer> holders = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!CompanionStorage.get(player).isEmpty()) holders.add(player);
        }
        if (holders.size() < 2) return;

        // Best pair wins the tick: lowest scene ordinal = highest priority (§10's fixed order), so
        // a same-species pair anywhere in the level beats a free-swimmer pair. One scene per tick
        // per level is plenty — scenes last seconds and pairs re-evaluate every tick anyway.
        ServerPlayer bestFirst = null, bestSecond = null;
        CompanionSceneType bestScene = null;
        int bestPriority = Integer.MAX_VALUE;
        for (int i = 0; i < holders.size(); i++) {
            for (int j = i + 1; j < holders.size(); j++) {
                ServerPlayer a = holders.get(i), b = holders.get(j);
                CompanionSceneType scene = choose(level, a, b, now);
                if (scene == null) continue;
                int priority = scene.ordinal();
                if (priority < bestPriority) {
                    bestPriority = priority;
                    bestFirst = a;
                    bestSecond = b;
                    bestScene = scene;
                }
            }
        }
        if (bestScene == null) return;
        start(level, bestFirst, bestSecond, bestScene, now);
    }

    /**
     * The scene two companions should play right now, or null. This is the fixed priority order
     * (R5.6): same species first, then two free swimmers. Each later scene family slots in below
     * the ones above it.
     */
    @Nullable
    private static CompanionSceneType choose(ServerLevel level, ServerPlayer a, ServerPlayer b, long now) {
        if (isBusy(a.getUUID(), now) || isBusy(b.getUUID(), now)) return null;
        if (a.position().distanceToSqr(b.position()) > RANGE * RANGE) return null;
        if (busyFishing(level, a) || busyFishing(level, b)) return null;

        ItemStack first = CompanionStorage.get(a);
        ItemStack second = CompanionStorage.get(b);
        if (first.is(second.getItem())) return CompanionSceneType.G1_SAME_SPECIES;
        Locomotion firstClass = locomotionOf(level, first);
        Locomotion secondClass = locomotionOf(level, second);
        if (firstClass == Locomotion.FREE_SWIM && secondClass == Locomotion.FREE_SWIM) {
            return CompanionSceneType.G2_FREE_SWIM_EIGHT;
        }
        return null;
    }

    /**
     * Whether this player's companion is committed elsewhere: mid-minigame, or with a line out
     * (§5 puts it at the bobber then). The server knows both — the minigame manager tracks
     * sessions, and vanilla keeps {@code player.fishing} set while a hook is in the world.
     */
    private static boolean busyFishing(ServerLevel level, ServerPlayer player) {
        FishingMinigameManager manager = FishingMinigameManager.get(level);
        if (manager != null && manager.isPlayerInActiveSession(player.getUUID())) return true;
        return player.fishing != null && !player.fishing.isRemoved();
    }

    private static void start(ServerLevel level, ServerPlayer first, ServerPlayer second,
                              CompanionSceneType scene, long now) {
        long until = now + scene.durationTicks() + COOLDOWN_TICKS;
        BUSY_UNTIL.put(first.getUUID(), until);
        BUSY_UNTIL.put(second.getUUID(), until);

        CompanionScenePacket packet = new CompanionScenePacket(scene, first.getUUID(), second.getUUID(), now);
        Fishtastic.LOGGER.debug("Companion scene {} between {} and {}", scene, first.getName().getString(),
                second.getName().getString());
        for (ServerPlayer recipient : level.players()) {
            if (recipient.distanceToSqr(first) > REACH * REACH
                    && recipient.distanceToSqr(second) > REACH * REACH) continue;
            send(recipient, packet);
        }
    }

    private static void send(ServerPlayer recipient, CompanionScenePacket packet) {
        BiConsumer<ServerPlayer, CompanionScenePacket> sink = packetSinkForTest;
        if (sink != null) {
            sink.accept(recipient, packet);
        } else {
            recipient.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(packet));
        }
    }

    /** Whether {@code companion} (by owner UUID) is in a scene or its cooldown at {@code now}. */
    public static boolean isBusy(UUID owner, long now) {
        Long until = BUSY_UNTIL.get(owner);
        return until != null && until >= now;
    }

    /** Dev/test seam: forget every busied companion and the packet sink. */
    public static void resetForTest() {
        BUSY_UNTIL.clear();
        packetSinkForTest = null;
    }

    /**
     * The companion species' locomotion class, resolved from its fish profile the same way the
     * client's renderer resolves it. Deliberately a local mirror of {@code TankFlockAdapter
     * .locomotionOf} (which lives in the client renderer and must stay off the server's classpath):
     * pose and locomotion are linked by this table and only this table, so the two copies must be
     * changed together.
     */
    private static Locomotion locomotionOf(ServerLevel level, ItemStack fish) {
        FishAnimationConfig animation = BuiltInRegistries.ITEM.getResourceKey(fish.getItem())
                .map(key -> level.registryAccess()
                        .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                        .getOptional(ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY,
                                key.identifier()))
                        .map(profile -> profile.animation().orElse(FishAnimationConfig.HorizontalSwim.DEFAULT))
                        .orElse(FishAnimationConfig.HorizontalSwim.DEFAULT))
                .orElse(FishAnimationConfig.HorizontalSwim.DEFAULT);
        return switch (animation) {
            case FishAnimationConfig.HorizontalSwim ignored -> Locomotion.FREE_SWIM;
            case FishAnimationConfig.BellyDown ignored -> Locomotion.GLIDE;
            case FishAnimationConfig.UprightFloat ignored -> Locomotion.DRIFT;
            case FishAnimationConfig.FloorSit ignored -> Locomotion.BENTHIC;
            case FishAnimationConfig.UprightSit ignored -> Locomotion.BENTHIC;
            case FishAnimationConfig.Planted ignored -> Locomotion.ANCHORED;
        };
    }
}
