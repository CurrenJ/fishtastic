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
import grill24.fishtastic.client.renderer.FishVisuals;
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
 * Bounded, world-anchored water around the player, populated from authoritative fishing contexts.
 *
 * <p>Client settings arm the natural runtime in normal play. The development harness retains an
 * explicit full-density seam ({@link ClientAmbientFish#startNatural}).
 *
 * <p><b>Structure.</b> {@link WaterDiscovery} finds loaded water and keeps each body of it under a
 * stable patch key; this class owns one {@link WaterDomain} and one {@link FlockEngine} per shown
 * patch, samples representable eligible species with whole-body clearance, and retires individuals
 * whose water moves out from under them. Every tick's work is budgeted: discovery has its own cell
 * and column budgets, at most one domain is rebuilt per tick, and at most
 * {@link #MEMBERSHIP_CHANGES_PER_TICK} individuals are added or removed per tick, so no frame ever
 * carries an unbounded flavour of the field build.
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

    /** Patches shown at once (nearest to the player). One shown patch per request origin, so this
     * is exactly the context request's wire bound: the two are the same number by construction. */
    public static final int MAX_DOMAINS = grill24.fishtastic.network.AmbientContextRequestPacket.MAX_ORIGINS;
    /** Individuals across every domain — the Phase 0 headroom (2–4 domains × ~8 fish). */
    public static final int MAX_INDIVIDUALS = AmbientBudget.MAX_INDIVIDUALS;
    /** Individuals wanted per domain; the pool cycles species, so a domain holds each in turn. */
    public static final int TARGET_PER_DOMAIN = 8;
    /** A patch smaller than this is a puddle, not somewhere to put a shoal. */
    public static final int MIN_PATCH_COLUMNS = 4;
    /** How often a live domain is rebuilt from the current columns (geometry edits, fluid edits). */
    public static final int REBUILD_INTERVAL_TICKS = 20;
    /**
     * How long a revived domain may hold its rebuild while it waits for the patch's cache to refill
     * to the size it was last built at. Rediscovery is budgeted, so a large patch takes tens of
     * ticks to come back; the bound is what stops a patch that really did come back smaller from
     * living on as a stale domain forever.
     */
    public static final int REFILL_TIMEOUT_TICKS = 200;
    /** Membership changes (spawns, retirements) allowed per tick. */
    public static final int MEMBERSHIP_CHANGES_PER_TICK = 2;
    /** Fade length of a retiring individual, ticks at 20 tps (~1.2 s). */
    public static final int FADE_TICKS = 24;
    /** After a domain omits a spawn because nothing fits, wait this long before trying again. */
    public static final int OMISSION_RETRY_TICKS = 20;
    /** Spawns land within this horizontal radius of the player. */
    public static final float SPAWN_RADIUS = 10f;
    /** …but never right on their nose. */
    public static final float SPAWN_NEAREST = 2.5f;
    /** Minimum gap between a new individual and a resident one. */
    public static final float SPAWN_SEPARATION = 0.5f;
    /** Readable surface population, rather than eight fish scattered through a deep ocean band. */
    public static final float DISPLAY_DEPTH = 4f;
    private static final int SPAWN_ATTEMPTS = 24;
    private static final float ROTATION_JITTER = 20f;

    /** Why an individual is fading out. */
    public static final int REASON_OPEN_FACE = 0;
    public static final int REASON_INVALID = 1;
    public static final int REASON_DOMAIN_LOST = 2;
    /** Its species left the authoritative pool (Phase 5); fades over {@code POPULATION_FADE_TICKS}. */
    public static final int REASON_EXCLUDED = 3;
    /** Its own bounded lifetime ran out (Phase 5); fades over {@code POPULATION_FADE_TICKS}. */
    public static final int REASON_EXPIRED = 4;

    /** One spawned individual: its engine index, its spec, and its retirement state. */
    private static final class Member {
        final long id;
        final String species;
        final FishSpec spec;
        final WaterDomain.BodyBox body;
        final ItemStack stack;
        final FishAnimationConfig anim;
        final long bornTick;
        /** This individual's own lifetime, drawn once at spawn, independent of species. */
        final int lifetimeTicks;
        int slot;
        final ItemStackRenderState state = new ItemStackRenderState();
        Matrix4f retirementPose;
        int engineIndex = -1;
        long retireTick = Long.MIN_VALUE;
        int retireReason = -1;
        int fadeTicks = FADE_TICKS;
        float fade = 1f;

        Member(long id, String species, FishSpec spec, WaterDomain.BodyBox body, ItemStack stack,
               FishAnimationConfig anim, long bornTick, int lifetimeTicks) {
            this.id = id;
            this.species = species;
            this.spec = spec;
            this.body = body;
            this.stack = stack;
            this.anim = anim;
            this.bornTick = bornTick;
            this.lifetimeTicks = lifetimeTicks;
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
    private static final class DomainState {
        final long patchKey;
        final FlockEngine engine = new FlockEngine(Tunables.GROUP);
        final List<Member> members = new ArrayList<>();
        /**
         * The patch's own phase-5 random stream — spawn draws and lifetimes. Seeded from the patch
         * key, restorable from a retained state, and touched by nothing else: the catch path has its
         * own randomness and never shares this one.
         */
        final AmbientRandom speciesRandom;
        AmbientPopulationRolls rolls = new AmbientPopulationRolls();
        WaterDiscovery.Patch patch;
        WaterDomain domain;
        int builtColumns = -1;
        long builtTick = Long.MIN_VALUE;
        long buildNanos;
        boolean dying;
        /** A revived domain waiting for its patch's cache to refill to {@link #builtColumns}. */
        boolean refilling;
        long refillSince;
        int spawnCount;
        long lastOmissionTick = Long.MIN_VALUE;
        /** Whether the store currently holds an OK context for this patch; false blocks new spawns. */
        boolean contextOk;
        long unknownSince = Long.MIN_VALUE;
        /** The visual sampler for the applied context, or null before the first usable answer. */
        VisualSampler sampler;
        /** The fingerprint the current sampler was built from; {@link Long#MIN_VALUE} is "never". */
        long appliedFingerprint = Long.MIN_VALUE;
        long builtGeometrySignature = Long.MIN_VALUE;
        long samplerGeometrySignature = Long.MIN_VALUE;
        /** Head-room the domain must reserve for the tallest representable species, or zero. */
        float topMargin;
        /** The margin the current domain was built with; a mismatch marks it stale. */
        float builtTopMargin = Float.NaN;
        double waterVolume;

        DomainState(WaterDiscovery.Patch patch, long seed) {
            this.patch = patch;
            this.patchKey = patch.key();
            this.speciesRandom = new AmbientRandom(seed);
        }

        int originX() { return patch.originX(); }
        int originY() { return patch.originY(); }
        int originZ() { return patch.originZ(); }
    }

    /** A dropped patch's retained random state, waiting out {@link AmbientTuning#RETENTION_TICKS}. */
    private record Retained(long streamState, int spawnCount, long storedTick, AmbientPopulationRolls rolls) {
    }

    /** What one individual looks like right now — the harness's window into the population. */
    public record Individual(long id, long patchKey, String species, double length, double x, double y,
                             double z, float yaw, boolean retiring, int retireReason, float fade,
                             long bornTick, int lifetimeTicks,
                             double openFaceDistance, double surfaceClearance, double bedClearance,
                             boolean inWater, boolean bodyFits) {}

    /** Per-domain summary, for the harness log. */
    public record DomainReport(long patchKey, int columns, int originX, int originY, int originZ,
                               int individuals, int retiring, long builds, double buildMillis,
                               boolean openMinL, boolean openMaxL, boolean openMinY, boolean openMaxY,
                               boolean openMinD, boolean openMaxD) {}

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
        public long bodyFitsFailures;
        public long steps;
        public long worldReads;
        public long ticks;
        public double totalTickMillis, peakTickMillis;
        public long renderFrames;
        public double totalSubmitMillis, peakSubmitMillis;
        // ── Phase 5: authoritative pools, sampling and turnover ────────────────
        /** Samplers built from applied contexts (one per applied fingerprint change). */
        public long samplerBuilds;
        /** Species omitted from samplers because they cannot be represented at all. */
        public long unrepresentableOmissions;
        /** All species omitted from samplers, unrepresentable ones included, cumulative. */
        public long exclusions;
        /** Domain-ticks whose patch had no usable (OK) context — the spawn-blocked clock. */
        public long contextGaps;
        /** Ticks where a spawn was wanted but no shown domain had a usable context. */
        public long spawnsBlockedNoContext;
        /** Ticks where a spawn was wanted but every eligible domain's pool was empty. */
        public long spawnsBlockedEmptyPool;
        /** Patch random states put into retention when a domain dropped. */
        public long retainedStores;
        /** Retained states that were restored into a re-adopted patch. */
        public long retainedRestores;
        /** Retained states that expired unreused (age, sweep, or the size bound). */
        public long retainedExpiries;

        public double meanBuildMillis() {
            return domainBuilds == 0 ? 0d : totalBuildMillis / domainBuilds;
        }
    }

    private final WaterDiscovery discovery = new WaterDiscovery();
    private final AmbientWaterSource source = new AmbientWaterSource();
    private final Map<Long, DomainState> domains = new LinkedHashMap<>();
    private final Stats stats = new Stats();
    private final Random random = new Random(20261013L);
    private final Random poseRandom = new Random(7L);
    /** Resolved species visuals, by {@code namespace:path} — built lazily from the context's ids. */
    private final Map<String, SpeciesVisual> visuals = new LinkedHashMap<>();
    /** Species ids that resolved to nothing drawable; never re-attempted within a session. */
    private final java.util.Set<String> unrepresentable = new java.util.LinkedHashSet<>();
    /** Patch random states kept across brief absences, bounded by count and by age. */
    private final Map<Long, Retained> retained = new LinkedHashMap<>();
    private final List<Member> scratchMembers = new ArrayList<>();
    private float[] depthScratch = new float[0];
    private int[] orderScratch = new int[0];
    private ClientLevel level;
    private long lastTick = Long.MIN_VALUE;
    private long nextId = 1;
    private boolean armed;
    private AmbientBudget budget = AmbientBudget.forDensity(100);
    private int worldSubmitsThisFrame;
    /** Why this tick's spawn attempt did nothing: diagnosis for {@link #debugState()}. */
    private String lastSpawnSkip = "not-ticked";

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
        nextId = 1;
        worldSubmitsThisFrame = 0;
        visuals.clear();
        unrepresentable.clear();
        retained.clear();
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

    public WaterDiscovery discovery() {
        return discovery;
    }

    public AmbientWaterSource waterSource() {
        return source;
    }

    public int submitsThisFrame() {
        return worldSubmitsThisFrame;
    }

    public int domainCount() {
        return domains.size();
    }

    public long steps() {
        return stats.steps;
    }

    /**
     * Every species any shown patch's sampler can currently draw, in first-seen order — the union
     * of the live visual populations. Before the first context arrives, or where nothing is
     * representable, this is empty: the runtime shows only what an authoritative pool named.
     */
    public List<String> pool() {
        List<String> out = new ArrayList<>();
        for (DomainState st : domains.values()) {
            if (st.sampler == null) continue;
            for (VisualSampler.Entry entry : st.sampler.entries()) {
                if (!out.contains(entry.species())) out.add(entry.species());
            }
        }
        return List.copyOf(out);
    }

    /**
     * The species one patch's sampler may draw, in candidate order — the harness's legality window
     * ("every live individual's species must be in here"). Empty when the patch has no usable
     * context or nothing representable.
     */
    public List<String> representableSpecies(long patchKey) {
        DomainState st = domains.get(patchKey);
        if (st == null || st.sampler == null) return List.of();
        List<String> out = new ArrayList<>(st.sampler.size());
        for (VisualSampler.Entry entry : st.sampler.entries()) out.add(entry.species());
        return List.copyOf(out);
    }

    /** One patch's sampler entries (shares included), for the scene log; empty when unusable. */
    public List<VisualSampler.Entry> samplerFor(long patchKey) {
        DomainState st = domains.get(patchKey);
        return st == null || st.sampler == null ? List.of() : st.sampler.entries();
    }

    /** The fingerprint a patch's applied sampler was built from, or {@link Long#MIN_VALUE}. */
    public long appliedFingerprint(long patchKey) {
        DomainState st = domains.get(patchKey);
        return st == null ? Long.MIN_VALUE : st.appliedFingerprint;
    }

    /** Patch random states currently held for a quick return (bounded; diagnostics). */
    public int retainedStateCount() {
        return retained.size();
    }

    /**
     * One line of internal state for the harness log: the population, and for each shown domain why
     * it is or is not taking fish. Diagnosis only — nothing in the sim reads it.
     */
    public String debugState() {
        StringBuilder sb = new StringBuilder(160);
        WaterDiscovery.Counters c = discovery.counters();
        int knownNear = 0, waterNear = 0;
        BlockPos at = Minecraft.getInstance().player != null
                ? Minecraft.getInstance().player.blockPosition() : BlockPos.ZERO;
        for (WaterDiscovery.Column col : discovery.columns()) {
            if (Math.abs(col.x() - at.getX()) > 8 || Math.abs(col.z() - at.getZ()) > 8) continue;
            knownNear++;
            if (!col.water().isEmpty()) waterNear++;
        }
        sb.append("armed=").append(armed).append(" indivs=").append(stats.individuals)
                .append('/').append(budget.individuals()).append(" density=").append(budget.density())
                .append(" tickMs[mean=").append(stats.totalTickMillis / Math.max(1, stats.ticks))
                .append(" peak=").append(stats.peakTickMillis).append("] submitMs[mean=")
                .append(stats.totalSubmitMillis / Math.max(1, stats.renderFrames))
                .append(" peak=").append(stats.peakSubmitMillis).append("] species=").append(pool().size())
                .append(" retained=").append(retained.size()).append('/').append(AmbientTuning.MAX_RETAINED_PATCHES)
                .append(" domains=").append(domains.size())
                .append(" livePatches=").append(discovery.livePatches().size())
                .append(" submits=").append(worldSubmitsThisFrame)
                .append(" retired[face=").append(stats.retireOpenFace).append(" invalid=").append(stats.retireInvalid)
                .append(" domain=").append(stats.retireDomainLost).append(" excluded=").append(stats.retireExcluded)
                .append(" expired=").append(stats.retireExpired).append(']')
                .append(" known=").append(discovery.columns().size())
                .append(" nearKnown=").append(knownNear).append(" nearWater=").append(waterNear)
                .append(" scansThisTick=").append(c.scansThisTick)
                .append(" cellsThisTick=").append(c.cellsThisTick)
                .append(" cursor=").append(c.cursorStepsThisTick)
                .append(" spawnSkip=").append(lastSpawnSkip);
        int shown = 0;
        for (DomainState st : domains.values()) {
            if (shown++ == 4) break;
            WaterDiscovery.Patch now = discovery.patch(st.patchKey);
            int omitted = st.sampler == null ? 0 : st.sampler.omitted().size();
            int representable = st.sampler == null ? 0 : st.sampler.size();
            sb.append(" [key=").append(st.patchKey)
                    .append(" patchCols=").append(st.patch == null ? -1 : st.patch.columns())
                    .append(" mapCols=").append(now == null ? -1 : now.columns())
                    .append(" builtCols=").append(st.builtColumns)
                    .append(" water=").append(st.domain == null ? "none" : "built")
                    .append(" refilling=").append(st.refilling)
                    .append(" ctx=").append(st.contextOk ? "ok" : "none")
                    .append(" pool=").append(representable).append('/').append(representable + omitted)
                    .append(" fp=").append(st.appliedFingerprint == Long.MIN_VALUE ? "-"
                            : Long.toHexString(st.appliedFingerprint))
                    .append(" top=").append(String.format(java.util.Locale.ROOT, "%.2f", st.topMargin))
                    .append(" members=").append(st.members.size()).append('/').append(targetFor(st))
                    .append(" spawn=").append(skipReason(st))
                    .append(']');
        }
        individuals().stream().sorted(Comparator.comparingDouble(p -> at.distToCenterSqr(p.x(), p.y(), p.z())))
                .limit(3).forEach(p -> sb.append(String.format(java.util.Locale.ROOT,
                        " [fish=%s xyz=%.1f,%.1f,%.1f depth=%.1f fade=%.2f reason=%d]",
                        p.species(), p.x(), p.y(), p.z(), p.surfaceClearance(), p.fade(), p.retireReason())));
        return sb.toString();
    }

    /** Why {@link #spawnOne} would skip this domain, in one word; {@code -} means eligible. */
    private String skipReason(DomainState st) {
        if (st.dying) return "dying";
        if (st.domain == null) return "unbuilt";
        if (st.members.size() >= targetFor(st)) return "full";
        if (!st.contextOk) return "ctxx";
        if (st.sampler == null || st.sampler.isEmpty()) return "norep";
        if (recent(st.lastOmissionTick, lastTick, OMISSION_RETRY_TICKS)) return "retry";
        return "-";
    }

    /** Whether {@code tick} is a real timestamp within {@code window} of {@code now}. */
    private static boolean recent(long tick, long now, long window) {
        // A "never" marker is Long.MIN_VALUE, and `now - Long.MIN_VALUE` overflows back to a
        // negative number, so every such comparison has to test the marker before subtracting.
        return tick != Long.MIN_VALUE && now - tick < window;
    }

    /** Notes why a spawn attempt did nothing, for {@link #debugState()}; returns zero, always. */
    private int spawnSkip(String why) {
        lastSpawnSkip = why;
        return 0;
    }

    public List<DomainReport> domainReports() {
        List<DomainReport> out = new ArrayList<>();
        for (DomainState st : domains.values()) {
            int retiring = 0;
            for (Member m : st.members) {
                if (m.retiring()) retiring++;
            }
            WaterDomain d = st.domain;
            out.add(new DomainReport(st.patchKey, st.patch == null ? 0 : st.patch.columns(),
                    st.originX(), st.originY(), st.originZ(), st.members.size(), retiring,
                    st.builtColumns < 0 ? 0 : 1, st.buildNanos / 1_000_000.0,
                    d != null && d.openFace(WaterDomain.FACE_MIN_L), d != null && d.openFace(WaterDomain.FACE_MAX_L),
                    d != null && d.openFace(WaterDomain.FACE_MIN_Y), d != null && d.openFace(WaterDomain.FACE_MAX_Y),
                    d != null && d.openFace(WaterDomain.FACE_MIN_D), d != null && d.openFace(WaterDomain.FACE_MAX_D)));
        }
        return out;
    }

    /**
     * Live cached column count for a patch key, straight from the discovery cache. Unlike
     * {@link #domainReports()}, which shows the stale count held by the last successful build,
     * this drops to zero as soon as the patch's columns are evicted — the harness's window into
     * "this water is gone / this water is back".
     */
    public int patchColumns(long patchKey) {
        return discovery.columnCount(patchKey);
    }

    /**
     * One live shown patch a context request may reference: its key, one deterministic
     * water-surface sample cell inside its cached water, and a signature of the geometry that
     * sample was taken from.
     */
    public record PatchSample(long patchKey, BlockPos pos, long geometrySignature) {
    }

    /**
     * Water-surface samples for the patches this runtime is currently showing — at most one per
     * non-dying domain ({@link #MAX_DOMAINS}), deterministic for a given geometry. Phase 4's
     * context manager requests these; nothing in the simulation reads them.
     */
    public List<PatchSample> patchSamples() {
        List<PatchSample> out = new ArrayList<>(domains.size());
        for (DomainState st : domains.values()) {
            if (st.dying) continue;
            PatchSample sample = sampleOf(st.patchKey);
            if (sample != null) out.add(sample);
        }
        return List.copyOf(out);
    }

    /**
     * The top water cell of the patch column nearest its seed — a position inside every
     * open-face margin, on the actual fluid surface, so the server can re-validate "water, now?"
     * exactly where the client believes water is. The geometry signature folds the sorted column
     * set and every span, so any real terrain change moves it and no hash iteration order can
     * fake one.
     */
    @Nullable
    private PatchSample sampleOf(long patchKey) {
        WaterDiscovery.Patch patch = discovery.patch(patchKey);
        if (patch == null) return null;
        List<WaterDiscovery.Column> columns = discovery.columnsOf(patchKey);
        columns.sort(Comparator.comparingInt(WaterDiscovery.Column::x).thenComparingInt(WaterDiscovery.Column::z));
        WaterDiscovery.Column best = null;
        long bestDistance = Long.MAX_VALUE;
        for (WaterDiscovery.Column c : columns) {
            if (c.water().isEmpty()) continue;
            if (level == null || !level.hasChunkAt(new BlockPos(c.x(),
                    (int) Math.floor(c.water().surfaceY() - 1e-4), c.z()))) continue;
            long dx = c.x() - patch.seedX(), dz = c.z() - patch.seedZ();
            long distance = dx * dx + dz * dz;
            if (best == null || distance < bestDistance
                    || (distance == bestDistance && (c.x() < best.x() || (c.x() == best.x() && c.z() < best.z())))) {
                best = c;
                bestDistance = distance;
            }
        }
        if (best == null) return null;
        int y = (int) Math.floor(best.water().surfaceY() - 1e-4);
        return new PatchSample(patchKey, new BlockPos(best.x(), y, best.z()), geometrySignature(columns));
    }

    private static long geometrySignature(List<WaterDiscovery.Column> sortedColumns) {
        long hash = 0xcbf29ce484222325L;
        for (WaterDiscovery.Column c : sortedColumns) {
            hash = (hash ^ c.x()) * 0x100000001b3L;
            hash = (hash ^ c.z()) * 0x100000001b3L;
            hash = (hash ^ c.contextSignature()) * 0x100000001b3L;
            WaterColumn water = c.water();
            hash = (hash ^ (water.truncated() ? 1 : 0)) * 0x100000001b3L;
            for (int i = 0; i < water.spanCount(); i++) {
                hash = (hash ^ Float.floatToIntBits(water.lo(i))) * 0x100000001b3L;
                hash = (hash ^ Float.floatToIntBits(water.hi(i))) * 0x100000001b3L;
            }
        }
        return hash;
    }

    /** Snapshot of every individual, in world coordinates with its clearance figures. */
    public List<Individual> individuals() {
        List<Individual> out = new ArrayList<>();
        for (DomainState st : domains.values()) {
            for (int i = 0; i < st.members.size(); i++) out.add(snapshot(st, i));
        }
        return out;
    }

    private Individual snapshot(DomainState st, int i) {
        Member m = st.members.get(i);
        float l = st.engine.posL()[i], y = st.engine.posY()[i], d = st.engine.posD()[i];
        WaterDomain dom = st.domain;
        boolean inWater = dom != null && dom.contains(l, y, d);
        boolean fits = dom != null && dom.bodyFits(l, y, d, m.body);
        double open = dom == null ? Float.MAX_VALUE : dom.openFaceDistance(l, y, d);
        double surface = dom == null ? Double.MAX_VALUE : dom.surfaceAt(l, d) - y;
        double bed = dom == null ? Double.MAX_VALUE : y - dom.bedAt(l, d);
        return new Individual(m.id, st.patchKey, m.species, m.spec.length(),
                st.originX() + l, st.originY() + y, st.originZ() + d, st.engine.yawDeg[i],
                m.retiring(), m.retireReason, m.fade, m.bornTick, m.lifetimeTicks,
                open, surface, bed, inWater, fits);
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

    /**
     * The whole volume one individual's animated body can occupy, worst case: the horizontal radius
     * a yaw, bank and surf rotation can reach, plus the vertical room the pose and bob need above
     * and below its centre. A floor-walker stands on the floor, so nothing is needed below it — and
     * so does an anchored species (a garden eel stands in its burrow and sways from there).
     */
    public static WaterDomain.BodyBox bodyFor(Locomotion loco, float length) {
        float reach = 0.7071f * length;
        return switch (loco) {
            case BENTHIC, ANCHORED -> new WaterDomain.BodyBox(reach, length, 0f);
            case GLIDE -> new WaterDomain.BodyBox(reach, reach + 0.08f, reach + 0.08f);
            case DRIFT -> new WaterDomain.BodyBox(reach, reach + 0.05f, reach + 0.05f);
            default -> new WaterDomain.BodyBox(reach, reach + 0.125f, reach + 0.125f);
        };
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
        lastSpawnSkip = "no-spawn";
        if (mc.player == null) return;
        BlockPos player = mc.player.blockPosition();
        int px = player.getX(), py = player.getY(), pz = player.getZ();

        // Discovery's own tick carries the near pass, the budgeted revalidation of known columns —
        // the only path that ever notices an edit inside already-cached water — and the ring sweep,
        // all inside one shared cell budget (see WaterDiscovery.revalidatePass for the split).
        discovery.tick(source, px, py, pz, time);

        syncDomains(time, px, py, pz);
        applyContexts(time);
        expireMembers(time);
        retireAtFaces(time);
        updateFades(time);
        int changes = retireFinished(time);
        changes += spawnOne(time, px, py, pz, changes);
        stepEngines();
        dropDeadDomains(time);
        sweepRetained(time);
        recordStats();
        double millis = (System.nanoTime() - tickStarted) / 1_000_000.0;
        stats.ticks++;
        stats.totalTickMillis += millis;
        stats.peakTickMillis = Math.max(stats.peakTickMillis, millis);
    }

    // ------------------------------------------------------------------ contexts and turnover

    /**
     * Brings each shown patch's visual population in line with its authoritative context: reads the
     * store's fresh answer, and when the fingerprint moves, rebuilds the sampler and reconciles the
     * residents. Runs after {@link #syncDomains} so newly adopted patches are covered the tick they
     * appear, and before the retirement passes so an exclusion starts fading the same tick it is
     * decided.
     *
     * <p>A patch without a usable (OK) answer is left exactly as it is: no sampler is built, no
     * resident is classified, and {@link #spawnOne} will not spawn into it. Existing individuals
     * keep living under the ordinary policy — bounded lifetime, open faces, domain loss — rather
     * than being frozen or mass-retired, and a later answer resumes the same patch.
     */
    private void applyContexts(long time) {
        for (DomainState st : domains.values()) {
            AmbientContextStore.Context ctx = AmbientContextClient.store().freshContext(st.patchKey);
            st.contextOk = ctx != null;
            if (ctx == null) {
                stats.contextGaps++;
                if (st.unknownSince == Long.MIN_VALUE) st.unknownSince = time;
                if (time - st.unknownSince >= AmbientContextStore.PENDING_TIMEOUT_TICKS) {
                    for (Member member : st.members) beginRetire(st, member, REASON_INVALID, time);
                }
                continue;
            }
            st.unknownSince = Long.MIN_VALUE;
            if (st.domain != null && (st.sampler == null || ctx.fingerprint() != st.appliedFingerprint
                    || st.samplerGeometrySignature != st.builtGeometrySignature)) {
                st.appliedFingerprint = ctx.fingerprint();
                buildSampler(st, ctx);
                reconcile(st, ctx, time);
            }
        }
    }

    /**
     * Builds one patch's sampler from an authoritative context: the mixture over the candidates
     * this client can actually show here. Restriction happens before normalisation and every
     * omission is recorded — a species that fails the global visual check (no item, locomotion
     * mismatch) or this domain's own locomotion gate is left out of the sampler and can never be
     * drawn, never shown as something it is not.
     */
    private void buildSampler(DomainState st, AmbientContextStore.Context ctx) {
        List<WaterDiscovery.Column> placementColumns = discovery.columnsOf(st.patchKey);
        placementColumns.sort(Comparator.comparingInt(WaterDiscovery.Column::x).thenComparingInt(WaterDiscovery.Column::z));
        int global = 0;
        for (var candidate : ctx.candidates()) {
            if (visualFor(candidate.species(), level) == null) global++;
        }
        st.sampler = VisualSampler.build(ctx.candidates(), id -> {
            SpeciesVisual visual = visualFor(id, level);
            return visual != null && st.engine.passesGate(visual.spec().locomotion(), visual.spec().length())
                    && hasPlacement(st, visual, placementColumns);
        });
        stats.samplerBuilds++;
        st.samplerGeometrySignature = st.builtGeometrySignature;
        stats.unrepresentableOmissions += global;
        stats.exclusions += st.sampler.omitted().size();
        // The domain reserves head-room for the tallest species the sampler can draw; a change
        // here marks the domain stale so the next sync rebuilds it with the right margin.
        float top = 0f;
        for (VisualSampler.Entry entry : st.sampler.entries()) {
            SpeciesVisual visual = visuals.get(entry.species());
            if (visual != null) top = Math.max(top, visual.body().above());
        }
        st.topMargin = top;
    }

    /** Bounded deterministic fit probe before normalizing; no random placement retries change weights. */
    private static boolean hasPlacement(DomainState st, SpeciesVisual visual, List<WaterDiscovery.Column> columns) {
        WaterDomain.BodyBox body = visual.body();
        int probes = Math.min(32, columns.size());
        for (int i = 0; i < probes; i++) {
            WaterDiscovery.Column c = columns.get(i * columns.size() / probes);
            float l = c.x() + 0.5f - st.originX(), d = c.z() + 0.5f - st.originZ();
            if ((visual.spec().locomotion() == Locomotion.BENTHIC || visual.spec().locomotion() == Locomotion.ANCHORED)
                    && !Float.isFinite(st.domain.bedAt(l, d))) continue;
            float low = st.domain.bottomAt(l, d) + body.below() + 0.02f;
            float high = st.domain.surfaceAt(l, d) - body.above() - 0.02f;
            float y = AmbientPlacement.height(visual.spec().locomotion(), st.domain.bedAt(l, d), low, high, 0.5f);
            if (high > low && st.domain.bodyFits(l, y, d, body)) return true;
        }
        return false;
    }

    /**
     * Classifies every resident against the raw candidate list of a newly applied context:
     * a species that is no longer in the pool at all is excluded and starts its eligibility fade;
     * everything else is retained untouched. A current OK answer with no candidates excludes
     * everyone; pending/rejected/stale answers are handled separately by the bounded grace policy.
     */
    private void reconcile(DomainState st, AmbientContextStore.Context ctx, long time) {
        java.util.Set<String> pool = new java.util.HashSet<>(ctx.candidates().size());
        for (var candidate : ctx.candidates()) pool.add(candidate.species().toString());
        for (Member m : st.members) {
            if (m.retiring()) continue;
            if (!pool.contains(m.species)) beginRetire(st, m, REASON_EXCLUDED, time);
        }
    }

    /**
     * Retires every resident whose own bounded lifetime has run out. The lifetime was drawn at
     * spawn from the patch's stream with no species input, so every species turns over at the same
     * rate and abundance is decided by what replaces an individual, not by who lives longer.
     */
    private void expireMembers(long time) {
        for (DomainState st : domains.values()) {
            for (Member m : st.members) {
                if (m.retiring()) continue;
                if (time - m.bornTick >= m.lifetimeTicks) beginRetire(st, m, REASON_EXPIRED, time);
            }
        }
    }

    /**
     * Brings the set of shown domains up to date: patches that are no longer live (or no longer
     * among the nearest few) start dying, new ones are adopted, and at most one domain is rebuilt
     * from the current columns each tick.
     */
    private void syncDomains(long time, int px, int py, int pz) {
        List<WaterDiscovery.Patch> live = new ArrayList<>();
        for (WaterDiscovery.Patch p : discovery.livePatches()) {
            if (p.columns() >= MIN_PATCH_COLUMNS) live.add(p);
        }
        var occupied = new java.util.HashSet<Long>();
        for (DomainState st : domains.values()) {
            if (!st.dying && !st.members.isEmpty()) occupied.add(st.patchKey);
        }
        var selection = AmbientPatchSelection.select(live, discovery.columns(), occupied, px, pz, budget.domains());
        live = selection.patches();
        patchDistances = selection.distances();

        for (DomainState st : domains.values()) {
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
        for (WaterDiscovery.Patch p : live) {
            DomainState st = domains.get(p.key());
            if (st != null) continue;
            if (domains.size() >= budget.domains()) break;
            DomainState adopted = new DomainState(p, p.key());
            restoreRetained(adopted, time);
            domains.put(p.key(), adopted);
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
            if (st.dying || st.refilling) continue;
            int distance = patchDistance(st.patch);
            // A margin the domain was not built with is as much a staleness as a moved column:
            // the reserved head-room is what keeps a tall species' spawns inside the water.
            boolean stale = st.builtColumns != st.patch.columns()
                    || st.builtTopMargin != st.topMargin
                    || !recent(st.builtTick, time, REBUILD_INTERVAL_TICKS);
            if (stale && distance < bestDistance) {
                bestDistance = distance;
                dirtiest = st;
            }
        }
        if (dirtiest != null) rebuild(dirtiest, time, px, py, pz);
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
        List<WaterDiscovery.Column> columns = discovery.columnsOf(st.patchKey);
        st.builtTick = time;
        st.builtColumns = st.patch.columns();
        st.builtTopMargin = st.topMargin;
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
        columns.sort(Comparator.comparingInt(WaterDiscovery.Column::x).thenComparingInt(WaterDiscovery.Column::z));
        st.builtGeometrySignature = geometrySignature(columns);
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
                stats.outsideWater++;
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
        m.fadeTicks = (reason == REASON_EXCLUDED || reason == REASON_EXPIRED)
                ? AmbientTuning.POPULATION_FADE_TICKS : FADE_TICKS;
        if (reason == REASON_OPEN_FACE) stats.retireOpenFace++;
        else if (reason == REASON_INVALID) stats.retireInvalid++;
        else if (reason == REASON_DOMAIN_LOST) stats.retireDomainLost++;
        else if (reason == REASON_EXCLUDED) stats.retireExcluded++;
        else if (reason == REASON_EXPIRED) stats.retireExpired++;
    }

    /** Removes members whose fade has run out, up to the tick's membership budget. */
    private int retireFinished(long time) {
        int removed = 0;
        for (DomainState st : domains.values()) {
            scratchMembers.clear();
            for (Member m : st.members) {
                if (m.retiring() && m.fade <= 0f && removed < MEMBERSHIP_CHANGES_PER_TICK) {
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
                if (!m.retiring()) continue;
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

    /** Drops a dying domain once its last resident has faded away, retaining its random state. */
    private void dropDeadDomains(long time) {
        var it = domains.entrySet().iterator();
        while (it.hasNext()) {
            DomainState st = it.next().getValue();
            if (st.dying && st.members.isEmpty()) {
                retainState(st, time);
                it.remove();
            }
        }
    }

    /**
     * Puts a dropped patch's random stream and spawn bookkeeping into the bounded retention map, so
     * stepping out of a patch and back does not restart its stream from the seed and re-deal the
     * same first draws. The map is bounded twice — by age ({@code RETENTION_TICKS}) and by count
     * ({@code MAX_RETAINED_PATCHES}, oldest evicted) — and cleared when the runtime does. Only
     * state is kept: no individuals survive a drop, so the patch repopulates through the ordinary
     * spawn and turnover policy when it is next shown.
     */
    private void retainState(DomainState st, long time) {
        if (retained.size() >= AmbientTuning.MAX_RETAINED_PATCHES && !retained.containsKey(st.patchKey)) {
            Map.Entry<Long, Retained> oldest = null;
            for (Map.Entry<Long, Retained> entry : retained.entrySet()) {
                if (oldest == null || entry.getValue().storedTick() < oldest.getValue().storedTick()) {
                    oldest = entry;
                }
            }
            if (oldest != null) {
                retained.remove(oldest.getKey());
                stats.retainedExpiries++;
            }
        }
        retained.put(st.patchKey, new Retained(st.speciesRandom.state(), st.spawnCount, time, st.rolls));
        stats.retainedStores++;
    }

    /** Restores a retained stream into a freshly adopted domain, if it is still inside its bound. */
    private void restoreRetained(DomainState st, long time) {
        Retained state = retained.remove(st.patchKey);
        if (state == null) return;
        if (time - state.storedTick() > AmbientTuning.RETENTION_TICKS) {
            stats.retainedExpiries++;
            return;
        }
        st.speciesRandom.restore(state.streamState());
        st.rolls = state.rolls();
        st.spawnCount = state.spawnCount();
        stats.retainedRestores++;
    }

    /** Forgets retained states older than the retention bound. The map is small and bounded. */
    private void sweepRetained(long time) {
        retained.entrySet().removeIf(entry -> {
            boolean expired = time - entry.getValue().storedTick() > AmbientTuning.RETENTION_TICKS;
            if (expired) stats.retainedExpiries++;
            return expired;
        });
    }

    /**
     * Adds at most one individual: to the nearest domain that is short of its target, has room and
     * a usable authoritative context, drawing its species from that patch's visual sampler — one
     * independent draw per individual, never species-then-school. A species the domain cannot host
     * or no position can hold is omitted, never demoted, and a patch with no usable context (or
     * nothing representable) spawns nothing at all: the population only ever comes from a pool the
     * server named.
     */
    private int spawnOne(long time, int px, int py, int pz, int changes) {
        if (changes >= MEMBERSHIP_CHANGES_PER_TICK) return spawnSkip("changes");
        if (stats.individuals >= budget.individuals()) return spawnSkip("cap");

        DomainState best = null;
        int bestDistance = Integer.MAX_VALUE;
        int dying = 0, unbuilt = 0, full = 0, retry = 0, noCtx = 0, empty = 0;
        for (DomainState st : domains.values()) {
            if (st.dying) {
                dying++;
                continue;
            }
            if (st.domain == null) {
                unbuilt++;
                continue;
            }
            if (st.members.size() >= targetFor(st)) {
                full++;
                continue;
            }
            if (recent(st.lastOmissionTick, time, OMISSION_RETRY_TICKS)) {
                retry++;
                continue;
            }
            if (!st.contextOk) {
                noCtx++;
                continue;
            }
            if (st.sampler == null || st.sampler.isEmpty()) {
                empty++;
                continue;
            }
            int distance = patchDistance(st.patch);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = st;
            }
        }
        if (best == null) {
            if (noCtx > 0) stats.spawnsBlockedNoContext++;
            else if (empty > 0) stats.spawnsBlockedEmptyPool++;
            return spawnSkip("none d=" + dying + " u=" + unbuilt + " f=" + full + " r=" + retry
                    + " c=" + noCtx + " e=" + empty);
        }

        // One draw per spawn attempt, spent even when the attempt fails: slots are filled by
        // independent individual draws, so nothing here can turn "hard to place" into "keeps
        // being retried". The stream is the patch's own and retention resumes it mid-sequence.
        int slot = 0;
        while (slot < AmbientPopulationRolls.MAX_SLOTS) {
            boolean occupied = false;
            for (Member member : best.members) if (member.slot == slot) occupied = true;
            if (!occupied) break;
            slot++;
        }
        if (slot == AmbientPopulationRolls.MAX_SLOTS) return spawnSkip("slots");
        DomainState chosen = best;
        AmbientPopulationRolls.Choice choice = best.rolls.choose(slot, best.appliedFingerprint, time,
                id -> chosen.sampler.entries().stream().anyMatch(entry -> entry.species().equals(id)),
                () -> new AmbientPopulationRolls.Choice(chosen.sampler.sample(chosen.speciesRandom),
                        time + AmbientTuning.lifetimeTicks(chosen.speciesRandom)));
        String species = choice.species();
        best.spawnCount++;
        if (species == null) return spawnSkip("norep");
        SpeciesVisual visual = visuals.get(species);
        if (visual == null) return spawnSkip("novisual " + species);

        FishSpec spec = visual.spec();
        WaterDomain.BodyBox body = visual.body();
        boolean mirrored = random.nextBoolean();
        spec = new FishSpec(spec.length(), spec.locomotion(), mirrored, spec.species(), spec.shelterUse());
        if (!best.engine.passesGate(spec.locomotion(), spec.length())) {
            // The domain moved under the sampler (it was gate-checked when the sampler was built).
            best.lastOmissionTick = time;
            stats.omissions++;
            return spawnSkip("gate " + spec.species());
        }
        float[] found = new float[3];
        if (!findSpawn(best, spec, body, px, pz, found)) {
            best.lastOmissionTick = time;
            stats.omissions++;
            return spawnSkip("nowhere " + spec.species());
        }
        spawnSkip("spawned " + spec.species());
        Member m = new Member(nextId++, species, spec, body, visual.stack().copy(), visual.anim(),
                time, (int) Math.max(1, choice.expiresTick() - time));
        m.slot = slot;
        best.members.add(m);
        reseed(best);
        int index = best.members.size() - 1;
        best.engine.placeFish(index, found[0], found[1], found[2]);
        stats.spawns++;
        return 1;
    }

    private int targetFor(DomainState st) {
        return budget.target(st.waterVolume);
    }

    /** Samples a position inside this domain's water where the whole body fits with margin. */
    private boolean findSpawn(DomainState st, FishSpec spec, WaterDomain.BodyBox body, int px, int pz, float[] out) {
        WaterDomain d = st.domain;
        if (d == null) return false;
        for (int attempt = 0; attempt < SPAWN_ATTEMPTS; attempt++) {
            float wx = px + 0.5f + (random.nextFloat() * 2f - 1f) * SPAWN_RADIUS;
            float wz = pz + 0.5f + (random.nextFloat() * 2f - 1f) * SPAWN_RADIUS;
            float dx = wx - (px + 0.5f), dz = wz - (pz + 0.5f);
            float dist2 = dx * dx + dz * dz;
            if (dist2 < SPAWN_NEAREST * SPAWN_NEAREST || dist2 > SPAWN_RADIUS * SPAWN_RADIUS) continue;
            float l = wx - st.originX(), dep = wz - st.originZ();
            if ((spec.locomotion() == Locomotion.BENTHIC || spec.locomotion() == Locomotion.ANCHORED)
                    && !Float.isFinite(d.bedAt(l, dep))) continue;
            float bed = d.bottomAt(l, dep), surface = d.surfaceAt(l, dep);
            if (!Float.isFinite(bed) || !Float.isFinite(surface)) continue;
            float lo = bed + body.below() + 0.02f;
            float hi = surface - body.above() - 0.02f;
            if (hi <= lo) continue;
            float y = AmbientPlacement.height(spec.locomotion(), d.bedAt(l, dep), lo, hi, random.nextFloat());
            if (!d.bodyFits(l, y, dep, body)) continue;
            if (d.openFaceDistance(l, y, dep) <= WaterDomain.OPEN_FACE_MARGIN + 0.05f) continue;
            boolean crowded = false;
            for (int i = 0; i < st.members.size(); i++) {
                float dl = st.engine.posL()[i] - l, dy = st.engine.posY()[i] - y, dd = st.engine.posD()[i] - dep;
                if (dl * dl + dy * dy + dd * dd < SPAWN_SEPARATION * SPAWN_SEPARATION) {
                    crowded = true;
                    break;
                }
            }
            if (crowded) continue;
            out[0] = l;
            out[1] = y;
            out[2] = dep;
            return true;
        }
        return false;
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
                if (d == null) continue;
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
            for (int k = 1; k < n; k++) {
                int i = orderScratch[k], at = k;
                while (at > 0 && depthScratch[orderScratch[at - 1]] < depthScratch[i]) {
                    orderScratch[at] = orderScratch[at - 1];
                    at--;
                }
                orderScratch[at] = i;
            }
            for (int k = 0; k < n; k++) {
                int i = orderScratch[k];
                Member m = st.members.get(i);
                double x = st.originX() + st.engine.renderX[i], y = st.originY() + st.engine.renderY[i],
                        z = st.originZ() + st.engine.renderZ[i];
                if (m.retirementPose != null) {
                    x = st.originX() + m.retirementPose.m30();
                    y = st.originY() + m.retirementPose.m31();
                    z = st.originZ() + m.retirementPose.m32();
                }
                double radius = Math.max(1.0, st.engine.lengths[i] * 2.0);
                if (!level.hasChunkAt(BlockPos.containing(x, y, z))) continue;
                if (!camera.getCullFrustum().isVisible(new AABB(x - radius, y - radius, z - radius,
                        x + radius, y + radius, z + radius))) continue;
                poses.pushPose();
                poses.translate(st.originX() - cam.x, st.originY() - cam.y, st.originZ() - cam.z);
                if (m.retiring()) {
                    if (m.retirementPose == null) {
                        PoseStack frozen = new PoseStack();
                        GroupFishPose.apply(frozen, st.engine, m.anim, i, t, 0f, 0f, 0f, poseRandom);
                        m.retirementPose = new Matrix4f(frozen.last().pose());
                    }
                    poses.mulPose(m.retirementPose);
                } else {
                    GroupFishPose.apply(poses, st.engine, m.anim, i, t, 0f, 0f, 0f, poseRandom);
                }
                mc.getItemModelResolver().updateForTopItem(m.state, m.stack, ItemDisplayContext.FIXED,
                        null, null, 0);
                if (m.fade < 1f) AmbientFishFade.apply(poses, m.fade, m.anim, m.state.getModelBoundingBox());
                int light = LevelRenderer.getLightCoords(level, BlockPos.containing(x, y, z));
                m.state.submit(poses, nodes, light, OverlayTexture.NO_OVERLAY, 0);
                poses.popPose();
                worldSubmitsThisFrame++;
            }
        }
        double millis = (System.nanoTime() - submitStarted) / 1_000_000.0;
        stats.renderFrames++;
        stats.totalSubmitMillis += millis;
        stats.peakSubmitMillis = Math.max(stats.peakSubmitMillis, millis);
    }
}
