package grill24.fishtastic.server;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.config.FishtasticServerConfig;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.network.FishSchoolInputPacket;
import grill24.fishtastic.network.FishSchoolSetupPacket;
import grill24.fishtastic.network.FishSchoolStatePacket;
import grill24.fishtastic.school.FishSchoolWire;
import grill24.fishtastic.network.FishtasticTransport;
import grill24.fishtastic.client.renderer.FishVisuals;
import grill24.fishtastic.school.FishSchoolSim;
import grill24.fishsim.core.FishSpec;
import grill24.fishsim.domain.WaterDomain;
import grill24.fishtastic.util.IFishingHookExtension;
import grill24.fishtastic.util.IServerLevelSchools;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Server authority for finite fish-school visits, attraction, bite ownership and tracking. */
public final class FishSchoolManager {
    private static final int REGION_SIZE = 128;
    private static final int REGION_RETENTION_TICKS = 1_440_000; // 60 in-game days
    private static final int MAX_RETAINED_REGIONS = 4096;
    private static final int GLOBAL_SCHOOL_CAP = 16;
    /**
     * A school's water is bounded by how far it spreads, not by its volume: at most this many
     * columns (about 11 blocks out from the anchor), each to its full depth in the search box. A
     * 256-cell cap filled a deep lake as a blob ~4 blocks across, so a bobber 2-3 blocks beside a
     * fish near its edge was outside the school's water and never got an approach (fishschoolreach
     * self-test, 2026-10-25: 9/32 bobber cells at 2 blocks, 13/32 at 3).
     */
    private static final int MAX_WATER_COLUMNS = 400;
    /** Safety bound on the search itself: 400 columns of the full 11-block search depth fit. */
    private static final int MAX_WATER_CELLS = 4400;
    private static final int MIN_WATER_CELLS = 6;
    private static final int MAX_HOOK_CHECKS_PER_TICK = 8;
    private static final int MAX_PLAYERS_PER_SNAPSHOT_TICK = 32;
    private static final int WATER_RADIUS = 16;
    private static final int WATER_DOWN = 6;
    private static final int WATER_UP = 4;
    private static final int SCHOOL_FORMATION_RADIUS = 4;
    private static final int SCHOOL_MEMBER_SYNC_TICKS = 10;
    /** A departing visit fades out over this long, then its members are gone (design decision). */
    private static final int DEPARTURE_TICKS = 60;
    private static final double INTEREST_DISTANCE_SQR = 1024.0;
    /** A bobber that drifts this far from where its fish was sent is a new cast, blocks. */
    private static final double ANCHOR_DRIFT = 1.0;
    /** Saved positions are refreshed from the sims every tick but only written out this often. */
    private static final int POSITION_SAVE_TICKS = 100;
    /** A sim's held nose outlasts the longest bite it can be asked to wait for by this, ticks. */
    private static final int HOLD_SLACK_TICKS = 40;

    /** A changed block's school is rescanned this long after the changes stop... */
    private static final int REGION_DEBOUNCE_TICKS = 20;
    /** ...or this long after the first of them, if they never stop (water freezing all over). */
    private static final int REGION_MAX_DIRTY_TICKS = 100;
    /** A region that met an unloaded chunk is rechecked this often, so its edge rejoins on load. */
    private static final int REGION_INCOMPLETE_RECHECK_TICKS = 100;
    /**
     * Safety rescan for changes that bypass {@code setBlock}, spread over this many ticks more by
     * school so schools created together do not rescan together.
     */
    private static final int REGION_SAFETY_RESCAN_TICKS = 1200, REGION_SAFETY_SPREAD_TICKS = 200;
    private static final long NOT_DIRTY = Long.MIN_VALUE;

    /**
     * A school's water, rescanned only when a block in its search box changed, a chunk it met
     * unloaded may have loaded, or the slow safety period ran out (performance plan, step A).
     */
    private static final class RegionCache {
        Set<BlockPos> cells;
        long checkedAt;
        /** The scan met an unloaded chunk, so cells there are missing for now. */
        boolean incomplete;
        long firstDirtyAt = NOT_DIRTY, lastDirtyAt = NOT_DIRTY;
        /** Per cell ({@link BlockPos#asLong}): whether the species may live there. Biome and height do not change. */
        final Map<Long, Boolean> zoneEligible = new HashMap<>();

        RegionCache(WaterScan scan, long now) {
            set(scan, now);
        }

        void set(WaterScan scan, long now) {
            cells = scan.cells();
            incomplete = scan.incomplete();
            checkedAt = now;
            firstDirtyAt = lastDirtyAt = NOT_DIRTY;
        }

        void markDirty(long now) {
            if (firstDirtyAt == NOT_DIRTY) firstDirtyAt = now;
            lastDirtyAt = now;
        }

        boolean due(long now, UUID school) {
            if (firstDirtyAt != NOT_DIRTY
                    && (now - lastDirtyAt >= REGION_DEBOUNCE_TICKS || now - firstDirtyAt >= REGION_MAX_DIRTY_TICKS)) return true;
            if (incomplete && now - checkedAt >= REGION_INCOMPLETE_RECHECK_TICKS) return true;
            return now - checkedAt >= REGION_SAFETY_RESCAN_TICKS + Math.floorMod(school.hashCode(), REGION_SAFETY_SPREAD_TICKS);
        }
    }

    /** A connected-water search: the cells, and whether it met an unloaded chunk. */
    private record WaterScan(Set<BlockPos> cells, boolean incomplete) {}

    /**
     * One school's motion for the current epoch: its sim, the setup it was built from and every
     * input applied since, in order, which is exactly what a client copy needs to replay to step N.
     * An epoch begins when the school is created or first ticked after a load; positions carry
     * over from the saved data, so a reload is a new start from where the fish were.
     */
    private static final class SchoolRuntime {
        final FishSchoolSim sim;
        final FishSchoolSim.Setup setup;
        /** Identifies this epoch to clients: a copy of another epoch is rebuilt, never patched. */
        final int epoch;
        final List<FishSchoolSim.Stamped> inputs = new ArrayList<>();
        /** Players whose client holds this epoch's setup, and so is sent each new input. */
        final Set<UUID> trackers = new HashSet<>();
        /** Fingerprint of the positions right after the latest step, before that step's inputs. */
        int checksum;
        final java.util.function.Consumer<FishSchoolSim.Stamped> onInput;
        /** Anchors whose fish has touched them this reservation. */
        final Set<UUID> touched = new HashSet<>();
        /** The world point each live anchor was placed at. */
        final Map<UUID, Vec3> anchorPoints = new HashMap<>();
        /** The water the sim's domain was last built from. */
        Set<BlockPos> water;
        long lastAdvancedAt = Long.MIN_VALUE;
        /** Why the last approach ended without a bite, for {@link #debugState}. */
        String lastFailure = "none";

        SchoolRuntime(FishSchoolSim sim, FishSchoolSim.Setup setup, int epoch, Set<BlockPos> water,
                      java.util.function.Consumer<FishSchoolSim.Stamped> onInput) {
            this.sim = sim;
            this.setup = setup;
            this.epoch = epoch;
            this.water = water;
            this.onInput = onInput;
            this.checksum = sim.checksum();
        }

        boolean apply(FishSchoolSim.Input input) {
            FishSchoolSim.Stamped stamped = new FishSchoolSim.Stamped(sim.steps(), input);
            inputs.add(stamped);
            onInput.accept(stamped);
            return sim.apply(input);
        }
    }

    /** In-memory session token. The saved member remains the authority for its state. */
    public record SessionBinding(UUID schoolId, UUID memberId, UUID playerId, int sessionId,
                                 int targetIndex, ResourceLocation species, long rewardSeed) {
        public SessionBinding withTargetIndex(int index) {
            return new SessionBinding(schoolId, memberId, playerId, sessionId, index, species, rewardSeed);
        }
    }

    private final ServerLevel level;
    private final FishSchoolSavedData data;
    private final Map<UUID, RegionCache> waterRegions = new HashMap<>();
    /**
     * Per player: the schools whose setup its client holds, and the epoch it holds. Tied to the
     * player instance: a relog or respawn makes a new one, whose client holds nothing yet.
     */
    private record Tracking(ServerPlayer player, Map<UUID, Integer> schools) {}
    private final Map<UUID, Tracking> tracked = new HashMap<>();
    /** A hook the engine just refused a fish for is not offered another this soon (ticks). */
    private final Map<UUID, Long> approachRetryAt = new HashMap<>();
    private int nextEpoch;
    /** Each live school's motion, by school id. Not saved: a reload starts a new epoch. */
    private final Map<UUID, SchoolRuntime> sims = new HashMap<>();
    private int playerCursor;
    private int interestPlayerCursor;
    private int syncPlayerCursor;

