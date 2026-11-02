package grill24.fishtastic.client.ambient;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.FishtasticRegistries;
import grill24.fishsim.ambient.WaterColumn;
import grill24.fishsim.ambient.WaterDiscovery;
import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.WaterDomain;
import grill24.fishtastic.ecology.AmbientFluid;
import grill24.fishtastic.ecology.AmbientRoster;
import grill24.fishtastic.ecology.EcologyClient;
import grill24.fishtastic.ecology.NaturalPool;
import grill24.fishtastic.client.renderer.FishVisuals;
import grill24.fishtastic.client.renderer.GroupFishDraw;
import grill24.fishtastic.client.renderer.GroupFishPose;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.util.Ids;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import grill24.fishtastic.server.FishingPoolResolver;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.joml.Matrix4f;

/**
 * Bounded, world-anchored water around the player, populated by the seeded shared roster (world
 * fish exploration E6, E12, E13): no server request, no held-rod preview, no stored fish.
 *
 * <p>Client settings arm the natural runtime in normal play. The development harness retains an
 * explicit full-density seam ({@link ClientAmbientFish#startNatural}).
 *
 * <p><b>Roster.</b> {@link AmbientChunkField} scans the ambient fluid of every loaded chunk around
 * the player; {@link AmbientRoster} turns the chunk's usable volume and the game time into the fish
 * present (seeded slots with staggered lifespans, IDs shared by every client). A fish whose home
 * cell lies in a shown patch is drawn in that patch's domain, if the density setting draws it (a
 * stable subset by ID rank). The species comes from {@link NaturalPool} under the conditions at the
 * fish's arrival, vanilla fish excluded, and is remembered per ID so a later weather change does not
 * swap a living fish.
 *
 * <p><b>Structure.</b> {@link WaterDiscovery} finds loaded fluid and keeps each body of it under a
 * stable patch key; this class owns one {@link WaterDomain} and one {@link FlockEngine} per shown
 * patch (the tank-sim motion, E12: each client moves its own copy of a fish, leashed to the fish's
 * home chunk) and retires individuals whose water moves out from under them. Every tick's work is
 * budgeted: discovery has its own cell and column budgets, at most one chunk is scanned and one
 * domain rebuilt per tick, and at most {@link #membershipChangesPerTick} individuals are added or
 * removed per tick.
 *
 * <p><b>Frames.</b> A patch's origin is chosen by {@link WaterDiscovery} when the patch is first
 * found and never moves, and each shown patch keeps its own {@link DomainState} keyed by the patch
 * key, so a domain's local frame is world-fixed for as long as it lives. That is what makes the
 * carry in {@link FlockEngine#rebuildPreserving} a pure world-position carry: nothing has to be
 * rebased, and no fish ever turns because the player moved. Discovery's own boundary is handled by
 * the domain's open faces, not by the frame: a fish near an open face fades out (see
 * {@link #FADE_TICKS}) over the margin, so it is never pinned against an invisible wall.
 */
public final class AmbientFishRuntime {

    /**
     * Fallback size for a species with no profile, in cm — the Phase 5 pool is whatever the server's
     * authoritative context names, so there is no authored table to read a representative size out
     * of. Every shipped species has a profile; this only covers an item that somehow does not.
     */
    public static final float DEFAULT_SIZE_CM = 30f;

    /** Patches shown at once (nearest to the player). */
    public static final int MAX_DOMAINS = 4;
    /** Individuals alive across every domain; the density setting also caps this ({@link AmbientBudget#individuals}). */
    public static final int MAX_INDIVIDUALS = AmbientBudget.MAX_INDIVIDUALS;
    /** A patch smaller than this is a puddle, not somewhere to put a shoal. */
    public static final int MIN_PATCH_COLUMNS = 4;
    /** How often a live domain is rebuilt from the current columns (geometry edits, fluid edits). */
    public static final int REBUILD_INTERVAL_TICKS = 20;
    /**
     * A domain whose water only grew (discovery found more of it) is rebuilt at most this often.
     * While the player travels, discovery adds columns at the leading edge every tick, and a rebuild
     * per tick allocated a fresh grid and column list every tick (~580 KB a tick at radius 32,
     * ambientswim). New water joins at most this late: ~1.4 blocks at a 5.6 b/s swim.
     */
    public static final int GROWTH_REBUILD_TICKS = 5;
    /**
     * How long a revived domain may hold its rebuild while it waits for the patch's cache to refill
     * to the size it was last built at. Rediscovery is budgeted, so a large patch takes tens of
     * ticks to come back; the bound is what stops a patch that really did come back smaller from
     * living on as a stale domain forever.
     */
    public static final int REFILL_TIMEOUT_TICKS = 200;
    /**
     * Fewest membership changes (spawns, removals) allowed per tick; the budget grows with the cap
     * ({@link #membershipChangesPerTick}). At a fixed 2, a 10 b/s swim spent ~25% of its ticks
     * with spawns waiting on this limit (ambientswim).
     */
    public static final int MIN_MEMBERSHIP_CHANGES_PER_TICK = 2;
    /** The cap is divided by this to give the per-tick membership budget. */
    public static final int MEMBERSHIP_CAP_DIVISOR = 8;
    /** Fade length of a retiring individual, ticks at 20 tps (~1.2 s). */
    public static final int FADE_TICKS = 24;
    /** After a fish cannot be placed because nothing fits, wait this long before trying it again. */
    public static final int OMISSION_RETRY_TICKS = 20;
    /** Minimum gap between a new individual and a resident one. */
    public static final float SPAWN_SEPARATION = 0.5f;
    /** Readable surface population, rather than eight fish scattered through a deep ocean band. */
    public static final float DISPLAY_DEPTH = 4f;
    private static final int SPAWN_ATTEMPTS = 8;
    private static final float ROTATION_JITTER = 20f;

    /** The seeded roster's slot and lifespan model. */
    public static final AmbientRoster.Params ROSTER = AmbientRoster.DEFAULT;
    /** Chunks scanned around the player beyond the discovery radius, so a border chunk is known. */
    public static final int CHUNK_MARGIN = 1;
    /** A fish whose lifespan began this recently arrives from the fluid edge; older ones are already there. */
    public static final int ARRIVAL_WINDOW_TICKS = 120;
    /** Fade-in of an arriving fish, ticks. */
    public static final int ARRIVAL_FADE_TICKS = 40;
    /**
     * Fade-in of every other new fish, ticks. A fish already living in the water when the player
     * comes into range used to appear at full size; it now grows in like an arrival, quicker.
     */
    public static final int SPAWN_FADE_TICKS = 30;
    /** A fish whose spawn spot is in view and nearer than this waits for the player to look away. */
    public static final float NEAR_VIEW_SPAWN_DISTANCE = 12f;
    /** The longest a fish waits for that, ticks; after it, it fades in where it is seen. */
    public static final int MAX_SPAWN_DEFER_TICKS = 60;
    /**
     * How long a domain whose water merged into another body waits for the survivor's domain to be
     * built before giving up and fading its residents as a lost domain (the old behaviour), ticks.
     */
    public static final int MERGE_TIMEOUT_TICKS = 100;
    /** How often a deferred fish's spot is looked at again, ticks. */
    private static final int DEFER_RETRY_TICKS = 5;
    /** A fish further than this many blocks outside its home chunk's footprint is out of its leash. */
    public static final float LEASH_MARGIN = 8f;
    /** Re-derivation of the roster at most this often when nothing changed it (ticks). */
    private static final int PRUNE_INTERVAL_TICKS = 200;

    /** Why an individual is fading out. */
    public static final int REASON_OPEN_FACE = 0;
    public static final int REASON_INVALID = 1;
    public static final int REASON_DOMAIN_LOST = 2;
    /** The density setting no longer draws it, or its slot disappeared; fades over {@code POPULATION_FADE_TICKS}. */
    public static final int REASON_EXCLUDED = 3;
    /** Its seeded lifespan ended; fades over {@code POPULATION_FADE_TICKS}. */
    public static final int REASON_EXPIRED = 4;
    /** It swam beyond its leash to the home chunk; it re-enters at its home cell. */
    public static final int REASON_LEASH = 5;
    /**
     * Its slot went to a nearer waiting fish: the cap is full, it is out of view and clearly further
     * from the player than the nearest fish the roster wants drawn. Fades over
     * {@code POPULATION_FADE_TICKS}; still on the roster, so it can come back when the player does.
     */
    public static final int REASON_DISPLACED = 6;
    /** A resident must be this many blocks further than the waiting fish before it gives up its slot. */
    public static final float DISPLACE_HYSTERESIS = 4f;
    /** Extra degrees around the view frustum within which a fish counts as seen (never displaced). */
    public static final float VIEW_MARGIN_DEGREES = 15f;

    /** One spawned individual: its engine index, its spec, and its retirement state. */
    static final class Member {
        /** The seeded ID every client derives for this fish (hash of seed, chunk, slot, lifespan index). */
        final long id;
        final String species;
        final FishSpec spec;
        final WaterDomain.BodyBox body;
        final ItemStack stack;
        final FishAnimationConfig anim;
        final long bornTick;
        /** Ticks from spawn to the end of the seeded lifespan. */
        final int lifetimeTicks;
        final AmbientRoster.Lifespan lifespan;
        final boolean arriving;
        final ItemStackRenderState state = new ItemStackRenderState();
        Matrix4f retirementPose;
        int engineIndex = -1;
        long retireTick = Long.MIN_VALUE;
        int retireReason = -1;
        int fadeTicks = FADE_TICKS;
        float fade = 1f;

