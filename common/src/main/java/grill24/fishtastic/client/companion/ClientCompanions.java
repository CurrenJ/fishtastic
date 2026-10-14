package grill24.fishtastic.client.companion;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishsim.companion.CompanionWorld;
import grill24.fishsim.companion.PartyGrouping;
import grill24.fishsim.companion.PartyLayer;
import grill24.fishsim.companion.PointOfInterest;
import grill24.fishsim.companion.Temperament;
import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.companion.CompanionFishingOutcome;
import grill24.fishtastic.companion.CompanionGroups;
import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionSceneType;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
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
import java.util.Locale;
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
 * <p>Fishing reactions (the dock on the minigame HUD) and pet-to-pet scenes are deferred to later
 * phases of the sim rewrite (plan §5): their packets still arrive here and are ignored for now, and
 * the dock's picture-in-picture renderer stays registered but parked.
 */
public final class ClientCompanions {
    private ClientCompanions() {}

    /**
     * One player's party as this client runs it: the synced party, one companion per group, and the
     * party layer that steps them together (built on the first tick, when there's a world to build
     * it in). A dev phantom is a party of one with no owner.
     */
    private static final class OwnerParty {
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

        /** Builds (first tick) or steps the party's sim; returns the time it took, ns. */
        long tick(ClientLevel level, CompanionWorldAdapter.LevelBlocks blocks, List<PointOfInterest> devPoints) {
            int n = companions.size();
            CompanionWorld[] worlds = new CompanionWorld[n];
            for (int i = 0; i < n; i++) {
                worlds[i] = companions.get(i).world(level, blocks, othersNear(this, companions.get(i)), devPoints);
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

    private static final Map<UUID, OwnerParty> PARTIES = new HashMap<>();
    /** Dev-only companions with no owner, round an imaginary still owner — a stand-in for another player's. */
    private static final Map<UUID, OwnerParty> PHANTOMS = new HashMap<>();
    @Nullable private static ClientLevel trackedLevel;
    @Nullable private static CompanionWorldAdapter.LevelBlocks blocks;
    /** Another companion counts as a point of interest within this range of an owner, blocks. */
    private static final double COMPANION_RANGE = 16.0;

    /** Dev: the temperament forced on the local party's companion holding each slot, or null for its species' own. */
    private static final Temperament[] temperamentOverrides = new Temperament[CompanionParty.SIZE];
    /** Dev: the party slot (0 = lead) the overlay and the dev triggers act on. */
    private static int devSlot = CompanionParty.LEAD;
    /** Dev: draw the debug overlay. */
    private static boolean overlay;

    /** Dev diagnostics: companion draws submitted by the most recent world pass (one frame's worth). */
    public static int worldSubmitsThisFrame;
    /** Dev diagnostics: perch-layer calls and the draws it made, cumulative. */
    public static int perchLayerCalls, perchSubmits;
    /** Dev diagnostics: the parked docked-fish renderer's run count (always 0 until fishing returns). */
    public static int pipRenders;

    // Cost of the sim, for the overlay (plan §7 risk 3): block samples and time per tick, averaged
    // over the last second, and the worst tick.
    private static long lastSamples, lastSampleNanos;
    private static final int COST_WINDOW = 20;
    private static final long[] costSamples = new long[COST_WINDOW], costSampleNanos = new long[COST_WINDOW],
            costStepNanos = new long[COST_WINDOW];
    private static int costIndex;
    /** Peaks since the last {@link #devResetCost}: block samples and whole-step time in one tick. */
    private static long peakSamples, peakStepNanos;

    // ---------------------------------------------------------------- tracking

    /** Client tick (20 Hz). */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != trackedLevel) {
            PARTIES.clear();
            PHANTOMS.clear();
            CompanionDevPoints.clear();
            trackedLevel = level;
            blocks = level == null ? null : new CompanionWorldAdapter.LevelBlocks(level);
            lastSamples = lastSampleNanos = 0;
        }
        if (level == null) return;

        Set<UUID> seen = new HashSet<>();
        for (Player player : level.players()) {
            CompanionParty party = CompanionStorage.party(player);
            if (party.isEmpty()) continue;
            UUID id = player.getUUID();
            seen.add(id);
            OwnerParty existing = PARTIES.get(id);
            if (existing == null || existing.owner() != player || !existing.party().matches(party)) {
                PARTIES.put(id, build(player, party, level));
            }
        }
        PARTIES.keySet().retainAll(seen);

        CompanionDevPoints.tick(level);
        List<PointOfInterest> devPoints = CompanionDevPoints.points();
        long step = 0;
        for (OwnerParty party : parties()) step += party.tick(level, blocks, devPoints);
        recordCost(step);
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
    private static List<ClientCompanion> all() {
        List<ClientCompanion> all = new ArrayList<>();
        for (OwnerParty party : parties()) all.addAll(party.companions());
        return all;
    }

    /**
     * Other parties' companions near {@code self}'s fish, as its points of interest (from this
     * client's own sims). Its own party-mates are left out: its party layer adds them.
     */
    private static List<PointOfInterest> othersNear(OwnerParty own, ClientCompanion self) {
        if (self.agent() == null || count() < 2) return List.of();
        List<PointOfInterest> out = new ArrayList<>();
        int i = 0;
        for (ClientCompanion other : all()) {
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

    /** Dev: restarts the cost peaks (the self-test's walk leg). */
    public static void devResetCost() {
        peakSamples = 0;
        peakStepNanos = 0;
    }

    /** A block changed on the client: every companion forgets the blocks it sampled (cheap; resampled lazily). */
    public static void onBlockChanged(BlockPos pos) {
        if (PARTIES.isEmpty() && PHANTOMS.isEmpty()) return;
        for (ClientCompanion companion : all()) companion.invalidateBlocks();
    }

    /** A chunk arrived: cells sampled while it was missing read as air, so forget them. */
    public static void onChunkLoaded(ChunkPos pos) {
        onBlockChanged(BlockPos.ZERO);
    }

    /** The fishing-event packet's landing point. Fishing reactions are deferred (plan §5); ignored for now. */
    public static void onFishingEvent(UUID owner, CompanionFishingOutcome outcome) {
    }

    /** The scene packet's landing point. Pet-to-pet scenes return in Phase 4 (plan §5); ignored for now. */
    public static void onScenePacket(CompanionSceneType scene, UUID first, int firstSlot, UUID second, int secondSlot,
                                     long startTime) {
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

    /** Every tracked companion of any player — the perch layer's lookup by rendered entity. */
    public static List<ClientCompanion> companionsOf(Player player) {
        OwnerParty party = PARTIES.get(player.getUUID());
        return party == null ? List.of() : party.companions();
    }

    /** The local companion the dev triggers and the overlay act on: the group holding {@link #devSlot}. */
    @Nullable
    private static ClientCompanion selected() {
        return inSlot(localCompanions(), devSlot, false);
    }

    @Nullable
    private static ClientCompanion inSlot(List<ClientCompanion> companions, int slot, boolean fallBack) {
        for (ClientCompanion c : companions) if (c.holdsSlot(slot)) return c;
        return fallBack && !companions.isEmpty() ? companions.get(0) : null;
    }

    /** How many companions the client is currently tracking (one per group), phantoms included. */
    public static int count() {
        int n = 0;
        for (OwnerParty party : parties()) n += party.companions().size();
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
    public static void submitWorld(PoseStack poseStack, SubmitNodeCollector nodes, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        worldSubmitsThisFrame = 0;
        if (mc.level == null || count() == 0) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (OwnerParty party : PARTIES.values()) {
            for (ClientCompanion companion : party.companions()) {
                if (!shouldDraw(companion, firstPerson)) continue;
                if (companion.submitWorld(poseStack, nodes, cam, partialTick)) worldSubmitsThisFrame++;
                companion.submitNames(poseStack, nodes, cam, partialTick);
            }
        }
        if (FishtasticClientConfig.hideOtherCompanions()) return;
        for (OwnerParty phantom : PHANTOMS.values()) {
            for (ClientCompanion c : phantom.companions()) {
                if (c.submitWorld(poseStack, nodes, cam, partialTick)) worldSubmitsThisFrame++;
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

    // ---------------------------------------------------------------- HUD pass

    /** Called every HUD frame after the minigame draws. Draws the dev debug overlay when it is on. */
    public static void renderHud(GuiGraphicsExtractor graphics, @Nullable FishingMinigameAnimation minigame, float partialTick) {
        if (!overlay) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        List<String> lines = new ArrayList<>(partyLines());
        ClientCompanion selected = selected();
        if (selected == null) lines.add("slot " + (devSlot + 1) + ": empty");
        else lines.addAll(selected.debugLines());
        lines.add(costLine());
        int y = 4;
        int width = 0;
        for (String line : lines) width = Math.max(width, font.width(line));
        graphics.fill(2, 2, 8 + width, 6 + lines.size() * 10, 0xA0000000);
        for (String line : lines) {
            graphics.text(font, line, 5, y, 0xFFE0E0E0);
            y += 10;
        }
    }

    /**
     * The overlay's party header (party spec Phase 3b): one line per unit, the selected one marked,
     * with each one's home perch, where it is and the hand turn; then the party's hand clock and
     * its lease refusals.
     */
    private static List<String> partyLines() {
        List<ClientCompanion> companions = localCompanions();
        if (companions.isEmpty()) return List.of("party: empty");
        List<String> lines = new ArrayList<>();
        lines.add("party (debug slot " + (devSlot + 1) + ")");
        for (ClientCompanion c : companions) lines.add((c.holdsSlot(devSlot) ? "> " : "  ") + c.partyStateLine());
        lines.add(partyClockLine(companions));
        return lines;
    }

    /** The party's hand clock (shared, or each percher's own) and its lease refusals, with their rate. */
    private static String partyClockLine(List<ClientCompanion> companions) {
        int refusals = 0, perchers = 0, sharedWait = -1;
        long ageTicks = 0;
        String turn = null;
        for (ClientCompanion c : companions) {
            ageTicks = Math.max(ageTicks, c.ageTicks);
            for (int i = 0; i < c.memberCount(); i++) {
                var m = c.agent() == null ? null : c.member(i);
                if (m == null || m.perchHome() == null) continue;
                perchers++;
                refusals += m.leaseRefusals();
                if (m.handClockShared()) {
                    sharedWait = m.handClockWait();
                    if (m.handClockReady()) turn = c.partyLine().substring(0, c.partyLine().indexOf(':'));
                }
            }
        }
        if (perchers == 0) return "  hand clock: none (no perchers)";
        String clock = sharedWait < 0 ? "each percher's own"
                : sharedWait > 0 ? "shared, next call in " + (sharedWait + 19) / 20 + " s"
                : "shared, ready" + (turn == null ? " (nobody's turn: someone is on or going to a hand)" : ", turn: " + turn);
        double minutes = Math.max(ageTicks, 1) / (20.0 * 60.0);
        return String.format(Locale.ROOT, "  hand clock: %s  lease refusals %d (%.1f/min)", clock, refusals,
                refusals / minutes);
    }

    /** The sim's cost over the last second: block samples, their time, and the whole step (plan §7 risk 3). */
    static String costLine() {
        long samples = 0, sampleNanos = 0, stepNanos = 0, worstStep = 0;
        for (int i = 0; i < COST_WINDOW; i++) {
            samples += costSamples[i];
            sampleNanos += costSampleNanos[i];
            stepNanos += costStepNanos[i];
            worstStep = Math.max(worstStep, costStepNanos[i]);
        }
        return String.format(Locale.ROOT, "%d companions (%d fish)  blocks %.0f/tick %.0f us/tick  step %.0f us/tick (worst %d us)"
                        + "  peak blocks %d/tick, step %d us",
                count(), bodyCount(), samples / (double) COST_WINDOW, sampleNanos / 1000.0 / COST_WINDOW,
                stepNanos / 1000.0 / COST_WINDOW, worstStep / 1000, peakSamples, peakStepNanos / 1000);
    }

    /** Called from {@link CompanionPipRenderer}: the docked fish is parked with the fishing reactions. */
    static void submitDockedFish(PoseStack poseStack, SubmitNodeCollector nodes, int light, float gameTime) {
    }

    /** The local player's running minigame, if any. */
    @Nullable
    public static FishingMinigameAnimation activeMinigame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return null;
        return ((IGameRendererExtension) mc.gameRenderer).fishtastic$getActiveAnimation() instanceof FishingMinigameAnimation m
                && m.isActive() ? m : null;
    }

    // ---------------------------------------------------------------- dev tooling

    /**
     * Dev-only: a companion with no owner, swimming round an imaginary owner standing still at
     * {@code eye} — a stand-in for another player's companion.
     */
    public static UUID addPhantom(ItemStack fish, Vec3 eye, float yaw) {
        Minecraft mc = Minecraft.getInstance();
        UUID id = UUID.randomUUID();
        if (mc.level != null) {
            PHANTOMS.put(id, new OwnerParty(null, null, List.of(ClientCompanion.phantom(eye, yaw, fish, mc.level))));
        }
        return id;
    }

    public static void clearPhantoms() {
        PHANTOMS.clear();
    }

    /** Dev-only: the ids of every dev phantom currently parked. */
    public static Set<UUID> devPhantomIds() {
        return new HashSet<>(PHANTOMS.keySet());
    }

    /** Dev-only (`/fishtastic companion phantom <species> [size]`): another companion, 4 blocks ahead. */
    public static String devAddPhantom(ItemStack fish) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 ahead = mc.player.getEyePosition().add(mc.player.getForward().multiply(1, 0, 1).normalize().scale(4));
        addPhantom(fish, ahead, mc.player.getYRot() + 180f);
        return "Phantom " + fish.getHoverName().getString() + " added 4 blocks ahead (an imaginary owner it swims round)";
    }

    /**
     * Dev-only (`/fishtastic companion debug slot <1-3>`): which party slot the overlay and the dev
     * triggers act on — the companion (solo or group) holding that slot.
     */
    public static String devSlot(int slot) {
        devSlot = slot;
        ClientCompanion c = selected();
        return "Companion debug slot " + (slot + 1) + (c == null ? " (empty)" : ": " + c.partyLine());
    }

    /** Dev-only: the party slot the dev triggers act on, 0-based. */
    public static int devSlot() {
        return devSlot;
    }

    /**
     * Dev-only (`/fishtastic companion temperament <id>|species`): rebuilds the selected companion
     * (the debug slot's) with a temperament of your choosing, or its species' own.
     */
    public static String devTemperament(@Nullable String id) {
        Temperament chosen = null;
        if (id != null) {
            Temperament t = Temperament.named(id);
            if (!t.name().equals(id.toLowerCase(Locale.ROOT)) && !id.equalsIgnoreCase("baseline")) {
                return "Unknown temperament " + id;
            }
            chosen = t;
        }
        // A group shares one mind: every slot it holds gets the override, so it survives regrouping.
        ClientCompanion c = selected();
        if (c != null) {
            for (int s : c.group().slots()) temperamentOverrides[s] = chosen;
        } else {
            temperamentOverrides[devSlot] = chosen;
        }
        Player player = Minecraft.getInstance().player;
        if (player != null) PARTIES.remove(player.getUUID()); // rebuilt next tick
        return "Companion temperament (slot " + (devSlot + 1) + "): " + (id == null ? "its species' own" : chosen.name());
    }

    /** Dev-only: every temperament id the command accepts. */
    public static List<String> devTemperamentIds() {
        List<String> ids = new ArrayList<>();
        ids.add("baseline");
        for (Temperament t : Temperament.ALL) ids.add(t.name());
        return ids;
    }

    /** Dev-only (`/fishtastic companion fake hostile|cat|item|companion|clear`): seen by the whole local party. */
    public static String devFake(String kind) {
        if (kind.equals("clear")) {
            CompanionDevPoints.clear();
            return "Fake points cleared";
        }
        ClientCompanion local = selected();
        Player player = Minecraft.getInstance().player;
        if (local == null || player == null) return "No companion in debug slot " + (devSlot + 1) + " — bond one first";
        CompanionDevPoints.Kind k = CompanionDevPoints.Kind.valueOf(kind.toUpperCase(Locale.ROOT));
        return CompanionDevPoints.add(k, new CompanionWorldAdapter.OwnerTracker().owner(player, true));
    }

    /** Dev-only (`/fishtastic companion hurt`): the selected companion sees its owner hurt next tick. */
    public static String devHurt() {
        ClientCompanion local = selected();
        if (local == null) return noSelected();
        local.devHurt();
        return "Owner hurt (for the slot " + (devSlot + 1) + " companion only)";
    }

    /** Dev-only (`/fishtastic companion drowsy`): as if you'd stood still long enough for it to settle. */
    public static String devDrowsy() {
        ClientCompanion local = selected();
        if (local == null) return noSelected();
        local.devDrowsy();
        return "Companion drowsy — stand still and it settles";
    }

    /** Dev-only (`/fishtastic companion ground`): a ground benthic (and its group) back down off the perch. */
    public static String devGround() {
        ClientCompanion local = selected();
        if (local == null) return noSelected();
        return local.devGround() ? "Companion back down to the ground" : "That companion doesn't walk on the ground";
    }

    /** Dev-only (`/fishtastic companion climb`): a ground benthic (and its group, in turn) climbs onto the perch. */
    public static String devClimb() {
        ClientCompanion local = selected();
        if (local == null) return noSelected();
        return local.devClimb() ? "Companion climbing on — stand still" : "That companion doesn't walk on the ground";
    }

    /** Dev-only (`/fishtastic companion tumble`): a perched ground benthic is knocked off (waits until attached). */
    public static String devTumble() {
        ClientCompanion local = selected();
        if (local == null) return noSelected();
        return local.devTumble() ? "Companion tumbles off once it's perched and out of cooldown"
                : "That companion doesn't walk on the ground";
    }

    private static String noSelected() {
        return "No companion in debug slot " + (devSlot + 1);
    }

    /** Dev-only (`/fishtastic companion debug on|off`): the debug overlay. */
    public static String devOverlay(boolean on) {
        overlay = on;
        return "Companion debug overlay " + (on ? "on" : "off");
    }

    /**
     * Dev-only (`/fishtastic companion catchdemo`): opens a demo bar whose one fish target is a
     * member of the local lead companion's own species, flagged undiscovered. Lives here rather than
     * in the command because the command class must never use {@code Minecraft.player} — see the
     * class note on {@code CompanionCommand}.
     */
    public static String devOpenCatchDemo() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "No client player";
        ItemStack companion = CompanionStorage.lead(mc.player);
        if (companion.isEmpty()) return "No lead companion — bond one first (or /fishtastic companion on)";
        ItemStack reward = companion.copyWithCount(1);
        FishingMinigameAnimation animation = new FishingMinigameAnimation();
        // The bare constructor stages three example TREASURE targets; clear them first, exactly as
        // the start-packet handler and the self-test scenes do.
        animation.getMinigameState().getTargets().clear();
        grill24.fishtastic.util.FishingTarget target = new grill24.fishtastic.util.FishingTarget(
                java.util.List.of(reward), grill24.fishtastic.util.FishingTarget.TargetCategory.FISH,
                new java.util.Random(7L), 0.5f, 0.2f, java.util.List.of());
        target.setNoCatchDrain(true);
        animation.getMinigameState().addTarget(target);
        animation.setUndiscoveredSpecies(java.util.Set.of(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(reward.getItem())));
        ((IGameRendererExtension) mc.gameRenderer).fishtastic$displayItemActivation(() -> animation);
        return "Demo catch open — /fishtastic companion catch to land it";
    }

    /** Dev-only (`/fishtastic companion catch`): lands the first active target through the real catch path. */
    public static String devForceCatch() {
        FishingMinigameAnimation animation = activeMinigame();
        if (animation == null) return "No minigame open — /fishtastic companion catchdemo first";
        var targets = animation.getMinigameState().getTargets();
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).getState() == grill24.fishtastic.util.FishingTarget.TargetState.ACTIVE) {
                animation.devCatchTarget(i);
                return "Caught target " + i;
            }
        }
        return "No active target to catch";
    }

    /** Dev diagnostics for the local player's party: every group, then the selected one in detail. */
    public static String debugState() {
        ClientCompanion local = selected();
        return "tracked=" + count() + " bodies=" + bodyCount() + " perchLayer=" + perchLayerCalls + "/" + perchSubmits
                + " | " + String.join(" | ", partyLines())
                + " | " + (local == null ? "no companion in debug slot " + (devSlot + 1) : String.join(" | ", local.debugLines()))
                + " | " + costLine();
    }
}
