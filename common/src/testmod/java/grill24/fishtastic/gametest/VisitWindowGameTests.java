package grill24.fishtastic.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import grill24.fishtastic.config.FishtasticServerConfig;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.ecology.VisitDerivation;
import grill24.fishtastic.ecology.VisitId;
import grill24.fishtastic.ecology.VisitWeatherLog;
import grill24.fishtastic.ecology.VisitWindows;
import grill24.fishtastic.server.FishSchoolManager;
import grill24.fishtastic.server.FishSchoolSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Visit windows against a real level (docs/world-fish-exploration-implementation-plan.md, Step 2):
 * a visit is the same wherever and whenever it is revealed, the scheduler only reveals what the hash
 * schedules, depletion is the only thing saved, and old school and region data is discarded.
 *
 * <p>Windows are exercised at simulated game times through the manager's test seams, because the
 * schedule is a hash of the world seed: a test cannot wait for a particular window, so it searches
 * for one that suits (its chunk hosts a visit and the rolled species fits the test pond) and then
 * plays the clock around it. Every test is synchronous (no ticks pass inside it), and lifts the
 * protection caps, which the many fixture schools of a shared test world would otherwise bind.
 */
public final class VisitWindowGameTests {
    private VisitWindowGameTests() {}

    private static final int[] NO_CAPS = {1000, 100_000};

    /** A snapshot of everything that must be identical between two reveals of one window. */
    private record Shot(UUID schoolId, String species, String shape, BlockPos anchor, long activeUntil,
                        List<Integer> indices, List<String> members) {
        static Shot of(FishSchoolSavedData.School school) {
            List<String> members = new ArrayList<>();
            List<Integer> indices = new ArrayList<>();
            for (FishSchoolSavedData.Member m : school.members()) {
                FishSchoolSavedData.Position p = m.position();
                indices.add(m.index());
                members.add(m.id() + "|" + m.index() + "|" + m.rewardSeed() + "|" + p.x() + "|" + p.y() + "|" + p.z() + "|" + p.yaw());
            }
            return new Shot(school.id(), school.species().toString(), school.shape().getSerializedName(), school.anchor(),
                    school.activeUntil(), indices, members);
        }

        /** The members of this (full) roster still there at game time {@code t}, by their seeded departures; null if none. */
        Shot presentAt(long base, long start, long t) {
            long length = activeUntil - start;
            List<Integer> keptIndices = new ArrayList<>();
            List<String> kept = new ArrayList<>();
            for (int i = 0; i < indices.size(); i++) {
                if (VisitDerivation.hasDeparted(VisitDerivation.departureTick(base, indices.get(i), start, length), t)) continue;
                keptIndices.add(indices.get(i));
                kept.add(members.get(i));
            }
            return kept.isEmpty() ? null : new Shot(schoolId, species, shape, anchor, activeUntil, keptIndices, kept);
        }
    }

    /** A window that reveals in the pond: its id and window start, its full roster at the start, and the hash base. */
    private record Found(VisitId id, long start, long base, Shot full) {
        long length() {
            return full.activeUntil() - start;
        }
    }