    /** Ticks before a hook the engine refused a fish for is offered one again. */
    private static final int APPROACH_RETRY_TICKS = 40;
    /** Dev/self-test: log every approach's start, bite and failure (with why) to the server log. */
    public static boolean logApproaches;
    /** Past this many inputs an epoch is retired and a new one started, so its replay stays bounded. */
    private static final int EPOCH_INPUT_LIMIT = FishSchoolWire.MAX_INPUTS - 64;
    /**
     * An epoch no client holds is renewed past this many steps, so a late joiner replays at most
     * about this much (~5 ms at 4 us a step) instead of a whole visit (performance plan, step E).
     */
    private static final int UNWATCHED_EPOCH_STEPS = 1200;

    FishSchoolManager(ServerLevel level) {
        this.level = level;
        this.data = FishSchoolSavedData.get(level);
        // Epochs only need to differ from what a connected client may still hold.
        this.nextEpoch = level.getRandom().nextInt(1 << 30);
        ((IServerLevelSchools) level).fishtastic$setSchools(this);
    }

    /**
     * A block changed in this level (from {@code ServerLevelSchoolsMixin}, on every successful
     * {@code setBlock}). Marks each school whose water search box, plus the block above its top
     * (a surface's height reads it), holds the change; the rescan waits for the changes to settle.
     */
    public void onBlockChanged(BlockPos pos) {
        if (data.schools().isEmpty() || !level.getServer().isSameThread()) return;
        long now = level.getGameTime();
        for (FishSchoolSavedData.School school : data.schools()) {
            BlockPos a = school.anchor();
            if (Math.abs(pos.getX() - a.getX()) > WATER_RADIUS + 1 || Math.abs(pos.getZ() - a.getZ()) > WATER_RADIUS + 1
                    || pos.getY() < a.getY() - WATER_DOWN - 1 || pos.getY() > a.getY() + WATER_UP + 1) continue;
            RegionCache cache = waterRegions.get(school.id());
            if (cache != null) cache.markDirty(now);
        }
    }

    public static FishSchoolManager get(ServerLevel level) {
        return FishSchoolSavedData.get(level).manager(level);
    }

    public void tick() {
        long now = level.getGameTime();
        FishSchoolPerf.skipLevel = level.players().isEmpty() && data.schools().isEmpty();
        long tickStart = FishSchoolPerf.start();
        long t = FishSchoolPerf.start();
        if (now % 20 == 0) scheduleNearbyRegionAttempts(now);
        FishSchoolPerf.end(FishSchoolPerf.Phase.SCHEDULE, t);
        t = FishSchoolPerf.start();
        advanceSims(now);
        FishSchoolPerf.end(FishSchoolPerf.Phase.ADVANCE, t);
        t = FishSchoolPerf.start();
        tickVisitsAndMembers(now);
        FishSchoolPerf.end(FishSchoolPerf.Phase.VISITS, t);
        t = FishSchoolPerf.start();
        if (now % 5 == 0) reserveNearbyHooks(now);
        FishSchoolPerf.end(FishSchoolPerf.Phase.HOOKS, t);
        if (now % POSITION_SAVE_TICKS == 0 && !data.schools().isEmpty()) data.markDirtyNow();
        t = FishSchoolPerf.start();
        if (now % SCHOOL_MEMBER_SYNC_TICKS == 0) syncNearbyPlayers(now);
        FishSchoolPerf.end(FishSchoolPerf.Phase.SYNC, t);
        FishSchoolPerf.endTick(tickStart);
    }

    /**
     * One engine step per game tick for every school whose anchor is loaded: once per tick however
     * often {@link #tick} is called, because every client copy steps once per tick too. Saved
     * positions follow the engine; touches are recorded for the bite check.
     */
    private void advanceSims(long now) {
        for (FishSchoolSavedData.School school : data.schools()) {
            // Simulation pauses while the anchor chunk is unloaded; no call below loads it.
            if (!level.hasChunkAt(school.anchor())) continue;
            SchoolRuntime runtime = sims.get(school.id());
            if (runtime != null && (runtime.inputs.size() > EPOCH_INPUT_LIMIT
                    || runtime.trackers.isEmpty() && runtime.anchorPoints.isEmpty() && runtime.sim.steps() >= UNWATCHED_EPOCH_STEPS)) {
                // A new epoch from where the fish are; tracking clients are sent its setup. One
                // nobody holds is renewed while it is short, so the next to arrive replays little.
                sims.remove(school.id());
                runtime = null;
            }
            runtime = runtime(school, now);
            if (runtime == null || runtime.lastAdvancedAt == now) continue;
            boolean first = runtime.lastAdvancedAt == Long.MIN_VALUE;
            runtime.lastAdvancedAt = now;
            if (first) continue; // the epoch's first tick is its step 0, the setup itself
            SchoolRuntime stepped = runtime;
            runtime.sim.step(event -> {
                if (event.touch()) stepped.touched.add(event.anchor());
            });
            runtime.checksum = runtime.sim.checksum();
            syncPositions(school, runtime);
        }
    }

    private static void syncPositions(FishSchoolSavedData.School school, SchoolRuntime runtime) {
        FishSchoolSim sim = runtime.sim;
        BlockPos o = sim.origin();
        for (FishSchoolSavedData.Member member : school.members()) {
            int i = sim.indexOf(member.id());
            if (i < 0) continue;
            member.setPosition(new FishSchoolSavedData.Position(o.getX() + (double) sim.engine().posL()[i],
                    o.getY() + (double) sim.engine().posY()[i], o.getZ() + (double) sim.engine().posD()[i],
                    sim.yawDeg(i)));
        }
    }

    /** The school's sim, starting an epoch if it has none. Null when its water cannot host one. */
    private @Nullable SchoolRuntime runtime(FishSchoolSavedData.School school, long now) {
        SchoolRuntime runtime = sims.get(school.id());
        if (runtime != null) return runtime;
        Set<BlockPos> water = currentWaterRegion(school, now);
        FishSpec spec = speciesSpec(school.species());
        if (water.isEmpty() || spec == null) return null;
        BlockPos origin = school.anchor();
        List<FishSchoolSim.Placement> placements = new ArrayList<>();
        for (FishSchoolSavedData.Member member : school.members()) {
            if (inWater(member.state())) placements.add(placement(member, origin, water));
        }
        FishSchoolSim.Setup setup = new FishSchoolSim.Setup(origin, columns(water), spec,
                school.id().getMostSignificantBits() ^ now, placements);
        FishSchoolSim sim = FishSchoolSim.create(setup);
        if (sim == null) return null;
        UUID id = school.id();
        int epoch = nextEpoch++ & Integer.MAX_VALUE;
        SchoolRuntime created = new SchoolRuntime(sim, setup, epoch, water, stamped -> broadcastInput(id, stamped));
        sims.put(id, created);
        return created;
    }

    /** States in which a member is a fish in the water, and so in its school's sim. */
    private static boolean inWater(FishSchoolSavedData.MemberState state) {
        return switch (state) {
            case AVAILABLE, APPROACHING, BITE_READY, RECOVERING, DEPARTING -> true;
            case IN_SESSION, CAUGHT, DEPARTED -> false;
        };
    }