        Member(AmbientRoster.Lifespan lifespan, String species, FishSpec spec, WaterDomain.BodyBox body, ItemStack stack,
               FishAnimationConfig anim, long bornTick, boolean arriving) {
            this.lifespan = lifespan;
            this.id = lifespan.fishId();
            this.species = species;
            this.spec = spec;
            this.body = body;
            this.stack = stack;
            this.anim = anim;
            this.bornTick = bornTick;
            this.lifetimeTicks = (int) Math.max(1, Math.min(Integer.MAX_VALUE, lifespan.departureTick() - bornTick));
            this.arriving = arriving;
            fade = 0f; // every new fish grows in: an arrival over ARRIVAL_FADE_TICKS, others SPAWN_FADE_TICKS
        }

        boolean retiring() {
            return retireTick != Long.MIN_VALUE;
        }
    }

    /**
     * Everything needed to show one species, resolved once per item id and cached: the stack at its
     * profile-driven size, the descriptor, the animation and the whole-body box. {@code null} from
     * {@link #visualFor} means the species is unrepresentable — it is recorded and never shown,
     * never shrunk or demoted into something the context did not offer.
     */
    private record SpeciesVisual(String species, Identifier id, ItemStack stack, FishSpec spec,
                                 WaterDomain.BodyBox body, FishAnimationConfig anim) {
    }

    /** One shown patch: its fixed frame, its water, its engine, and the individuals in it. */
    static final class DomainState {
        final long patchKey;
        final FlockEngine engine = new FlockEngine(Tunables.GROUP);
        final List<Member> members = new ArrayList<>();
        WaterDiscovery.Patch patch;
        WaterDomain domain;
        int builtColumns = -1;
        long builtTick = Long.MIN_VALUE;
        long buildNanos;
        boolean dying;
        /** A revived domain waiting for its patch's cache to refill to {@link #builtColumns}. */
        boolean refilling;
        long refillSince;
        /** Head-room the domain must reserve for the tallest species drawn in it, or zero (only ever grows). */
        float topMargin;
        /** The margin the current domain was built with; a mismatch marks it stale. */
        float builtTopMargin = Float.NaN;
        /** The patch version ({@link WaterDiscovery#patchVersion}) the current domain was built from. */
        long builtVersion = Long.MIN_VALUE;
        /** The player block the current domain's open faces were judged from. */
        int builtPx, builtPy, builtPz;
        /**
         * The surviving key when this domain's water was found to be part of a bigger body (a patch
         * merge), else 0. The domain keeps simulating its residents in the water it already has
         * until the survivor's domain is built, then hands them over ({@code transferMerged}).
         */
        long mergedInto;
        long mergedAt;
        double waterVolume;

        DomainState(WaterDiscovery.Patch patch) {
            this.patch = patch;
            this.patchKey = patch.key();
        }

        int originX() { return patch.originX(); }
        int originY() { return patch.originY(); }
        int originZ() { return patch.originZ(); }
    }

    /** Totals and peaks the harness asserts on. */
    public static final class Stats {
        public int domains;
        public int individuals;
        public int active;
        public int retiring;
        public long spawns;
        public long omissions;
        /** Retirement reasons, counted per resident so they add up to {@link #retirements}. */
        public long retireOpenFace;
        public long retireInvalid;
        public long retireDomainLost;
        /** Residents retired because their species left the authoritative pool (Phase 5). */
        public long retireExcluded;
        /** Residents retired because their own bounded lifetime ran out (Phase 5). */
        public long retireExpired;
        /** Domains that went away with residents in them (one domain can retire several residents). */
        public long domainsLost;
        /** Dying domains that became viable again before their residents were gone. */
        public long revivals;
        /** Rebuilds refused because the patch's grid was larger than a domain may allocate. */
        public long buildRejections;
        /** Carried residents a rebuild could no longer host (gated out, or an unusable position). */
        public long unhostable;
        public long retirements;
        public long rebuilds;
        public long domainBuilds;
        public double peakBuildMillis;
        public double totalBuildMillis;
        public int peakDiscoveryCells;
        public int peakDiscoveryColumns;
        public long discoveryCells;
        public long discoveryColumns;
        public long unloadedScans;
        public long truncatedColumns;
        public long columnsChanged;
        public long columnsEvicted;
        public long patchMerges;
        /** Revalidations deferred because the scan band no longer covers the whole column. */
        public long revalidationsDeferred;
        public double minSurfaceClearance = Double.MAX_VALUE;
        public double minBedClearance = Double.MAX_VALUE;
        public double minOpenFaceDistance = Double.MAX_VALUE;
        public long outsideWater;
        /** Carried centres invalidated by changed geometry, retired before the next step. */
        public long rebuildInvalidatedCenters;
        /** Post-placement verification, only populated by the invariant harness. */
        public long spawnFitChecks, spawnFitFailures;
        public long bodyFitsFailures;
        public long steps;
        public long worldReads;
        public long ticks;
        public double totalTickMillis, peakTickMillis;
        public long renderFrames;
        public double totalSubmitMillis, peakSubmitMillis;
        // ── Seeded roster ──────────────────────────────────────────────────────
        /** Fish present in the scanned chunks at the last roster pass (the shared roster, all clients). */
        public int rosterPresent;
        /** …of which the density setting draws. */
        public int rosterDrawn;
        /** …of which the density setting hides (a stable subset by ID rank). */
        public int rosterHiddenDensity;
        /** …drawn by density but with no shown patch around their home cell yet. */
        public int rosterNoDomain;
        /** …drawn and in a shown patch but not alive: waiting on the cap, a retry or a fit. */
        public int rosterPending;
        /** Species draws made (one per fish ID, remembered). */
        public long speciesDraws;
        /** Fish a pass could not place (no body fit near the home cell), counted per attempt. */
        public long unplaceable;
        /** Fish that swam beyond their leash and re-entered. */
        public long retireLeash;
        /** Out-of-view residents that gave their slot to a nearer waiting fish. */
        public long retireDisplaced;
        /** Chunk scans done. */
        public long chunkScans;
        /** Time spent in water discovery, total. */
        public long discoveryNanos;
        /** Patch merges that reached a domain with residents; residents carried across; residents faded instead. */
        public long merges, mergeTransfers, mergeFallbacks;
        /** Bytes the ambient tick allocated (self-test only: measured while invariant stats are on). */
        public long allocBytes;
        /** The same, by tick section (see {@code allocMark}). */
        public final long[] allocBySection = new long[7];
        /** Spawn attempts held back because the spot was in view and near; and fish that waited out the limit. */
        public long deferrals, deferralsExpired;

        public double meanBuildMillis() {
            return domainBuilds == 0 ? 0d : totalBuildMillis / domainBuilds;
        }
    }

    final WaterDiscovery discovery = new WaterDiscovery();
    private final AmbientWaterSource source = new AmbientWaterSource();
    final Map<Long, DomainState> domains = new LinkedHashMap<>();
    final Stats stats = new Stats();
    private final Random poseRandom = new Random(7L);
    /** Resolved species visuals, by {@code namespace:path} — built lazily from the roster's ids. */
    private final Map<String, SpeciesVisual> visuals = new LinkedHashMap<>();
    /** Species ids that resolved to nothing drawable; never re-attempted within a session. */
    private final java.util.Set<String> unrepresentable = new java.util.LinkedHashSet<>();
    /** The chunk fluid scans the roster is derived from. */
    final AmbientChunkField field = new AmbientChunkField();
    /** Species drawn per fish ID, kept for the fish's lifespan so a weather change cannot swap a living fish. */
    private final Map<Long, String> speciesById = new java.util.HashMap<>();
    /** Pools resolved per environment, so the 100th fish at dawn in a river costs a lookup. */
    private final Map<FishingPoolResolver.EnvironmentContext, VisualSampler> samplers = new java.util.HashMap<>();
    /** Fish IDs that could not be placed, until when. */
    private final Map<Long, Long> retryAfter = new java.util.HashMap<>();
    /** Recent merges, loser key to the key that absorbed it, for the harness (bounded). */
    final Map<Long, Long> mergeHistory = new LinkedHashMap<>();
    /** Fish IDs held back because their spot was in view and near, since when. */
    private final Map<Long, Long> deferredSince = new java.util.HashMap<>();
    /** The latest roster pass, for the debug view and the harness. */
    final List<RosterFish> roster = new ArrayList<>();
    private long lastPrune = Long.MIN_VALUE;
    private long seed;
    private boolean seeded;
    private AmbientFluid fluid = AmbientFluid.WATER;

    /** Why a rostered fish is not shown. */
    public enum Status { ALIVE, HIDDEN_DENSITY, NO_DOMAIN, PENDING, UNPLACEABLE, NO_SPECIES }

    /**
     * One fish of the shared roster at a pass: its ID, species, lifespan and where it stands. The
     * debug view lists these; {@code status} is the drawn/hidden split.
     */
    public record RosterFish(long id, int chunkX, int chunkZ, int slot, String species, double phase,
                             long arrivalTick, long departureTick, int homeX, int homeZ, long patchKey,
                             Status status) {}
    private final List<Member> scratchMembers = new ArrayList<>();
    private float[] depthScratch = new float[0];
    private int[] orderScratch = new int[0];
    private ClientLevel level;
    long lastTick = Long.MIN_VALUE;
    boolean armed;
    AmbientBudget budget = AmbientBudget.forDensity(100);
    int worldSubmitsThisFrame;
    /** Why this tick's spawn attempt did nothing: diagnosis for the debug line. */
    String lastSpawnSkip = "not-ticked";

    // ------------------------------------------------------------------ lifecycle

    /** Arms discovery for a client level. The caller checks the development-environment gate. */
    public boolean start(ClientLevel level) {
        return start(level, AmbientBudget.forDensity(100));
    }

    public boolean start(ClientLevel level, AmbientBudget budget) {
        if (level == null) return false;
        clear();
        this.level = level;
        source.setLevel(level);
        fluid = source.fluid();
        field.reset(fluid);
        configure(budget);
        armed = true;
        return true;
    }

    public AmbientBudget budget() { return budget; }

