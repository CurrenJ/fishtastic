package grill24.fishtastic.server;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.config.FishtasticServerConfig;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.ecology.NaturalPool;
import grill24.fishtastic.ecology.VisitDerivation;
import grill24.fishtastic.ecology.VisitId;
import grill24.fishtastic.ecology.VisitWeatherLog;
import grill24.fishtastic.ecology.VisitWindows;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.network.FishSchoolInputPacket;
import grill24.fishtastic.network.FishSchoolSetupPacket;
import grill24.fishtastic.network.FishSchoolStatePacket;
import grill24.fishtastic.school.FishSchoolWire;
import grill24.fishtastic.network.FishtasticTransport;
import grill24.fishtastic.client.renderer.FishVisuals;
import grill24.fishtastic.school.FishSchoolSim;
import grill24.fishtastic.school.SurfaceJumpArc;
import grill24.fishtastic.school.SurfaceJumpPlaybackProfile;
import grill24.fishtastic.school.VisitShape;
import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.domain.WaterDomain;
import grill24.fishtastic.util.IFishingHookExtension;
import grill24.fishtastic.util.IServerLevelSchools;
import it.unimi.dsi.fastutil.longs.LongArrayList;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Server authority for finite fish-school visits, attraction, bite ownership and tracking. */
public final class FishSchoolManager {
    private static final int GLOBAL_SCHOOL_CAP = 16;
    /** How often the visit scheduler looks at the loaded water chunks near players, ticks. */
    private static final int SCHEDULE_PERIOD_TICKS = 20;
    /** The most chunks one scheduler pass checks; the rest wait for the rotation to come round. */
    private static final int MAX_CHUNK_CHECKS_PER_PASS = 512;
    /** The most windows one pass reveals, so a crowd of simultaneous windows does not spike a tick. */
    private static final int MAX_REVEALS_PER_PASS = 2;
    /**
     * A live visit is dropped once no player is within the tracking range plus this, blocks. The
     * reveal reach is the tracking range (to a chunk's edge, so up to ~23 blocks more to its start
     * cell), so this keeps a visit from being dropped just after it is revealed, and a player
     * watching from outside tracking range keeps seeing it come back where it was.
     */
    private static final int DROP_MARGIN_BLOCKS = 48;
    /** A window that failed for a reason the world can change (no room, a tight fit) is tried again after this, ticks. */
    private static final long ATTEMPT_RETRY_TICKS = 1200;
    /** A window held up for something transient (an unloaded neighbour chunk, a spent cap) is tried again after this, ticks. */
    private static final long ATTEMPT_DEFER_TICKS = 100;
    /** The depletion ledger is pruned this often, ticks. */
    private static final int LEDGER_PRUNE_TICKS = 1200;
    /**
     * A school's water is bounded by how far it spreads, not by its volume: at most this many
     * columns (about 11 blocks out from the anchor), each to its full depth in the search box. A
     * 256-cell cap filled a deep lake as a blob ~4 blocks across, so a bobber 2-3 blocks beside a
     * fish near its edge was outside the school's water and never got an approach (fishschoolreach
     * self-test, 2026-10-25: 9/32 bobber cells at 2 blocks, 13/32 at 3).
     */
    private static final int MAX_WATER_COLUMNS = 400;
    /** Safety bound on the search itself: 400 columns of the full 17-block search depth fit. */
    private static final int MAX_WATER_CELLS = 6800;
    private static final int MIN_WATER_CELLS = 6;
    private static final int MAX_HOOK_CHECKS_PER_TICK = 8;
    private static final int MAX_PLAYERS_PER_SNAPSHOT_TICK = 32;
    private static final int WATER_RADIUS = 16;
    private static final int WATER_DOWN = 12;
    private static final int WATER_UP = 4;
    private static final int SCHOOL_FORMATION_RADIUS = 4;
    private static final int SCHOOL_MEMBER_SYNC_TICKS = 10;
    /**
     * Surface jumps (wild-fish-surface-jumps-design-spec §12.5): a visit's first roll comes this long
     * after it forms, and each roll after a roll or a completed jump this long after the last, the
     * delay being the minimum plus a spread picked per visit.
     */
    private static final int SURFACE_JUMP_MIN_DELAY_TICKS = 600;
    private static final int SURFACE_JUMP_DELAY_SPREAD_TICKS = 600;
    private static final int SURFACE_JUMP_RECHECK_TICKS = 40;
    /** Chance that a roll starts a goal. */
    private static final float SURFACE_JUMP_CHANCE = 0.6f;
    /** Only a member at most this far below the surface is sent to breach, blocks. */
    private static final float SURFACE_JUMP_MAX_DEPTH = 4f;
    /** A goal not reached in this long is called off, ticks: a four-block climb at {@link FishSchoolSim#GOAL_SPEED_SCALE} takes about 300. */
    private static final int SURFACE_JUMP_GOAL_TIMEOUT_TICKS = 400;
    /** After a goal was called off, or none could be started, the visit waits this long to try again, ticks. */
    private static final int SURFACE_JUMP_RETRY_TICKS = 200;
    /** How far from the member a launch point is looked for, blocks, and how many are tried per member. */
    private static final float SURFACE_JUMP_LAUNCH_REACH = 3f;
    private static final int SURFACE_JUMP_LAUNCH_ATTEMPTS = 8;
    /** The most ticks the wind-up, burst, flight and glide may take together; a 0.5-block breach takes about 45. */
    private static final int SURFACE_JUMP_DURATION_TICKS = 64;
    /** The same budget for a legendary breach's higher, longer arc (about 60 ticks at 1.25 blocks), in source ticks. */
    private static final int LEGENDARY_SURFACE_JUMP_DURATION_TICKS = FishSchoolSim.MAX_SURFACE_JUMP_TICKS;
    /** A departing visit fades out over this long, then its members are gone (design decision). */
    private static final int DEPARTURE_TICKS = 60;
    private static final double INTEREST_DISTANCE_SQR = 1024.0;
    /** A bobber that drifts this far from where its fish was sent is a new cast, blocks. */
    private static final double ANCHOR_DRIFT = 1.0;
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
     * Locomotion classes whose approach to a bobber is finished and accepted in game; a species of
     * any other class appears only through the allow override (encounter spec §4, §5.1).
     */
    private static final Set<Locomotion> READY_LOCOMOTIONS = EnumSet.of(Locomotion.FREE_SWIM, Locomotion.BENTHIC,
            Locomotion.DRIFT, Locomotion.ANCHORED);
    /** How often eel visits are told whether someone is rushing them, ticks. */
    private static final int WATCHER_TICKS = 5;
    /** A sprinting player this close to an eel visit's anchor is rushing it, blocks. */
    private static final double RUSH_RADIUS = 8.0;
    /** A rushing player's watcher point is resent once they have moved this far, blocks. */
    private static final float WATCHER_RESEND = 0.5f;

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
        /** The last {@link FishSchoolSim.Watcher} sent (eel visits only): whether someone was rushing, and where. */
        boolean watcherNear;
        float watcherL, watcherY, watcherD;
        /** The surface goal this epoch has running (one per visit at a time), and when it was sent. */
        @Nullable UUID jumpGoal;
        long jumpGoalSentAt;
        /** When the running goal's fish reached its launch point, or -1 while it is still on its way. */
        long jumpGoalArrivedAt = -1;

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
    /** Test/self-test only: visits whose water is judged without their species' habitat. */
    private final Set<UUID> habitatFreeForTest = new HashSet<>();

    /**
     * Test/self-test only: judges {@code school}'s water without its species' habitat, so an ocean
     * creature can be exercised in a test world's plains. Behaviour is otherwise unchanged.
     */
    public void ignoreHabitatForTest(UUID school) {
        habitatFreeForTest.add(school);
        RegionCache cached = waterRegions.get(school);
        if (cached != null) cached.markDirty(level.getGameTime());
    }

    /**
     * Windows that were tried and did not produce a visit (or whose visit has ended), by id: when to
     * try again, and when the entry stops mattering. In memory; a restart simply tries again.
     */
    private record Attempt(long retryAt, long expiresAt) {}
    private final Map<VisitId, Attempt> attempts = new HashMap<>();
    private long ecologySeed;
    private boolean ecologySeedKnown;
    /** Why the last {@link #createSchool} returned null, and how long until a derived window may try again. */
    private String createNote = "";
    private long createRetryTicks;
    /** Debug counters of the scheduler, shown by {@code /fishtastic visits windows}. */
    private long windowsRevealed, windowsDropped, windowsConcluded, capBinds;
    /** Test only: {maxSchools, maxMembers} instead of the configured protection caps (a shared test world holds many fixtures). */
    private int @Nullable [] capsForTest;
    private String lastCapBind = "none";

    /** Per species: whether its profile allows in-world visits ({@link #readyByProfile}). */
    private final Map<Item, Boolean> profileReady = new HashMap<>();
    /** The registry {@link #profileReady} was computed from; another one means the profiles were reloaded. */
    private Registry<FishProfile> profileReadyRegistry;
    /** Test only: species treated as no longer eligible, standing in for a datapack or profile change. */
    private final Set<ResourceLocation> excludedForTest = new HashSet<>();

    /** Each live school's motion, by school id. Not saved: a reload starts a new epoch. */
    private final Map<UUID, SchoolRuntime> sims = new HashMap<>();
    private int scheduleCursor;
    private int interestPlayerCursor;
    private int syncPlayerCursor;

