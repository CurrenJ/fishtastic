package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.companion.CompanionWorld;
import grill24.fishsim.companion.CrossScene;
import grill24.fishsim.companion.PartyGrouping;
import grill24.fishsim.companion.PartyLayer;
import grill24.fishsim.companion.PointOfInterest;
import grill24.fishsim.companion.Temperament;
import grill24.fishsim.companion.Vec;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.companion.CompanionGroups;
import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionSceneType;
import grill24.fishtastic.companion.CompanionStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Every companion fish the client can see (docs/fish-companions-sim-plan.md §3.5,
 * docs/fish-companions-party.md §3). Each tick it reads the synced party of every player in the
 * client level ({@link CompanionStorage#party}), groups it ({@link PartyGrouping}, via
 * {@link CompanionGroups#members}) and keeps one {@link ClientCompanion} per group — created when a
 * player with a party comes into view, all rebuilt when their party changes, dropped when they leave
 * or empty it — then steps the party's {@link PartyLayer} (party spec §6, Phase 3: perch homes and
 * leases, one shared hand clock, ground separation) from the worlds the {@link CompanionWorldAdapter}
 * builds. A one-unit party steps exactly as a lone companion did.
 * A companion is identified by its owner and its group's lowest slot; the party lead is found by
 * slot ({@link ClientCompanion#holdsSlot}), since it may follow in its own school (D9).
 *
 * <p>The client settings are respected at the submit sites: other players' companions can be hidden
 * entirely, and your own can be hidden in first person.
 *
 * <p>The dev tooling (phantoms, the overlay, the dev triggers, the live switches) is in
 * {@link ClientCompanionsDev}.
 *
 * <p>Pet-to-pet scenes arrive as {@link #onScenePacket} (party spec §28). Fishing reactions were
 * parked by the sim rewrite (plan §5) and their code removed; bring them back from git history.
 */
public final class ClientCompanions {
    private ClientCompanions() {}

    /**
     * One player's party as this client runs it: the synced party, one companion per group, and the
     * party layer that steps them together (built on the first tick, when there's a world to build
     * it in). A dev phantom is a party of one with no owner.
     */
    static final class OwnerParty {
        @Nullable private final Player owner;
        @Nullable private final CompanionParty party;
        private final List<ClientCompanion> companions;
        @Nullable private PartyLayer layer;

        OwnerParty(@Nullable Player owner, @Nullable CompanionParty party, List<ClientCompanion> companions) {
            this.owner = owner;
            this.party = party;
            this.companions = companions;
        }

        @Nullable Player owner() { return owner; }
        @Nullable CompanionParty party() { return party; }
        List<ClientCompanion> companions() { return companions; }

        // ── A cross-player scene (party spec §28) ──
        /** This party's side of a scene, the shared start tick, the partner and its slot; null for none. */
        @Nullable private CrossScene scene;
        private long sceneStart;
        @Nullable private UUID scenePartner;
        private int scenePartnerSlot;
        private boolean heartFired;

        @Nullable PartyLayer layer() { return layer; }

        /** Builds (first tick) or steps the party's sim; returns the time it took, ns. */
        long tick(ClientLevel level, CompanionWorldAdapter.LevelBlocks blocks, List<PointOfInterest> devPoints,
                  List<ClientCompanion> everyone) {
            int n = companions.size();
            CompanionWorld[] worlds = new CompanionWorld[n];
            // What depends on the owner alone is read once for the whole party, not once per unit.
            var ownerAmbient = CompanionAmbient.at(level, companions.get(0).featureEye(), List.of());
            var ownerPoints = owner != null ? CompanionWorldAdapter.ownerPoints(level, owner) : null;
            for (int i = 0; i < n; i++) {
                worlds[i] = companions.get(i).world(level, blocks, othersNear(this, companions.get(i), everyone),
                        devPoints, ownerAmbient, ownerPoints);
            }
            long start = System.nanoTime();
            if (layer == null) {
                List<PartyGrouping.Group> groups = new ArrayList<>(n);
                Map<PartyGrouping.Member, ClientCompanion> byLeader = new HashMap<>();
                for (ClientCompanion c : companions) {
                    groups.add(c.group());
                    byLeader.put(c.group().leader(), c);
                }
                layer = new PartyLayer(ClientCompanion.TUNABLES, groups, m -> byLeader.get(m).temperament(),
                        m -> byLeader.get(m).driftStyle(), worlds[0], slot -> pointId(owner, slot, 0));
                for (int i = 0; i < n; i++) companions.get(i).attach(layer.unit(i));
            } else {
                PartyLayer l = layer;
                l.step(u -> worlds[l.units().indexOf(u)]);
            }
            long nanos = System.nanoTime() - start;
            for (ClientCompanion c : companions) c.afterStep(nanos);
            return nanos;
        }
    }

    static final Map<UUID, OwnerParty> PARTIES = new HashMap<>();
    /** Scene packets waiting for both parties' sims (party spec §28.2, D30), and how long they may wait. */
    private static final class PendingScene {
        final CompanionSceneType type;
        final UUID first, second;
        final int firstSlot, secondSlot;
        final long startTime;
        /** Client ticks waited, not game time: a loaded client's synced clock can jump. */
        int waited;
        /** The game time it arrived at, for the log (how far the synced clock moved while it waited). */
        long arrivedGameTime;

        PendingScene(CompanionSceneType type, UUID first, int firstSlot, UUID second, int secondSlot, long startTime) {
            this.type = type;
            this.first = first;
            this.firstSlot = firstSlot;
            this.second = second;
            this.secondSlot = secondSlot;
            this.startTime = startTime;
        }
    }
    private static final List<PendingScene> PENDING_SCENES = new ArrayList<>();
    private static final int PENDING_SCENE_TICKS = 40;
    /** G1's bubble heart, on the shared clock: mid-way round the first lap. */
    private static final int HEART_TICK = CrossScene.GREET_TICKS + 40;
    /**
     * The heart's size, blocks per heart-curve unit, and how far above the owners' eyes it floats:
     * between the two players, below the ring, so both see it whole.
     */
    private static final double HEART_SCALE = 0.03, HEART_RISE = 0.2;
    /** How far a heart bubble rises before it pops: 1 block holds the heart about 2-4 s (0.5 lost half by 1 s). */
    private static final double HEART_POP_RISE = 1.0;
    /** Dev-only companions with no owner, round an imaginary still owner — a stand-in for another player's. */
    static final Map<UUID, OwnerParty> PHANTOMS = new HashMap<>();
    @Nullable private static ClientLevel trackedLevel;
    @Nullable private static WorldFeatureIndex featureIndex;
    public static WorldFeatureIndex featureIndex() { return featureIndex; }
    @Nullable private static CompanionWorldAdapter.LevelBlocks blocks;
    /** Another companion counts as a point of interest within this range of an owner, blocks. */
    private static final double COMPANION_RANGE = 16.0;
    /**
     * Distance level of detail for other players' parties (audit §6.2): one is built when its owner
     * comes within {@link #SIM_BUILD_RANGE} blocks of this client's player and dropped beyond
     * {@link #SIM_DROP_RANGE}; the gap keeps an owner on the boundary from rebuilding it every tick.
     * At that range a companion is a pixel or two, and a rebuilt party starts round its owner as one
     * joining the world does. Your own party is always run.
     */
    static final double SIM_BUILD_RANGE = 64.0, SIM_DROP_RANGE = 72.0;

    /** Dev: the temperament forced on the local party's companion holding each slot, or null for its species' own. */
    static final Temperament[] temperamentOverrides = new Temperament[CompanionParty.SIZE];

    /** Dev diagnostics: companion draws submitted by the most recent world pass (one frame's worth). */
    public static int worldSubmitsThisFrame;
    /** Dev diagnostics: perch-layer calls and the draws it made, cumulative. */
    public static int perchLayerCalls, perchSubmits;

    // Cost of the sim, for the overlay (plan §7 risk 3): block samples and time per tick, averaged
    // over the last second, and the worst tick.
    private static long lastSamples, lastSampleNanos;
    static final int COST_WINDOW = 20;
    static final long[] costSamples = new long[COST_WINDOW], costSampleNanos = new long[COST_WINDOW],
            costStepNanos = new long[COST_WINDOW];
    private static int costIndex;
    /** Peaks since the last {@link ClientCompanionsDev#devResetCost}: block samples and whole-step time in one tick. */
    static long peakSamples, peakStepNanos;

    // ---------------------------------------------------------------- tracking

    /** Client tick (20 Hz). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != trackedLevel) {
            PARTIES.clear();
            PHANTOMS.clear();
            CompanionDevPoints.clear();
            CompanionMusic.clear();
            trackedLevel = level;
            featureIndex = level == null ? null : new WorldFeatureIndex(level);
            blocks = level == null ? null : new CompanionWorldAdapter.LevelBlocks(level);
            lastSamples = lastSampleNanos = 0;
        }
        if (level == null) return;

        Set<UUID> seen = new HashSet<>();
        for (Player player : level.players()) {
            CompanionParty party = CompanionStorage.party(player);
            if (party.isEmpty()) continue;
            UUID id = player.getUUID();
            OwnerParty existing = PARTIES.get(id);
            if (!inSimRange(player, mc.player, existing != null)) continue; // dropped below, if it ran
            seen.add(id);
            if (existing == null || existing.owner() != player || !existing.party().matches(party)) {
                PARTIES.put(id, build(player, party, level));
            }
        }
        PARTIES.keySet().retainAll(seen);

        startPendingScenes(level);
        feedScenes(level);

        CompanionDevPoints.tick(level);
        List<PointOfInterest> devPoints = CompanionDevPoints.points();
        List<BlockPos> featureOwners = new ArrayList<>();
        for (OwnerParty p : parties()) for (ClientCompanion c : p.companions()) featureOwners.add(c.featureOwner());
        featureIndex.tick(featureOwners.stream().distinct().toList());
        List<ClientCompanion> everyone = all();
        long step = 0;
        for (OwnerParty party : parties()) step += party.tick(level, blocks, devPoints, everyone);
        recordCost(step);
    }

    /** Whether {@code player}'s party is run here: always your own; another's by distance, see {@link #SIM_BUILD_RANGE}. */
    private static boolean inSimRange(Player player, @Nullable Player local, boolean running) {
        if (local == null || player == local) return true;
        double range = running ? SIM_DROP_RANGE : SIM_BUILD_RANGE;
        return player.distanceToSqr(local) <= range * range;
    }

    /** One companion per group of {@code player}'s party, in group order. */
    private static OwnerParty build(Player player, CompanionParty party, ClientLevel level) {
        UUID id = player.getUUID();
        boolean local = player == Minecraft.getInstance().player;
        List<ClientCompanion> companions = new ArrayList<>();
        for (PartyGrouping.Group group : PartyGrouping.group(CompanionGroups.members(party, level.registryAccess(),
                s -> ClientCompanion.trueLength(s, level), s -> ClientCompanion.stackSeed(id, s)))) {
            List<ItemStack> stacks = new ArrayList<>();
            Temperament override = null;
            for (PartyGrouping.Member member : group.members()) {
                stacks.add(party.get(member.slot()));
                if (local && override == null) override = temperamentOverrides[member.slot()];
            }
            companions.add(new ClientCompanion(player, null, 0f, group, stacks, level, override));
        }
        return new OwnerParty(player, party, List.copyOf(companions));
    }

    /** Every party this client runs, phantoms included. */
    private static List<OwnerParty> parties() {
        List<OwnerParty> all = new ArrayList<>(PARTIES.values());
        all.addAll(PHANTOMS.values());
        return all;
    }

    /** Every companion this client runs, phantoms included. */
    static List<ClientCompanion> all() {
        List<ClientCompanion> all = new ArrayList<>();
        for (OwnerParty party : parties()) all.addAll(party.companions());
        return all;
    }

    /**
     * Other parties' companions near {@code self}'s fish, as its points of interest (from this
     * client's own sims). Its own party-mates are left out: its party layer adds them.
     */
    private static List<PointOfInterest> othersNear(OwnerParty own, ClientCompanion self, List<ClientCompanion> everyone) {
        if (self.agent() == null || everyone.size() < 2) return List.of();
        List<PointOfInterest> out = new ArrayList<>();
        int i = 0;
        for (ClientCompanion other : everyone) {
            i++;
            if (own.companions().contains(other) || other.agent() == null) continue;
            if (other.agent().position().distanceTo(self.agent().position()) > COMPANION_RANGE) continue;
            PointOfInterest p = other.asPoint(pointId(other.owner(), other.lowestSlot(), i));
            if (p != null) out.add(p);
        }
        return out;
    }

    /**
     * A stable id per companion that can't collide with an entity's (party spec §5.2): its owner's
     * entity id and its group's lowest slot (its identity; the formation leader can change with
     * sizes), negated. Phantoms have no owner; they take an index below every real id.
     */
    static int pointId(@Nullable Player owner, int lowestSlot, int index) {
        return owner != null ? -1 - (owner.getId() * CompanionParty.SIZE + lowestSlot)
                : Integer.MIN_VALUE + index;
    }

    private static void recordCost(long stepNanos) {
        if (blocks == null) return;
        costSamples[costIndex] = blocks.samples - lastSamples;
        costSampleNanos[costIndex] = blocks.nanos - lastSampleNanos;
        costStepNanos[costIndex] = stepNanos;
        peakSamples = Math.max(peakSamples, costSamples[costIndex]);
        peakStepNanos = Math.max(peakStepNanos, stepNanos);
        costIndex = (costIndex + 1) % COST_WINDOW;
        lastSamples = blocks.samples;
        lastSampleNanos = blocks.nanos;
    }

    /** A block changed on the client: every companion forgets the blocks it sampled (cheap; resampled lazily). */
    public static void onBlockChanged(BlockPos pos) {
        if (featureIndex != null) featureIndex.changed(pos);
        if (PARTIES.isEmpty() && PHANTOMS.isEmpty()) return;
        for (ClientCompanion companion : all()) companion.invalidateBlocks();
    }

    /** A chunk arrived: cells sampled while it was missing read as air, so forget them. */
    public static void onChunkLoaded(ChunkPos pos) {
        if (featureIndex != null) featureIndex.chunkChanged(pos);
        for (ClientCompanion companion : all()) companion.invalidateBlocks();
    }

    public static void onChunkUnloaded(ChunkPos pos) {
        if (featureIndex != null) featureIndex.chunkChanged(pos);
    }

    /** Scene packets received this session, and the last one (the two-client self-test reads them). */
    public static int devScenePackets;
    @Nullable public static String devLastScene;

    /**
     * The scene packet's landing point (party spec §28): each named party plays its side, through
     * the unit holding the named slot. It waits up to a second for both parties' sims (the first
     * tick after a player comes into view has none yet), then is dropped.
     */
    public static void onScenePacket(CompanionSceneType scene, UUID first, int firstSlot, UUID second, int secondSlot,
                                     long startTime) {
        devScenePackets++;
        devLastScene = scene + " " + first + "#" + firstSlot + " + " + second + "#" + secondSlot + " @" + startTime;
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        PendingScene pending = new PendingScene(scene, first, firstSlot, second, secondSlot, startTime);
        pending.arrivedGameTime = level.getGameTime();
        PENDING_SCENES.add(pending);
    }

    /** Starts every waiting scene whose two parties now have sims; drops those that waited too long. */
    private static void startPendingScenes(ClientLevel level) {
        PENDING_SCENES.removeIf(p -> {
            OwnerParty a = PARTIES.get(p.first), b = PARTIES.get(p.second);
            if (a == null || b == null || a.layer() == null || b.layer() == null) {
                if (++p.waited <= PENDING_SCENE_TICKS) return false;
                grill24.fishtastic.Fishtastic.LOGGER.info("[companion] scene {} dropped: party {} {}", p.type,
                        a == null || a.layer() == null ? p.first : p.second, a == null || b == null ? "not in view" : "not built");
                return true;
            }
            CrossScene.Kind kind = p.type == CompanionSceneType.G1_SAME_SPECIES ? CrossScene.Kind.RING : CrossScene.Kind.EIGHT;
            grill24.fishtastic.Fishtastic.LOGGER.info("[companion] scene {} started after {} client ticks; game time moved {}",
                    p.type, p.waited, level.getGameTime() - p.arrivedGameTime);
            startScene(a, kind, 0, p.firstSlot, p.startTime, p.second, p.secondSlot);
            startScene(b, kind, 1, p.secondSlot, p.startTime, p.first, p.firstSlot);
            scenesStarted++;
            return true;
        });
    }

    private static void startScene(OwnerParty party, CrossScene.Kind kind, int role, int slot, long start, UUID partner,
                                   int partnerSlot) {
        CrossScene scene = new CrossScene(kind, role);
        if (party.layer().crossScene(slot, scene) == null) {
            // D25: no free swimmer holds that slot here (the party changed since the server chose).
            grill24.fishtastic.Fishtastic.LOGGER.info("[companion] scene {} refused by slot {}", kind, slot + 1);
            return;
        }
        party.scene = scene;
        party.sceneStart = start;
        party.scenePartner = partner;
        party.scenePartnerSlot = partnerSlot;
        party.heartFired = false;
    }

    /**
     * Each scene's inputs for this tick, before the parties step: the shared clock, both owners'
     * feet in the packet's order, and the partner unit's leader as this client's sim has it. A
     * partner whose party is gone (logged out, out of view, another dimension) ends it (D30).
     */
    private static void feedScenes(ClientLevel level) {
        long now = level.getGameTime();
        for (OwnerParty party : PARTIES.values()) {
            CrossScene scene = party.scene;
            if (scene == null || party.layer() == null) continue;
            if (scene.endReason() != null) {
                if (party.owner() == Minecraft.getInstance().player) devLastSceneEnd = scene.kind + " " + scene.endReason();
                party.scene = null;
                continue;
            }
            OwnerParty other = PARTIES.get(party.scenePartner);
            if (other == null || other.layer() == null) {
                scene.cancel("partner gone");
                continue;
            }
            Player first = scene.role == 0 ? party.owner() : other.owner();
            Player second = scene.role == 0 ? other.owner() : party.owner();
            grill24.fishsim.companion.CompanionAgent partner = null;
            for (PartyLayer.Unit u : other.layer().units()) {
                for (int s : u.group().slots()) if (s == party.scenePartnerSlot) partner = u.leader();
            }
            scene.update((int) (now - party.sceneStart), feet(first), feet(second),
                    partner != null && partner.appear() >= 1f ? partner.position() : null);
            if (scene.role == 0 && scene.kind == CrossScene.Kind.RING && !party.heartFired && scene.ticks() >= HEART_TICK
                    && scene.active()) {
                party.heartFired = true;
                spawnSceneHeart(level, first.getEyePosition().add(second.getEyePosition()).scale(0.5));
            }
        }
    }

    private static grill24.fishsim.companion.Vec feet(Player p) {
        return new grill24.fishsim.companion.Vec(p.getX(), p.getY(), p.getZ());
    }

    /**
     * G1's bubble heart (spec §10, party §28.2): bubbles traced along the heart curve in this
     * client's camera plane between the two players, with one vanilla heart through the middle. Drawn once
     * per scene per client (by the first participant's side), and never with minimal particles.
     * The mod's own medium bubble, since the vanilla bubble pops at once out of water.
     */
    private static void spawnSceneHeart(ClientLevel level, Vec3 eyes) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.particles().get() == net.minecraft.client.ParticleStatus.MINIMAL) return;
        Vec3 centre = eyes.add(0, HEART_RISE, 0);
        net.minecraft.client.Camera camera = mc.gameRenderer.getMainCamera();
        org.joml.Vector3fc left = camera.getLeftVector(), upV = camera.getUpVector();
        Vec3 right = new Vec3(-left.x(), -left.y(), -left.z()), up = new Vec3(upV.x(), upV.y(), upV.z());
        List<net.minecraft.client.particle.Particle> bubbles = new ArrayList<>();
        for (int i = 0; i < 32; i++) {
            double t = i / 32.0 * Math.PI * 2;
            double hx = 16 * Math.pow(Math.sin(t), 3);
            double hy = 13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t);
            Vec3 p = centre.add(right.scale(hx * HEART_SCALE)).add(up.scale(hy * HEART_SCALE));
            // Medium bubble args: drift x, the height it pops at, drift z. Kept so the self-test can
            // check they live in air (vanilla's bubble removes itself on its first tick out of water).
            net.minecraft.client.particle.Particle bubble = mc.particleEngine.createParticle(
                    grill24.fishtastic.FishtasticParticleTypes.MEDIUM_BUBBLE.value(), p.x, p.y, p.z, 0, p.y + HEART_POP_RISE, 0);
            if (bubble != null) bubbles.add(bubble);
        }
        devHeartBubbles = bubbles;
        level.addParticle(net.minecraft.core.particles.ParticleTypes.HEART, centre.x, centre.y, centre.z, 0, 0.02, 0);
        heartsSpawned++;
    }

    /** Cross-player scenes started on this client, and bubble hearts drawn (the self-test reads them). */
    public static int scenesStarted, heartsSpawned;
    /** The last bubble heart's bubbles, for the self-test's "do they live in air" check. */
    public static List<net.minecraft.client.particle.Particle> devHeartBubbles = List.of();

    /** How the local party's last scene ended ("EIGHT done"), for the self-test; null before one ends. */
    @Nullable public static String devLastSceneEnd;

    /** The local party's cross-player scene, for the self-test: kind, role, clock, and why it ended (or null). */
    @Nullable
    public static String devLocalScene() {
        Player player = Minecraft.getInstance().player;
        OwnerParty party = player == null ? null : PARTIES.get(player.getUUID());
        if (party == null || party.scene == null) return null;
        CrossScene s = party.scene;
        PartyLayer.Unit u = party.layer() == null ? null : party.layer().crossUnit();
        return s.kind + " role " + s.role + " t" + s.ticks() + " " + (s.endReason() == null ? "on" : s.endReason())
                + (u == null ? "" : " leader " + u.leader().goal()
                + (u.leader().sceneLeft() == null ? "" : " left:" + u.leader().sceneLeft()));
    }

    /**
     * The local player's lead companion: the group holding slot 1 (the lead), or the first group
     * when the lead slot is empty.
     */
    @Nullable
    public static ClientCompanion local() {
        return inSlot(localCompanions(), CompanionParty.LEAD, true);
    }

    /** Every companion of the local player's party, in group order. */
    public static List<ClientCompanion> localCompanions() {
        Player player = Minecraft.getInstance().player;
        return player == null ? List.of() : companionsOf(player);
    }

    /** The local player's party layer, or null before it's built or without a party. */
    @Nullable
    static PartyLayer localLayer() {
        Player player = Minecraft.getInstance().player;
        OwnerParty party = player == null ? null : PARTIES.get(player.getUUID());
        return party == null ? null : party.layer();
    }

    /** Every tracked companion of any player — the perch layer's lookup by rendered entity. */
    public static List<ClientCompanion> companionsOf(Player player) {
        OwnerParty party = PARTIES.get(player.getUUID());
        return party == null ? List.of() : party.companions();
    }

    @Nullable
    static ClientCompanion inSlot(List<ClientCompanion> companions, int slot, boolean fallBack) {
        for (ClientCompanion c : companions) if (c.holdsSlot(slot)) return c;
        return fallBack && !companions.isEmpty() ? companions.get(0) : null;
    }

    /** How many companions the client is currently tracking (one per group), phantoms included. */
    public static int count() {
        int n = 0;
        for (OwnerParty party : PARTIES.values()) n += party.companions().size();
        for (OwnerParty party : PHANTOMS.values()) n += party.companions().size();
        return n;
    }

    /** How many fish bodies the client is currently tracking, phantoms included. */
    public static int bodyCount() {
        int n = 0;
        for (ClientCompanion c : all()) n += c.bodyCount();
        return n;
    }

    // ---------------------------------------------------------------- world pass

    /** Called from the platform's level submit hook; the stack is camera-relative. */
    public static void submitWorld(PoseStack poseStack, MultiBufferSource buffers, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        worldSubmitsThisFrame = 0;
        if (mc.level == null || count() == 0) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (OwnerParty party : PARTIES.values()) {
            for (ClientCompanion companion : party.companions()) {
                if (!shouldDraw(companion, firstPerson)) continue;
                if (companion.submitWorld(poseStack, buffers, cam, partialTick)) worldSubmitsThisFrame++;
                companion.submitNames(poseStack, buffers, cam, partialTick);
            }
        }
        if (FishtasticClientConfig.hideOtherCompanions()) return;
        for (OwnerParty phantom : PHANTOMS.values()) {
            for (ClientCompanion c : phantom.companions()) {
                if (c.submitWorld(poseStack, buffers, cam, partialTick)) worldSubmitsThisFrame++;
            }
        }
    }

    /**
     * Whether this companion should be drawn at all — false for another player's when companions
     * are hidden or that player is invisible, and for your own in first person when the setting
     * hides it.
     */
    private static boolean shouldDraw(ClientCompanion companion, boolean firstPerson) {
        Player owner = companion.owner();
        if (owner == null) return true;
        boolean local = owner == Minecraft.getInstance().player;
        if (!local) return !FishtasticClientConfig.hideOtherCompanions() && !owner.isInvisible();
        return !(firstPerson && FishtasticClientConfig.hideOwnCompanionFirstPerson());
    }

    /** True when the player layer should draw this companion perched on its owner's body. */
    public static boolean shouldDrawOnPerch(ClientCompanion companion) {
        Player owner = companion.owner();
        if (owner == null) return false;
        boolean local = owner == Minecraft.getInstance().player;
        if (!local && FishtasticClientConfig.hideOtherCompanions()) return false;
        if (owner.isInvisible()) return false;
        return companion.drawsOnPerch();
    }

}