    public void configure(AmbientBudget budget) {
        if (this.budget.equals(budget) && armed) return;
        this.budget = budget;
        discovery.configure(budget.radius(), WaterDiscovery.DEFAULT_Y_ABOVE,
                WaterDiscovery.DEFAULT_Y_BELOW, budget.columnsPerTick(), budget.cellsPerTick());
        // The cache must hold the whole kept window (radius plus eviction slack), or a wide radius
        // trims columns the sweep then finds again, forever.
        int side = 2 * (budget.radius() + WaterDiscovery.DEFAULT_EVICT_SLACK) + 1;
        discovery.setMaxColumns(Math.max(WaterDiscovery.DEFAULT_MAX_COLUMNS, side * side));
    }

    public void clear() {
        patchDistances = Map.of();
        armed = false;
        domains.clear();
        discovery.reset();
        source.setLevel(null);
        source.resetCounters();
        level = null;
        lastTick = Long.MIN_VALUE;
        worldSubmitsThisFrame = 0;
        visuals.clear();
        unrepresentable.clear();
        field.reset(AmbientFluid.WATER);
        speciesById.clear();
        samplers.clear();
        retryAfter.clear();
        deferredSince.clear();
        mergeHistory.clear();
        roster.clear();
        lastPrune = Long.MIN_VALUE;
        seeded = false;
        scratchMembers.clear();
        depthScratch = new float[0];
        orderScratch = new int[0];
        stats.domains = stats.individuals = stats.active = stats.retiring = 0;
    }

    public boolean armed() {
        return armed;
    }

    public Stats stats() {
        return stats;
    }

    private final AmbientDiagnostics diagnostics = new AmbientDiagnostics(this);

    /** The harness's read-only view of the population (individuals, domains, pools, the debug line). */
    public AmbientDiagnostics diagnostics() {
        return diagnostics;
    }

    public WaterDiscovery discovery() {
        return discovery;
    }

    public AmbientWaterSource waterSource() {
        return source;
    }

    public int submitsThisFrame() {
        return worldSubmitsThisFrame;
    }

    /** Why the last tick's spawn attempt did nothing (diagnosis only). */
    public String lastSpawnSkip() {
        return lastSpawnSkip;
    }

    /** The chunk fluid scans the roster is derived from (diagnosis only). */
    public AmbientChunkField chunkField() {
        return field;
    }

    public int domainCount() {
        return domains.size();
    }

    public long steps() {
        return stats.steps;
    }

    /** Whether {@code tick} is a real timestamp within {@code window} of {@code now}. */
    static boolean recent(long tick, long now, long window) {
        // A "never" marker is Long.MIN_VALUE, and `now - Long.MIN_VALUE` overflows back to a
        // negative number, so every such comparison has to test the marker before subtracting.
        return tick != Long.MIN_VALUE && now - tick < window;
    }

    /** Notes why a spawn attempt did nothing, for {@link #debugState()}; returns zero, always. */
    private int spawnSkip(String why) {
        lastSpawnSkip = why;
        return 0;
    }

    // ------------------------------------------------------------------ pool

    /**
     * Resolves one species of an authoritative context into everything needed to show it: stack at
     * its profile-driven size, descriptor, animation and the full animated body. Resolved once per
     * id and cached; {@code null} means the species cannot be represented at all — no registered
     * item, or a locomotion the animation does not agree with — and the caller records the omission
     * rather than substituting something.
     */
    @Nullable
    private SpeciesVisual visualFor(Identifier id, ClientLevel level) {
        String species = id.toString();
        SpeciesVisual cached = visuals.get(species);
        if (cached != null) return cached;
        if (unrepresentable.contains(species)) return null;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        if (item == null || item == Items.AIR) {
            unrepresentable.add(species);
            return null;
        }
        ItemStack stack = new ItemStack(item);
        ItemSizeHelper.setSize(stack, ambientSizeCm(item, level, DEFAULT_SIZE_CM));
        FishSpec spec = FishVisuals.descriptor(stack, level, false);
        FishAnimationConfig anim = FishVisuals.resolve(stack, level).animation();
        if (spec.locomotion() != FishVisuals.locomotionOf(anim)) {
            // A species whose animation disagrees with its declared motion would be a hover
            // specimen; the Phase 2 rule holds — omit it, never demote it.
            unrepresentable.add(species);
            return null;
        }
        SpeciesVisual visual = new SpeciesVisual(species, id, stack, spec,
                bodyFor(spec.locomotion(), spec.length()), anim);
        visuals.put(species, visual);
        return visual;
    }