    /** A 13 x 13 pond three blocks deep at sea level in the test area; its centre. */
    private static BlockPos pond(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos center = new BlockPos(origin.getX() + 2, level.getSeaLevel(), origin.getZ() + 2);
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                level.setBlock(center.offset(x, -3, z), Blocks.SAND.defaultBlockState(), 3);
                for (int y = -2; y <= 0; y++) {
                    level.setBlock(center.offset(x, y, z), Blocks.WATER.defaultBlockState(), 3);
                }
            }
        }
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                if (Math.abs(x) != 7 && Math.abs(z) != 7) continue;
                for (int y = -2; y <= 0; y++) {
                    level.setBlock(center.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        return center;
    }

    private static String dimension(ServerLevel level) {
        return level.dimension().location().toString();
    }

    /** Forgets every window-derived visit, so one test leaves nothing for the next. */
    private static void clearDerived(FishSchoolManager schools, FishSchoolSavedData data) {
        for (FishSchoolSavedData.School school : data.schoolsForTest()) {
            if (school.visitId() != null) schools.forgetVisitForTest(school);
        }
    }

    /**
     * The first window of {@code chunk} (searching from window 1) whose visit can be derived in the test
     * pond: its roll fits. With {@code mustHost} it must also be scheduled and unsuppressed. The probe
     * visit is forgotten again.
     */
    private static VisitId findRevealable(GameTestHelper helper, FishSchoolManager schools, ChunkPos chunk, boolean mustHost) {
        return findWindow(helper, schools, chunk, mustHost, found -> true).id();
    }

    private static Found findWindow(GameTestHelper helper, FishSchoolManager schools, ChunkPos chunk, boolean mustHost,
                                    java.util.function.Predicate<Found> want) {
        ServerLevel level = helper.getLevel();
        long seed = schools.ecologySeedForTest();
        VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
        for (long k = 1; k < 20_000; k++) {
            if (mustHost && !VisitWindows.hostsVisit(seed, chunk.x, chunk.z, k, params)) continue;
            VisitId id = new VisitId(dimension(level), chunk.x, chunk.z, k);
            long start = id.start(seed, params);
            FishSchoolSavedData.School probe = schools.revealWindowForTest(id, start);
            if (probe == null) continue;
            Found found = new Found(id, start, VisitDerivation.base(seed, id), Shot.of(probe));
            schools.forgetVisitForTest(probe);
            if (want.test(found)) return found;
        }
        helper.fail("no suitable window of chunk " + chunk + " in the test pond: " + schools.lastRevealNoteForTest());
        throw new IllegalStateException("unreachable");
    }

    public static void aVisitRevealedAtWindowStartAndByALateArrivalAreTheSame(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = pond(helper);
        ChunkPos chunk = new ChunkPos(center);
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        schools.setCapsForTest(NO_CAPS);
        try {
            VisitId id = findRevealable(helper, schools, chunk, false);
            long seed = schools.ecologySeedForTest();
            VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
            long start = id.start(seed, params);

            FishSchoolSavedData.School atStart = schools.revealWindowForTest(id, start);
            helper.assertTrue(atStart != null, "the window must reveal at its start: " + schools.lastRevealNoteForTest());
            helper.assertTrue(id.equals(atStart.visitId()), "a revealed visit carries its VisitId");
            Shot expected = Shot.of(atStart);
            helper.assertTrue(!expected.members().isEmpty(), "a visit holds members");
            schools.forgetVisitForTest(atStart);

            Shot full = expected;
            long base = VisitDerivation.base(seed, id);
            for (long late : new long[]{1, 600, 1700, params.visitTicks() - 60}) {
                FishSchoolSavedData.School arrived = schools.revealWindowForTest(id, start + late);
                if (start + late >= full.activeUntil()) {
                    helper.assertTrue(arrived == null, "a reveal after the species' own end shows nothing");
                    continue;
                }
                Shot predicted = full.presentAt(base, start, start + late);
                if (predicted == null) {
                    helper.assertTrue(arrived == null && schools.lastRevealNoteForTest().contains("departed"),
                            "when every fish has left a late arrival sees nothing: " + schools.lastRevealNoteForTest());
                    continue;
                }
                helper.assertTrue(arrived != null, "a late arrival at +" + late + " must reveal the window: " + schools.lastRevealNoteForTest());
                helper.assertTrue(Shot.of(arrived).equals(predicted),
                        "a reveal at +" + late + " must be the same visit, minus the fish that have left (VisitId, species, shape,"
                                + " members, seeds, positions):\n" + Shot.of(arrived) + "\nvs\n" + predicted);
                schools.forgetVisitForTest(arrived);
            }
            helper.succeed();
        } finally {
            clearDerived(schools, data);
            schools.setCapsForTest(null);
        }
    }

    public static void theSchedulerRevealsOnlyWhatTheHashSchedulesAndNeverRerolls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = pond(helper);
        ChunkPos chunk = new ChunkPos(center);
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        schools.setCapsForTest(NO_CAPS);
        try {
            Found found = findWindow(helper, schools, chunk, true, f -> true);
            VisitId id = found.id();
            long seed = schools.ecologySeedForTest();
            VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
            long start = found.start();
            // Before the first seeded departure (a tenth into the visit) the roster is full, so every reveal is comparable.
            long early = start + found.length() / 20, beforeFirstDeparture = start + found.length() / 10 - 1;
            Vec3 here = Vec3.atCenterOf(center);
            List<Vec3> near = List.of(here);

            // Nothing reveals outside the window's open span, with nobody near, or with a player too far.
            schools.scheduleVisitsForTest(start + found.length() + 50, near);
            helper.assertTrue(schools.liveVisitForTest(id) == null, "a window past its species' visit must not reveal");
            schools.clearAttemptsForTest();
            schools.scheduleVisitsForTest(early, List.of());
            helper.assertTrue(schools.liveVisitForTest(id) == null, "with nobody near nothing reveals");
            schools.scheduleVisitsForTest(early, List.of(here.add(600, 0, 0)));
            helper.assertTrue(schools.liveVisitForTest(id) == null, "a player far away must not reveal it");

            // A player at the pond reveals the scheduled window, once.
            schools.scheduleVisitsForTest(early, near);
            FishSchoolSavedData.School live = schools.liveVisitForTest(id);
            helper.assertTrue(live != null && id.equals(live.visitId()), "the scheduled window must reveal for a player at the pond");
            Shot first = Shot.of(live);
            helper.assertTrue(first.equals(found.full()), "the early reveal is the full seeded roster");
            for (long dt : new long[]{20, 200, 900, 1500, 2300}) {
                schools.scheduleVisitsForTest(early + dt, near);
                long count = data.schoolsForTest().stream().filter(s -> id.equals(s.visitId())).count();
                helper.assertTrue(count == 1 && schools.liveVisitForTest(id) == live,
                        "passes at early+" + dt + " must neither add nor replace the window's visit (" + count + ")");
            }

            // The site's window is a function of time alone: nothing about position or crossing a line moves it.
            for (long t = start; t < start + params.periodTicks(); t += 97) {
                helper.assertTrue(VisitId.at(dimension(level), seed, chunk.x, chunk.z, t, params).equals(id),
                        "every tick of the window maps to the same VisitId, at +" + (t - start));
            }

            // Leaving (nobody within the drop distance) drops the live visit; returning derives the same one.
            long dropped = schools.windowsDroppedForTest();
            schools.dropFarVisitsForTest(List.of(here.add(1000, 0, 0)));
            helper.assertTrue(schools.liveVisitForTest(id) == null && schools.windowsDroppedForTest() >= dropped + 1,
                    "with nobody within tracking range + margin the live visit is dropped and counted");
            schools.scheduleVisitsForTest(beforeFirstDeparture, near);
            FishSchoolSavedData.School back = schools.liveVisitForTest(id);
            helper.assertTrue(back != null && Shot.of(back).equals(first),
                    "returning must reveal the very same visit (no new window, no reroll)");

            // Which chunks happen to be in range or loaded does not change what a window is.
            schools.dropFarVisitsForTest(List.of());
            schools.scheduleVisitsForTest(beforeFirstDeparture, List.of(here.add(40, 0, 0)));
            FishSchoolSavedData.School other = schools.liveVisitForTest(id);
            helper.assertTrue(other != null && Shot.of(other).equals(first),
                    "a different set of chunks in range must reveal the same visit");
            helper.succeed();
        } finally {
            clearDerived(schools, data);
            schools.setCapsForTest(null);
        }
    }

    /**
     * Over thousands of windows of the pond's chunk the scheduler reveals exactly the windows the pure
     * schedule says host a visit (and whose rolled species fits), including never an active window that a
     * neighbour suppresses; the answer does not depend on what else is loaded.
     */
    public static void suppressionAndSchedulingAreTheSameForAnyLoadedSet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = pond(helper);
        ChunkPos chunk = new ChunkPos(center);
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        long seed = schools.ecologySeedForTest();
        VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
        schools.setCapsForTest(NO_CAPS);
        try {
            // The pure part: order and subset of the chunks asked about change nothing.
            Random random = new Random(7);
            for (long k = 1; k < 40; k++) {
                for (int i = 0; i < 40; i++) {
                    int x = chunk.x + random.nextInt(15) - 7, z = chunk.z + random.nextInt(15) - 7;
                    boolean alone = VisitWindows.hostsVisit(seed, x, z, k, params);
                    for (int dx = -2; dx <= 2; dx++) VisitWindows.hostsVisit(seed, x + dx, z, k, params); // "load" neighbours
                    helper.assertTrue(VisitWindows.hostsVisit(seed, x, z, k, params) == alone,
                            "hostsVisit must not depend on what was evaluated before");
                }
            }
            // The scheduler part.
            Vec3 here = Vec3.atCenterOf(center);
            int hosted = 0, revealed = 0, suppressedActive = 0;
            for (long k = 1; k <= 8000; k++) {
                VisitId id = new VisitId(dimension(level), chunk.x, chunk.z, k);
                long now = id.start(seed, params) + 200;
                boolean active = VisitWindows.isActive(seed, chunk.x, chunk.z, k, params);
                boolean hosts = VisitWindows.hostsVisit(seed, chunk.x, chunk.z, k, params);
                schools.scheduleVisitsForTest(now, List.of(here));
                FishSchoolSavedData.School live = schools.liveVisitForTest(id);
                if (!hosts) {
                    helper.assertTrue(live == null, "window " + k + " is " + (active ? "suppressed" : "inactive") + " and must not reveal");
                    if (active) suppressedActive++;
                } else {
                    hosted++;
                    if (live != null) {
                        revealed++;
                    } else {
                        FishSchoolSavedData.School direct = schools.revealWindowForTest(id, now);
                        helper.assertTrue(direct == null, "window " + k + " hosts a visit and its derivation works, so the scheduler must reveal it");
                    }
                }
                clearDerived(schools, data);
            }
            helper.assertTrue(hosted > 0 && revealed > 0, "the sample must contain hosting windows: " + hosted + "/" + revealed);
            helper.assertTrue(suppressedActive > 0, "the sample must contain an active-but-suppressed window");
            helper.succeed();
        } finally {
            clearDerived(schools, data);
            schools.setCapsForTest(null);
        }
    }

    public static void depletionSurvivesSaveAndReloadAndOmitsCaughtMembers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = pond(helper);
        ChunkPos chunk = new ChunkPos(center);
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        schools.setCapsForTest(NO_CAPS);
        try {
            VisitId id = findRevealable(helper, schools, chunk, false);
            long start = id.start(schools.ecologySeedForTest(), FishtasticServerConfig.visitWindowParams());
            FishSchoolSavedData.School school = schools.revealWindowForTest(id, start);
            Shot full = Shot.of(school);
            UUID player = UUID.randomUUID();

            FishSchoolSavedData.Member caught = school.members().get(0);
            long before = data.caughtMask(id);
            data.setMemberForTest(caught, FishSchoolSavedData.MemberState.IN_SESSION,
                    new FishSchoolSavedData.Reservation(player, UUID.randomUUID(), start, start + 1000, 7, 0));
            boolean resolved = schools.resolveCaught(new FishSchoolManager.SessionBinding(school.id(), caught.id(), player, 7, 0,
                    school.species(), caught.rewardSeed()));
            helper.assertTrue(resolved, "a bound member's catch must resolve");
            helper.assertTrue(before == 0 && VisitDerivation.isCaught(data.caughtMask(id), caught.index()),
                    "the catch must mark the member in the depletion ledger");

            var encoded = FishSchoolSavedData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
            FishSchoolSavedData restored = FishSchoolSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
            helper.assertTrue(restored.caughtMask(id) == data.caughtMask(id) && restored.caughtMask(id) != 0,
                    "depletion must survive a save and reload");
            helper.assertTrue(restored.schoolsForTest().isEmpty(), "live visits are not saved: they are derived again");

            // A re-reveal (a different player arriving later) leaves the caught fish out and the rest untouched.
            schools.forgetVisitForTest(school);
            FishSchoolSavedData.School again = schools.revealWindowForTest(id, start + 5);
            if (full.members().size() > 1) {
                helper.assertTrue(again != null, "the window still holds uncaught fish: " + schools.lastRevealNoteForTest());
                helper.assertTrue(again.members().size() == full.members().size() - 1
                                && again.members().stream().noneMatch(m -> m.id().equals(caught.id())),
                        "a re-reveal must omit the caught member");
                List<String> rest = Shot.of(again).members();
                helper.assertTrue(full.members().containsAll(rest), "the other members must be exactly as before");
                // Catch the rest: the window is depleted and no longer reveals.
                for (FishSchoolSavedData.Member member : again.members()) {
                    data.setMemberForTest(member, FishSchoolSavedData.MemberState.IN_SESSION,
                            new FishSchoolSavedData.Reservation(player, UUID.randomUUID(), start, start + 1000, 8, 0));
                    helper.assertTrue(schools.resolveCaught(new FishSchoolManager.SessionBinding(again.id(), member.id(), player, 8, 0,
                            again.species(), member.rewardSeed())), "each remaining catch resolves");
                }
                schools.forgetVisitForTest(again);
            } else {
                schools.forgetVisitForTest(again == null ? school : again);
            }
            FishSchoolSavedData.School depleted = schools.revealWindowForTest(id, start + 5);
            helper.assertTrue(depleted == null && schools.lastRevealNoteForTest().contains("depleted"),
                    "a fully caught window must stay depleted: " + schools.lastRevealNoteForTest());
            helper.succeed();
        } finally {
            clearDerived(schools, data);
            schools.pruneLedgerForTest(Long.MAX_VALUE / 4);
            schools.setCapsForTest(null);
        }
    }

    public static void oldSchoolAndRegionDataIsDiscardedOnLoad(GameTestHelper helper) {
        String old = """
                {"schools":[{"id":"00000000-0000-0000-0000-000000000001","species":"fishtastic:bluegill",
                  "anchor":[1,2,3],"created_at":10,"active_until":500,"state":"active",
                  "formation":{"biome":"minecraft:plains","time":"day","weather":"clear","moon":"full_moon","zones":["surface"]},
                  "members":[{"id":"00000000-0000-0000-0000-000000000002","reward_seed":5,
                    "position":{"x":1.5,"y":2.5,"z":3.5,"yaw":0.0},"state":"caught"}]}],
                 "regions":[{"x":2,"z":-3,"next_attempt":24000,"last_seen":20}]}""";
        JsonObject onlyOld = JsonParser.parseString(old).getAsJsonObject();
        FishSchoolSavedData loaded = FishSchoolSavedData.CODEC.parse(JsonOps.INSTANCE, onlyOld).getOrThrow();
        helper.assertTrue(loaded.schoolsForTest().isEmpty(), "saved schools must be discarded on load (P4)");
        helper.assertTrue(loaded.depletionForTest().isEmpty(), "old data carries no depletion");
        JsonObject written = FishSchoolSavedData.CODEC.encodeStart(JsonOps.INSTANCE, loaded).getOrThrow().getAsJsonObject();
        helper.assertTrue(!written.has("schools") && !written.has("regions"),
                "the next save must not write the old fields back: " + written);

        onlyOld.add("depletion", JsonParser.parseString("[{\"x\":5,\"z\":6,\"window\":7,\"caught\":3}]"));
        FishSchoolSavedData mixed = FishSchoolSavedData.CODEC.parse(JsonOps.INSTANCE, onlyOld).getOrThrow();
        helper.assertTrue(mixed.schoolsForTest().isEmpty() && mixed.depletionForTest().size() == 1
                        && mixed.depletionForTest().get(0).caughtMask() == 3L,
                "a file with both old fields and the new ledger keeps only the ledger");
        helper.succeed();
    }

    public static void ledgerRowsArePrunedAfterTheirWindowAndProtectionCapsAreCounted(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        long seed = schools.ecologySeedForTest();
        VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
        VisitId early = new VisitId(dimension(level), 900, 900, 5);
        VisitId later = new VisitId(dimension(level), 900, 900, 9);
        try {
            data.markCaught(early, 0);
            data.markCaught(early, 2);
            data.markCaught(later, 1);
            helper.assertTrue(data.caughtMask(early) == 0b101L && data.caughtMask(later) == 0b10L, "members accumulate in the mask");
            schools.pruneLedgerForTest(early.start(seed, params) + params.periodTicks() - 1);
            helper.assertTrue(data.caughtMask(early) != 0, "a row stays while its window can still be revealed");
            schools.pruneLedgerForTest(early.start(seed, params) + params.periodTicks());
            helper.assertTrue(data.caughtMask(early) == 0 && data.caughtMask(later) != 0, "a row goes once its window is over");
        } finally {
            schools.pruneLedgerForTest(Long.MAX_VALUE / 4);
        }

        BlockPos center = pond(helper);
        ChunkPos chunk = new ChunkPos(center);
        schools.setCapsForTest(NO_CAPS);
        try {
            VisitId id = findRevealable(helper, schools, chunk, false);
            schools.setCapsForTest(new int[]{0, 100_000});
            long binds = schools.capBindsForTest();
            helper.assertTrue(schools.revealWindowForTest(id, id.start(seed, params)) == null
                            && schools.lastRevealNoteForTest().contains("protection cap"),
                    "a reveal beyond the school cap is refused: " + schools.lastRevealNoteForTest());
            helper.assertTrue(schools.capBindsForTest() == binds + 1, "a binding cap must be counted");
            schools.setCapsForTest(new int[]{1000, 0});
            helper.assertTrue(schools.revealWindowForTest(id, id.start(seed, params)) == null
                            && schools.capBindsForTest() == binds + 2,
                    "the member cap binds and is counted too");
            helper.succeed();
        } finally {
            clearDerived(schools, data);
            schools.setCapsForTest(null);
        }
    }

    public static void theVisitDebugCommandsReportAndForceAReveal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = pond(helper);
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        schools.setCapsForTest(NO_CAPS);
        try {
            Vec3 here = Vec3.atCenterOf(center);
            String report = schools.visitWindowsReport(here);
            helper.assertTrue(report.startsWith("Visit windows in " + dimension(level)) && report.contains("T=") && report.contains("L="),
                    "the windows report must say what it shows: " + report);
            long before = data.schoolsForTest().stream().filter(s -> s.visitId() != null).count();
            String forced = schools.revealNearestForDebug(here);
            helper.assertTrue(forced.startsWith("FORCED OVERRIDE"), "a forced reveal must say it is an override: " + forced);
            long live = data.schoolsForTest().stream().filter(s -> s.visitId() != null).count();
            if (forced.contains("revealed")) {
                helper.assertTrue(live == before + 1 && forced.contains("schedule itself is untouched"),
                        "a forced reveal adds one visit and says the schedule is untouched (" + before + " -> " + live + "): " + forced);
                helper.assertTrue(schools.revealNearestForDebug(here).contains("already has a live"),
                        "a second forced reveal of the same window refuses");
            } else if (forced.contains("already has a live")) {
                helper.assertTrue(live == before, "an already-live window is reported without adding a visit: " + forced);
            } else {
                helper.assertTrue(live == before && forced.contains("no visit could form"), "a failed forced reveal says why: " + forced);
            }
            helper.succeed();
        } finally {
            clearDerived(schools, data);
            schools.setCapsForTest(null);
        }
    }

    /** E11: arriving halfway shows exactly the fish whose seeded departure is still to come, at the same seeded spots. */
    public static void aRevealHalfwayThroughTheWindowMatchesTheMembersPresentPrediction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = pond(helper);
        ChunkPos chunk = new ChunkPos(center);
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        schools.setCapsForTest(NO_CAPS);
        try {
            Found found = findWindow(helper, schools, chunk, false, f -> {
                Shot half = f.full().presentAt(f.base(), f.start(), f.start() + f.length() / 2);
                return f.full().indices().size() >= 3 && half != null && half.indices().size() < f.full().indices().size();
            });
            long mid = found.start() + found.length() / 2;
            Shot predicted = found.full().presentAt(found.base(), found.start(), mid);
            FishSchoolSavedData.School halfway = schools.revealWindowForTest(found.id(), mid);
            helper.assertTrue(halfway != null, "the window reveals halfway: " + schools.lastRevealNoteForTest());
            helper.assertTrue(Shot.of(halfway).equals(predicted),
                    "halfway shows exactly the members present by their seeded departures, at their seeded spots:\n"
                            + Shot.of(halfway) + "\nvs\n" + predicted);
            helper.assertTrue(halfway.departedAtReveal() == found.full().indices().size() - predicted.indices().size()
                            && halfway.members().stream().allMatch(m -> m.state() == FishSchoolSavedData.MemberState.AVAILABLE),
                    "the departed are counted, the present are free");
            schools.forgetVisitForTest(halfway);
            helper.succeed();
        } finally {
            clearDerived(schools, data);
            schools.setCapsForTest(null);
        }
    }

    /** A window whose fish have all left or been caught never reveals again, even with the memory of it gone. */
    public static void aWindowWhoseFishAllLeftOrWereCaughtNeverRevealsAgain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = pond(helper);
        ChunkPos chunk = new ChunkPos(center);
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        schools.setCapsForTest(NO_CAPS);
        try {
            Found found = findWindow(helper, schools, chunk, true, f -> true);
            long last = found.start() + found.length() - 1;
            Vec3 here = Vec3.atCenterOf(center);
            helper.assertTrue(schools.revealWindowForTest(found.id(), last) == null
                            && schools.lastRevealNoteForTest().contains("departed"),
                    "on the last tick everyone has left: " + schools.lastRevealNoteForTest());
            for (int pass = 0; pass < 2; pass++) {
                schools.clearAttemptsForTest(); // as after a restart: no memory of the window
                schools.scheduleVisitsForTest(last, List.of(here));
                helper.assertTrue(schools.liveVisitForTest(found.id()) == null, "a window whose fish all left must not reveal (pass " + pass + ")");
            }

            // The same by catches: everyone caught, early in the window.
            for (int index : found.full().indices()) data.markCaught(found.id(), index);
            schools.clearAttemptsForTest();
            schools.scheduleVisitsForTest(found.start() + 5, List.of(here));
            helper.assertTrue(schools.liveVisitForTest(found.id()) == null, "a window whose fish were all caught must not reveal");
            helper.succeed();
        } finally {
            clearDerived(schools, data);
            schools.pruneLedgerForTest(Long.MAX_VALUE / 4);
            schools.setCapsForTest(null);
        }
    }

    /** A live visit thins out across its window: each member leaves at its seeded tick, through the existing departure. */
    public static void membersLeaveLiveAtTheirSeededTimes(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = pond(helper);
        ChunkPos chunk = new ChunkPos(center);
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        schools.setCapsForTest(NO_CAPS);
        try {
            Found found = findWindow(helper, schools, chunk, false, f -> {
                if (f.full().indices().size() < 2) return false;
                long first = firstDeparture(f);
                return first - 120 > f.start() && first + 100 < f.start() + f.length() - 20;
            });
            long first = firstDeparture(found);
            int firstIndex = -1;
            for (int index : found.full().indices()) {
                if (VisitDerivation.departureTick(found.base(), index, found.start(), found.length()) == first) firstIndex = index;
            }
            int leaver = firstIndex;
            long t = first - 80;
            FishSchoolSavedData.School school = schools.revealWindowForTest(found.id(), t);
            helper.assertTrue(school != null && school.members().size() == found.full().indices().size(), "everyone is there before the first departure");
            FishSchoolSavedData.Member member = school.members().stream().filter(m -> m.index() == leaver).findFirst().orElseThrow();
            schools.tickVisitForTest(school, first - 1);
            helper.assertTrue(member.state() == FishSchoolSavedData.MemberState.AVAILABLE,
                    "before its seeded tick the fish stays: " + member.state());
            schools.tickVisitForTest(school, first);
            helper.assertTrue(member.state() == FishSchoolSavedData.MemberState.DEPARTING,
                    "at its seeded tick the fish starts leaving through the departure path: " + member.state());
            schools.tickVisitForTest(school, first + 61);
            helper.assertTrue(member.state() == FishSchoolSavedData.MemberState.DEPARTED && !schools.memberInWaterForTest(school.id(), member.id()),
                    "after the fade the fish is gone from the water: " + member.state());
            helper.assertTrue(school.state() == FishSchoolSavedData.SchoolState.ACTIVE,
                    "one fish leaving does not end the visit: it thins out");
            helper.succeed();
        } finally {
            clearDerived(schools, data);
            schools.setCapsForTest(null);
        }
    }

    private static long firstDeparture(Found f) {
        long first = Long.MAX_VALUE;
        for (int index : f.full().indices()) {
            first = Math.min(first, VisitDerivation.departureTick(f.base(), index, f.start(), f.length()));
        }
        return first;
    }

    /** E14: draining the pond makes the live visit depart through the existing path, and its window stays concluded. */
    public static void aWaterEditThatStopsTheWaterMakesALiveVisitDepartAndStayGone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = pond(helper);
        ChunkPos chunk = new ChunkPos(center);
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        schools.setCapsForTest(NO_CAPS);
        try {
            Found found = findWindow(helper, schools, chunk, true, f -> true);
            long t = found.start() + found.length() / 20;
            FishSchoolSavedData.School school = schools.revealWindowForTest(found.id(), t);
            helper.assertTrue(school != null, "the visit reveals: " + schools.lastRevealNoteForTest());
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) {
                    for (int y = -2; y <= 0; y++) level.setBlock(center.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            schools.tickVisitForTest(school, t + 100);
            helper.assertTrue(school.state() == FishSchoolSavedData.SchoolState.DEPARTING,
                    "draining its water starts the normal departure: " + schools.debugState(school.id()));
            schools.tickVisitForTest(school, t + 200);
            helper.assertTrue(schools.liveVisitForTest(found.id()) == null,
                    "with no water left the live visit must have departed and gone: " + schools.debugState(school.id()));
            Vec3 here = Vec3.atCenterOf(center);
            helper.assertTrue(schools.visitWindowsReport(here).contains("concluded"),
                    "the window is concluded, not re-revealed: " + schools.visitWindowsReport(here));
            helper.succeed();
        } finally {
            clearDerived(schools, data);
            schools.setCapsForTest(null);
        }
    }

    /**
     * E14: the pool a window is derived from is read at each reveal and nothing but depletion is kept. Removing a
     * species from the pool (standing in for a datapack or profile change) changes what the window derives, keeps
     * its caught fish caught, and a live visit of the removed species departs through the existing path.
     */
    public static void aProfileChangeChangesLaterDerivationsButKeepsDepletion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = pond(helper);
        ChunkPos chunk = new ChunkPos(center);
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        schools.setCapsForTest(NO_CAPS);
        Found found = findWindow(helper, schools, chunk, false, f -> true);
        long start = found.start();
        ResourceLocation original = grill24.fishtastic.util.Ids.parse(found.full().species());
        schools.setExcludedSpeciesForTest(Set.of());
        data.markCaught(found.id(), 0);
        long mask = data.caughtMask(found.id());
        try {
            FishSchoolSavedData.School before = schools.revealWindowForTest(found.id(), start + 5);
            if (found.full().indices().size() > 1) {
                helper.assertTrue(before != null && before.species().equals(original)
                                && before.members().stream().noneMatch(m -> m.index() == 0),
                        "before the change: the same species, the caught fish still caught");
                schools.forgetVisitForTest(before);
            }

            schools.setExcludedSpeciesForTest(Set.of(original));
            FishSchoolSavedData.School after = schools.revealWindowForTest(found.id(), start + 5);
            helper.assertTrue(after == null || !after.species().equals(original),
                    "after the change the window derives from the new pool, never the removed species");
            helper.assertTrue(data.caughtMask(found.id()) == mask, "depletion is untouched by the change");
            if (after != null) {
                helper.assertTrue(after.members().stream().noneMatch(m -> m.index() == 0), "the window's caught index stays caught");
                schools.forgetVisitForTest(after);
            }

            // A live visit of a species that stops being eligible leaves through the existing path.
            schools.setExcludedSpeciesForTest(Set.of());
            data.markCaught(found.id(), 63); // keep the ledger non-trivial; index 63 is never a member
            FishSchoolSavedData.School live = schools.revealWindowForTest(found.id(), start + 5);
            if (live == null) {
                helper.succeed(); // a single caught fish: nothing live to test
                return;
            }
            schools.setExcludedSpeciesForTest(Set.of(original));
            schools.tickVisitForTest(live, start + 10);
            helper.assertTrue(live.state() == FishSchoolSavedData.SchoolState.DEPARTING,
                    "a live visit whose species stopped being eligible must depart: " + live.state() + " " + live.species()
                            + " vs " + original + " " + schools.debugState(live.id()));
            helper.succeed();
        } finally {
            schools.setExcludedSpeciesForTest(Set.of());
            clearDerived(schools, data);
            schools.pruneLedgerForTest(Long.MAX_VALUE / 4);
            schools.setCapsForTest(null);
        }
    }

    /** A real rain-sensitive draw keeps its start-weather pool after clearing, dropping and save/reload. */
    public static void aWindowKeepsItsStartWeatherAfterClearingAndRereveal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = pond(helper);
        ChunkPos chunk = new ChunkPos(center);
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        List<VisitWeatherLog.Entry> previous = data.weatherEntries();
        schools.setCapsForTest(NO_CAPS);
        try {
            VisitId chosen = null;
            Shot rainy = null;
            long start = 0;
            for (long k = 1; k < 4000; k++) {
                VisitId id = new VisitId(dimension(level), chunk.x, chunk.z, k);
                long at = id.start(schools.ecologySeedForTest(), FishtasticServerConfig.visitWindowParams());
                data.replaceWeatherForTest(List.of(new VisitWeatherLog.Entry(at, VisitWeatherLog.State.RAIN)));
                FishSchoolSavedData.School wet = schools.revealWindowForTest(id, at + 5);
                if (wet == null) continue;
                Shot wetShot = Shot.of(wet);
                schools.forgetVisitForTest(wet);
                data.replaceWeatherForTest(List.of(new VisitWeatherLog.Entry(at, VisitWeatherLog.State.CLEAR)));
                FishSchoolSavedData.School dry = schools.revealWindowForTest(id, at + 5);
                boolean different = dry != null && !dry.species().toString().equals(wetShot.species());
                if (dry != null) schools.forgetVisitForTest(dry);
                if (different) {
                    chosen = id;
                    rainy = wetShot;
                    start = at;
                    break;
                }
            }
            helper.assertTrue(chosen != null, "the fixture must include a window whose actual species draw differs in rain and clear weather");
            data.replaceWeatherForTest(List.of(new VisitWeatherLog.Entry(start, VisitWeatherLog.State.RAIN),
                    new VisitWeatherLog.Entry(start + 1, VisitWeatherLog.State.CLEAR)));
            FishSchoolSavedData.School afterClear = schools.revealWindowForTest(chosen, start + 5);
            helper.assertTrue(afterClear != null && Shot.of(afterClear).equals(rainy)
                            && (afterClear.formation().weather() == FishProfile.WeatherCondition.RAIN
                            || afterClear.formation().weather() == FishProfile.WeatherCondition.SNOW),
                    "a reveal after clearing must use the rain pool from window start");
            schools.dropFarVisitsForTest(List.of());
            FishSchoolSavedData.School returned = schools.revealWindowForTest(chosen, start + 6);
            helper.assertTrue(returned != null && Shot.of(returned).equals(rainy),
                    "dropping and returning in the window keeps its rainy species and roster");
            schools.forgetVisitForTest(returned);
            var encoded = FishSchoolSavedData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
            FishSchoolSavedData restored = FishSchoolSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
            data.replaceWeatherForTest(restored.weatherEntries());
            FishSchoolSavedData.School reloaded = schools.revealWindowForTest(chosen, start + 7);
            helper.assertTrue(reloaded != null && Shot.of(reloaded).equals(rainy),
                    "save/reload retains the weather input needed to derive the same rainy visit");
            helper.succeed();
        } finally {
            data.replaceWeatherForTest(previous);
            clearDerived(schools, data);
            schools.clearAttemptsForTest();
            schools.setCapsForTest(null);
        }
    }

    public static void theWeatherLogSurvivesSaveReloadAndMarksMissingHistory(GameTestHelper helper) {
        FishSchoolSavedData data = new FishSchoolSavedData();
        data.recordWeather(100, VisitWeatherLog.State.RAIN);
        data.recordWeather(200, VisitWeatherLog.State.CLEAR);
        var encoded = FishSchoolSavedData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow();
        FishSchoolSavedData restored = FishSchoolSavedData.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow();
        helper.assertTrue(restored.weatherEntries().equals(data.weatherEntries())
                        && restored.weatherAt(150, VisitWeatherLog.State.CLEAR).state() == VisitWeatherLog.State.RAIN
                        && !restored.weatherAt(150, VisitWeatherLog.State.CLEAR).fallback(),
                "the saved weather entries retain the rain at the window start");
        helper.assertTrue(restored.weatherAt(99, VisitWeatherLog.State.THUNDER)
                        .equals(new VisitWeatherLog.Sample(VisitWeatherLog.State.THUNDER, true)),
                "a window before the recorded history explicitly falls back to current weather");
        FishSchoolManager schools = FishSchoolManager.get(helper.getLevel());
        helper.assertTrue(schools.weatherLogReport().contains("FALLBACK: no history"),
                "the debug report explains the missing-history fallback");
        helper.succeed();
    }

    public static void theWeatherTickPrunesOldEntriesButKeepsTheCutoffAnchor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        List<VisitWeatherLog.Entry> previous = data.weatherEntries();
        VisitWindows.Params params = FishtasticServerConfig.visitWindowParams();
        long retention = params.periodTicks() + VisitDerivation.revealSpanTicks(params.visitTicks(),
                params.periodTicks(), FishProfile.WorldEncounter.MAX_LIFETIME_SCALE);
        long now = level.getGameTime() + retention + 1000;
        long cutoff = now - retention;
        try {
            data.replaceWeatherForTest(List.of(new VisitWeatherLog.Entry(cutoff - 20, VisitWeatherLog.State.CLEAR),
                    new VisitWeatherLog.Entry(cutoff - 10, VisitWeatherLog.State.RAIN),
                    new VisitWeatherLog.Entry(cutoff + 1, VisitWeatherLog.State.THUNDER)));
            FishSchoolManager.get(level).captureWeatherForTest(now);
            helper.assertTrue(data.weatherEntries().get(0).equals(new VisitWeatherLog.Entry(cutoff - 10, VisitWeatherLog.State.RAIN)),
                    "the weather tick prunes obsolete entries and retains the newest one before the cutoff");
            helper.assertTrue(data.weatherAt(cutoff, VisitWeatherLog.State.CLEAR).state() == VisitWeatherLog.State.RAIN,
                    "a lookup at the retained span's beginning still has its true weather");
            helper.succeed();
        } finally {
            data.replaceWeatherForTest(previous);
        }
    }

    public static void weatherCommandsAreLoggedFromTheAuthoritativeFlags(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FishSchoolManager schools = FishSchoolManager.get(level);
        FishSchoolSavedData data = FishSchoolSavedData.get(level);
        List<VisitWeatherLog.Entry> previous = data.weatherEntries();
        var server = level.getServer();
        var source = server.createCommandSourceStack().withSuppressedOutput();
        String previousWeather = level.getLevelData().isThundering() ? "thunder"
                : level.getLevelData().isRaining() ? "rain" : "clear";
        try {
            data.replaceWeatherForTest(List.of());
            long now = level.getGameTime();
            int i = 0;
            for (VisitWeatherLog.State state : List.of(VisitWeatherLog.State.RAIN, VisitWeatherLog.State.THUNDER, VisitWeatherLog.State.CLEAR)) {
                String command = "weather " + state.getSerializedName() + " 1000";
                server.getCommands().getDispatcher().execute(command, source);
                schools.captureWeatherForTest(now + i++);
                VisitWeatherLog.Entry recorded = data.weatherEntries().get(data.weatherEntries().size() - 1);
                helper.assertTrue(recorded.state() == state && recorded.gameTime() == now + i - 1,
                        command + " is logged from its flags before the visual rain ramp catches up");
            }
            helper.assertTrue(data.weatherEntries().size() == 3, "each command's changed flags append one weather entry");
            helper.assertTrue(server.getCommands().getDispatcher().execute("fishtastic visits weatherlog", source) == 1,
                    "the in-game weather log debug command is registered");
            helper.succeed();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                server.getCommands().getDispatcher().execute("weather " + previousWeather + " 1000", source);
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new IllegalStateException(e);
            }
            data.replaceWeatherForTest(previous);
        }
    }

    public static void windowStartMoonPhaseMatchesTheEnvironmentAttribute(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        long clock = level.getDayTime();
        helper.assertTrue(VisitDerivation.moonPhaseIndex(clock) == FishMoonPhase.at(level, pos).index(),
                "the moon phase derived from the clock must equal the environment's at " + clock);
        helper.succeed();
    }
}