    /** A member's saved position in the school's frame, moved to the nearest water cell if out of it. */
    private static FishSchoolSim.Placement placement(FishSchoolSavedData.Member member, BlockPos origin,
                                                     Set<BlockPos> water) {
        FishSchoolSavedData.Position p = member.position();
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        if (!water.contains(BlockPos.containing(at))) {
            Vec3 from = at;
            BlockPos nearest = water.stream().min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(from))).orElseThrow();
            at = Vec3.atCenterOf(nearest);
        }
        return new FishSchoolSim.Placement(member.id(), (float) (at.x - origin.getX()),
                (float) (at.y - origin.getY()), (float) (at.z - origin.getZ()));
    }

    /** The engine descriptor for a school of {@code species} at its mean size, or null if unknown. */
    private @Nullable FishSpec speciesSpec(ResourceLocation species) {
        Item item = BuiltInRegistries.ITEM.getOptional(species).orElse(null);
        if (item == null) return null;
        Registry<FishProfile> profiles = level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        float size = profiles.getOptional(ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, species))
                .map(profile -> profile.size().mean()).orElse(30f);
        ItemStack stack = new ItemStack(item);
        grill24.fishtastic.util.ItemSizeHelper.setSize(stack, size);
        // FishVisuals holds no client types (CompanionGroups uses it from shared code too).
        FishSpec resolved = FishVisuals.descriptor(stack, level, false);
        return FishSchoolSim.spec(resolved.length(), resolved.locomotion(), resolved.shelterUse());
    }

    /**
     * The visit's water as the engine's columns: each block column's contiguous runs of cells, the
     * top of a run at its fluid surface. Sorted, so the same cells always make the same list.
     */
    private List<WaterDomain.Column> columns(Set<BlockPos> water) {
        Map<Long, TreeMap<Integer, BlockPos>> byColumn = new TreeMap<>();
        for (BlockPos pos : water) {
            byColumn.computeIfAbsent(columnKey(pos), k -> new TreeMap<>())
                    .put(pos.getY(), pos);
        }
        List<WaterDomain.Column> columns = new ArrayList<>(byColumn.size());
        for (TreeMap<Integer, BlockPos> column : byColumn.values()) {
            List<Float> spans = new ArrayList<>();
            int runStart = Integer.MIN_VALUE, previous = Integer.MIN_VALUE;
            for (int y : column.keySet()) {
                if (runStart != Integer.MIN_VALUE && y != previous + 1) {
                    addSpan(spans, column.get(previous), runStart);
                    runStart = Integer.MIN_VALUE;
                }
                if (runStart == Integer.MIN_VALUE) runStart = y;
                previous = y;
            }
            if (runStart != Integer.MIN_VALUE) addSpan(spans, column.get(previous), runStart);
            int n = Math.min(spans.size(), WaterDomain.MAX_SPANS * 2);
            float[] out = new float[n];
            for (int i = 0; i < n; i++) out[i] = spans.get(i);
            BlockPos any = column.firstEntry().getValue();
            columns.add(new WaterDomain.Column(any.getX(), any.getZ(), out, false));
        }
        return columns;
    }

    /** One key per (x, z) column. */
    private static long columnKey(BlockPos pos) {
        return ((long) pos.getX() << 32) ^ (pos.getZ() & 0xFFFFFFFFL);
    }

    private void addSpan(List<Float> spans, BlockPos top, int bottomY) {
        spans.add((float) bottomY);
        // Open water above the run: the surface is the fluid's own height in its top block.
        boolean wetAbove = level.getFluidState(top.above()).is(FluidTags.WATER);
        spans.add(top.getY() + (wetAbove ? 1f : level.getFluidState(top).getHeight(level, top)));
    }

    /** Calls off the fish sent to {@code anchor}, if any. */
    private void withdraw(FishSchoolSavedData.School school, UUID anchor) {
        SchoolRuntime runtime = sims.get(school.id());
        if (runtime == null) return;
        if (runtime.sim.hasAnchor(anchor)) runtime.apply(new FishSchoolSim.Withdraw(anchor));
        runtime.touched.remove(anchor);
        runtime.anchorPoints.remove(anchor);
    }

    /** Takes the member out of its school's water (hooked, caught or gone). */
    private void leaveWater(FishSchoolSavedData.School school, FishSchoolSavedData.Member member) {
        SchoolRuntime runtime = sims.get(school.id());
        if (runtime != null && runtime.sim.indexOf(member.id()) >= 0) runtime.apply(new FishSchoolSim.Leave(member.id()));
    }

    /**
     * One line on a school's motion, for the debug command and for test failures: the sim's step,
     * each member's engine index, state and frame position, the live anchors and the input count.
     */
    public String debugState(UUID schoolId) {
        FishSchoolSavedData.School school = findSchool(schoolId);
        if (school == null) return "no school " + schoolId;
        SchoolRuntime runtime = sims.get(schoolId);
        if (runtime == null) return "school " + schoolId + " has no sim (water=" + currentWaterRegion(school, level.getGameTime()).size() + ")";
        FishSchoolSim sim = runtime.sim;
        StringBuilder out = new StringBuilder("sim step=" + sim.steps() + " inputs=" + runtime.inputs.size()
                + " water=" + runtime.water.size() + " members=[");
        for (FishSchoolSavedData.Member member : school.members()) {
            int i = sim.indexOf(member.id());
            out.append(member.state()).append('#').append(i);
            if (i >= 0) {
                out.append(String.format(java.util.Locale.ROOT, "@(%.2f,%.2f,%.2f) shelter=%d", sim.engine().posL()[i],
                        sim.engine().posY()[i], sim.engine().posD()[i], sim.engine().shelterState(i)));
            }
            out.append(' ');
        }
        out.append("] anchors=").append(runtime.anchorPoints.keySet()).append(" touched=").append(runtime.touched)
                .append(" lastFailure=").append(runtime.lastFailure).append(" recent=[");
        for (int k = Math.max(0, runtime.inputs.size() - 8); k < runtime.inputs.size(); k++) {
            FishSchoolSim.Stamped in = runtime.inputs.get(k);
            out.append(in.step()).append(':').append(in.input().getClass().getSimpleName()).append(' ');
        }
        out.append(']');
        return out.toString();
    }

    /**
     * Debug: starts a visit in the water nearest {@code near} now, skipping the region schedule,
     * the encounter roll and the overlap rule but not the ecology or the member budgets, so what
     * appears is a school that could have formed there. A line saying what happened.
     */
    public String spawnForDebug(BlockPos near, @Nullable ResourceLocation species) {
        BlockPos water = nearestWater(near, 12, 5, 2);
        if (water == null) return "No valid source water within 12 blocks.";
        Holder<Item> forced = null;
        if (species != null) {
            Item item = BuiltInRegistries.ITEM.getOptional(species).orElse(null);
            if (item == null || !item.builtInRegistryHolder().is(FishtasticItemTags.FISH_SCHOOL_ENABLED)) {
                return species + " is not a school-enabled species.";
            }
            forced = item.builtInRegistryHolder();
        }
        FishSchoolSavedData.School school = tryCreateSchool(water, level.getGameTime(), forced);
        if (school == null) {
            return "No school formed at " + water.toShortString() + ": no school-enabled species fits this water,"
                    + " the water is too small, or the member budget is spent.";
        }
        return "Spawned " + school.members().size() + " " + school.species() + " at " + school.anchor().toShortString()
                + " (school " + school.id() + ").";
    }

    /** Debug: the nearest school's species, state and {@link #debugState}, or a line saying there is none. */
    public String debugNearest(Vec3 near) {
        FishSchoolSavedData.School nearest = data.schools().stream()
                .min(Comparator.comparingDouble(school -> school.anchor().distToCenterSqr(near))).orElse(null);
        if (nearest == null) return "No fish schools in this dimension.";
        return nearest.species() + " " + nearest.state() + " at " + nearest.anchor().toShortString()
                + " (" + (int) Math.sqrt(nearest.anchor().distToCenterSqr(near)) + " blocks): " + debugState(nearest.id());
    }

    /** Test seam: where each AVAILABLE member of the nearest school is, and the size of its water region. */
    public List<Vec3> availableMembersForTest(Vec3 near) {
        FishSchoolSavedData.School nearest = data.schools().stream()
                .min(Comparator.comparingDouble(school -> school.anchor().distToCenterSqr(near))).orElse(null);
        if (nearest == null) return List.of();
        List<Vec3> out = new ArrayList<>();
        for (FishSchoolSavedData.Member member : nearest.members()) {
            if (member.state() == FishSchoolSavedData.MemberState.AVAILABLE) out.add(vec(member.position()));
        }
        return out;
    }

    /** Test seam: whether {@code pos} is in the nearest school's water region. */
    public boolean inSchoolWaterForTest(Vec3 near, BlockPos pos) {
        FishSchoolSavedData.School nearest = data.schools().stream()
                .min(Comparator.comparingDouble(school -> school.anchor().distToCenterSqr(near))).orElse(null);
        return nearest != null && currentWaterRegion(nearest, level.getGameTime()).contains(pos);
    }

    /** Test seam: the school's sim step count this epoch, or -1 when it has no sim. */
    public long simStepsForTest(UUID schoolId) {
        SchoolRuntime runtime = sims.get(schoolId);
        return runtime == null ? -1 : runtime.sim.steps();
    }

    /** Test seam: whether the member is a fish in its school's water right now. */
    public boolean memberInWaterForTest(UUID schoolId, UUID memberId) {
        SchoolRuntime runtime = sims.get(schoolId);
        return runtime != null && runtime.sim.indexOf(memberId) >= 0;
    }

    /**
     * Test seam (performance plan step 0): forgets which setups every client holds, so the next
     * sync resends each one with its whole input history, exactly as to a late joiner.
     */
    public void forgetTrackingForTest() {
        tracked.clear();
        for (SchoolRuntime runtime : sims.values()) runtime.trackers.clear();
    }

    private @Nullable FishSchoolSavedData.School findSchool(UUID schoolId) {
        for (FishSchoolSavedData.School school : data.schools()) {
            if (school.id().equals(schoolId)) return school;
        }
        return null;
    }

    /** A school owns bite transitions only while one of its reservations owns this exact hook. */
    public static boolean ownsBite(FishingHook hook) {
        if (!(hook.level() instanceof ServerLevel serverLevel)) return false;
        return get(serverLevel).ownsHook(hook.getUUID());
    }

    private boolean ownsHook(UUID hookId) {
        for (FishSchoolSavedData.School school : data.schools()) {
            for (FishSchoolSavedData.Member member : school.members()) {
                FishSchoolSavedData.Reservation reservation = member.reservation();
                if (reservation != null && reservation.hookId().equals(hookId)
                        && (member.state() == FishSchoolSavedData.MemberState.APPROACHING
                        || member.state() == FishSchoolSavedData.MemberState.BITE_READY)) return true;
            }
        }
        return false;
    }

    /** Atomically transfers a bite-ready hook reservation into the newly-created minigame. */
    @Nullable
    public SessionBinding bindReadyMember(ServerPlayer player, FishingHook hook, int sessionId) {
        if (player.level() != level || player.isDeadOrDying() || player.fishing != hook || hook.isRemoved()
                || FishingMinigameManager.findFishtasticRod(player).isEmpty()) return null;
        long now = level.getGameTime();
        for (FishSchoolSavedData.School school : data.schools()) {
            for (FishSchoolSavedData.Member member : school.members()) {
                FishSchoolSavedData.Reservation r = member.reservation();
                if (member.state() != FishSchoolSavedData.MemberState.BITE_READY || r == null
                        || !r.playerId().equals(player.getUUID()) || !r.hookId().equals(hook.getUUID())
                        || now > r.leaseUntil()) continue;
                member.setState(FishSchoolSavedData.MemberState.IN_SESSION);
                member.setReservation(new FishSchoolSavedData.Reservation(player.getUUID(), hook.getUUID(),
                        r.startedAt(), r.leaseUntil(), sessionId, -1));
                // Hooked: out of the water for the fight, its bobber gone with it.
                leaveWater(school, member);
                withdraw(school, r.hookId());
                ((IFishingHookExtension) hook).fishtastic$clearSchoolBite();
                data.markDirtyNow();
                return new SessionBinding(school.id(), member.id(), player.getUUID(), sessionId, -1,
                        school.species(), member.rewardSeed());
            }
        }
        return null;
    }

    @Nullable
    public SessionBinding setBindingTarget(SessionBinding binding, int targetIndex) {
        FishSchoolSavedData.Member member = findMember(binding.schoolId(), binding.memberId());
        if (member == null || member.state() != FishSchoolSavedData.MemberState.IN_SESSION
                || member.reservation() == null || member.reservation().sessionId() != binding.sessionId()) return null;
        FishSchoolSavedData.Reservation old = member.reservation();
        if (!old.playerId().equals(binding.playerId()) || targetIndex < 0) return null;
        member.setReservation(new FishSchoolSavedData.Reservation(old.playerId(), old.hookId(), old.startedAt(),
                old.leaseUntil(), old.sessionId(), targetIndex));
        data.markDirtyNow();
        return binding.withTargetIndex(targetIndex);
    }

    /** Commits depletion before the ordinary reward pipeline runs, making replay harmless. */
    public boolean resolveCaught(SessionBinding binding) {
        FishSchoolSavedData.Member member = findMember(binding.schoolId(), binding.memberId());
        if (member == null || member.state() != FishSchoolSavedData.MemberState.IN_SESSION
                || member.reservation() == null
                || !member.reservation().playerId().equals(binding.playerId())
                || member.reservation().sessionId() != binding.sessionId()
                || member.reservation().targetIndex() != binding.targetIndex()) return false;
        member.setState(FishSchoolSavedData.MemberState.CAUGHT);
        member.setReservation(null);
        data.markDirtyNow();
        return true;
    }

    public void releaseBinding(SessionBinding binding) {
        FishSchoolSavedData.Member member = findMember(binding.schoolId(), binding.memberId());
        if (member == null || member.state() != FishSchoolSavedData.MemberState.IN_SESSION
                || member.reservation() == null
                || !member.reservation().playerId().equals(binding.playerId())
                || member.reservation().sessionId() != binding.sessionId()) return;
        FishSchoolSavedData.School school = findSchool(binding.schoolId());
        recover(school, member, level.getGameTime());
        // It got away: back in the water where it was taken.
        SchoolRuntime runtime = school == null ? null : sims.get(school.id());
        if (runtime != null) {
            FishSchoolSim.Placement at = placement(member, runtime.sim.origin(), runtime.water);
            runtime.apply(new FishSchoolSim.Rejoin(member.id(), at.l(), at.y(), at.d()));
        }
        data.markDirtyNow();
    }

    private @Nullable FishSchoolSavedData.Member findMember(UUID schoolId, UUID memberId) {
        for (FishSchoolSavedData.School school : data.schools()) {
            if (!school.id().equals(schoolId)) continue;
            for (FishSchoolSavedData.Member member : school.members()) {
                if (member.id().equals(memberId)) return member;
            }
        }
        return null;
    }

    private void scheduleNearbyRegionAttempts(long now) {
        if (!FishtasticServerConfig.fishSchoolsEnabled() || level.players().isEmpty()) return;
        List<ServerPlayer> players = level.players();
        int checks = Math.min(2, players.size());
        for (int offset = 0; offset < checks; offset++) {
            ServerPlayer player = players.get(Math.floorMod(playerCursor++, players.size()));
            BlockPos water = nearestWater(player.blockPosition(), 12, 5, 2);
            if (water == null) continue;

            int regionX = Math.floorDiv(water.getX(), REGION_SIZE);
            int regionZ = Math.floorDiv(water.getZ(), REGION_SIZE);
            FishSchoolSavedData.RegionSchedule schedule = findRegion(regionX, regionZ);
            if (schedule == null) {
                data.setRegion(new FishSchoolSavedData.RegionSchedule(regionX, regionZ,
                        now + FishtasticServerConfig.fishSchoolAttemptIntervalTicks(), now));
                pruneRegions(now);
                continue;
            }
            if (now - schedule.lastSeenAt() > 1200) {
                schedule = new FishSchoolSavedData.RegionSchedule(regionX, regionZ,
                        schedule.nextAttemptAt(), now);
                data.setRegion(schedule);
            }
            if (now < schedule.nextAttemptAt()) continue;

            // Persist the next opportunity before any environmental, budget or random decision.
            data.setRegion(new FishSchoolSavedData.RegionSchedule(regionX, regionZ,
                    now + FishtasticServerConfig.fishSchoolAttemptIntervalTicks(), now));
            if (activeSchools() >= FishtasticServerConfig.fishSchoolMaxPerDimension()
                    || activeMembers() >= FishtasticServerConfig.fishSchoolMaxMembersPerDimension()
                    || globalActiveSchools() >= GLOBAL_SCHOOL_CAP
                    || overlapsExisting(water, 64.0)) continue;
            if (level.getRandom().nextFloat() >= FishtasticServerConfig.fishSchoolEncounterChance()) continue;

            tryCreateSchool(water, now);
        }
    }

    private @Nullable BlockPos nearestWater(BlockPos center, int radius, int down, int up) {
        BlockPos best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (int y = -down; y <= up; y++) {
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int d = x * x + z * z + y * y * 2;
                    if (d >= bestDistance) continue;
                    BlockPos pos = center.offset(x, y, z);
                    if (validWaterCell(pos)) {
                        best = pos;
                        bestDistance = d;
                    }
                }
            }
        }
        return best;
    }

    private @Nullable FishSchoolSavedData.School tryCreateSchool(BlockPos seed, long now) {
        return tryCreateSchool(seed, now, null);
    }

    /**
     * @param forced a species to use instead of drawing one from the natural pool (debug only): it
     *               must still be school-enabled, but this water's habitat is not checked for it
     */
    private @Nullable FishSchoolSavedData.School tryCreateSchool(BlockPos seed, long now, @Nullable Holder<Item> forced) {
        WaterScan waterScan = scanConnectedWater(seed, null, null);
        Set<BlockPos> water = waterScan.cells();
        if (water.size() < MIN_WATER_CELLS) return null;

        FishingPoolResolver.EnvironmentContext env = FishingPoolResolver.resolveEnvironment(level, seed, null);
        Registry<FishProfile> profiles = level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        Registry<Item> items = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ITEM);
        FishingPoolResolver.SpeciesContext naturalPool = FishingPoolResolver.resolve(items, profiles, env,
                FishingPoolResolver.Inputs.of(BaitEffect.NO_BAIT, null,
                        FishingPoolResolver.PersonalEquipment.NEUTRAL));
        List<FishingPoolResolver.WeightedFish> candidates = naturalPool.candidates().stream()
                .filter(candidate -> candidate.item().is(FishtasticItemTags.FISH_SCHOOL_ENABLED))
                .filter(candidate -> candidate.item().value() instanceof FishtasticFishItem)
                .toList();
        FishingPoolResolver.WeightedFish selected = forced != null ? new FishingPoolResolver.WeightedFish(forced, 1)
                : weightedChoice(candidates);
        if (selected == null) return null;
        ResourceLocation species = selected.item().unwrapKey().map(key -> key.location()).orElse(null);
        if (species == null) return null;

        Map<Long, Boolean> zoneEligible = new HashMap<>();
        WaterScan speciesScan = forced != null ? null : scanConnectedWater(seed, selected.item(), zoneEligible);
        Set<BlockPos> speciesWater = forced != null ? water : speciesScan.cells();
        if (speciesWater.size() < MIN_WATER_CELLS) return null;
        int remaining = FishtasticServerConfig.fishSchoolMaxMembersPerDimension() - activeMembers();
        int maxMembers = Math.min(FishtasticServerConfig.fishSchoolMaxMembersPerSchool(), remaining);
        int members = chooseSchoolSize(selected, profiles, maxMembers);
        if (members < 2) return null;

        List<BlockPos> cells = speciesWater.stream()
                .filter(pos -> Math.abs(pos.getX() - seed.getX()) <= SCHOOL_FORMATION_RADIUS
                        && Math.abs(pos.getZ() - seed.getZ()) <= SCHOOL_FORMATION_RADIUS
                        && Math.abs(pos.getY() - seed.getY()) <= 2)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (cells.size() < 2) return null;
        List<FishSchoolSavedData.Member> schoolMembers = new ArrayList<>(members);
        RandomSource random = level.getRandom();
        for (int i = cells.size() - 1; i > 0; i--) {
            java.util.Collections.swap(cells, i, random.nextInt(i + 1));
        }
        for (BlockPos pos : cells) {
            if (schoolMembers.size() >= members) break;
            Vec3 candidate = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            boolean clear = schoolMembers.stream().allMatch(member -> distanceSqr(member.position(), candidate) >= 1.0);
            if (!clear) continue;
            float yaw = random.nextFloat() * 360f;
            FishSchoolSavedData.Position position = new FishSchoolSavedData.Position(
                    candidate.x, candidate.y, candidate.z, yaw);
            schoolMembers.add(FishSchoolSavedData.newMember(random.nextLong(), position));
        }
        if (schoolMembers.size() < 2) return null;

        FishSchoolSavedData.School school = FishSchoolSavedData.newSchool(species, seed, now,
                now + FishtasticServerConfig.fishSchoolActiveLifetimeTicks(),
                FishSchoolSavedData.snapshot(env), schoolMembers);
        data.addSchool(school);
        RegionCache region = new RegionCache(speciesScan != null ? speciesScan : waterScan, now);
        region.zoneEligible.putAll(zoneEligible);
        // A forced (debug) species' water was found without its habitat: judge it with it soon,
        // as the next rescan always did.
        if (forced != null) region.markDirty(now);
        waterRegions.put(school.id(), region);
        Fishtastic.LOGGER.debug("Created fish school {} ({}) with {} members near {} in {}",
                school.id(), species, schoolMembers.size(), seed, level.dimension().location());
        return school;
    }

    private @Nullable FishingPoolResolver.WeightedFish weightedChoice(List<FishingPoolResolver.WeightedFish> candidates) {
        long total = candidates.stream().mapToLong(FishingPoolResolver.WeightedFish::weight).sum();
        if (total <= 0 || total > Integer.MAX_VALUE) return null;
        int draw = level.getRandom().nextInt((int) total);
        for (FishingPoolResolver.WeightedFish candidate : candidates) {
            draw -= candidate.weight();
            if (draw < 0) return candidate;
        }
        return null;
    }

    private int chooseSchoolSize(FishingPoolResolver.WeightedFish fish, Registry<FishProfile> profiles, int max) {
        if (max < 2) return 0;
        int rarity = FishtasticFishItem.getProfile(fish.item(), profiles)
                .map(FishProfile::baseWeight).orElse(FishProfile.DEFAULT_BASE_WEIGHT);
        int minimum = rarity <= 10 ? 2 : rarity <= 25 ? 2 : 3;
        minimum = Math.min(minimum, max);
        return minimum + level.getRandom().nextInt(max - minimum + 1);
    }

    private boolean overlapsExisting(BlockPos point, double radius) {
        double radiusSqr = radius * radius;
        for (FishSchoolSavedData.School school : data.schools()) {
            if (school.state() == FishSchoolSavedData.SchoolState.ACTIVE
                    && school.anchor().distToCenterSqr(Vec3.atCenterOf(point)) < radiusSqr) return true;
        }
        return false;
    }

    private int globalActiveSchools() {
        int total = 0;
        for (ServerLevel serverLevel : level.getServer().getAllLevels()) {
            total += (int) FishSchoolSavedData.get(serverLevel).schools().stream()
                    .filter(school -> school.state() == FishSchoolSavedData.SchoolState.ACTIVE).count();
        }
        return total;
    }

    private int activeSchools() {
        return (int) data.schools().stream().filter(s -> s.state() == FishSchoolSavedData.SchoolState.ACTIVE).count();
    }

    private int activeMembers() {
        return data.schools().stream().flatMap(s -> s.members().stream())
                .mapToInt(m -> m.state() == FishSchoolSavedData.MemberState.CAUGHT
                        || m.state() == FishSchoolSavedData.MemberState.DEPARTED ? 0 : 1).sum();
    }

    private @Nullable FishSchoolSavedData.RegionSchedule findRegion(int x, int z) {
        return data.regions().stream().filter(region -> region.x() == x && region.z() == z).findFirst().orElse(null);
    }

    private void pruneRegions(long now) {
        data.regions().removeIf(region -> now - region.lastSeenAt() > REGION_RETENTION_TICKS);
        if (data.regions().size() > MAX_RETAINED_REGIONS) {
            data.regions().sort(Comparator.comparingLong(FishSchoolSavedData.RegionSchedule::lastSeenAt).reversed());
            data.regions().subList(MAX_RETAINED_REGIONS, data.regions().size()).clear();
        }
        data.markDirtyNow();
    }

    private void tickVisitsAndMembers(long now) {
        boolean dirty = false;
        List<FishSchoolSavedData.School> remove = new ArrayList<>();
        for (FishSchoolSavedData.School school : data.schools()) {
            // Simulation pauses while the anchor chunk is unloaded; no call below loads it.
            if (!level.hasChunkAt(school.anchor())) continue;
            Set<BlockPos> cells = currentWaterRegion(school, now);
            SchoolRuntime runtime = sims.get(school.id());
            if (runtime == null) {
                dirty |= beginDeparture(school, now);
            } else if (!cells.isEmpty() && cells != runtime.water) {
                // Each rescan returns a new set. Unchanged water is adopted as is, so the ticks until
                // the next rescan compare by identity instead of cell by cell (~0.35 ms a school a
                // tick at 4000 cells: performance plan, step 0).
                boolean changed = !cells.equals(runtime.water);
                runtime.water = cells;
                if (changed) {
                    // The water changed shape under the visit: every copy rebuilds its domain.
                    runtime.apply(new FishSchoolSim.Water(columns(cells)));
                    FishSchoolPerf.waterInputs++;
                }
            }
            Item item = BuiltInRegistries.ITEM.getOptional(school.species()).orElse(null);
            if (cells.size() < MIN_WATER_CELLS || item == null
                    || !item.builtInRegistryHolder().is(FishtasticItemTags.FISH_SCHOOL_ENABLED)) {
                dirty |= beginDeparture(school, now);
            }
            if (!FishtasticServerConfig.fishSchoolsEnabled()) {
                dirty |= beginDeparture(school, now);
            }
            if (school.state() == FishSchoolSavedData.SchoolState.ACTIVE && now >= school.activeUntil()) {
                dirty |= beginDeparture(school, now);
            }

            for (FishSchoolSavedData.Member member : school.members()) {
                switch (member.state()) {
                    case APPROACHING -> dirty |= tickApproach(school, member, cells, now);
                    case BITE_READY -> dirty |= tickBiteWindow(school, member, now);
                    case RECOVERING -> {
                        if (now >= member.recoveryUntil()) {
                            if (school.state() == FishSchoolSavedData.SchoolState.ACTIVE && cells.contains(blockPos(member.position()))) {
                                member.setState(FishSchoolSavedData.MemberState.AVAILABLE);
                                dirty = true;
                            } else {
                                member.setState(FishSchoolSavedData.MemberState.DEPARTING);
                                dirty = true;
                            }
                        }
                    }
                    case AVAILABLE -> {
                        if (school.state() != FishSchoolSavedData.SchoolState.ACTIVE) {
                            member.setState(FishSchoolSavedData.MemberState.DEPARTING);
                            dirty = true;
                        }
                    }
                    case DEPARTING -> {
                        // It swims on with its school while it fades; then it is gone.
                        if (now >= school.departureUntil()) {
                            member.setState(FishSchoolSavedData.MemberState.DEPARTED);
                            leaveWater(school, member);
                            dirty = true;
                        }
                    }
                    case IN_SESSION, CAUGHT, DEPARTED -> { }
                }
            }

            boolean occupied = school.members().stream().anyMatch(m -> m.state() == FishSchoolSavedData.MemberState.IN_SESSION);
            boolean living = school.members().stream().anyMatch(m -> m.state() != FishSchoolSavedData.MemberState.CAUGHT
                    && m.state() != FishSchoolSavedData.MemberState.DEPARTED);
            if (!living && !occupied) remove.add(school);
        }
        for (FishSchoolSavedData.School school : remove) {
            data.removeSchool(school);
            waterRegions.remove(school.id());
            sims.remove(school.id());
            dirty = true;
        }
        if (dirty) data.markDirtyNow();
    }

    private Set<BlockPos> currentWaterRegion(FishSchoolSavedData.School school, long now) {
        RegionCache cached = waterRegions.get(school.id());
        if (cached != null && !cached.due(now, school.id())) return cached.cells;
        Item item = BuiltInRegistries.ITEM.getOptional(school.species()).orElse(null);
        Holder<Item> holder = item == null ? null : item.builtInRegistryHolder();
        if (cached == null) {
            cached = new RegionCache(new WaterScan(Set.of(), false), now);
            waterRegions.put(school.id(), cached);
        }
        long t = FishSchoolPerf.start();
        WaterScan scan = scanConnectedWater(school.anchor(), holder, cached.zoneEligible);
        FishSchoolPerf.end(FishSchoolPerf.Phase.REGION, t);
        cached.set(scan, now);
        return cached.cells;
    }

    private boolean beginDeparture(FishSchoolSavedData.School school, long now) {
        if (school.state() == FishSchoolSavedData.SchoolState.DEPARTING) return false;
        school.setState(FishSchoolSavedData.SchoolState.DEPARTING);
        school.setDepartureUntil(now + DEPARTURE_TICKS);
        for (FishSchoolSavedData.Member member : school.members()) {
            if (member.state() == FishSchoolSavedData.MemberState.APPROACHING
                    || member.state() == FishSchoolSavedData.MemberState.BITE_READY) {
                clearReservationBite(member);
                if (member.reservation() != null) withdraw(school, member.reservation().hookId());
                member.setState(FishSchoolSavedData.MemberState.DEPARTING);
                member.setReservation(null);
            } else if (member.state() == FishSchoolSavedData.MemberState.AVAILABLE
                    || member.state() == FishSchoolSavedData.MemberState.RECOVERING) {
                member.setState(FishSchoolSavedData.MemberState.DEPARTING);
                member.setReservation(null);
            }
        }
        return true;
    }

    /**
     * A reserved member's approach. The swim is the school's engine's (a host-sent nose errand);
     * this only watches it: the bite comes when the fish touches its bobber, and the reservation
     * ends if the hook, the line or the water stops being valid, the bobber drifts off, the fish
     * gives up at it, or it takes too long.
     */
    private boolean tickApproach(FishSchoolSavedData.School school, FishSchoolSavedData.Member member,
                                 Set<BlockPos> cells, long now) {
        FishSchoolSavedData.Reservation reservation = member.reservation();
        if (reservation == null || now > reservation.leaseUntil()) {
            recover(school, member, now);
            return true;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(reservation.playerId());
        FishingHook hook = player == null ? null : player.fishing;
        SchoolRuntime runtime = sims.get(school.id());
        UUID anchor = reservation.hookId();
        Vec3 anchorPoint = runtime == null ? null : runtime.anchorPoints.get(anchor);
        boolean touched = runtime != null && runtime.touched.contains(anchor);
        String failure = player == null || player.level() != level || player.isDeadOrDying() ? "player gone"
                : hook == null || hook.isRemoved() || !hook.getUUID().equals(anchor) ? "line gone"
                : FishingMinigameManager.findFishtasticRod(player).isEmpty() ? "no rod"
                : hook.distanceToSqr(player) > INTEREST_DISTANCE_SQR ? "line too long"
                : !cells.contains(hookWaterCell(hook)) || !validWaterCell(hookWaterCell(hook)) ? "bobber out of the water"
                : anchorPoint == null ? "no anchor"
                : horizontalDistanceSqr(hook.position(), anchorPoint) > ANCHOR_DRIFT * ANCHOR_DRIFT ? "bobber drifted"
                : !touched && !runtime.sim.errandActive(anchor) ? "fish gave up"
                : now - reservation.startedAt() > FishtasticServerConfig.fishSchoolApproachMaxTicks() ? "too slow"
                : null;
        if (failure != null) {
            if (runtime != null) runtime.lastFailure = failure + " @" + now;
            if (logApproaches) {
                Fishtastic.LOGGER.info("[schools] approach failed after {} ticks: {} (member {})",
                        now - reservation.startedAt(), failure, member.id().toString().substring(0, 8));
            }
            clearReservationBite(member);
            recover(school, member, now);
            return true;
        }
        if (touched && now - reservation.startedAt() >= FishtasticServerConfig.fishSchoolApproachMinTicks()) {
            member.setState(FishSchoolSavedData.MemberState.BITE_READY);
            member.setReservation(new FishSchoolSavedData.Reservation(reservation.playerId(), reservation.hookId(),
                    now, now + FishtasticServerConfig.fishSchoolBiteWindowTicks(), reservation.sessionId(),
                    reservation.targetIndex()));
            ((IFishingHookExtension) hook).fishtastic$startSchoolBite();
            if (logApproaches) {
                Fishtastic.LOGGER.info("[schools] bite after {} ticks (member {})", now - reservation.startedAt(),
                        member.id().toString().substring(0, 8));
            }
            return true;
        }
        return false;
    }

    private static double horizontalDistanceSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private boolean tickBiteWindow(FishSchoolSavedData.School school, FishSchoolSavedData.Member member, long now) {
        FishSchoolSavedData.Reservation reservation = member.reservation();
        if (reservation == null) {
            recover(school, member, now);
            return true;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(reservation.playerId());
        FishingHook hook = player == null ? null : player.fishing;
        if (player == null || player.level() != level || player.isDeadOrDying()
                || FishingMinigameManager.findFishtasticRod(player).isEmpty()
                || hook == null || hook.isRemoved() || !hook.getUUID().equals(reservation.hookId())
                || now > reservation.leaseUntil()) {
            clearReservationBite(member);
            recover(school, member, now);
            return true;
        }
        if (now - reservation.startedAt() > FishtasticServerConfig.fishSchoolBiteWindowTicks()) {
            clearReservationBite(member);
            recover(school, member, now);
            return true;
        }
        return false;
    }

    private void reserveNearbyHooks(long now) {
        approachRetryAt.values().removeIf(at -> at <= now);
        FishingMinigameManager fishing = FishingMinigameManager.get(level);
        List<ServerPlayer> players = level.players();
        if (players.isEmpty()) return;
        int checks = Math.min(MAX_HOOK_CHECKS_PER_TICK, players.size());
        for (int offset = 0; offset < checks; offset++) {
            ServerPlayer player = players.get(Math.floorMod(interestPlayerCursor++, players.size()));
            HookChoice choice = hookChoice(player, fishing, now);
            if (choice.blocked() != null) continue;
            FishingHook hook = choice.hook();
            FishSchoolSavedData.School chosenSchool = choice.school();
            FishSchoolSavedData.Member chosenMember = choice.member();
            SchoolRuntime runtime = sims.get(chosenSchool.id());
            float[] at = choice.anchor();
            int holdTicks = FishtasticServerConfig.fishSchoolApproachMinTicks()
                    + FishtasticServerConfig.fishSchoolBiteWindowTicks() + HOLD_SLACK_TICKS;
            boolean sent = runtime.apply(new FishSchoolSim.Approach(hook.getUUID(), chosenMember.id(), at[0], at[1], at[2],
                    FishtasticServerConfig.fishSchoolApproachSpeedScale(), holdTicks / 20f));
            if (logApproaches) {
                Fishtastic.LOGGER.info("[schools] approach {}: member {} at {} blocks", sent ? "sent" : "REFUSED by engine",
                        chosenMember.id().toString().substring(0, 8),
                        String.format(java.util.Locale.ROOT, "%.1f", Math.sqrt(vec(chosenMember.position()).distanceToSqr(hook.position()))));
            }
            if (!sent) {
                approachRetryAt.put(hook.getUUID(), now + APPROACH_RETRY_TICKS);
                continue;
            }
            runtime.anchorPoints.put(hook.getUUID(), hook.position());
            runtime.touched.remove(hook.getUUID());
            chosenMember.setState(FishSchoolSavedData.MemberState.APPROACHING);
            chosenMember.setReservation(new FishSchoolSavedData.Reservation(player.getUUID(), hook.getUUID(), now,
                    now + FishtasticServerConfig.fishSchoolApproachMaxTicks() + FishtasticServerConfig.fishSchoolBiteWindowTicks(),
                    0, -1));
            data.markDirtyNow();
        }
    }

    /**
     * Which school fish a player's bobber would get, or why none: {@code blocked} is null exactly
     * when {@code school}, {@code member} and {@code anchor} are set. The one place the reservation
     * rules live, so {@link #debugNearest} explains a bobber with the very checks that decide it.
     */
    private record HookChoice(@Nullable String blocked, @Nullable FishingHook hook,
                              FishSchoolSavedData.@Nullable School school, FishSchoolSavedData.@Nullable Member member,
                              float @Nullable [] anchor) {
        static HookChoice no(String why) {
            return new HookChoice(why, null, null, null, null);
        }
    }

    private HookChoice hookChoice(ServerPlayer player, FishingMinigameManager fishing, long now) {
        if (player.isDeadOrDying()) return HookChoice.no("player is dead");
        if (fishing.isPlayerInActiveSession(player.getUUID())) return HookChoice.no("already in a minigame");
        FishingHook hook = player.fishing;
        if (hook == null || hook.isRemoved()) return HookChoice.no("no line out");
        if (FishingMinigameManager.findFishtasticRod(player).isEmpty()) return HookChoice.no("not holding a Fishtastic rod");
        if (((IFishingHookExtension) hook).fishtastic$isBiting()) return HookChoice.no("bobber already has a bite");
        if (ownsHook(hook.getUUID())) return HookChoice.no("a school fish is already on this bobber");
        Long retryAt = approachRetryAt.get(hook.getUUID());
        if (retryAt != null && now < retryAt) return HookChoice.no("waiting " + (retryAt - now) + " ticks after a refused approach");
        BlockPos hookPos = hookWaterCell(hook);
        if (!validWaterCell(hookPos)) {
            return HookChoice.no("bobber's block " + hookPos.toShortString() + " is not source water ("
                    + level.getBlockState(hookPos).getBlock().getName().getString() + ")");
        }

        FishSchoolSavedData.School chosenSchool = null;
        FishSchoolSavedData.Member chosenMember = null;
        double bestDistance = Double.MAX_VALUE;
        double radius = FishtasticServerConfig.fishSchoolInterestRadius();
        double nearestAvailable = Double.MAX_VALUE;
        int schoolsHere = 0, unreachable = 0;
        for (FishSchoolSavedData.School school : data.schools()) {
            if (school.state() != FishSchoolSavedData.SchoolState.ACTIVE || !sims.containsKey(school.id())) continue;
            Set<BlockPos> cells = currentWaterRegion(school, now);
            if (!cells.contains(hookPos)) continue;
            schoolsHere++;
            for (FishSchoolSavedData.Member member : school.members()) {
                if (member.state() != FishSchoolSavedData.MemberState.AVAILABLE) continue;
                double distance = vec(member.position()).distanceToSqr(hook.position());
                nearestAvailable = Math.min(nearestAvailable, distance);
                if (distance > radius * radius || distance >= bestDistance) continue;
                // Reachability only: the swim itself is the engine's.
                if (!withinPath(blockPos(member.position()), hookPos, cells, (int) Math.floor(radius + 3))) {
                    unreachable++;
                    continue;
                }
                chosenSchool = school;
                chosenMember = member;
                bestDistance = distance;
            }
        }
        if (chosenSchool == null) {
            if (schoolsHere == 0) return HookChoice.no("bobber is not in any active school's water");
            if (nearestAvailable == Double.MAX_VALUE) return HookChoice.no("no school fish is free right now");
            if (unreachable > 0) return HookChoice.no(unreachable + " fish in range but no water path to the bobber");
            return HookChoice.no(String.format(java.util.Locale.ROOT, "nearest free fish is %.1f blocks away (radius %d)",
                    Math.sqrt(nearestAvailable), (int) radius));
        }
        SchoolRuntime runtime = sims.get(chosenSchool.id());
        BlockPos o = runtime.sim.origin();
        float[] at = runtime.sim.anchorPoint((float) (hook.getX() - o.getX()), (float) (hook.getY() - o.getY()),
                (float) (hook.getZ() - o.getZ()));
        if (at == null) {
            return HookChoice.no(String.format(java.util.Locale.ROOT, "no room for a fish's nose under the bobber (bobber y %.2f)",
                    hook.getY()));
        }
        return new HookChoice(null, hook, chosenSchool, chosenMember, at);
    }

    /** How far under a bobber's centre its water is judged, blocks: where the hook hangs. */
    private static final double HOOK_WATER_DEPTH = 0.5;

    /**
     * The water cell a bobber fishes in: the block half a block under its centre. A floating bobber
     * bobs across the surface, so the block holding its centre is the air above the water about
     * half the time; judged there, every bob cancelled the approach ("bobber out of the water"
     * every few seconds in the bite self-test) and a fish seldom reached the hook.
     */
    private static BlockPos hookWaterCell(FishingHook hook) {
        return BlockPos.containing(hook.getX(), hook.getY() - HOOK_WATER_DEPTH, hook.getZ());
    }

    /** Debug: what a player's bobber would get from the schools right now, in words. */
    public String hookReport(ServerPlayer player) {
        HookChoice choice = hookChoice(player, FishingMinigameManager.get(level), level.getGameTime());
        if (choice.blocked() != null) return "Your bobber: " + choice.blocked() + ".";
        return String.format(java.util.Locale.ROOT, "Your bobber: eligible; member %s is %.1f blocks away.",
                choice.member().id().toString().substring(0, 8), Math.sqrt(vec(choice.member().position())
                        .distanceToSqr(choice.hook().position())));
    }

    private void recover(@Nullable FishSchoolSavedData.School school, FishSchoolSavedData.Member member, long now) {
        if (school != null && member.reservation() != null) withdraw(school, member.reservation().hookId());
        member.setState(FishSchoolSavedData.MemberState.RECOVERING);
        member.setReservation(null);
        member.setRecoveryUntil(now + FishtasticServerConfig.fishSchoolRecoveryTicks());
    }

    private void clearReservationBite(FishSchoolSavedData.Member member) {
        FishSchoolSavedData.Reservation reservation = member.reservation();
        if (reservation == null) return;
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(reservation.playerId());
        FishingHook hook = owner == null ? null : owner.fishing;
        if (hook != null && hook.getUUID().equals(reservation.hookId())) {
            ((IFishingHookExtension) hook).fishtastic$clearSchoolBite();
        }
    }

    /**
     * @param zoneEligible per-cell habitat results to reuse and fill in (a school's cache), or null
     */
    private WaterScan scanConnectedWater(BlockPos seed, @Nullable Holder<Item> species,
                                         @Nullable Map<Long, Boolean> zoneEligible) {
        if (!validWaterCell(seed)) return new WaterScan(Set.of(), !level.hasChunkAt(seed));
        Registry<FishProfile> profiles = level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        boolean incomplete = false;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        Set<Long> columns = new HashSet<>();
        queue.add(seed.immutable());
        seen.add(seed.immutable());
        columns.add(columnKey(seed));
        while (!queue.isEmpty() && seen.size() < MAX_WATER_CELLS) {
            BlockPos current = queue.removeFirst();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (Math.abs(next.getX() - seed.getX()) > WATER_RADIUS
                        || Math.abs(next.getZ() - seed.getZ()) > WATER_RADIUS
                        || next.getY() < seed.getY() - WATER_DOWN || next.getY() > seed.getY() + WATER_UP
                        || seen.contains(next)) continue;
                // Once the column budget is spent, the search only deepens the columns it has.
                long column = columnKey(next);
                if (!columns.contains(column) && columns.size() >= MAX_WATER_COLUMNS) continue;
                if (!level.hasChunkAt(next)) {
                    incomplete = true;
                    continue;
                }
                if (!validWaterCell(next)) continue;
                if (species != null) {
                    Boolean eligible = zoneEligible == null ? null : zoneEligible.get(next.asLong());
                    if (eligible == null) {
                        FishSchoolPerf.zoneChecks++;
                        var biome = level.getBiome(next);
                        Set<FishProfile.Zone> zones = FishProfile.Zone.resolve(biome, next.getY(), level.getSeaLevel());
                        eligible = FishtasticFishItem.isZoneEligible(species, profiles, zones);
                        if (zoneEligible != null) zoneEligible.put(next.asLong(), eligible);
                    }
                    if (!eligible) continue;
                }
                seen.add(next.immutable());
                columns.add(column);
                queue.addLast(next.immutable());
            }
        }
        FishSchoolPerf.regionScans++;
        FishSchoolPerf.regionCells += seen.size();
        return new WaterScan(seen, incomplete);
    }

    private boolean validWaterCell(BlockPos pos) {
        FishSchoolPerf.waterCellChecks++;
        if (!level.hasChunkAt(pos)) return false;
        var fluid = level.getFluidState(pos);
        if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) return false;
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Whether {@code end} is at most {@code maxSteps} face-steps from {@code start} through
     * {@code cells}. The search stops at that depth: a farther bobber is refused anyway, and
     * searching on explored up to ~3000 cells (performance plan, finding 4).
     */
    private static boolean withinPath(BlockPos start, BlockPos end, Set<BlockPos> cells, int maxSteps) {
        if (!cells.contains(start) || !cells.contains(end)) return false;
        if (start.equals(end)) return true;
        ArrayDeque<BlockPos> frontier = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        frontier.add(start);
        seen.add(start);
        for (int depth = 1; depth <= maxSteps && !frontier.isEmpty(); depth++) {
            for (int n = frontier.size(); n > 0; n--) {
                BlockPos current = frontier.removeFirst();
                for (Direction direction : Direction.values()) {
                    BlockPos next = current.relative(direction);
                    if (!cells.contains(next) || !seen.add(next)) continue;
                    if (next.equals(end)) {
                        FishSchoolPerf.path(seen.size());
                        return true;
                    }
                    frontier.addLast(next);
                }
            }
        }
        FishSchoolPerf.path(seen.size());
        return false;
    }

    /**
     * Brings nearby players' copies up to date (docs/interactive-fish-schools-motion-plan.md): a
     * school a player has newly come in range of, or whose epoch changed, gets its setup and every
     * input so far; then each player gets the state of every school it tracks. A school dropped
     * from that list is no longer tracked, and the client lets its copy go.
     */
    private void syncNearbyPlayers(long now) {
        Set<UUID> online = new HashSet<>();
        double range = FishtasticServerConfig.fishSchoolTrackingRange();
        double rangeSqr = range * range;
        List<ServerPlayer> players = level.players();
        for (ServerPlayer player : players) online.add(player.getUUID());
        int checks = Math.min(MAX_PLAYERS_PER_SNAPSHOT_TICK, players.size());
        ResourceLocation dimension = level.dimension().location();
        for (int offset = 0; offset < checks; offset++) {
            ServerPlayer player = players.get(Math.floorMod(syncPlayerCursor++, players.size()));
            Tracking tracking = tracked.get(player.getUUID());
            if (tracking == null || tracking.player() != player) {
                tracking = new Tracking(player, new HashMap<>());
                tracked.put(player.getUUID(), tracking);
            }
            Map<UUID, Integer> mine = tracking.schools();
            boolean hadAny = !mine.isEmpty();
            List<FishSchoolStatePacket.School> states = new ArrayList<>();
            Set<UUID> seen = new HashSet<>();
            for (FishSchoolSavedData.School school : data.schools()) {
                // An unloaded anchor suspends the whole visit; hide its frozen presentation too.
                if (!level.hasChunkAt(school.anchor())) continue;
                SchoolRuntime runtime = sims.get(school.id());
                if (runtime == null || school.anchor().distToCenterSqr(player.position()) > rangeSqr) continue;
                if (states.size() >= FishSchoolStatePacket.MAX_SCHOOLS) break;
                Integer held = mine.get(school.id());
                if (held == null || held != runtime.epoch) {
                    FishtasticTransport.sendToPlayer(player, new FishSchoolSetupPacket(dimension, school.id(),
                            runtime.epoch, school.species(), runtime.setup, List.copyOf(runtime.inputs),
                            runtime.sim.steps()));
                    mine.put(school.id(), runtime.epoch);
                }
                runtime.trackers.add(player.getUUID());
                seen.add(school.id());
                states.add(state(school, runtime, now));
            }
            for (UUID gone : new ArrayList<>(mine.keySet())) {
                if (seen.contains(gone)) continue;
                mine.remove(gone);
                SchoolRuntime runtime = sims.get(gone);
                if (runtime != null) runtime.trackers.remove(player.getUUID());
            }
            if (!states.isEmpty() || hadAny) {
                FishtasticTransport.sendToPlayer(player, new FishSchoolStatePacket(dimension, states));
            }
        }
        tracked.keySet().removeIf(id -> !online.contains(id));
        for (SchoolRuntime runtime : sims.values()) runtime.trackers.retainAll(online);
    }

    private static FishSchoolStatePacket.School state(FishSchoolSavedData.School school, SchoolRuntime runtime, long now) {
        List<FishSchoolStatePacket.Member> members = new ArrayList<>();
        for (FishSchoolSavedData.Member member : school.members()) {
            if (runtime.sim.indexOf(member.id()) < 0) continue;
            int visual = switch (member.state()) {
                case AVAILABLE -> FishSchoolStatePacket.AVAILABLE;
                case APPROACHING -> FishSchoolStatePacket.APPROACHING;
                case BITE_READY -> FishSchoolStatePacket.BITE_READY;
                case RECOVERING -> FishSchoolStatePacket.RECOVERING;
                default -> FishSchoolStatePacket.DEPARTING;
            };
            members.add(new FishSchoolStatePacket.Member(member.id(), visual));
        }
        int fade = school.state() == FishSchoolSavedData.SchoolState.DEPARTING
                ? (int) Math.max(0, school.departureUntil() - now) : -1;
        return new FishSchoolStatePacket.School(school.id(), runtime.epoch, runtime.sim.steps(), runtime.checksum,
                fade, members);
    }

    /** Sends an input the moment it is applied to every player whose client holds the epoch. */
    private void broadcastInput(UUID schoolId, FishSchoolSim.Stamped stamped) {
        SchoolRuntime runtime = sims.get(schoolId);
        if (runtime == null || runtime.trackers.isEmpty()) return;
        FishSchoolInputPacket packet = new FishSchoolInputPacket(level.dimension().location(), schoolId,
                runtime.epoch, List.of(stamped));
        for (UUID id : runtime.trackers) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && player.level() == level) FishtasticTransport.sendToPlayer(player, packet);
        }
    }

    private static double distanceSqr(FishSchoolSavedData.Position from, Vec3 to) {
        return vec(from).distanceToSqr(to);
    }

    private static double distanceSqr(double x, double y, double z, double px, double py, double pz) {
        double dx = x - px, dy = y - py, dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    private static BlockPos blockPos(FishSchoolSavedData.Position position) {
        return BlockPos.containing(position.x(), position.y(), position.z());
    }

    private static Vec3 vec(FishSchoolSavedData.Position position) {
        return new Vec3(position.x(), position.y(), position.z());
    }
}