    /** Profile-driven mean size in cm — the same size the item renders at. */
    private static float ambientSizeCm(Item item, ClientLevel level, float fallback) {
        var key = BuiltInRegistries.ITEM.getResourceKey(item);
        if (key.isEmpty()) return fallback;
        ResourceKey<FishProfile> profileKey = ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY,
                key.get().identifier());
        return level.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                .getOptional(profileKey).map(p -> p.size().mean()).orElse(fallback);
    }

    /** The volume one individual's animated body can occupy: {@link WaterDomain.BodyBox#of}. */
    public static WaterDomain.BodyBox bodyFor(Locomotion loco, float length) {
        return WaterDomain.BodyBox.of(loco, length);
    }

    // ------------------------------------------------------------------ per-tick

    /** One client tick: discover, adopt, rebuild (at most one domain), spawn or retire, step. */
    public void tick(Minecraft mc) {
        if (!armed) return;
        ClientLevel lvl = mc.level;
        if (lvl == null) {
            clear();
            return;
        }
        if (lvl != level) {
            start(lvl, budget);
        }
        if (mc.isPaused() || !lvl.tickRateManager().runsNormally()) return;
        long time = lvl.getGameTime();
        if (time == lastTick) return;
        lastTick = time;
        long tickStarted = System.nanoTime();
        long allocStarted = invariantStats ? allocatedBytes() : 0L;
        allocCursor = allocStarted;
        lastSpawnSkip = "no-spawn";
        if (mc.player == null) return;
        BlockPos player = mc.player.blockPosition();
        int px = player.getX(), py = player.getY(), pz = player.getZ();

        // Discovery's own tick carries the near pass, the budgeted revalidation of known columns —
        // the only path that ever notices an edit inside already-cached water — and the ring sweep,
        // all inside one shared cell budget (see WaterDiscovery.revalidatePass for the split).
        long discoveryStarted = System.nanoTime();
        discovery.tick(source, px, py, pz, time);
        stats.discoveryNanos += System.nanoTime() - discoveryStarted;
        allocMark(0);
        syncDomains(time, px, py, pz);
        allocMark(1);
        deriveRoster(lvl, time, px, py, pz);
        allocMark(2);
        int changes = reconcileRoster(time, px, py, pz);
        retireAtFaces(time);
        updateFades(time);
        changes += retireFinished(time);
        allocMark(3);
        spawnRoster(time, px, py, pz, changes);
        allocMark(4);
        stepEngines();
        allocMark(5);
        enforceLeash(time);
        dropDeadDomains(time);
        recordStats();
        allocMark(6);
        double millis = (System.nanoTime() - tickStarted) / 1_000_000.0;
        if (invariantStats) stats.allocBytes += allocatedBytes() - allocStarted;
        stats.ticks++;
        stats.totalTickMillis += millis;
        stats.peakTickMillis = Math.max(stats.peakTickMillis, millis);
    }

    // ------------------------------------------------------------------ the seeded roster

    /** One fish of the roster at this pass, with what the runtime knows about showing it. */
    private record Plan(AmbientRoster.Lifespan lifespan, AmbientChunkField.Scan scan, int column, String species,
                        long patchKey, boolean drawn) {
        int homeX() { return scan.blockX(column); }
        int homeZ() { return scan.blockZ(column); }
    }

    private final List<Plan> plans = new ArrayList<>();

    /**
     * Derives the fish present now in every scanned chunk: the pure part is {@link AmbientRoster}; this
     * adds the chunk scans, the home cell's patch, the species and the density draw. No random number
     * is consumed, so two clients holding the same seed and water derive the same list.
     */
    private void deriveRoster(ClientLevel lvl, long time, int px, int py, int pz) {
        plans.clear();
        stats.rosterPresent = stats.rosterDrawn = stats.rosterHiddenDensity = stats.rosterNoDomain = 0;
        String dimension = lvl.dimension().identifier().toString();
        Long held = EcologyClient.seedFor(dimension);
        seeded = held != null;
        if (!seeded) return;
        seed = held;
        int radius = Math.max(1, (discovery.radius() + 15) / 16 + CHUNK_MARGIN);
        field.refresh(lvl, px >> 4, pz >> 4, radius, time);
        stats.chunkScans = field.scansDone();
        if (lastPrune == Long.MIN_VALUE || time - lastPrune >= PRUNE_INTERVAL_TICKS) {
            if (lastPrune != Long.MIN_VALUE) pruneMemos();
            lastPrune = time;
        }
        int density = budget.density();
        for (AmbientChunkField.Scan scan : field.all()) {
            if (Math.abs(scan.chunkX() - (px >> 4)) > radius || Math.abs(scan.chunkZ() - (pz >> 4)) > radius) continue;
            int slots = AmbientRoster.slotCount(scan.usableCells(), ROSTER);
            if (slots == 0 || scan.columnCount() == 0) continue;
            for (AmbientRoster.Lifespan lifespan : AmbientRoster.present(seed, scan.chunkX(), scan.chunkZ(), slots, time, ROSTER)) {
                stats.rosterPresent++;
                int column = AmbientRoster.homeColumn(lifespan.fishId(), scan.columnCount());
                String species = speciesFor(lvl, lifespan, scan, column);
                long patchKey = discovery.patchKeyAt(scan.blockX(column), scan.blockZ(column));
                boolean drawn = AmbientRoster.drawn(lifespan.fishId(), density);
                plans.add(new Plan(lifespan, scan, column, species, patchKey, drawn));
                if (!drawn) {
                    stats.rosterHiddenDensity++;
                    continue;
                }
                stats.rosterDrawn++;
                DomainState st = patchKey == 0L ? null : domains.get(patchKey);
                if (st == null || st.dying || species == null) {
                    stats.rosterNoDomain++;
                    continue;
                }
                SpeciesVisual visual = visuals.get(species);
                if (visual != null && visual.body().above() > st.topMargin) st.topMargin = visual.body().above();
            }
        }
    }

    /** Forgets memoised draws of fish no longer on the roster, so the maps stay bounded. */
    private void pruneMemos() {
        java.util.Set<Long> alive = new java.util.HashSet<>(plans.size() * 2);
        for (Plan plan : plans) alive.add(plan.lifespan().fishId());
        speciesById.keySet().retainAll(alive);
        retryAfter.keySet().retainAll(alive);
        deferredSince.keySet().retainAll(alive);
        if (samplers.size() > 256) samplers.clear();
    }

    /**
     * The fish species: drawn once from the natural pool at its home cell under the conditions at its
     * arrival (time of day and moon from the arrival clock time; weather as this client sees it now, so
     * two clients can differ for a fish that arrived before a weather change one of them saw late),
     * vanilla fish excluded, and remembered for the fish life.
     */
    @Nullable
    private String speciesFor(ClientLevel lvl, AmbientRoster.Lifespan lifespan, AmbientChunkField.Scan scan, int column) {
        String cached = speciesById.get(lifespan.fishId());
        if (cached != null) return cached.isEmpty() ? null : cached;
        BlockPos cell = new BlockPos(scan.blockX(column), scan.topY()[column], scan.blockZ(column));
        long clockOffset = lvl.getOverworldClockTime() - lvl.getGameTime();
        var env = NaturalPool.ambientEnvironment(lvl.getBiome(cell), lifespan.arrivalTick() + clockOffset,
                FishProfile.WeatherCondition.fromLevel(lvl, cell), cell.getY(), lvl.getSeaLevel());
        VisualSampler sampler = samplers.get(env);
        if (sampler == null) {
            var profiles = lvl.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
            var pool = NaturalPool.resolve(lvl.registryAccess().lookupOrThrow(Registries.ITEM), profiles, env);
            sampler = VisualSampler.build(NaturalPool.ambientCandidates(profiles, pool), id -> visualFor(id, lvl) != null);
            samplers.put(env, sampler);
        }
        String species = sampler.sampleAt(AmbientRoster.speciesUnit(lifespan.fishId()));
        stats.speciesDraws++;
        speciesById.put(lifespan.fishId(), species == null ? "" : species);
        return species;
    }

    /** Starts the fade of every resident the roster no longer wants drawn: lifespan over, density lowered, slot gone. */
    private int reconcileRoster(long time, int px, int py, int pz) {
        if (!seeded) {
            for (DomainState st : domains.values()) {
                for (Member m : st.members) beginRetire(st, m, REASON_EXCLUDED, time);
            }
            return 0;
        }
        java.util.Set<Long> wanted = new java.util.HashSet<>(plans.size() * 2);
        for (Plan plan : plans) if (plan.drawn()) wanted.add(plan.lifespan().fishId());
        for (DomainState st : domains.values()) {
            for (Member m : st.members) {
                if (m.retiring() || wanted.contains(m.id)) continue;
                beginRetire(st, m, m.lifespan.departureTick() <= time ? REASON_EXPIRED : REASON_EXCLUDED, time);
            }
        }
        return 0;
    }

    /**
     * Brings the set of shown domains up to date: patches that are no longer live (or no longer
     * among the nearest few) start dying, new ones are adopted, and at most one domain is rebuilt
     * from the current columns each tick.
     */
    private void syncDomains(long time, int px, int py, int pz) {
        noteMerges(time);
        List<WaterDiscovery.Patch> live = new ArrayList<>();
        for (WaterDiscovery.Patch p : discovery.livePatches()) {
            if (p.columns() >= MIN_PATCH_COLUMNS) live.add(p);
        }
        var occupied = new java.util.HashSet<Long>();
        for (DomainState st : domains.values()) {
            // A merging domain's residents are bound for the survivor, so the survivor holds a slot.
            if (st.mergedInto != 0L) occupied.add(st.mergedInto);
            else if (!st.dying && !st.members.isEmpty()) occupied.add(st.patchKey);
        }
        // An occupied patch keeps its slot while any of its water is in range: at a 32-block range
        // several bodies of water compete for the slots, and the old 10-block hold let a few steps
        // swap a populated patch for an empty one, fading its whole population (ambientwalk).
        var selection = AmbientPatchSelection.select(live, discovery.nearestSquaredDistances(px, pz), occupied,
                budget.domains(), budget.radius() * budget.radius());
        live = selection.patches();
        patchDistances = selection.distances();

        for (DomainState st : domains.values()) {
            if (st.mergedInto != 0L) {
                // Its key is gone by design; it lives on until the survivor can take its residents.
                if (time - st.mergedAt < MERGE_TIMEOUT_TICKS) continue;
                stats.mergeFallbacks += st.members.size();
                st.mergedInto = 0L;
            }
            boolean stillLive = false;
            for (WaterDiscovery.Patch p : live) {
                if (p.key() == st.patchKey) {
                    // A dying domain whose water is back before its residents have faded is not a
                    // new domain: it is the same water, and they come back with it.
                    if (st.dying) revive(st, p, time);
                    else st.patch = p;
                    stillLive = true;
                    break;
                }
            }
            if (!stillLive) beginDying(st, time);
        }
        // A merging domain does not hold a slot of its own: its water is the survivor's now.
        int slots = 0;
        for (DomainState st : domains.values()) if (st.mergedInto == 0L) slots++;
        for (WaterDiscovery.Patch p : live) {
            DomainState st = domains.get(p.key());
            if (st != null) continue;
            if (slots >= budget.domains()) break;
            domains.put(p.key(), new DomainState(p));
            slots++;
        }

        // A revived domain waits for its patch's cache to refill before it is rebuilt. Rediscovery
        // is budgeted, so for the first ticks after a revival the cache holds only a corner of the
        // water; a rebuild from that corner would read residents standing in water it has not found
        // yet as outside the domain, and retire the ones among them whose bodies it cannot fit. The
        // wait is bounded, so a patch that really came back smaller is rebuilt anyway and the
        // retirements that follow are honest ones.
        for (DomainState st : domains.values()) {
            if (st.refilling && (st.patch.columns() >= Math.max(0, st.builtColumns)
                    || time - st.refillSince >= REFILL_TIMEOUT_TICKS)) {
                st.refilling = false;
            }
        }

        // One domain build per tick, nearest first: the field build is the only unbounded-looking
        // cost, and this is what keeps it out of any single frame.
        DomainState dirtiest = null;
        int bestDistance = Integer.MAX_VALUE;
        for (DomainState st : domains.values()) {
            if (st.dying || st.refilling || st.mergedInto != 0L) continue;
            int distance = patchDistance(st.patch);
            // A margin the domain was not built with is as much a staleness as a moved column:
            // the reserved head-room is what keeps a tall species' spawns inside the water.
            // Anything else a rebuild reads — the columns' water and context (the patch version)
            // and the player block its open faces are judged from — is picked up at most once per
            // interval, as the old timed rebuild did; a domain none of that has moved for is not
            // rebuilt at all.
            boolean inputsMoved = st.builtVersion != discovery.patchVersion(st.patchKey)
                    || st.builtPx != px || st.builtPy != py || st.builtPz != pz;
            // Growth alone (more columns found, nothing else moved) waits GROWTH_REBUILD_TICKS;
            // a shrink is a retirement waiting to happen and is never held.
            boolean grew = st.patch.columns() > st.builtColumns;
            boolean stale = st.builtTick == Long.MIN_VALUE
                    || (st.builtColumns != st.patch.columns()
                        && (!grew || !recent(st.builtTick, time, GROWTH_REBUILD_TICKS)))
                    || st.builtTopMargin != st.topMargin
                    || (inputsMoved && !recent(st.builtTick, time, REBUILD_INTERVAL_TICKS));
            if (stale && distance < bestDistance) {
                bestDistance = distance;
                dirtiest = st;
            }
        }
        if (dirtiest != null) rebuild(dirtiest, time, px, py, pz);
        transferMerged(time);
    }

    /**
     * Marks every domain whose water discovery just merged into another body. Its residents are
     * not lost: they stay where they are, in water that is still there, and move to the survivor
     * once the survivor's domain covers it. A merge used to retire the whole absorbed domain, which
     * faded every fish in it at once and respawned them a moment later.
     */
    private void noteMerges(long time) {
        for (long[] merge : discovery.drainMerges()) {
            long loser = merge[0], winner = merge[1];
            mergeHistory.put(loser, winner);
            if (mergeHistory.size() > 64) mergeHistory.remove(mergeHistory.keySet().iterator().next());
            for (DomainState st : domains.values()) {
                if (st.mergedInto == loser) st.mergedInto = winner; // a chain: follow the survivor
            }
            DomainState st = domains.get(loser);
            if (st == null || st.dying || st.mergedInto != 0L || st.members.isEmpty()) continue;
            st.mergedInto = winner;
            st.mergedAt = time;
            st.refilling = false;
            stats.merges++;
        }
    }

    /**
     * Moves each merged domain's residents into the survivor once the survivor's domain has been
     * built from the joined water: same world position, motion, phases and individuality
     * ({@link FlockEngine#adopt}), so nothing visibly changes. A resident whose body the new water
     * cannot hold, or one already fading, stays behind and fades with the old domain.
     */
    private void transferMerged(long time) {
        for (DomainState from : domains.values()) {
            if (from.mergedInto == 0L) continue;
            DomainState to = domains.get(from.mergedInto);
            if (to == null || to.dying || to.domain == null || to.builtTick == Long.MIN_VALUE
                    || to.builtTick < from.mergedAt || from.domain == null) continue;
            float dl = from.originX() - to.originX();
            float dy = from.originY() - to.originY();
            float dd = from.originZ() - to.originZ();
            List<Member> moving = new ArrayList<>();
            List<Integer> sources = new ArrayList<>();
            for (int i = 0; i < from.members.size(); i++) {
                Member m = from.members.get(i);
                if (m.retiring()) continue;
                float l = from.engine.posL()[i] + dl, y = from.engine.posY()[i] + dy, d = from.engine.posD()[i] + dd;
                if (!to.domain.bodyFits(l, y, d, m.body)) continue;
                moving.add(m);
                sources.add(i);
            }
            if (!moving.isEmpty()) {
                int first = to.members.size();
                for (Member m : moving) {
                    m.engineIndex = -1;
                    to.members.add(m);
                }
                reseed(to);
                for (int k = 0; k < moving.size(); k++) {
                    to.engine.adopt(first + k, moving.get(k).spec, from.engine, sources.get(k), dl, dy, dd);
                }
                from.members.removeAll(moving);
                stats.mergeTransfers += moving.size();
            }
            from.mergedInto = 0L;
            reseed(from);
            beginDying(from, time);
        }
    }

    /** Starts a domain's one-way exit: everyone fades out, the state goes when the last one has. */
    private void beginDying(DomainState st, long time) {
        st.refilling = false;
        if (st.dying) return;
        st.dying = true;
        if (!st.members.isEmpty()) stats.domainsLost++;
        for (Member m : st.members) beginRetire(st, m, REASON_DOMAIN_LOST, time);
    }

    /**
     * Brings a fading domain back: its patch is live again — the water reappeared within the key's
     * lifetime, or the window slid back over water it had left — and the residents are only fading,
     * never gone until the fade runs out. The patch's frame never moved (the key only comes back as
     * itself), so their positions still mean what they meant; the domain is marked stale so the next
     * sync rebuilds it from the current columns before anyone is stepped — but only once the cache
     * has refilled, see {@link #REFILL_TIMEOUT_TICKS} and the refill gate in {@code syncDomains}.
     */
    private void revive(DomainState st, WaterDiscovery.Patch patch, long time) {
        st.dying = false;
        st.patch = patch;
        st.builtTick = Long.MIN_VALUE;
        st.refilling = true;
        st.refillSince = time;
        stats.revivals++;
        for (Member m : st.members) {
            // Re-entry can cancel a departure, never an eligibility or lifetime retirement.
            if (m.retireReason != REASON_DOMAIN_LOST) continue;
            m.retireTick = Long.MIN_VALUE;
            m.retireReason = -1;
            m.fade = 1f;
            m.retirementPose = null;
        }
    }

    /** Rebuilds one domain's water and re-seeds its engine, carrying every resident's state. */
    private void rebuild(DomainState st, long time, int px, int py, int pz) {
        if (invariantStats) diagnostics.rebuilding(st, time, px, py, pz);
        List<WaterDiscovery.Column> columns = discovery.columnsOf(st.patchKey);
        st.builtTick = time;
        st.builtColumns = st.patch.columns();
        st.builtTopMargin = st.topMargin;
        st.builtVersion = discovery.patchVersion(st.patchKey);
        st.builtPx = px;
        st.builtPy = py;
        st.builtPz = pz;
        if (columns.isEmpty()) {
            beginDying(st, time);
            return;
        }
        // The band every open-face judgement is made against is the discovery's own — one source,
        // so a reconfiguration cannot leave the two drifting apart.
        int bandTop = py + discovery.yAbove(), bandBottom = py - discovery.yBelow();
        boolean[] faces = new boolean[6];
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        List<WaterDomain.Column> domainColumns = new ArrayList<>(columns.size());
        st.waterVolume = 0;
        for (WaterDiscovery.Column c : columns) {
            boolean bottomOpen = c.water().truncated() && c.water().touchesBandBottom(bandBottom);
            // Truncated at the top means cut by the band, not by the air above it: the flag is
            // only set when the cell above the band really holds water (or could not be read), so
            // the face is open exactly when the water reaches the band's own top cell.
            boolean topOpen = c.water().truncated() && c.water().touchesBandTop(bandTop);
            if (bottomOpen) faces[WaterDomain.FACE_MIN_Y] = true;
            if (topOpen) faces[WaterDomain.FACE_MAX_Y] = true;
            // Only a column whose water continues below the band has an unknown bed; a truncated
            // top keeps its real floor and is covered by the domain-wide top face instead.
            // Lower disconnected spans can have a different cave/biome context from the surface
            // sample. Omit them rather than display fish from the wrong authoritative pool.
            int span = c.water().spanCount() - 1;
            float lo = c.water().lo(span), hi = c.water().hi(span);
            if (hi - lo > DISPLAY_DEPTH) {
                lo = hi - DISPLAY_DEPTH;
                bottomOpen = true; // A visual cutoff is not a seabed for crawlers or anchored fish.
                faces[WaterDomain.FACE_MIN_Y] = true;
            }
            domainColumns.add(new WaterDomain.Column(c.x(), c.z(), new float[]{lo, hi}, bottomOpen));
            st.waterVolume += Math.max(0, hi - lo - 0.25);
            minX = Math.min(minX, c.x());
            maxX = Math.max(maxX, c.x());
            minZ = Math.min(minZ, c.z());
            maxZ = Math.max(maxZ, c.z());
        }
        // A horizontal face is open when the patch's edge lies at the sweep's own boundary — the
        // water there may well continue, and discovery simply never looked. An edge well inside the
        // radius was seen and is real bank.
        int radius = discovery.radius();
        if (maxX - px >= radius) faces[WaterDomain.FACE_MAX_L] = true;
        if (px - minX >= radius) faces[WaterDomain.FACE_MIN_L] = true;
        if (maxZ - pz >= radius) faces[WaterDomain.FACE_MAX_D] = true;
        if (pz - minZ >= radius) faces[WaterDomain.FACE_MIN_D] = true;

        long started = System.nanoTime();
        WaterDomain built = WaterDomain.build(domainColumns, st.originX(), st.originY(), st.originZ(),
                st.topMargin, faces);
        st.buildNanos = System.nanoTime() - started;
        stats.domainBuilds++;
        stats.totalBuildMillis += st.buildNanos / 1_000_000.0;
        stats.peakBuildMillis = Math.max(stats.peakBuildMillis, st.buildNanos / 1_000_000.0);
        if (built == null) {
            if (!WaterDomain.gridFits(domainColumns)) {
                // The water is real but its bounding box is bigger than a domain may allocate —
                // a patch scattered around dry land. Not an exit: the last known water stays and
                // the residents keep living in it while the window moves on.
                stats.buildRejections++;
                return;
            }
            // Every column went dry under us; keep the last known water until the residents are
            // gone, so they fade where they were rather than vanishing.
            beginDying(st, time);
            return;
        }
        st.domain = built;
        reseed(st);
        stats.rebuilds++;
        // Positions survive the rebuild exactly (the patch frame never moves), so the only thing a
        // rebuild can invalidate is a resident the new water cannot host — a body the water no
        // longer covers, or a locomotion the rebuild's own size gate just demoted (which would
        // otherwise stay on as a frozen hover).
        for (int i = 0; i < st.members.size(); i++) {
            Member m = st.members.get(i);
            if (m.retiring()) continue;
            if (!st.engine.residentFits(i, m.spec)) {
                stats.unhostable++;
                beginRetire(st, m, REASON_INVALID, time);
                continue;
            }
            if (!built.contains(st.engine.posL()[i], st.engine.posY()[i], st.engine.posD()[i])) {
                // Geometry may disappear underneath a carried resident. That resident is retired
                // below; it is not an active fish escaping containment during an engine step.
                stats.rebuildInvalidatedCenters++;
                if (invariantStats) {
                    grill24.fishtastic.Fishtastic.LOGGER.info(
                            "[ambient-invariant] rebuild invalidated id={} species={} columns={} refilling={} xyz={}/{}/{}",
                            m.id, m.species, st.builtColumns, st.refilling,
                            st.engine.posL()[i], st.engine.posY()[i], st.engine.posD()[i]);
                }
            }
            if (!built.bodyFits(st.engine.posL()[i], st.engine.posY()[i], st.engine.posD()[i], m.body)) {
                // A fish whose water went away fades where it is while the new domain holds it
                // inside whatever water is left, rather than being snapped to a new position.
                beginRetire(st, m, REASON_INVALID, time);
            }
        }
    }

    /** Rebuilds the engine from the current member list, carrying every surviving fish verbatim. */
    private void reseed(DomainState st) {
        if (st.domain == null) return;
        int n = st.members.size();
        FishSpec[] specs = new FishSpec[n];
        int[] carry = new int[n];
        for (int i = 0; i < n; i++) {
            Member m = st.members.get(i);
            specs[i] = m.spec;
            carry[i] = m.engineIndex;
        }
        st.engine.rebuildPreserving(specs, carry, st.patchKey, 0f, ROTATION_JITTER, st.domain);
        for (int i = 0; i < n; i++) st.members.get(i).engineIndex = i;
    }

    private void beginRetire(DomainState st, Member m, int reason, long time) {
        if (m.retiring()) return;
        m.retireTick = time;
        m.retireReason = reason;
        // Context-driven retirements get the spec's ~2 s eligibility fade; the water-boundary
        // retirements keep the Phase 2 fade they were tuned with.
        m.fadeTicks = (reason == REASON_EXCLUDED || reason == REASON_EXPIRED || reason == REASON_LEASH
                || reason == REASON_DISPLACED) ? AmbientTuning.POPULATION_FADE_TICKS : FADE_TICKS;
        if (reason == REASON_DISPLACED) stats.retireDisplaced++;
        if (reason == REASON_OPEN_FACE) stats.retireOpenFace++;
        else if (reason == REASON_INVALID) stats.retireInvalid++;
        else if (reason == REASON_DOMAIN_LOST) stats.retireDomainLost++;
        else if (reason == REASON_EXCLUDED) stats.retireExcluded++;
        else if (reason == REASON_EXPIRED) stats.retireExpired++;
        else if (reason == REASON_LEASH) stats.retireLeash++;
    }

    /** Removes members whose fade has run out, up to the tick's membership budget. */
    private int retireFinished(long time) {
        int removed = 0;
        for (DomainState st : domains.values()) {
            scratchMembers.clear();
            for (Member m : st.members) {
                if (m.retiring() && m.fade <= 0f && removed < membershipChangesPerTick()) {
                    scratchMembers.add(m);
                    removed++;
                }
            }
            if (scratchMembers.isEmpty()) continue;
            st.members.removeAll(scratchMembers);
            stats.retirements += scratchMembers.size();
            reseed(st);
        }
        return removed;
    }

    /**
     * Advances every fade.
     *
     * <p>A fish leaving through an <em>open face</em> is faded by position as well as by time: the
     * fade is {@code (distance / OPEN_FACE_MARGIN)²}, so it is already most of the way gone by the
     * time it reaches the avoidance zone (0.14–0.25 blocks at the locomotion sets' wall margins)
     * and effectively invisible at the face plane itself. That is what keeps a fish from visibly
     * pivoting against a wall that is not there — the boundary retires it, it never turns on it.
     * A fish retired for any other reason (its water went away, its patch went away) has no
     * meaningful distance to fade against and fades on time alone.
     */
    private void updateFades(long time) {
        for (DomainState st : domains.values()) {
            WaterDomain d = st.domain;
            for (int i = 0; i < st.members.size(); i++) {
                Member m = st.members.get(i);
                if (!m.retiring()) {
                    // An arrival swims in from the fluid edge, fading up as it goes; any other new
                    // fish grows in where it was placed.
                    if (m.fade < 1f) m.fade = Math.min(1f, (time - m.bornTick)
                            / (float) (m.arriving ? ARRIVAL_FADE_TICKS : SPAWN_FADE_TICKS));
                    continue;
                }
                float timeFade = 1f - (time - m.retireTick) / (float) m.fadeTicks;
                float fade = timeFade;
                if (m.retireReason == REASON_OPEN_FACE && d != null) {
                    float open = d.openFaceDistance(st.engine.posL()[i], st.engine.posY()[i], st.engine.posD()[i]);
                    if (open != Float.MAX_VALUE) {
                        float t = Math.max(0f, Math.min(1f, open / WaterDomain.OPEN_FACE_MARGIN));
                        fade = Math.min(fade, t * t);
                    }
                }
                // One-way: a fish pushed back out of the band must not grow back on its way out.
                m.fade = Math.max(0f, Math.min(Math.min(m.fade, 1f), fade));
            }
        }
    }

    /** Drops a dying domain once its last resident has faded away. */
    private void dropDeadDomains(long time) {
        domains.values().removeIf(st -> st.dying && st.members.isEmpty());
    }

    /**
     * Adds the roster's drawn fish that are not yet alive, nearest to the player first, within the
     * membership budget and the density setting cap. A fish no body fits near its home cell is
     * omitted (never demoted) and tried again after {@link #OMISSION_RETRY_TICKS}.
     */
    private void spawnRoster(long time, int px, int py, int pz, int changes) {
        if (!seeded) return;
        int changeBudget = membershipChangesPerTick();
        if (changes >= changeBudget) {
            spawnSkip("changes");
            return;
        }
        // The cap bounds the living population; fading fish are short-lived and only bounded by the
        // ceiling, so a displaced fish's slot is free the moment its fade begins.
        int total = 0, alive = 0;
        int cap = budget.individuals(), ceiling = cap + Math.max(2, cap / 2);
        java.util.Set<Long> existing = new java.util.HashSet<>();
        for (DomainState st : domains.values()) {
            total += st.members.size();
            for (Member m : st.members) {
                existing.add(m.id);
                if (!m.retiring()) alive++;
            }
        }
        List<Plan> pending = new ArrayList<>();
        for (Plan plan : plans) {
            if (!plan.drawn() || plan.species() == null || existing.contains(plan.lifespan().fishId())) continue;
            DomainState st = plan.patchKey() == 0L ? null : domains.get(plan.patchKey());
            if (st == null || st.dying || st.domain == null) continue;
            Long after = retryAfter.get(plan.lifespan().fishId());
            if (after != null && time < after) continue;
            pending.add(plan);
        }
        stats.rosterPending = pending.size();
        pending.sort(Comparator.comparingDouble(p -> {
            double dx = p.homeX() + 0.5 - px, dz = p.homeZ() + 0.5 - pz;
            return dx * dx + dz * dz;
        }));
        if (alive >= cap && !pending.isEmpty()) alive -= displaceFor(pending, time, px, pz);
        if (alive >= cap || total >= ceiling) {
            spawnSkip(alive >= cap ? "cap" : "ceiling");
            return;
        }
        for (Plan plan : pending) {
            if (changes >= changeBudget || alive >= cap || total >= ceiling) break;
            if (spawnFish(plan, domains.get(plan.patchKey()), time)) {
                changes++;
                total++;
                alive++;
            }
        }
    }

    /**
     * With the cap full, hands the slots of the furthest out-of-view residents to the nearest waiting
     * fish (pending is sorted nearest first). A resident gives up its slot only when it is more than
     * {@link #DISPLACE_HYSTERESIS} further from the player than the fish taking it, so a still player
     * holds a stable population and only travel moves the slots along. Returns the residents retired.
     */
    private int displaceFor(List<Plan> pending, long time, int px, int pz) {
        Minecraft mc = Minecraft.getInstance();
        var camera = mc.gameRenderer == null ? null : mc.gameRenderer.getMainCamera();
        // Residents are ranked by their home cell, exactly as waiting fish are: a fish's live position
        // wanders, and ranking it by that would let a slot-holder that drifted away lose its slot to a
        // fish no nearer than itself, which would then drift and lose it again — endless churn for a
        // player standing still. Home cells don't move, so every displacement strictly lowers the
        // furthest resident's distance and a still player's population settles.
        Map<Long, Plan> homes = new java.util.HashMap<>(plans.size() * 2);
        for (Plan plan : plans) homes.put(plan.lifespan().fishId(), plan);
        List<DomainState> owners = new ArrayList<>();
        List<Member> candidates = new ArrayList<>();
        List<Double> distances = new ArrayList<>();
        for (DomainState st : domains.values()) {
            for (int i = 0; i < st.members.size(); i++) {
                Member m = st.members.get(i);
                if (m.retiring() || st.domain == null) continue;
                double x = st.originX() + st.engine.posL()[i], y = st.originY() + st.engine.posY()[i],
                        z = st.originZ() + st.engine.posD()[i];
                if (camera != null && inView(mc, camera, x, y, z)) continue;
                Plan home = homes.get(m.id);
                if (home == null) continue; // off the roster: reconcileRoster is already retiring it
                owners.add(st);
                candidates.add(m);
                distances.add(Math.hypot(home.homeX() - px, home.homeZ() - pz));
            }
        }
        Integer[] order = new Integer[candidates.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(distances.get(b), distances.get(a)));
        int displaced = 0, k = 0;
        // Only a fish that can actually be placed may take a slot: one that cannot would free the
        // slot, fail, and hand it straight back to the resident it evicted — a loop that never ends.
        int limit = membershipChangesPerTick();
        for (int p = 0; p < pending.size() && k < order.length && displaced < limit; p++) {
            Plan plan = pending.get(p);
            double want = Math.hypot(plan.homeX() - px, plan.homeZ() - pz);
            int i = order[k];
            if (distances.get(i) <= want + DISPLACE_HYSTERESIS) break;
            if (!placeable(plan, time)) continue;
            beginRetire(owners.get(i), candidates.get(i), REASON_DISPLACED, time);
            displaced++;
            k++;
        }
        return displaced;
    }

    /** Whether {@link #spawnFish} would find this fish a place now; a miss is put on the retry clock. */
    private boolean placeable(Plan plan, long time) {
        DomainState st = domains.get(plan.patchKey());
        SpeciesVisual visual = visuals.get(plan.species());
        if (st == null || st.domain == null || visual == null) return false;
        FishSpec spec = visual.spec();
        long id = plan.lifespan().fishId();
        boolean arriving = time - plan.lifespan().arrivalTick() < ARRIVAL_WINDOW_TICKS;
        float[] found = new float[3];
        if (st.engine.passesGate(spec.locomotion(), spec.length())
                && findPlacement(st, plan, spec, visual.body(), arriving, found)) {
            // A fish that would be held back (its spot is in view and near) cannot take a slot now
            // either, or the freed slot would sit empty and go straight back to the fish it evicted.
            return !nearViewSeen(st, found) || deferExpired(id, time);
        }
        retryAfter.put(id, time + OMISSION_RETRY_TICKS);
        return false;
    }

    /** Spawns and removals allowed per tick (and displacements): grows with the cap. */
    int membershipChangesPerTick() {
        return Math.max(MIN_MEMBERSHIP_CHANGES_PER_TICK, budget.individuals() / MEMBERSHIP_CAP_DIVISOR);
    }

    /** Whether a world point is inside the camera's view, widened by {@link #VIEW_MARGIN_DEGREES}. */
    private static boolean inView(Minecraft mc, net.minecraft.client.Camera camera, double x, double y, double z) {
        Vec3 eye = camera.position();
        var f = camera.forwardVector();
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        double depth = dx * f.x() + dy * f.y() + dz * f.z();
        if (depth <= 0) return false;
        double rx = -f.z(), rz = f.x();
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-6) return true; // looking straight up or down: treat everything as seen
        rx /= rl;
        rz /= rl;
        double ux = -rz * f.y(), uy = rz * f.x() - rx * f.z(), uz = rx * f.y();
        double margin = Math.toRadians(VIEW_MARGIN_DEGREES);
        double halfV = Math.toRadians(mc.options.fov().get()) / 2;
        double aspect = mc.getWindow().getWidth() / (double) Math.max(1, mc.getWindow().getHeight());
        double halfH = Math.atan(Math.tan(halfV) * aspect);
        double tanH = Math.tan(Math.min(Math.toRadians(89), halfH + margin));
        double tanV = Math.tan(Math.min(Math.toRadians(89), halfV + margin));
        return Math.abs(dx * rx + dz * rz) <= depth * tanH && Math.abs(dx * ux + dy * uy + dz * uz) <= depth * tanV;
    }

    private boolean spawnFish(Plan plan, DomainState st, long time) {
        long id = plan.lifespan().fishId();
        SpeciesVisual visual = visuals.get(plan.species());
        if (visual == null) return false;
        FishSpec spec = visual.spec();
        WaterDomain.BodyBox body = visual.body();
        spec = new FishSpec(spec.length(), spec.locomotion(), AmbientRoster.mirrored(id), spec.species(), spec.shelterUse());
        if (!st.engine.passesGate(spec.locomotion(), spec.length())) {
            retryAfter.put(id, time + OMISSION_RETRY_TICKS);
            stats.omissions++;
            spawnSkip("gate " + spec.species());
            return false;
        }
        boolean arriving = time - plan.lifespan().arrivalTick() < ARRIVAL_WINDOW_TICKS;
        float[] found = new float[3];
        if (!findPlacement(st, plan, spec, body, arriving, found)) {
            retryAfter.put(id, time + OMISSION_RETRY_TICKS);
            stats.omissions++;
            stats.unplaceable++;
            spawnSkip("nowhere " + spec.species());
            return false;
        }
        if (deferNearView(st, id, found, time)) return false;
        spawnSkip("spawned " + spec.species());
        Member m = new Member(plan.lifespan(), plan.species(), spec, body, visual.stack().copy(), visual.anim(), time, arriving);
        st.members.add(m);
        reseed(st);
        int index = st.members.size() - 1;
        st.engine.placeFish(index, found[0], found[1], found[2]);
        if (invariantStats) {
            stats.spawnFitChecks++;
            if (!st.domain.bodyFits(st.engine.posL()[index], st.engine.posY()[index],
                    st.engine.posD()[index], body)) stats.spawnFitFailures++;
        }
        stats.spawns++;
        return true;
    }

    /**
     * Holds back a fish whose spot is in view and near, so the player does not watch it appear:
     * it is looked at again every {@link #DEFER_RETRY_TICKS} and spawns the moment the spot is out
     * of view (or far enough), or after {@link #MAX_SPAWN_DEFER_TICKS} in any case; a fish in water
     * the player keeps staring at is better faded in than never there.
     */
    private boolean deferNearView(DomainState st, long id, float[] found, long time) {
        if (!nearViewSeen(st, found)) {
            deferredSince.remove(id);
            return false;
        }
        long since = deferredSince.computeIfAbsent(id, k -> time);
        if (deferExpired(id, time)) {
            deferredSince.remove(id);
            stats.deferralsExpired++;
            return false;
        }
        retryAfter.put(id, time + DEFER_RETRY_TICKS);
        stats.deferrals++;
        spawnSkip("deferred");
        return true;
    }

    /** Whether a domain-local spot is in view and nearer than {@link #NEAR_VIEW_SPAWN_DISTANCE}. */
    private boolean nearViewSeen(DomainState st, float[] found) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || mc.player == null) return false;
        var camera = mc.gameRenderer.getMainCamera();
        double x = st.originX() + found[0], y = st.originY() + found[1], z = st.originZ() + found[2];
        Vec3 eye = camera.position();
        double dx = x - eye.x, dy = y - eye.y, dz = z - eye.z;
        return dx * dx + dy * dy + dz * dz < NEAR_VIEW_SPAWN_DISTANCE * NEAR_VIEW_SPAWN_DISTANCE
                && inView(mc, camera, x, y, z);
    }

    private boolean deferExpired(long id, long time) {
        Long since = deferredSince.get(id);
        return since != null && time - since >= MAX_SPAWN_DEFER_TICKS;
    }

    /**
     * Where a fish enters: an arrival at the patch outermost column of its home chunk (it swims in
     * from the fluid edge), anyone already living there at its seeded home cell; then a few seeded
     * neighbours of the home cell if the body does not fit. Every choice is hashed from the fish ID.
     */
    private boolean findPlacement(DomainState st, Plan plan, FishSpec spec, WaterDomain.BodyBox body,
                                  boolean arriving, float[] out) {
        AmbientChunkField.Scan scan = plan.scan();
        long id = plan.lifespan().fishId();
        double height = AmbientRoster.heightFraction(id);
        if (arriving) {
            int edge = edgeColumn(st, scan);
            if (edge >= 0 && placeAt(st, spec, body, scan.blockX(edge), scan.blockZ(edge), height, out)) return true;
        }
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            int column = attempt == 0 ? plan.column()
                    : AmbientRoster.homeColumn(id + 0x9E3779B97F4A7C15L * attempt, scan.columnCount());
            int bx = scan.blockX(column), bz = scan.blockZ(column);
            if (discovery.patchKeyAt(bx, bz) != st.patchKey) continue;
            if (placeAt(st, spec, body, bx, bz, attempt == 0 ? height : (height + 0.37 * attempt) % 1.0, out)) return true;
        }
        return false;
    }

    /** The chunk column in this patch furthest from the chunk centre, or -1. */
    private int edgeColumn(DomainState st, AmbientChunkField.Scan scan) {
        int best = -1;
        float bestDistance = -1f;
        for (int i = 0; i < scan.columnCount(); i++) {
            int bx = scan.blockX(i), bz = scan.blockZ(i);
            if (discovery.patchKeyAt(bx, bz) != st.patchKey) continue;
            float distance = Math.max(Math.abs((scan.columns()[i] & 15) - 7.5f), Math.abs((scan.columns()[i] >> 4) - 7.5f));
            if (distance > bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    /** One placement probe in a column: the whole body fits with margin, clear of open faces and residents. */
    private boolean placeAt(DomainState st, FishSpec spec, WaterDomain.BodyBox body, int bx, int bz, double fraction,
                            float[] out) {
        WaterDomain d = st.domain;
        if (d == null) return false;
        float l = bx + 0.5f - st.originX(), dep = bz + 0.5f - st.originZ();
        if ((spec.locomotion() == Locomotion.BENTHIC || spec.locomotion() == Locomotion.ANCHORED)
                && !Float.isFinite(d.bedAt(l, dep))) return false;
        float bed = d.bottomAt(l, dep), surface = d.surfaceAt(l, dep);
        if (!Float.isFinite(bed) || !Float.isFinite(surface)) return false;
        float lo = bed + body.below() + 0.02f;
        float hi = surface - body.above() - 0.02f;
        if (hi <= lo) return false;
        float y = AmbientPlacement.height(spec.locomotion(), d.bedAt(l, dep), lo, hi, (float) fraction);
        if (!d.bodyFits(l, y, dep, body)) return false;
        if (d.openFaceDistance(l, y, dep) <= WaterDomain.OPEN_FACE_MARGIN + 0.05f) return false;
        for (int i = 0; i < st.members.size(); i++) {
            float dl = st.engine.posL()[i] - l, dy = st.engine.posY()[i] - y, dd = st.engine.posD()[i] - dep;
            if (dl * dl + dy * dy + dd * dd < SPAWN_SEPARATION * SPAWN_SEPARATION) return false;
        }
        out[0] = l;
        out[1] = y;
        out[2] = dep;
        return true;
    }

    /**
     * E12: each client moves its own copy of a fish, leashed to the fish home chunk. A fish that
     * wanders more than {@link #LEASH_MARGIN} blocks past its chunk footprint fades out and, being
     * still on the roster, re-enters at its seeded home cell.
     */
    private void enforceLeash(long time) {
        for (DomainState st : domains.values()) {
            if (st.domain == null) continue;
            for (int i = 0; i < st.members.size(); i++) {
                Member m = st.members.get(i);
                if (m.retiring()) continue;
                double x = st.originX() + st.engine.posL()[i], z = st.originZ() + st.engine.posD()[i];
                double x0 = (m.lifespan.chunkX() << 4) - LEASH_MARGIN, z0 = (m.lifespan.chunkZ() << 4) - LEASH_MARGIN;
                if (x < x0 || x > x0 + 16 + 2 * LEASH_MARGIN || z < z0 || z > z0 + 16 + 2 * LEASH_MARGIN) {
                    beginRetire(st, m, REASON_LEASH, time);
                }
            }
        }
    }

    /** The roster of the last pass with its drawn/hidden split, for the debug view and the harness. */
    public List<RosterFish> rosterSnapshot() {
        java.util.Map<Long, Member> alive = new java.util.HashMap<>();
        for (DomainState st : domains.values()) for (Member m : st.members) if (!m.retiring()) alive.put(m.id, m);
        List<RosterFish> out = new ArrayList<>(plans.size());
        long now = lastTick == Long.MIN_VALUE ? 0 : lastTick;
        for (Plan plan : plans) {
            AmbientRoster.Lifespan l = plan.lifespan();
            Status status;
            if (alive.containsKey(l.fishId())) status = Status.ALIVE;
            else if (!plan.drawn()) status = Status.HIDDEN_DENSITY;
            else if (plan.species() == null) status = Status.NO_SPECIES;
            else if (plan.patchKey() == 0L || domains.get(plan.patchKey()) == null) status = Status.NO_DOMAIN;
            else if (retryAfter.containsKey(l.fishId()) && now < retryAfter.get(l.fishId())) status = Status.UNPLACEABLE;
            else status = Status.PENDING;
            out.add(new RosterFish(l.fishId(), l.chunkX(), l.chunkZ(), l.slot(), plan.species(), l.phaseAt(now),
                    l.arrivalTick(), l.departureTick(), plan.homeX(), plan.homeZ(), plan.patchKey(), status));
        }
        return out;
    }

    /**
     * A shown ambient fish a spyglass could be held on: what the server needs to derive it again
     * (chunk, slot, lifespan index, home cell) and where this client has it now.
     */
    public record Observable(long id, String species, int chunkX, int chunkZ, int slot, long lifespanIndex,
                             BlockPos home, double x, double y, double z) {}

    /**
     * Every fully faded-in ambient fish this client is showing, for the spyglass detector. Fish
     * still arriving or retiring are left out, so a hold is never started on one about to vanish.
     */
    public List<Observable> observableFish() {
        List<Observable> out = new ArrayList<>();
        for (DomainState st : domains.values()) {
            if (st.domain == null) continue;
            for (int i = 0; i < st.members.size(); i++) {
                Member m = st.members.get(i);
                if (m.retiring() || m.fade < 0.75f) continue;
                AmbientChunkField.Scan scan = field.get(m.lifespan.chunkX(), m.lifespan.chunkZ());
                if (scan == null || scan.columnCount() == 0) continue;
                int column = AmbientRoster.homeColumn(m.id, scan.columnCount());
                BlockPos home = new BlockPos(scan.blockX(column), scan.topY()[column], scan.blockZ(column));
                out.add(new Observable(m.id, m.species, m.lifespan.chunkX(), m.lifespan.chunkZ(), m.lifespan.slot(),
                        m.lifespan.index(), home, st.originX() + st.engine.posL()[i],
                        st.originY() + st.engine.posY()[i], st.originZ() + st.engine.posD()[i]));
            }
        }
        return out;
    }

    /** The ambient fluid of this session: water, or lava in the Nether. */
    public AmbientFluid fluid() {
        return fluid;
    }

    public boolean seeded() {
        return seeded;
    }

    private void stepEngines() {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eye = mc.player != null ? mc.player.getEyePosition() : null;
        for (DomainState st : domains.values()) {
            if (st.domain == null) continue;
            if (eye != null) {
                st.engine.setWatcher(true, (float) (eye.x - st.originX()), (float) (eye.y - st.originY()),
                        (float) (eye.z - st.originZ()));
            } else {
                st.engine.setWatcher(false, 0f, 0f, 0f);
            }
            st.engine.step();
            stats.steps++;
        }
    }

    /**
     * Whether {@link #recordStats} also probes every resident against its water (containment, body
     * fit, clearances, open-face distance). Those figures exist for the self-test's assertions, and
     * the probes cost five domain queries per fish per tick, so they run only while it is armed.
     */
    private static boolean invariantStats;

    private static final java.lang.management.ThreadMXBean THREADS = java.lang.management.ManagementFactory.getThreadMXBean();

    private long allocCursor;

    /**
     * Self-test only: charges what was allocated since the last mark to {@code section} (0 discovery,
     * 1 domains, 2 roster, 3 reconcile/retire, 4 spawn, 5 engines, 6 the rest).
     */
    private void allocMark(int section) {
        if (!invariantStats) return;
        long now = allocatedBytes();
        stats.allocBySection[section] += now - allocCursor;
        allocCursor = now;
    }

    /** Bytes this thread has allocated so far, or 0 where the JVM cannot say. */
    private static long allocatedBytes() {
        return THREADS instanceof com.sun.management.ThreadMXBean sun ? sun.getCurrentThreadAllocatedBytes() : 0L;
    }

    public static void setInvariantStats(boolean enabled) {
        invariantStats = enabled;
    }

    /** Per-tick headline figures: totals, the discovery budget's peaks, and clearance minima. */
    private void recordStats() {
        WaterDiscovery.Counters c = discovery.counters();
        stats.peakDiscoveryCells = Math.max(stats.peakDiscoveryCells, c.cellsThisTick);
        stats.peakDiscoveryColumns = Math.max(stats.peakDiscoveryColumns, c.scansThisTick);
        stats.discoveryCells = c.cellsClassified;
        stats.discoveryColumns = c.columnsScanned;
        stats.unloadedScans = c.unloadedScans;
        stats.truncatedColumns = c.truncatedColumns;
        stats.columnsChanged = c.columnsChanged;
        stats.columnsEvicted = c.columnsEvicted;
        stats.patchMerges = c.patchMerges;
        stats.revalidationsDeferred = c.revalidationsDeferred;
        stats.worldReads = source.flagReads() + source.fluidReads() + source.collisionReads();
        stats.domains = domains.size();
        int active = 0, retiring = 0;
        for (DomainState st : domains.values()) {
            for (int i = 0; i < st.members.size(); i++) {
                Member m = st.members.get(i);
                if (m.retiring()) {
                    retiring++;
                    continue;
                }
                active++;
                WaterDomain d = st.domain;
                if (d == null || !invariantStats) continue;
                float l = st.engine.posL()[i], y = st.engine.posY()[i], dz = st.engine.posD()[i];
                if (!d.contains(l, y, dz)) stats.outsideWater++;
                if (!d.bodyFits(l, y, dz, m.body)) stats.bodyFitsFailures++;
                float surface = d.surfaceAt(l, dz), bed = d.bedAt(l, dz);
                if (Float.isFinite(surface)) stats.minSurfaceClearance = Math.min(stats.minSurfaceClearance, surface - y);
                if (Float.isFinite(bed)) stats.minBedClearance = Math.min(stats.minBedClearance, y - bed);
                double open = d.openFaceDistance(l, y, dz);
                if (open != Double.MAX_VALUE) stats.minOpenFaceDistance = Math.min(stats.minOpenFaceDistance, open);
            }
        }
        stats.active = active;
        stats.retiring = retiring;
        stats.individuals = active + retiring;
    }

    /**
     * Retires residents that have reached an open face: they are inside the avoidance zone and
     * inside the fade margin, so they leave the population the way they arrived rather than turning
     * against a wall that is not there. Runs once per tick per resident.
     */
    private void retireAtFaces(long time) {
        for (DomainState st : domains.values()) {
            WaterDomain d = st.domain;
            if (d == null) continue;
            for (int i = 0; i < st.members.size(); i++) {
                Member m = st.members.get(i);
                if (m.retiring()) continue;
                if (!level.hasChunkAt(BlockPos.containing(st.originX() + st.engine.posL()[i],
                        st.originY() + st.engine.posY()[i], st.originZ() + st.engine.posD()[i]))) {
                    beginRetire(st, m, REASON_INVALID, time);
                    continue;
                }
                float open = d.openFaceDistance(st.engine.posL()[i], st.engine.posY()[i], st.engine.posD()[i]);
                if (open <= WaterDomain.OPEN_FACE_MARGIN) beginRetire(st, m, REASON_OPEN_FACE, time);
            }
        }
    }

    private Map<Long, Integer> patchDistances = Map.of();

    private int patchDistance(WaterDiscovery.Patch p) {
        return patchDistances.getOrDefault(p.key(), Integer.MAX_VALUE);
    }

    // ------------------------------------------------------------------ rendering

    /** Submits every individual through the ordinary world pipelines, nearest last. */
    public void submitWorld(PoseStack poses, SubmitNodeCollector nodes, float partialTick) {
        worldSubmitsThisFrame = 0;
        Minecraft mc = Minecraft.getInstance();
        if (!armed || mc.level != level) return;
        long submitStarted = System.nanoTime();
        var camera = mc.gameRenderer.getMainCamera();
        Vec3 cam = camera.position();
        var forward = camera.forwardVector();
        float t = level.getGameTime() + partialTick;
        for (DomainState st : domains.values()) {
            int n = st.members.size();
            if (st.domain == null || n == 0) continue;
            st.engine.interpolate(partialTick);
            if (depthScratch.length < n) {
                depthScratch = new float[n];
                orderScratch = new int[n];
            }
            for (int i = 0; i < n; i++) {
                orderScratch[i] = i;
                Matrix4f frozen = st.members.get(i).retirementPose;
                depthScratch[i] = (float) ((st.originX() + (frozen == null ? st.engine.renderX[i] : frozen.m30()) - cam.x) * forward.x()
                        + (st.originY() + (frozen == null ? st.engine.renderY[i] : frozen.m31()) - cam.y) * forward.y()
                        + (st.originZ() + (frozen == null ? st.engine.renderZ[i] : frozen.m32()) - cam.z) * forward.z());
            }
            GroupFishDraw.backToFront(depthScratch, orderScratch, n);
            for (int k = 0; k < n; k++) {
                int i = orderScratch[k];
                Member m = st.members.get(i);
                // A retiring fish holds the pose it had when it began to fade.
                if (m.retiring() && m.retirementPose == null) {
                    PoseStack frozen = new PoseStack();
                    GroupFishPose.apply(frozen, st.engine, m.anim, i, t, 0f, 0f, 0f, poseRandom);
                    m.retirementPose = new Matrix4f(frozen.last().pose());
                }
                if (GroupFishDraw.submit(poses, nodes, level, camera, st.originX(), st.originY(), st.originZ(),
                        st.engine, i, m.anim, m.stack, m.state, m.fade, m.retiring() ? m.retirementPose : null,
                        t, poseRandom)) {
                    worldSubmitsThisFrame++;
                }
            }
        }
        double millis = (System.nanoTime() - submitStarted) / 1_000_000.0;
        stats.renderFrames++;
        stats.totalSubmitMillis += millis;
        stats.peakSubmitMillis = Math.max(stats.peakSubmitMillis, millis);
    }
}
