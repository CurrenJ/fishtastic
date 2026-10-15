package grill24.fishtastic.server;

import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.companion.CompanionGroups;
import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionSceneType;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.network.CompanionScenePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
 *   <li>only free swimmers take part (party spec D25): perchers, ground walkers, drifters and
 *       gliders play nothing until their own scenes (G3) exist; and G2 needs its owners at least
 *       {@link #G2_MIN_OWNER_DISTANCE} apart (D24), since its lobes go round each of them;</li>
 *   <li>the greeting on approach (R5.3) falls out of the cooldown: walking up to a companion you
 *       haven't seen in a while almost always starts a scene, and standing together repeats at
 *       roughly the cooldown's pace.</li>
 * </ul>
 *
 * <p>A companion is (owner, party slot) (docs/fish-companions-party.md §5.2, §7). A group of one
 * species takes part as one member, through its leader's slot. Scenes only pair companions of
 * different owners for now; scenes inside one party arrive with the social drive (sim Phase 4).
 * At most one scene per pair of owners runs at a time.
 *
 * <p>Everything here is in memory only — nothing is saved, exactly like the spec says. Scenes are
 * purely cosmetic; the state is a few numbers per player, so losing it on restart is free.
 */
public final class CompanionSceneManager {
    private CompanionSceneManager() {}

    /** How close two companions must be for a scene (the ideas doc's "~6 blocks"). */
    public static final double RANGE = 6.0;
    /** G2's closest owner spacing (party spec D24): nearer, its lobes would pass through the owners. */
    public static final double G2_MIN_OWNER_DISTANCE = 2.5;
    /** How long after a scene (which already ends the participants' involvement) a companion sits out. */
    public static final int COOLDOWN_TICKS = 1200;
    /** How far a scene packet is worth sending, blocks — comfortably past where a companion is visible. */
    private static final double REACH = 128.0;

    /** One companion: its owner and party slot (a group's leader slot). */
    public record Member(UUID owner, int slot) {}

    /** Companion → the tick its involvement in the current scene (and its cooldown) ends at. */
    private static final Map<Member, Long> BUSY_UNTIL = new HashMap<>();

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

    /** A schedulable companion of one player: a group (or a solo fish) in that player's party. */
    private record Candidate(ServerPlayer player, CompanionGroups.Group group) {
        Member member() { return new Member(player.getUUID(), group.leader()); }
    }

    /**
     * The whole scheduler against an explicit clock — public so the game tests can step time
     * deterministically (game tests can't advance a level's game time).
     */
    public static void tickLevel(ServerLevel level, long now) {
        BUSY_UNTIL.values().removeIf(until -> until < now);
        PARTNER.keySet().retainAll(BUSY_UNTIL.keySet());

        List<ServerPlayer> holders = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!CompanionStorage.party(player).isEmpty()) holders.add(player);
        }
        if (holders.size() < 2) return;

        // Best pair wins the tick: lowest scene ordinal = highest priority (§10's fixed order), so
        // a same-species pair anywhere in the level beats a free-swimmer pair. One scene per tick
        // per level is plenty — scenes last seconds and pairs re-evaluate every tick anyway.
        Candidate bestFirst = null, bestSecond = null;
        CompanionSceneType bestScene = null;
        int bestPriority = Integer.MAX_VALUE;
        for (int i = 0; i < holders.size(); i++) {
            for (int j = i + 1; j < holders.size(); j++) {
                ServerPlayer a = holders.get(i), b = holders.get(j);
                if (!ownersFree(level, a, b, now)) continue;
                for (CompanionGroups.Group ga : CompanionGroups.of(CompanionStorage.party(a), level.registryAccess())) {
                    for (CompanionGroups.Group gb : CompanionGroups.of(CompanionStorage.party(b), level.registryAccess())) {
                        Candidate first = new Candidate(a, ga), second = new Candidate(b, gb);
                        CompanionSceneType scene = choose(first, second, now);
                        if (scene == null || scene.ordinal() >= bestPriority) continue;
                        bestPriority = scene.ordinal();
                        bestFirst = first;
                        bestSecond = second;
                        bestScene = scene;
                    }
                }
            }
        }
        if (bestScene == null) return;
        start(level, bestFirst, bestSecond, bestScene, now);
    }

    /**
     * Whether this pair of owners may start a scene at all: in range, neither fishing, and not
     * already sharing one (one scene per owner pair at a time).
     */
    private static boolean ownersFree(ServerLevel level, ServerPlayer a, ServerPlayer b, long now) {
        if (a.position().distanceToSqr(b.position()) > RANGE * RANGE) return false;
        if (busyFishing(level, a) || busyFishing(level, b)) return false;
        for (int sa = 0; sa < CompanionParty.SIZE; sa++) {
            for (int sb = 0; sb < CompanionParty.SIZE; sb++) {
                if (sharing(new Member(a.getUUID(), sa), new Member(b.getUUID(), sb), now)) return false;
            }
        }
        return true;
    }

    /** Pairs currently in a scene together (cooldowns don't count): both busy with the same end tick. */
    private static final Map<Member, Member> PARTNER = new HashMap<>();

    private static boolean sharing(Member a, Member b, long now) {
        return b.equals(PARTNER.get(a)) && isBusy(a, now)
                && BUSY_UNTIL.get(a) - COOLDOWN_TICKS >= now;
    }

    /**
     * The scene two companions should play right now, or null. This is the fixed priority order
     * (R5.6): same species first, then two free swimmers. Each later scene family slots in below
     * the ones above it.
     */
    @Nullable
    private static CompanionSceneType choose(Candidate a, Candidate b, long now) {
        if (isBusy(a.member(), now) || isBusy(b.member(), now)) return null;
        // D25: both scenes are swum; the other classes wait for scenes of their own (G3).
        if (a.group().locomotion() != Locomotion.FREE_SWIM || b.group().locomotion() != Locomotion.FREE_SWIM) return null;
        // A mixed shoal is no one species: two shoals take the free-swimmer scene below.
        if (a.group().species() == b.group().species() && a.group().kind() != CompanionGroups.Kind.SHOAL
                && b.group().kind() != CompanionGroups.Kind.SHOAL) return CompanionSceneType.G1_SAME_SPECIES;
        if (a.player().position().distanceToSqr(b.player().position()) >= G2_MIN_OWNER_DISTANCE * G2_MIN_OWNER_DISTANCE) {
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

    private static void start(ServerLevel level, Candidate first, Candidate second,
                              CompanionSceneType scene, long now) {
        long until = now + scene.durationTicks() + COOLDOWN_TICKS;
        BUSY_UNTIL.put(first.member(), until);
        BUSY_UNTIL.put(second.member(), until);
        PARTNER.put(first.member(), second.member());
        PARTNER.put(second.member(), first.member());

        CompanionScenePacket packet = new CompanionScenePacket(scene, first.player().getUUID(), first.group().leader(),
                second.player().getUUID(), second.group().leader(), now);
        Fishtastic.LOGGER.debug("Companion scene {} between {} (slot {}) and {} (slot {})", scene,
                first.player().getName().getString(), first.group().leader(),
                second.player().getName().getString(), second.group().leader());
        for (ServerPlayer recipient : level.players()) {
            if (recipient.distanceToSqr(first.player()) > REACH * REACH
                    && recipient.distanceToSqr(second.player()) > REACH * REACH) continue;
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

    /** Whether the companion in {@code slot} of {@code owner}'s party is in a scene or its cooldown at {@code now}. */
    public static boolean isBusy(UUID owner, int slot, long now) {
        return isBusy(new Member(owner, slot), now);
    }

    private static boolean isBusy(Member member, long now) {
        Long until = BUSY_UNTIL.get(member);
        return until != null && until >= now;
    }

    /** Dev/test seam: forget every busied companion and the packet sink. */
    public static void resetForTest() {
        BUSY_UNTIL.clear();
        PARTNER.clear();
        packetSinkForTest = null;
    }
}