    /** Ticks before a hook the engine refused a fish for is offered one again. */
    private static final int APPROACH_RETRY_TICKS = 40;
    /** How far above a bobber a fish may be and still take it, blocks: a fish riding the surface. */
    private static final double BITE_REACH_UP = 1.0;
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
        captureWeather(now);
        FishSchoolPerf.skipLevel = level.players().isEmpty() && data.schools().isEmpty();
        long tickStart = FishSchoolPerf.start();
        long t = FishSchoolPerf.start();
        if (now % SCHEDULE_PERIOD_TICKS == 0) {
            List<Vec3> centres = playerPositions();
            scheduleVisits(now, centres);
            dropFarVisits(centres);
        }
        if (now % LEDGER_PRUNE_TICKS == 0) pruneLedger(now);
        FishSchoolPerf.end(FishSchoolPerf.Phase.SCHEDULE, t);
        t = FishSchoolPerf.start();
        advanceSims(now);
        FishSchoolPerf.end(FishSchoolPerf.Phase.ADVANCE, t);
        t = FishSchoolPerf.start();
        tickVisitsAndMembers(now);
        if (now % WATCHER_TICKS == 0) feedWatchers();
        FishSchoolPerf.end(FishSchoolPerf.Phase.VISITS, t);
        t = FishSchoolPerf.start();
        if (now % 5 == 0) reserveNearbyHooks(now);
        FishSchoolPerf.end(FishSchoolPerf.Phase.HOOKS, t);
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
            tickSurfaceJump(school, runtime, now);
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
                school.id().getMostSignificantBits() ^ now, placements, school.shape());
        FishSchoolSim sim = FishSchoolSim.create(setup);
        // Water too small for the species would leave it a frozen display: the visit departs instead.
        if (sim == null || !sim.hostsSpecies()) return null;
        UUID id = school.id();
        int epoch = nextEpoch++ & Integer.MAX_VALUE;
        SchoolRuntime created = new SchoolRuntime(sim, setup, epoch, water,
                stamped -> broadcastInput(id, stamped));
        sims.put(id, created);
        return created;
    }

    /**
     * Surface jumps for a visit of free swimmers (wild-fish-surface-jumps-design-spec §12.5). The
     * visit rolls now and then; a roll that succeeds picks a member near enough to the surface and
     * a launch point under open sky, and sends it there as a {@link FishSchoolSim.SurfaceGoal}. The
     * swim, the jump on arrival and the hold are the sim's; this only starts the goal, calls it
     * off if the fish is too slow, and schedules the next roll. The engine position stays in the
     * water: catch approach, bite checks and saved member positions are untouched.
     */
    private void tickSurfaceJump(FishSchoolSavedData.School school, SchoolRuntime runtime, long now) {
        FishSchoolSim sim = runtime.sim;
        if (runtime.jumpGoal != null) {
            followSurfaceGoal(school, runtime, now);
            return;
        }
        if (school.state() != FishSchoolSavedData.SchoolState.ACTIVE || runtime.trackers.isEmpty()
                || sim.spec().locomotion() != Locomotion.FREE_SWIM
                || Math.floorMod(now + school.id().hashCode(), SURFACE_JUMP_RECHECK_TICKS) != 0) return;
        long due = school.nextSurfaceJumpAt() > 0 ? school.nextSurfaceJumpAt()
                : school.createdAt() + SURFACE_JUMP_MIN_DELAY_TICKS
                + Math.floorMod(school.id().hashCode(), SURFACE_JUMP_DELAY_SPREAD_TICKS);
        if (now < due) return;

        RandomSource random = RandomSource.create(school.id().getMostSignificantBits()
                ^ Long.rotateLeft(school.id().getLeastSignificantBits(), 17) ^ (now * 0x9E3779B97F4A7C15L));
        if (random.nextFloat() >= SURFACE_JUMP_CHANCE) {
            school.setNextSurfaceJumpAt(now + surfaceJumpCooldown(random));
            return;
        }
        // A roll that finds nobody to send does not use up the visit's turn: it waits a short while.
        if (startSurfaceGoal(school, runtime, now, random) == null) {
            school.setNextSurfaceJumpAt(now + SURFACE_JUMP_RETRY_TICKS);
        }
    }

    private static int surfaceJumpCooldown(RandomSource random) {
        return SURFACE_JUMP_MIN_DELAY_TICKS + random.nextInt(SURFACE_JUMP_DELAY_SPREAD_TICKS);
    }

    /**
     * Watches the running goal. When the sim has ended it, a jump that ran its course counts: the
     * next roll waits a cooldown. One that was called off (a bobber took the fish, it never got
     * there) does not count; the visit tries again after a short wait.
     */
    private void followSurfaceGoal(FishSchoolSavedData.School school, SchoolRuntime runtime, long now) {
        UUID goal = runtime.jumpGoal;
        FishSchoolSim sim = runtime.sim;
        if (sim.goalActive(goal)) {
            // A fish that committed to its leap on the way in is as good as arrived.
            if (runtime.jumpGoalArrivedAt < 0 && (runtime.touched.contains(goal) || sim.goalStarted(goal))) {
                runtime.jumpGoalArrivedAt = now;
            }
            if (runtime.jumpGoalArrivedAt < 0 && now - runtime.jumpGoalSentAt > SURFACE_JUMP_GOAL_TIMEOUT_TICKS) {
                runtime.apply(new FishSchoolSim.Withdraw(goal)); // ends it; counted as called off next tick
            }
            return;
        }
        boolean completed = runtime.jumpGoalArrivedAt >= 0 && sim.goalCompleted(goal);
        runtime.touched.remove(goal);
        runtime.jumpGoal = null;
        runtime.jumpGoalArrivedAt = -1;
        if (completed) {
            RandomSource random = RandomSource.create(goal.getLeastSignificantBits() ^ now);
            school.setNextSurfaceJumpAt(now + surfaceJumpCooldown(random));
        } else {
            school.setNextSurfaceJumpAt(now + SURFACE_JUMP_RETRY_TICKS);
        }
    }

    /**
     * Picks an available member within {@link #SURFACE_JUMP_MAX_DEPTH} of the surface and a launch
     * point near it with open sky, and stamps its goal. The member that was sent, or null if no
     * member could be, in which case nothing was stamped.
     */
    private FishSchoolSavedData.@Nullable Member startSurfaceGoal(FishSchoolSavedData.School school,
                                                                 SchoolRuntime runtime, long now, RandomSource random) {
        return startSurfaceGoal(school, runtime, now, random, null);
    }

    private FishSchoolSavedData.@Nullable Member startSurfaceGoal(FishSchoolSavedData.School school,
                                                                 SchoolRuntime runtime, long now, RandomSource random,
                                                                 @Nullable SurfaceJumpPlaybackProfile forcedProfile) {
        FishSchoolSim sim = runtime.sim;
        List<FishSchoolSavedData.Member> candidates = new ArrayList<>();
        for (FishSchoolSavedData.Member member : school.members()) {
            if (member.state() != FishSchoolSavedData.MemberState.AVAILABLE) continue;
            int i = sim.indexOf(member.id());
            if (i < 0 || sim.hasGoal(member.id()) || sim.hasSurfaceJump(member.id())) continue;
            float surface = sim.domain().surfaceAt(sim.engine().posL()[i], sim.engine().posD()[i]);
            float depth = surface - sim.engine().posY()[i];
            if (Float.isFinite(surface) && depth <= SURFACE_JUMP_MAX_DEPTH) candidates.add(member);
        }
        for (int k = candidates.size() - 1; k > 0; k--) { // shuffle
            Collections.swap(candidates, k, random.nextInt(k + 1));
        }
        for (FishSchoolSavedData.Member member : candidates) {
            int i = sim.indexOf(member.id());
            SurfaceJumpPlaybackProfile playbackProfile = forcedProfile != null ? forcedProfile
                    : member.quality() == FishQuality.Quality.LEGENDARY
                    ? SurfaceJumpPlaybackProfile.LEGENDARY_CINEMATIC : SurfaceJumpPlaybackProfile.NORMAL;
            for (int attempt = 0; attempt < SURFACE_JUMP_LAUNCH_ATTEMPTS; attempt++) {
                // The first try is straight above the fish; the rest are scattered round it.
                float angle = random.nextFloat() * Mth.TWO_PI;
                float reach = attempt == 0 ? 0f : random.nextFloat() * SURFACE_JUMP_LAUNCH_REACH;
                float l = sim.engine().posL()[i] + Mth.cos(angle) * reach;
                float d = sim.engine().posD()[i] + Mth.sin(angle) * reach;
                // A legendary breach wants its own big arc, but under a low ceiling it still leaps an
                // ordinary-height one rather than not at all.
                float breach = (float) playbackProfile.breachHeight();
                float[] at = launchPoint(sim, member.id(), l, d, breach, playbackProfile.reachScale());
                if (at == null && playbackProfile != SurfaceJumpPlaybackProfile.NORMAL) {
                    breach = (float) SurfaceJumpPlaybackProfile.NORMAL_BREACH_HEIGHT;
                    at = launchPoint(sim, member.id(), l, d, breach, playbackProfile.reachScale());
                }
                if (at == null) continue;
                UUID goal = new UUID(random.nextLong(), random.nextLong());
                float height = Math.max(0f, sim.domain().surfaceAt(at[0], at[2]) - at[1]) + breach;
                int duration = playbackProfile == SurfaceJumpPlaybackProfile.NORMAL
                        ? SURFACE_JUMP_DURATION_TICKS : LEGENDARY_SURFACE_JUMP_DURATION_TICKS;
                boolean sent = runtime.apply(new FishSchoolSim.SurfaceGoal(goal, member.id(), at[0], at[1], at[2],
                        duration, height, playbackProfile));
                runtime.checksum = sim.checksum();
                if (!sent) return null; // the engine refused; the input is replayed and refused alike everywhere
                runtime.jumpGoal = goal;
                runtime.jumpGoalSentAt = now;
                runtime.jumpGoalArrivedAt = -1;
                return member;
            }
        }
        return null;
    }

    /**
     * Where {@code member} can breach near column {@code (l, d)}: just under its surface, with the
     * whole body clear of blocks above along the arc it would fly (forward along the heading it
     * swims in on, SurfaceJumpArc.airReach), in loaded chunks only. Null if there is none. The sim
     * may still fit a shorter arc than this; never a longer one.
     */
    private float @Nullable [] launchPoint(FishSchoolSim sim, UUID member, float l, float d,
                                           float breachHeight, double reachScale) {
        float surface = sim.domain().surfaceAt(l, d);
        if (!Float.isFinite(surface)) return null;
        float[] at = sim.anchorPoint(l, surface, d);
        if (at == null) return null;
        float[] heading = sim.goalHeading(member, at[0], at[2]);
        int i = sim.indexOf(member);
        if (heading == null || i < 0) return null;
        double apex = SurfaceJumpArc.apexFor(Math.max(0f, surface - at[1]) + breachHeight,
                surface - at[1], sim.engine().lengths[i], FishSchoolSim.MAX_SURFACE_JUMP_HEIGHT);
        double reach = SurfaceJumpArc.airReach(apex) * reachScale;
        WaterDomain.BodyBox body = sim.body();
        BlockPos o = sim.origin();
        double top = o.getY() + surface + apex + body.above();
        // Body-sized boxes every half block along the flight, launch to re-entry.
        int samples = Math.max(1, (int) Math.ceil(reach / 0.5));
        for (int k = 0; k <= samples; k++) {
            double s = reach * k / samples;
            double x = o.getX() + at[0] + heading[0] * s, z = o.getZ() + at[2] + heading[1] * s;
            AABB clearance = new AABB(x - body.half(), o.getY() + surface, z - body.half(),
                    x + body.half(), top, z + body.half());
            if (!hasJumpClearance(clearance)) return null;
        }
        return at;
    }

    /** Conservative clearance over the whole rendered body; never touches an unloaded chunk. */
    private boolean hasJumpClearance(AABB box) {
        int minX = Mth.floor(box.minX), maxX = Mth.floor(box.maxX - 1.0e-4);
        int minY = Mth.floor(box.minY), maxY = Mth.floor(box.maxY - 1.0e-4);
        int minZ = Mth.floor(box.minZ), maxZ = Mth.floor(box.maxZ - 1.0e-4);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.hasChunkAt(new BlockPos(x, minY, z))) return false;
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (!state.getCollisionShape(level, pos).isEmpty()) return false;
                }
            }
        }
        return true;
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

    /**
     * Whether a species may appear as an interactive visit (encounter spec §5.1): a Fishtastic
     * fish, not denied, and either of a locomotion class whose in-world approach is finished or in
     * the allow override. Nether-only species are caught in lava, which visits cannot use yet
     * (§5.3). Habitat is otherwise judged separately, by the natural pool.
     */
    public boolean encounterEligible(Holder<Item> item) {
        if (!excludedForTest.isEmpty() && item.unwrapKey().map(key -> excludedForTest.contains(key.location())).orElse(false)) {
            return false;
        }
        if (!(item.value() instanceof FishtasticFishItem) || item.is(FishtasticItemTags.FISH_ENCOUNTER_DISABLED)) {
            return false;
        }
        return item.is(FishtasticItemTags.FISH_SCHOOL_ENABLED) || readyByProfile(item.value());
    }

    /**
     * The profile half of {@link #encounterEligible}: a ready locomotion class and a water habitat.
     * Profiles are fixed for the server's life, so it is cached; tags are not, so they are read live.
     */
    private boolean readyByProfile(Item item) {
        Registry<FishProfile> registry = level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        if (registry != profileReadyRegistry) {
            profileReady.clear(); // other profiles than the ones this was worked out from (E14)
            profileReadyRegistry = registry;
        }
        Boolean known = profileReady.get(item);
        if (known != null) return known;
        ResourceLocation species = BuiltInRegistries.ITEM.getKey(item);
        FishSpec spec = speciesSpec(species);
        boolean waterHabitat = level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                .getOptional(ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, species))
                .map(profile -> profile.zones().stream().anyMatch(zone -> zone != FishProfile.Zone.NETHER))
                .orElse(false);
        boolean ready = spec != null && READY_LOCOMOTIONS.contains(spec.locomotion()) && waterHabitat;
        profileReady.put(item, ready);
        return ready;
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
            // Water that goes on under the search band has an unknown bed: the lowest cell is the band
            // edge, not ground, so the engine must not stand a floor creature on it.
            boolean bedUnknown = validWaterCell(column.firstEntry().getValue().below());
            columns.add(new WaterDomain.Column(any.getX(), any.getZ(), out, bedUnknown));
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
            out.append(member.state()).append('/').append(member.quality().getSerializedName()).append('#').append(i);
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
        return spawnForDebug(near, species, null);
    }

    /** {@link #spawnForDebug(BlockPos, ResourceLocation)}, member {@code i} given {@code qualities[i % length]} if set. */
    public String spawnForDebug(BlockPos near, @Nullable ResourceLocation species, FishQuality.Quality @Nullable [] qualities) {
        return spawnForDebug(near, species, qualities, null);
    }

    /** As above, with the visit's shape forced to {@code shape} if set (still limited by its class). */
    public String spawnForDebug(BlockPos near, @Nullable ResourceLocation species, FishQuality.Quality @Nullable [] qualities,
                                @Nullable VisitShape shape) {
        BlockPos water = nearestWater(near, 12, 5, 2);
        if (water == null) return "No valid source water within 12 blocks.";
        Holder<Item> forced = null;
        if (species != null) {
            Item item = BuiltInRegistries.ITEM.getOptional(species).orElse(null);
            if (item == null || !encounterEligible(item.builtInRegistryHolder())) {
                return species + " cannot appear in the world: not a Fishtastic fish, denied by"
                        + " #fishtastic:fish_encounter_disabled, or its movement class has no in-world approach yet.";
            }
            forced = item.builtInRegistryHolder();
        }
        FishSchoolSavedData.School school = tryCreateSchool(water, level.getGameTime(), forced, qualities, shape);
        if (school == null) {
            return "No visit formed at " + water.toShortString() + ": no eligible species lives in this water,"
                    + " the water is too small for it, or the member budget is spent.";
        }
        return "Spawned " + school.members().size() + " " + school.species() + " as " + school.shape().getSerializedName()
                + " at " + school.anchor().toShortString() + " (school " + school.id() + ").";
    }

    /**
     * Test seam: skips the roll and the schedule and sends a member to breach now, so the render
     * self-test need not wait for ecology. The goal itself (swim up, jump on arrival) is the real one.
     */
    public String surfaceJumpForDebug(UUID schoolId) {
        return surfaceJumpForDebug(schoolId, null);
    }

    /** Test seam: force either canonical profile without changing the member's rolled quality. */
    public String surfaceJumpForDebug(UUID schoolId, @Nullable SurfaceJumpPlaybackProfile profile) {
        FishSchoolSavedData.School school = findSchool(schoolId);
        SchoolRuntime runtime = sims.get(schoolId);
        if (school == null || runtime == null || school.state() != FishSchoolSavedData.SchoolState.ACTIVE
                || runtime.sim.spec().locomotion() != Locomotion.FREE_SWIM) {
            return "School is not an active free-swimming visit.";
        }
        if (runtime.jumpGoal != null) return "School already has a surface goal running.";
        long now = level.getGameTime();
        FishSchoolSavedData.Member chosen = startSurfaceGoal(school, runtime, now,
                RandomSource.create(schoolId.getLeastSignificantBits() ^ now), profile);
        if (chosen == null) return "School has no available fish within reach of an open launch point.";
        return "Sent " + chosen.id() + " in school " + schoolId + " to breach.";
    }

    /** Debug: the nearest school's species, state and {@link #debugState}, or a line saying there is none. */
    public String debugNearest(Vec3 near) {
        FishSchoolSavedData.School nearest = data.schools().stream()
                .min(Comparator.comparingDouble(school -> school.anchor().distToCenterSqr(near))).orElse(null);
        if (nearest == null) return "No fish schools in this dimension.";
        return nearest.species() + " " + nearest.state() + " at " + nearest.anchor().toShortString()
                + " (" + (int) Math.sqrt(nearest.anchor().distToCenterSqr(near)) + " blocks): " + debugState(nearest.id());
    }

    /**
     * Debug: sends a fish of the nearest school to breach now ({@link #surfaceJumpForDebug}),
     * skipping the roll and the cooldown; the swim up and the jump are the real ones.
     */
    public String surfaceJumpNearest(Vec3 near) {
        FishSchoolSavedData.School nearest = data.schools().stream()
                .min(Comparator.comparingDouble(school -> school.anchor().distToCenterSqr(near))).orElse(null);
        if (nearest == null) return "No fish schools in this dimension.";
        return nearest.species() + " (" + (int) Math.sqrt(nearest.anchor().distToCenterSqr(near)) + " blocks): "
                + surfaceJumpForDebug(nearest.id());
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
        // The one thing saved about a visit: this fish is gone from its window for good.
        FishSchoolSavedData.School school = findSchool(binding.schoolId());
        if (school != null && school.visitId() != null) data.markCaught(school.visitId(), member.index());
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

    // ---- Visit windows (world fish exploration, implementation plan Step 2) ----

    /** The dimension's ecology seed, derived once (it is a hash of the world seed). */
    private long ecologySeed() {
        if (!ecologySeedKnown) {
            ecologySeed = EcologySeedSync.seedFor(level);
            ecologySeedKnown = true;
        }
        return ecologySeed;
    }

    private List<Vec3> playerPositions() {
        List<Vec3> out = new ArrayList<>(level.players().size());
        for (ServerPlayer player : level.players()) out.add(player.position());
        return out;
    }

    /** The visit of this window if one is live right now. */
    private FishSchoolSavedData.@Nullable School liveVisit(VisitId id) {
        for (FishSchoolSavedData.School school : data.schools()) {
            if (id.equals(school.visitId())) return school;
        }
        return null;
    }

    /**
     * The loaded chunks within {@code range} blocks (to the chunk's edge, horizontally) of any of
     * {@code centres}, nearest-agnostic and without duplicates. Loaded only: nothing here loads a chunk.
     */
    private List<ChunkPos> loadedChunksNear(List<Vec3> centres, double range) {
        return chunksNear(centres, range, true);
    }

    /** As {@link #loadedChunksNear}, optionally including chunks that are not loaded (the debug report lists those too). */
    private List<ChunkPos> chunksNear(List<Vec3> centres, double range, boolean loadedOnly) {
        Set<Long> seen = new HashSet<>();
        List<ChunkPos> out = new ArrayList<>();
        int reach = (int) Math.ceil(range / 16.0) + 1;
        for (Vec3 centre : centres) {
            int cx = Mth.floor(centre.x) >> 4, cz = Mth.floor(centre.z) >> 4;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    int x = cx + dx, z = cz + dz;
                    double ox = Math.max(Math.max(x * 16.0 - centre.x, 0.0), centre.x - (x * 16.0 + 16.0));
                    double oz = Math.max(Math.max(z * 16.0 - centre.z, 0.0), centre.z - (z * 16.0 + 16.0));
                    if (ox * ox + oz * oz > range * range) continue;
                    long key = ChunkPos.asLong(x, z);
                    if (!seen.add(key) || (loadedOnly && level.getChunkSource().getChunkNow(x, z) == null)) continue;
                    out.add(new ChunkPos(x, z));
                }
            }
        }
        return out;
    }

    /**
     * A budgeted rotation over the loaded chunks near players. Whether a window hosts a visit is a
     * pure function of the seed, the site and the time (E1, E5), so this never decides anything: it
     * only reveals an already-scheduled, unsuppressed, undepleted window that has no live visit.
     * Arriving, leaving or reloading cannot create, reroll or speed one up.
     */
    private void scheduleVisits(long now, List<Vec3> centres) {
        pruneAttempts(now);
        if (!FishtasticServerConfig.fishSchoolsEnabled() || centres.isEmpty()) return;
        VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
        long seed = ecologySeed();
        String dimension = level.dimension().location().toString();
        List<ChunkPos> chunks = loadedChunksNear(centres, FishtasticServerConfig.fishSchoolTrackingRange());
        if (chunks.isEmpty()) return;
        int budget = Math.min(chunks.size(), MAX_CHUNK_CHECKS_PER_PASS);
        int first = Math.floorMod(scheduleCursor, chunks.size());
        scheduleCursor += budget;
        int reveals = 0;
        for (int k = 0; k < budget && reveals < MAX_REVEALS_PER_PASS; k++) {
            ChunkPos chunk = chunks.get((first + k) % chunks.size());
            VisitId id = VisitId.at(dimension, seed, chunk.x, chunk.z, now, params);
            long windowStart = id.start(seed, params);
            if (now < windowStart || now >= windowStart + revealSpan(params)
                    || !VisitWindows.hostsVisit(seed, chunk.x, chunk.z, id.window(), params)) continue;
            Attempt attempt = attempts.get(id);
            if (attempt != null && now < attempt.retryAt()) continue;
            if (liveVisit(id) != null) continue;
            if (revealWindow(id, seed, params, now, false) != null) reveals++;
        }
    }

    /**
     * How far into a window a reveal is still tried: as long as the longest-lived species' visit, so a late
     * arrival sees a long-lived species for exactly as long as its visit lasts (the roll then refuses a reveal
     * past the species' own end). For the default L = 2 minutes, T = 3 minutes that is the whole period.
     */
    private static long revealSpan(VisitWindows.Params params) {
        return VisitDerivation.revealSpanTicks(params.visitTicks(), params.periodTicks(), FishProfile.WorldEncounter.MAX_LIFETIME_SCALE);
    }

    /**
     * Reveals window {@code id}: a visit derived from the seed and the window alone, as it would be
     * whenever it was revealed. Null (with {@link #createNote} and a retry time noted) when the water,
     * the species or a protection cap says no. {@code forced} is the debug override: it skips the
     * schedule (the caller has already chosen the window), not the derivation.
     *
     * <p>Step 3 seam (E11): everything a mid-window reveal changes is the member list built in
     * {@link #createSchool}'s derived branch - at the moment it reveals the full roster at seeded
     * spots, minus caught members; Step 3 removes members whose seeded departure time has passed.
     */
    private FishSchoolSavedData.@Nullable School revealWindow(VisitId id, long seed, VisitWindows.Params params,
                                                              long now, boolean forced) {
        long expiresAt = id.start(seed, params) + params.periodTicks();
        LevelChunk chunk = level.getChunkSource().getChunkNow(id.chunkX(), id.chunkZ());
        if (chunk == null) return failedReveal(id, expiresAt, now, "the chunk is not loaded", ATTEMPT_DEFER_TICKS, forced);
        LongArrayList cells = qualifyingWaterCells(chunk);
        if (cells.isEmpty()) return failedReveal(id, expiresAt, now, "the chunk holds no qualifying water", ATTEMPT_RETRY_TICKS, forced);
        BlockPos start = BlockPos.of(cells.getLong(VisitDerivation.startIndex(VisitDerivation.startHash(seed, id), cells.size())));
        createNote = "";
        createRetryTicks = ATTEMPT_RETRY_TICKS;
        FishSchoolSavedData.School school = createSchool(start, now, null, null, null,
                new Derived(id, seed, params, forced));
        if (school == null) return failedReveal(id, expiresAt, now, createNote, createRetryTicks, forced);
        windowsRevealed++;
        return school;
    }

    private FishSchoolSavedData.@Nullable School failedReveal(VisitId id, long expiresAt, long now, String why,
                                                              long retryTicks, boolean forced) {
        createNote = why;
        if (!forced) {
            attempts.put(id, new Attempt(retryTicks == Long.MAX_VALUE ? Long.MAX_VALUE : now + retryTicks, expiresAt));
        }
        return null;
    }

    /**
     * The chunk's qualifying water cells in a fixed order (section by section from the bottom, then
     * y, z, x): the list the start hash indexes into (E2). Loaded source water with an empty
     * collision shape, the existing rule; sections without any water are skipped unread.
     */
    private LongArrayList qualifyingWaterCells(LevelChunk chunk) {
        LongArrayList out = new LongArrayList();
        LevelChunkSection[] sections = chunk.getSections();
        int minSection = chunk.getMinSection();
        int baseX = chunk.getPos().getMinBlockX(), baseZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section.hasOnlyAir() || !section.maybeHas(state -> state.getFluidState().is(FluidTags.WATER))) continue;
            int baseY = (minSection + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        // The section read is the cheap half of validWaterCell's own fluid test, so
                        // only source water pays for the level lookups; the result is unchanged.
                        var fluid = section.getFluidState(x, y, z);
                        if (!fluid.is(FluidTags.WATER) || !fluid.isSource()) continue;
                        pos.set(baseX + x, baseY + y, baseZ + z);
                        if (validWaterCell(pos)) out.add(pos.asLong());
                    }
                }
            }
        }
        return out;
    }

    /**
     * The environment a window's visit is derived under: the biome and height of its start cell and
     * the world clock at the window's START, so a reveal at any moment of the window derives the same
     * species. Time of day and the moon follow from the clock (it runs with game time); weather
     * comes from the bounded dimension log. A window predating that log falls back to current
     * weather, explicitly marked in the windows report.
     */
    private FishingPoolResolver.EnvironmentContext environmentAtWindowStart(BlockPos start, long windowStart, long now) {
        // `now` is the game time the reveal happens at; it is the level's own except in tests that
        // simulate other moments, where the clock is carried along with it (it runs with game time).
        long clock = VisitDerivation.clockAtWindowStart(level.getDayTime() + (now - level.getGameTime()),
                now, windowStart);
        VisitWeatherLog.State weather = windowWeather(windowStart).state();
        FishProfile.WeatherCondition condition = switch (weather) {
            case CLEAR -> FishProfile.WeatherCondition.CLEAR;
            case THUNDER -> FishProfile.WeatherCondition.THUNDER;
            case RAIN -> level.getBiome(start).value().getPrecipitationAt(start) == Biome.Precipitation.SNOW
                    ? FishProfile.WeatherCondition.SNOW : FishProfile.WeatherCondition.RAIN;
        };
        return NaturalPool.environment(level.getBiome(start), FishProfile.TimeOfDay.fromGameTime(clock),
                condition, FishMoonPhase.fromIndex(VisitDerivation.moonPhaseIndex(clock)),
                start.getY(), level.getSeaLevel());
    }

    /** 26.1's {@code Level#canHaveWeather}: a sky, no ceiling, and not the End. */
    private boolean canHaveWeather() {
        return level.dimensionType().hasSkyLight() && !level.dimensionType().hasCeiling()
                && level.dimension() != net.minecraft.world.level.Level.END;
    }

    private VisitWeatherLog.State currentWeather() {
        return canHaveWeather() ? VisitWeatherLog.State.fromFlags(level.getLevelData().isRaining(),
                level.getLevelData().isThundering()) : VisitWeatherLog.State.CLEAR;
    }

    private VisitWeatherLog.Sample windowWeather(long windowStart) {
        return canHaveWeather() ? data.weatherAt(windowStart, currentWeather())
                : new VisitWeatherLog.Sample(VisitWeatherLog.State.CLEAR, false);
    }

    /** Capture the flags rather than the visual rain ramp, so /weather is recorded on the next tick. */
    private void captureWeather(long now) {
        if (!canHaveWeather()) return;
        data.recordWeather(now, currentWeather());
        VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
        data.pruneWeather(now - params.periodTicks() - revealSpan(params));
    }

    /** Debug: the recent world input used to derive window-start weather, with the unknown-history fallback. */
    public String weatherLogReport() {
        VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
        List<VisitWeatherLog.Entry> entries = data.weatherEntries();
        StringBuilder out = new StringBuilder("Visit weather log in " + level.dimension().location()
                + " at tick " + level.getGameTime() + ": current=" + currentWeather().getSerializedName()
                + ", retention=" + (params.periodTicks() + revealSpan(params)) + " ticks, entries=" + entries.size());
        if (!canHaveWeather()) return out.append("; this dimension has no weather (clear).").toString();
        out.append(". Windows before the first entry use current weather (FALLBACK: no history).");
        int first = Math.max(0, entries.size() - 64);
        if (first > 0) out.append("\n  ... ").append(first).append(" older entries not shown");
        for (int i = first; i < entries.size(); i++) {
            VisitWeatherLog.Entry entry = entries.get(i);
            out.append("\n  ").append(entry.gameTime()).append(": ").append(entry.state().getSerializedName());
        }
        return out.toString();
    }

    /**
     * Live visits derived from a window are dropped once no player is within the tracking range plus
     * {@link #DROP_MARGIN_BLOCKS} (horizontally, as reveals are): their state is derivable, so there is nothing to keep, and the
     * scheduler reveals them again when a player returns. A visit with a fish on a line stays.
     */
    private void dropFarVisits(List<Vec3> centres) {
        double range = FishtasticServerConfig.fishSchoolTrackingRange() + DROP_MARGIN_BLOCKS;
        double rangeSqr = range * range;
        List<FishSchoolSavedData.School> drop = null;
        for (FishSchoolSavedData.School school : data.schools()) {
            if (school.visitId() == null || busy(school)) continue;
            boolean near = false;
            for (Vec3 centre : centres) {
                // Horizontal, like the reveal range: a start cell can sit anywhere in its column's
                // height, and a 3D test dropped deep visits in the same pass that revealed them.
                double dx = school.anchor().getX() + 0.5 - centre.x, dz = school.anchor().getZ() + 0.5 - centre.z;
                if (dx * dx + dz * dz <= rangeSqr) {
                    near = true;
                    break;
                }
            }
            if (!near) {
                if (drop == null) drop = new ArrayList<>();
                drop.add(school);
            }
        }
        if (drop == null) return;
        for (FishSchoolSavedData.School school : drop) {
            forgetVisit(school);
            windowsDropped++;
        }
    }

    /** Whether a hook, a bite or a minigame holds one of the school's members. */
    private static boolean busy(FishSchoolSavedData.School school) {
        for (FishSchoolSavedData.Member member : school.members()) {
            switch (member.state()) {
                case APPROACHING, BITE_READY, IN_SESSION -> {
                    return true;
                }
                default -> { }
            }
        }
        return false;
    }

    private void forgetVisit(FishSchoolSavedData.School school) {
        data.removeSchool(school);
        waterRegions.remove(school.id());
        sims.remove(school.id());
        habitatFreeForTest.remove(school.id());
    }

    /** A visit that ended in this runtime (departed, depleted, or never fit) is not revealed again in its window. */
    private void concludeVisit(VisitId id) {
        VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
        attempts.put(id, new Attempt(Long.MAX_VALUE, id.start(ecologySeed(), params) + params.periodTicks()));
        windowsConcluded++;
    }

    private void pruneAttempts(long now) {
        if (!attempts.isEmpty()) attempts.values().removeIf(attempt -> now >= attempt.expiresAt());
    }

    /** Drops ledger rows whose window can no longer be revealed (a whole period after it opened). */
    private void pruneLedger(long now) {
        VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
        long seed = ecologySeed();
        data.pruneDepletion(entry -> now >= VisitWindows.windowStart(seed, entry.chunkX(), entry.chunkZ(),
                entry.window(), params) + params.periodTicks());
    }

    /** A protection cap stopped a window from forming: counted, and tried again soon. */
    private @Nullable FishSchoolSavedData.School capBound(String which) {
        capBinds++;
        lastCapBind = which;
        createNote = "protection cap reached: " + which;
        createRetryTicks = ATTEMPT_DEFER_TICKS;
        return null;
    }

    private @Nullable FishSchoolSavedData.School fail(String why, long retryTicks) {
        createNote = why;
        createRetryTicks = retryTicks;
        return null;
    }

    /** A window's derivation inputs: which window, under which seed and schedule, and whether a debug override asked. */
    private record Derived(VisitId id, long seed, VisitWindows.Params params, boolean forced) {
        long base() {
            return VisitDerivation.base(seed, id);
        }

        long windowStart() {
            return id.start(seed, params);
        }
    }

    /** Debug: the windows around {@code near}, in words (what is scheduled, suppressed, depleted and live). */
    public String visitWindowsReport(Vec3 near) {
        VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
        long seed = ecologySeed();
        long now = level.getGameTime();
        String dimension = level.dimension().location().toString();
        double range = FishtasticServerConfig.fishSchoolTrackingRange();
        List<ChunkPos> chunks = chunksNear(List.of(near), range, false);
        record Row(long start, double distance, String text) {}
        List<Row> rows = new ArrayList<>();
        int activeSites = 0;
        for (ChunkPos chunk : chunks) {
            long first = VisitWindows.windowIndex(seed, chunk.x, chunk.z, now, params);
            boolean any = false;
            for (long window = first; window <= first + 2; window++) {
                if (!VisitWindows.isActive(seed, chunk.x, chunk.z, window, params)) continue;
                any = true;
                long start = VisitWindows.windowStart(seed, chunk.x, chunk.z, window, params);
                long end = VisitWindows.windowEnd(seed, chunk.x, chunk.z, window, params);
                VisitId id = new VisitId(dimension, chunk.x, chunk.z, window);
                boolean suppressed = VisitWindows.isSuppressed(seed, chunk.x, chunk.z, window, params);
                long mask = data.caughtMask(id);
                FishSchoolSavedData.School live = liveVisit(id);
                Attempt attempt = attempts.get(id);
                double distance = Math.sqrt(Vec3.atCenterOf(new BlockPos(chunk.getMiddleBlockX(), (int) near.y,
                        chunk.getMiddleBlockZ())).distanceToSqr(near));
                String when = now < start ? "opens in " + seconds(start - now) : now < end ? "OPEN, closes in " + seconds(end - now)
                        : "closed";
                VisitWeatherLog.Sample weather = windowWeather(start);
                String weatherNote = now < start ? "weather pending" : "weather " + weather.state().getSerializedName()
                        + (weather.fallback() ? " (FALLBACK: current, no history)" : " at window start");
                rows.add(new Row(start, distance, String.format(java.util.Locale.ROOT,
                        "chunk %d,%d (%.0f blocks) window %d [%d, %d) %s | rank %08x | %s | %s | caught %s | %s%s",
                        chunk.x, chunk.z, distance, window, start, end, when,
                        (int) (VisitWindows.rank(seed, chunk.x, chunk.z, window) >>> 32),
                        suppressed ? "SUPPRESSED" : "hosts a visit",
                        weatherNote,
                        mask == 0 ? "none" : "0b" + Long.toBinaryString(mask),
                        live != null ? "LIVE: " + live.species() + " " + membersSummary(live) : "not live",
                        attempt == null ? "" : attempt.retryAt() == Long.MAX_VALUE ? " | concluded"
                                : attempt.retryAt() > now ? " | retry in " + seconds(attempt.retryAt() - now) : "")));
            }
            if (any) activeSites++;
        }
        rows.sort(Comparator.comparingLong(Row::start).thenComparingDouble(Row::distance));
        StringBuilder out = new StringBuilder(String.format(java.util.Locale.ROOT,
                "Visit windows in %s at tick %d: T=%d L=%d chance=%s R=%d, %d chunks within %.0f blocks, %d with an active window"
                        + " in the next %d ticks.\nLive visits %d, revealed %d, dropped (nobody near) %d, concluded %d, ledger rows %d,"
                        + " protection caps bound %d times (last: %s).",
                dimension, now, params.periodTicks(), params.visitTicks(), params.visitChance(),
                params.suppressionRadiusChunks(), chunks.size(), range, activeSites, 3 * params.periodTicks(),
                data.schools().stream().filter(school -> school.visitId() != null).count(), windowsRevealed, windowsDropped,
                windowsConcluded, data.depletionSize(), capBinds, lastCapBind));
        if (rows.isEmpty()) out.append("\nNo window is scheduled near you in the next three periods.");
        for (int i = 0; i < Math.min(rows.size(), 12); i++) out.append("\n  ").append(rows.get(i).text());
        if (rows.size() > 12) out.append("\n  ... and ").append(rows.size() - 12).append(" more");
        return out.toString();
    }

    /** Test seam: reveals the window as if it were game time {@code now}, skipping the schedule gate. */
    public FishSchoolSavedData.@Nullable School revealWindowForTest(VisitId id, long now) {
        return revealWindow(id, ecologySeed(), FishtasticServerConfig.visitWindowParams(), now, false);
    }

    /** Test seam: one scheduler pass at game time {@code now} for players at {@code centres}. */
    public void scheduleVisitsForTest(long now, List<Vec3> centres) {
        scheduleVisits(now, centres);
    }

    /** Test seam: one drop pass with players at {@code centres}. */
    public void dropFarVisitsForTest(List<Vec3> centres) {
        dropFarVisits(centres);
    }

    /** Test seam: forgets a live visit as the drop does (no conclusion, no ledger change). */
    public void forgetVisitForTest(FishSchoolSavedData.School school) {
        forgetVisit(school);
    }

    public void pruneLedgerForTest(long now) {
        pruneLedger(now);
    }

    /** Test seam: replaces the protection caps (null restores the configured ones). */
    public void setCapsForTest(int @Nullable [] maxSchoolsAndMembers) {
        capsForTest = maxSchoolsAndMembers;
    }

    public long capBindsForTest() {
        return capBinds;
    }

    /** Test seam: treats these species as ineligible, standing in for a datapack or profile change (E14). */
    public void setExcludedSpeciesForTest(Set<ResourceLocation> species) {
        excludedForTest.clear();
        excludedForTest.addAll(species);
    }

    public void clearAttemptsForTest() {
        attempts.clear();
    }

    /** Test seam: capture flags through the tick's weather path, without advancing unrelated visits. */
    public void captureWeatherForTest(long now) {
        captureWeather(now);
    }

    /** Test seam: one lifecycle pass for this visit only, without shifting the shared level clock. */
    public void tickVisitForTest(FishSchoolSavedData.School school, long now) {
        runtime(school, now); // the normal tick initializes it in advanceSims before the lifecycle pass
        tickVisitsAndMembers(now, List.of(school));
    }

    public long ecologySeedForTest() {
        return ecologySeed();
    }

    public FishSchoolSavedData.@Nullable School liveVisitForTest(VisitId id) {
        return liveVisit(id);
    }

    public String lastRevealNoteForTest() {
        return createNote;
    }

    public long windowsDroppedForTest() {
        return windowsDropped;
    }

    /** "3 present / 1 departed / 1 caught": the roster of a live visit, counting those gone before it was revealed. */
    private static String membersSummary(FishSchoolSavedData.School school) {
        int present = 0, departed = school.departedAtReveal(), caught = school.caughtAtReveal();
        for (FishSchoolSavedData.Member member : school.members()) {
            switch (member.state()) {
                case DEPARTING, DEPARTED -> departed++;
                case CAUGHT -> caught++;
                default -> present++;
            }
        }
        return present + " present / " + departed + " departed / " + caught + " caught";
    }

    private static String seconds(long ticks) {
        long s = Math.max(0, ticks) / 20;
        return s >= 60 ? (s / 60) + "m" + String.format(java.util.Locale.ROOT, "%02d", s % 60) + "s" : s + "s";
    }

    /**
     * Debug: forces the nearest site with water to reveal its CURRENT window now, hash-active or not.
     * An override for testing only: the deterministic schedule is untouched for everyone else (the
     * window keeps its own active/suppressed answer, the same visit would be derived by the real
     * schedule, and a catch still marks depletion for it). A line saying what happened.
     */
    public String revealNearestForDebug(Vec3 near) {
        VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
        long seed = ecologySeed();
        long now = level.getGameTime();
        String dimension = level.dimension().location().toString();
        List<ChunkPos> chunks = loadedChunksNear(List.of(near), FishtasticServerConfig.fishSchoolTrackingRange());
        chunks.sort(Comparator.comparingDouble(chunk -> new Vec3(chunk.getMiddleBlockX() + 0.5, near.y,
                chunk.getMiddleBlockZ() + 0.5).distanceToSqr(near)));
        String banner = "FORCED OVERRIDE (debug): ";
        String firstFailure = null;
        int tried = 0;
        for (ChunkPos chunk : chunks) {
            VisitId id = VisitId.at(dimension, seed, chunk.x, chunk.z, now, params);
            FishSchoolSavedData.School live = liveVisit(id);
            if (live != null) {
                return banner + "chunk " + chunk.x + "," + chunk.z + " window " + id.window() + " already has a live "
                        + live.species() + " visit at " + live.anchor().toShortString() + "; nothing forced.";
            }
            FishSchoolSavedData.School school = revealWindow(id, seed, params, now, true);
            if (school != null) {
                boolean active = VisitWindows.isActive(seed, chunk.x, chunk.z, id.window(), params);
                boolean suppressed = VisitWindows.isSuppressed(seed, chunk.x, chunk.z, id.window(), params);
                return banner + "revealed " + school.members().size() + " " + school.species() + " ("
                        + school.shape().getSerializedName() + ") at " + school.anchor().toShortString()
                        + " from chunk " + chunk.x + "," + chunk.z + " window " + id.window() + " (school " + school.id()
                        + "). The schedule itself is untouched: that window is " + (active ? "" : "NOT ") + "active by its hash and "
                        + (suppressed ? "" : "not ") + "suppressed, so nobody else gets this visit unless it really is scheduled."
                        + " It ends " + seconds(school.activeUntil() - now) + " from now; catches still mark this window's depletion.";
            }
            if (firstFailure == null) firstFailure = "chunk " + chunk.x + "," + chunk.z + ": " + createNote;
            if (++tried >= 12) break;
        }
        return banner + "no visit could form in the " + tried + " nearest loaded chunks"
                + (firstFailure == null ? "." : " (nearest: " + firstFailure + ").");
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
        return tryCreateSchool(seed, now, null, null, null);
    }

    /** The debug/test path: random draws, no window. */
    private @Nullable FishSchoolSavedData.School tryCreateSchool(BlockPos seed, long now, @Nullable Holder<Item> forced,
                                                                FishQuality.Quality @Nullable [] qualities,
                                                                @Nullable VisitShape forcedShape) {
        return createSchool(seed, now, forced, qualities, forcedShape, null);
    }

    /**
     * Forms a visit at {@code seed}'s water. With {@code derived} it is the window's visit: species,
     * shape, member count, member reward seeds and ids, and the placement order all come from the
     * window's hash and the natural pool under the environment at the window's start, so it is the same
     * visit whenever it is revealed; caught members are left out. Without it (debug spawns) every draw
     * is a random one as before.
     *
     * @param forced    a species to use instead of drawing one from the natural pool (debug only): it
     *                  must still be {@link #encounterEligible}, but this water's habitat is not checked for it
     * @param qualities debug only: member {@code i} gets {@code qualities[i % length]} (its reward
     *                  seed is re-drawn until {@link FishSchoolSavedData#qualityOf} says so), or null
     * @param forcedShape debug only: the visit's shape instead of the species' own, or null
     * @param derived   the window this visit is the derivation of, or null for a debug visit
     */
    private @Nullable FishSchoolSavedData.School createSchool(BlockPos seed, long now, @Nullable Holder<Item> forced,
                                                             FishQuality.Quality @Nullable [] qualities,
                                                             @Nullable VisitShape forcedShape, @Nullable Derived derived) {
        // Protection caps (not the model): in normal play they should never bind.
        if (derived != null) {
            int maxSchools = capsForTest != null ? capsForTest[0] : FishtasticServerConfig.fishSchoolMaxPerDimension();
            if (activeSchools() >= maxSchools) return capBound("schools per dimension");
            if (capsForTest == null && globalActiveSchools() >= GLOBAL_SCHOOL_CAP) return capBound("schools across dimensions");
        }
        WaterScan waterScan = scanConnectedWater(seed, null, null);
        Set<BlockPos> water = waterScan.cells();
        // A scan that met an unloaded chunk would give a result that depends on what happens to be
        // loaded, so a window waits for the chunks rather than derive from a partial lake.
        if (derived != null && waterScan.incomplete() && !derived.forced()) {
            return fail("the water runs into unloaded chunks", ATTEMPT_DEFER_TICKS);
        }
        if (water.size() < MIN_WATER_CELLS) return fail("fewer than " + MIN_WATER_CELLS + " connected water cells (E4)", ATTEMPT_RETRY_TICKS);

        FishingPoolResolver.EnvironmentContext env = derived != null
                ? environmentAtWindowStart(seed, derived.windowStart(), now)
                : FishingPoolResolver.resolveEnvironment(level, seed, null);
        Registry<FishProfile> profiles = level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        Registry<Item> items = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.ITEM);
        FishingPoolResolver.SpeciesContext naturalPool = NaturalPool.resolve(items, profiles, env);
        List<FishingPoolResolver.WeightedFish> candidates = naturalPool.candidates().stream()
                .filter(candidate -> encounterEligible(candidate.item()))
                .toList();
        FishingPoolResolver.WeightedFish selected;
        if (forced != null) {
            selected = new FishingPoolResolver.WeightedFish(forced, 1);
        } else if (derived != null) {
            int pick = VisitDerivation.pickWeighted(derived.base(),
                    candidates.stream().mapToInt(FishingPoolResolver.WeightedFish::weight).toArray());
            selected = pick < 0 ? null : candidates.get(pick);
        } else {
            selected = weightedChoice(candidates);
        }
        if (selected == null) return fail("no eligible species lives in this water", ATTEMPT_RETRY_TICKS);
        ResourceLocation species = selected.item().unwrapKey().map(key -> key.location()).orElse(null);
        if (species == null) return fail("the drawn species has no id", ATTEMPT_RETRY_TICKS);

        // The rolled species is the window's species. If it does not fit, the window passes with no
        // visit; there is no substitute (a re-roll would make the visit depend on the water's shape).
        Map<Long, Boolean> zoneEligible = new HashMap<>();
        WaterScan speciesScan = forced != null ? null : scanConnectedWater(seed, selected.item(), zoneEligible);
        Set<BlockPos> speciesWater = forced != null ? water : speciesScan.cells();
        if (speciesWater.size() < MIN_WATER_CELLS) return fail(species + " has fewer than " + MIN_WATER_CELLS + " cells of its habitat here", ATTEMPT_RETRY_TICKS);
        FishProfile profile = FishtasticFishItem.getProfile(selected.item(), profiles).orElse(null);
        FishSpec spec = speciesSpec(species);
        if (spec == null) return fail(species + " has no engine descriptor", ATTEMPT_RETRY_TICKS);
        VisitShape shape = VisitShape.allowedFor(forcedShape != null ? forcedShape
                : profile != null ? profile.visitShape() : VisitShape.fallback(FishProfile.DEFAULT_BASE_WEIGHT),
                spec.locomotion());
        MemberPlan plan;
        if (derived != null) {
            plan = planMembers(shape, profile, FishtasticServerConfig.fishSchoolMaxMembersPerSchool(), derived.base());
        } else {
            int remaining = FishtasticServerConfig.fishSchoolMaxMembersPerDimension() - activeMembers();
            plan = planMembers(shape, profile, Math.min(FishtasticServerConfig.fishSchoolMaxMembersPerSchool(), remaining), null);
        }
        if (plan == null) return fail("the member budget cannot fit even the smallest " + shape.getSerializedName(), ATTEMPT_RETRY_TICKS);
        if (derived != null && activeMembers() + plan.count()
                > (capsForTest != null ? capsForTest[1] : FishtasticServerConfig.fishSchoolMaxMembersPerDimension())) {
            return capBound("members per dimension");
        }
        // A species too big for this water would be a frozen display (encounter spec §4.1).
        if (!FishSchoolSim.hosts(seed, columns(speciesWater), spec, shape)) {
            return fail(species + " does not fit this water", ATTEMPT_RETRY_TICKS);
        }

        List<BlockPos> cells = speciesWater.stream()
                .filter(pos -> Math.abs(pos.getX() - seed.getX()) <= SCHOOL_FORMATION_RADIUS
                        && Math.abs(pos.getZ() - seed.getZ()) <= SCHOOL_FORMATION_RADIUS
                        && Math.abs(pos.getY() - seed.getY()) <= 2)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (cells.size() < plan.min()) return fail("too little room round the start cell for " + plan.min() + " fish", ATTEMPT_RETRY_TICKS);
        float lifetimeScale = profile == null ? 1f
                : profile.worldEncounter().map(FishProfile.WorldEncounter::lifetimeScale).orElse(1f);
        // A derived visit lasts from its window's start; each member leaves at its own seeded tick inside that span.
        long derivedLength = derived == null ? 0L : VisitDerivation.lifetimeTicks(derived.params().visitTicks(),
                derived.params().periodTicks(), lifetimeScale);
        int members = plan.count();
        double spacingSqr = (double) shape.spacing() * shape.spacing();
        List<FishSchoolSavedData.Member> schoolMembers = new ArrayList<>(members);
        // A window's placement is seeded, so a cell set means one arrangement; a debug visit is random.
        RandomSource random;
        if (derived != null) {
            cells.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getX));
            random = RandomSource.create(VisitDerivation.positionSeed(derived.base()));
        } else {
            random = level.getRandom();
        }
        for (int i = cells.size() - 1; i > 0; i--) {
            java.util.Collections.swap(cells, i, random.nextInt(i + 1));
        }
        for (BlockPos pos : cells) {
            if (schoolMembers.size() >= members) break;
            Vec3 candidate = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            boolean clear = schoolMembers.stream().allMatch(member -> distanceSqr(member.position(), candidate) >= spacingSqr);
            if (!clear) continue;
            float yaw = random.nextFloat() * 360f;
            FishSchoolSavedData.Position position = new FishSchoolSavedData.Position(
                    candidate.x, candidate.y, candidate.z, yaw);
            if (derived != null) {
                int index = schoolMembers.size();
                // A forced (debug) visit has no seeded departures: it may be revealed outside its window.
                long departAt = derived.forced() ? -1L
                        : VisitDerivation.departureTick(derived.base(), index, derived.windowStart(), derivedLength);
                schoolMembers.add(FishSchoolSavedData.derivedMember(VisitDerivation.memberId(derived.base(), index), index,
                        VisitDerivation.memberRewardSeed(derived.base(), index), departAt, position));
                continue;
            }
            long rewardSeed = random.nextLong();
            if (qualities != null) {
                FishQuality.Quality want = qualities[schoolMembers.size() % qualities.length];
                for (int tries = 0; tries < 100_000 && FishSchoolSavedData.qualityOf(rewardSeed) != want; tries++) {
                    rewardSeed = random.nextLong();
                }
            }
            schoolMembers.add(FishSchoolSavedData.newMember(rewardSeed, position));
        }
        if (schoolMembers.size() < plan.min()) return fail("could not seat " + plan.min() + " fish apart in this water", ATTEMPT_RETRY_TICKS);

        FishSchoolSavedData.School school;
        if (derived != null) {
            // E11: the full seeded roster stands at its seeded spots; the fish caught (the ledger) and the
            // fish whose seeded departure has passed are not there. Nothing else about the moment of reveal matters.
            long caught = data.caughtMask(derived.id());
            int caughtCount = 0, departedCount = 0;
            for (FishSchoolSavedData.Member member : new ArrayList<>(schoolMembers)) {
                if (VisitDerivation.isCaught(caught, member.index())) {
                    caughtCount++;
                    schoolMembers.remove(member);
                } else if (VisitDerivation.hasDeparted(member.departAt(), now)) {
                    departedCount++;
                    schoolMembers.remove(member);
                }
            }
            if (schoolMembers.isEmpty()) {
                return fail("every fish of this visit was caught or has left (depleted: " + caughtCount + " caught, "
                        + departedCount + " departed)", Long.MAX_VALUE);
            }
            long activeUntil = derived.windowStart() + derivedLength;
            if (derived.forced()) activeUntil = Math.max(activeUntil, now + derivedLength);
            if (now >= activeUntil) return fail("this species' visit is already over for the window", Long.MAX_VALUE);
            school = FishSchoolSavedData.derivedSchool(VisitDerivation.schoolId(derived.base()), derived.id(), species, seed,
                    now, activeUntil, FishSchoolSavedData.snapshot(env), shape, schoolMembers, departedCount, caughtCount);
        } else {
            long lifetime = Math.round(FishtasticServerConfig.visitLengthTicks() * (double) lifetimeScale);
            school = FishSchoolSavedData.newSchool(species, seed, now, now + lifetime,
                    FishSchoolSavedData.snapshot(env), shape, schoolMembers);
        }
        data.addSchool(school);
        RegionCache region = new RegionCache(speciesScan != null ? speciesScan : waterScan, now);
        region.zoneEligible.putAll(zoneEligible);
        // A forced (debug) species' water was found without its habitat: judge it with it soon,
        // as the next rescan always did.
        if (forced != null) region.markDirty(now);
        waterRegions.put(school.id(), region);
        Fishtastic.LOGGER.debug("Created fish school {} ({}, {}) with {} members near {} in {}{}",
                school.id(), species, shape.getSerializedName(), schoolMembers.size(), seed, level.dimension().location(),
                derived == null ? "" : " for " + derived.id());
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

    /** How many fish a visit needs at least, and how many it was drawn to hold. */
    private record MemberPlan(int min, int count) {}

    /**
     * The visit's member count: the shape's range, narrowed by the species' authored range, capped
     * by {@code max} (the per-visit and per-dimension budgets). Null when the budget cannot fit
     * even the smallest visit of this shape. Drawn from {@code derivedBase} (a window's hash) when
     * given, else at random.
     */
    private @Nullable MemberPlan planMembers(VisitShape shape, @Nullable FishProfile profile, int max,
                                             @Nullable Long derivedBase) {
        int min = shape.minMembers(), most = shape.maxMembers();
        var authored = profile == null ? java.util.Optional.<FishProfile.WorldEncounter.MemberRange>empty()
                : profile.worldEncounter().flatMap(FishProfile.WorldEncounter::members);
        if (authored.isPresent()) {
            min = Mth.clamp(authored.get().min(), shape.minMembers(), shape.maxMembers());
            most = Mth.clamp(authored.get().max(), min, shape.maxMembers());
        }
        most = Math.min(most, max);
        if (most < min) return null;
        return new MemberPlan(min, derivedBase != null ? VisitDerivation.memberCount(derivedBase, min, most)
                : min + level.getRandom().nextInt(most - min + 1));
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

    private void tickVisitsAndMembers(long now) {
        tickVisitsAndMembers(now, data.schools());
    }

    private void tickVisitsAndMembers(long now, List<FishSchoolSavedData.School> schools) {
        List<FishSchoolSavedData.School> remove = new ArrayList<>();
        for (FishSchoolSavedData.School school : schools) {
            // Simulation pauses while the anchor chunk is unloaded; no call below loads it.
            if (!level.hasChunkAt(school.anchor())) continue;
            Set<BlockPos> cells = currentWaterRegion(school, now);
            SchoolRuntime runtime = sims.get(school.id());
            if (runtime == null) {
                beginDeparture(school, now);
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
                    // Shrunk below what the species needs: leave rather than freeze in place.
                    if (!runtime.sim.hostsSpecies()) beginDeparture(school, now);
                }
            }
            Item item = BuiltInRegistries.ITEM.getOptional(school.species()).orElse(null);
            if (cells.size() < MIN_WATER_CELLS || item == null || !encounterEligible(item.builtInRegistryHolder())) {
                beginDeparture(school, now);
            }
            if (!FishtasticServerConfig.fishSchoolsEnabled()) {
                beginDeparture(school, now);
            }
            if (school.state() == FishSchoolSavedData.SchoolState.ACTIVE && now >= school.activeUntil()) {
                beginDeparture(school, now);
            }

            for (FishSchoolSavedData.Member member : school.members()) {
                switch (member.state()) {
                    case APPROACHING -> tickApproach(school, member, cells, now);
                    case BITE_READY -> tickBiteWindow(school, member, now);
                    case RECOVERING -> {
                        if (now >= member.recoveryUntil()) {
                            if (school.state() == FishSchoolSavedData.SchoolState.ACTIVE && cells.contains(blockPos(member.position()))) {
                                member.setState(FishSchoolSavedData.MemberState.AVAILABLE);
                            } else {
                                member.setState(FishSchoolSavedData.MemberState.DEPARTING);
                            }
                        }
                    }
                    case AVAILABLE -> {
                        if (school.state() != FishSchoolSavedData.SchoolState.ACTIVE) {
                            member.setState(FishSchoolSavedData.MemberState.DEPARTING);
                        } else if (VisitDerivation.hasDeparted(member.departAt(), now)) {
                            // Its seeded time came: it leaves the visit while the others stay (E11).
                            member.setState(FishSchoolSavedData.MemberState.DEPARTING);
                            member.setFadeUntil(now + DEPARTURE_TICKS);
                        }
                    }
                    case DEPARTING -> {
                        // It swims on with its school while it fades; then it is gone.
                        long until = member.fadeUntil() > 0 ? member.fadeUntil() : school.departureUntil();
                        if (now >= until) {
                            member.setState(FishSchoolSavedData.MemberState.DEPARTED);
                            leaveWater(school, member);
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
            if (school.visitId() != null) concludeVisit(school.visitId());
            forgetVisit(school);
        }
    }

    private Set<BlockPos> currentWaterRegion(FishSchoolSavedData.School school, long now) {
        RegionCache cached = waterRegions.get(school.id());
        if (cached != null && !cached.due(now, school.id())) return cached.cells;
        Item item = BuiltInRegistries.ITEM.getOptional(school.species()).orElse(null);
        Holder<Item> holder = item == null || habitatFreeForTest.contains(school.id()) ? null : item.builtInRegistryHolder();
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
                : now - reservation.startedAt() > approachLimitTicks(runtime) ? "too slow"
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

    /**
     * Tells each garden-eel visit whether a player is rushing it: the nearest sprinting player
     * within {@link #RUSH_RADIUS}, or nobody (encounter spec §4.3). Sent only on a change, or once
     * the rusher has moved {@link #WATCHER_RESEND}, so a calm visit adds no inputs.
     */
    private void feedWatchers() {
        for (FishSchoolSavedData.School school : data.schools()) {
            SchoolRuntime runtime = sims.get(school.id());
            if (runtime == null || runtime.sim.spec().locomotion() != Locomotion.ANCHORED) continue;
            Vec3 anchor = Vec3.atCenterOf(school.anchor());
            ServerPlayer rusher = null;
            double best = RUSH_RADIUS * RUSH_RADIUS;
            for (ServerPlayer player : level.players()) {
                if (!player.isSprinting() || player.isSpectator()) continue;
                double distance = player.position().distanceToSqr(anchor);
                if (distance < best) {
                    best = distance;
                    rusher = player;
                }
            }
            BlockPos o = runtime.sim.origin();
            if (rusher == null) {
                if (runtime.watcherNear) {
                    runtime.watcherNear = false;
                    runtime.apply(new FishSchoolSim.Watcher(false, 0f, 0f, 0f));
                }
                continue;
            }
            float l = (float) (rusher.getX() - o.getX()), y = (float) (rusher.getY() - o.getY()),
                    d = (float) (rusher.getZ() - o.getZ());
            float dl = l - runtime.watcherL, dy = y - runtime.watcherY, dd = d - runtime.watcherD;
            if (!runtime.watcherNear || dl * dl + dy * dy + dd * dd > WATCHER_RESEND * WATCHER_RESEND) {
                runtime.watcherNear = true;
                runtime.watcherL = l;
                runtime.watcherY = y;
                runtime.watcherD = d;
                runtime.apply(new FishSchoolSim.Watcher(true, l, y, d));
            }
        }
    }

    /**
     * How long a member of {@code school} may take to reach a bobber, ticks: the configured limit,
     * longer for the slow classes (a crawler walking the floor, a drifter carried over).
     */
    private long approachLimitTicks(@Nullable SchoolRuntime runtime) {
        long base = FishtasticServerConfig.fishSchoolApproachMaxTicks();
        if (runtime == null) return base;
        return switch (runtime.sim.spec().locomotion()) {
            case BENTHIC -> base * 2;
            case DRIFT -> base * 3;
            default -> base;
        };
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
                    now + approachLimitTicks(runtime) + FishtasticServerConfig.fishSchoolBiteWindowTicks(),
                    0, -1));
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
        float[] chosenGoal = null;
        double bestDistance = Double.MAX_VALUE;
        double nearestAvailable = Double.MAX_VALUE;
        int schoolsHere = 0, unreachable = 0, noGoal = 0, outOfDepth = 0;
        String noGoalWhy = null;
        for (FishSchoolSavedData.School school : data.schools()) {
            SchoolRuntime runtime = sims.get(school.id());
            if (school.state() != FishSchoolSavedData.SchoolState.ACTIVE || runtime == null) continue;
            Set<BlockPos> cells = currentWaterRegion(school, now);
            if (!cells.contains(hookPos)) continue;
            schoolsHere++;
            // An eel never travels: it takes only a bobber over its own burrow (encounter spec §4).
            boolean eel = runtime.sim.spec().locomotion() == Locomotion.ANCHORED;
            // Bite reach is a cylinder under the bobber: narrow sideways, deep downward. A bobber's
            // depth is not the player's to control, so only its position over the fish is a skill.
            double radius = eel ? FishSchoolSim.EEL_REACH : FishtasticServerConfig.fishSchoolBiteReachHorizontal();
            double reachDown = FishtasticServerConfig.fishSchoolBiteReachDepth();
            BlockPos o = runtime.sim.origin();
            float hl = (float) (hook.getX() - o.getX()), hy = (float) (hook.getY() - o.getY()),
                    hd = (float) (hook.getZ() - o.getZ());
            for (FishSchoolSavedData.Member member : school.members()) {
                if (member.state() != FishSchoolSavedData.MemberState.AVAILABLE) continue;
                Vec3 at = vec(member.position());
                double sideways = horizontalDistanceSqr(at, hook.position());
                double below = hook.getY() - at.y; // positive: the fish is under the bobber
                nearestAvailable = Math.min(nearestAvailable, sideways);
                if (sideways > radius * radius) continue;
                if (!eel && (below > reachDown || below < -BITE_REACH_UP)) {
                    outOfDepth++;
                    continue;
                }
                // The fish straight under the bobber wins; depth only breaks near-ties.
                double distance = sideways + 0.05 * below * below;
                if (distance >= bestDistance) continue;
                // Reachability only: the swim itself is the engine's.
                if (!eel && !withinPath(blockPos(member.position()), hookPos, cells,
                        (int) Math.ceil(Math.sqrt(sideways)) + (int) Math.ceil(Math.abs(below)) + 4)) {
                    unreachable++;
                    continue;
                }
                float[] goal = runtime.sim.goalFor(member.id(), hl, hy, hd);
                if (goal == null) {
                    noGoal++;
                    noGoalWhy = switch (runtime.sim.spec().locomotion()) {
                        case BENTHIC -> "no floor within " + (int) FishSchoolSim.BOTTOM_BITE_DEPTH + " blocks under the bobber";
                        case ANCHORED -> "the eel is hiding in its burrow";
                        default -> String.format(java.util.Locale.ROOT, "no room for a fish's nose under the bobber (bobber y %.2f)",
                                hook.getY());
                    };
                    continue;
                }
                chosenSchool = school;
                chosenMember = member;
                chosenGoal = goal;
                bestDistance = distance;
            }
        }
        if (chosenSchool == null) {
            if (schoolsHere == 0) return HookChoice.no("bobber is not in any active school's water");
            if (nearestAvailable == Double.MAX_VALUE) return HookChoice.no("no school fish is free right now");
            if (noGoal > 0) return HookChoice.no(noGoalWhy);
            if (unreachable > 0) return HookChoice.no(unreachable + " fish in range but no water path to the bobber");
            if (outOfDepth > 0) return HookChoice.no(outOfDepth + " fish under the bobber but more than "
                    + FishtasticServerConfig.fishSchoolBiteReachDepth() + " blocks below it");
            return HookChoice.no(String.format(java.util.Locale.ROOT, "nearest free fish is %.1f blocks away sideways",
                    Math.sqrt(nearestAvailable)));
        }
        return new HookChoice(null, hook, chosenSchool, chosenMember, chosenGoal);
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
                            runtime.sim.steps(), school.members().stream()
                                    .map(m -> new FishSchoolSetupPacket.MemberQuality(m.id(), m.quality())).toList(),
                            (int) Math.min(Integer.MAX_VALUE, Math.max(0L, now - school.createdAt()))));
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
