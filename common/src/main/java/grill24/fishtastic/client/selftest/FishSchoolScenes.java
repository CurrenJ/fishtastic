package grill24.fishtastic.client.selftest;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.FishSchoolClientRuntime;
import grill24.fishtastic.server.FishSchoolManager;
import grill24.fishtastic.server.FishSchoolPerf;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

import static grill24.fishtastic.client.selftest.RenderSelfTest.camera;
import static grill24.fishtastic.client.selftest.RenderSelfTest.check;
import static grill24.fishtastic.client.selftest.RenderSelfTest.origin;
import static grill24.fishtastic.client.selftest.RenderSelfTest.queue;
import static grill24.fishtastic.client.selftest.RenderSelfTest.run;
import static grill24.fishtastic.client.selftest.RenderSelfTest.screenshot;
import static grill24.fishtastic.client.selftest.RenderSelfTest.server;

/**
 * Repeatable in-game frame sequences for judging the interactive-school presentation. The school
 * is a real one, spawned on the integrated server: what is captured is the whole path — the
 * server's engine, the setup and state packets, the client's lockstep copy and its draw.
 */
final class FishSchoolScenes {
    private static final int FRAME_COUNT = 24;

    private FishSchoolScenes() {}

    private static void checkTrackedCopy(String checkName) {
        String state = FishSchoolClientRuntime.debugState();
        check(checkName, !state.startsWith("No tracked fish schools."), state);
    }

    private static void checkServerSchool(Minecraft mc, String checkName) {
        server(mc, s -> {
            FishSchoolManager schools = FishSchoolManager.get(s.getLevel(mc.level.dimension()));
            String state = schools.debugNearest(mc.player.position());
            check(checkName, !state.startsWith("No fish schools in this dimension."), state);
        });
    }

    /**
     * An open-topped stone pool, water from {@code y - 1} to {@code y + 1} (surface at about
     * {@code y + 1.9}): enough uniform water behind the fish for the pale glint to be judged, while
     * retaining surface reflections in the discovery view.
     */
    private static void queuePool(int x, int y, int z) {
        queue(1, mc -> server(mc, s -> {
            run(s, "fill " + (x - 8) + " " + (y - 2) + " " + (z - 14) + " "
                    + (x + 8) + " " + (y + 3) + " " + (z + 3) + " minecraft:air");
            // The self-test player stands on the flat-world floor; keep forced Bluegill valid when
            // the normal habitat rescan runs in this low-altitude fixture.
            run(s, "fillbiome " + (x - 8) + " " + (y - 2) + " " + (z - 14) + " "
                    + (x + 8) + " " + (y + 3) + " " + (z + 3) + " minecraft:river");
            run(s, "fill " + (x - 7) + " " + (y - 2) + " " + (z - 13) + " "
                    + (x + 7) + " " + (y - 2) + " " + (z + 2) + " minecraft:stone");
            run(s, "fill " + (x - 7) + " " + (y - 1) + " " + (z - 13) + " "
                    + (x - 7) + " " + (y + 1) + " " + (z + 2) + " minecraft:stone");
            run(s, "fill " + (x + 7) + " " + (y - 1) + " " + (z - 13) + " "
                    + (x + 7) + " " + (y + 1) + " " + (z + 2) + " minecraft:stone");
            run(s, "fill " + (x - 6) + " " + (y - 1) + " " + (z - 12) + " "
                    + (x + 6) + " " + (y + 1) + " " + (z + 1) + " minecraft:water");
        }));
    }

    /** Three blocks of water, so a fish has to swim up to the surface to breach. */
    private static void queueJumpPool(int x, int y, int z) {
        queue(1, mc -> server(mc, s -> {
            run(s, "fill " + (x - 8) + " " + (y - 2) + " " + (z - 14) + " "
                    + (x + 8) + " " + (y + 3) + " " + (z + 3) + " minecraft:air");
            run(s, "fillbiome " + (x - 8) + " " + (y - 2) + " " + (z - 14) + " " + (x + 8) + " " + (y + 3) + " " + (z + 3) + " minecraft:river");
            run(s, "fill " + (x - 7) + " " + (y - 2) + " " + (z - 13) + " "
                    + (x + 7) + " " + (y - 2) + " " + (z + 2) + " minecraft:stone");
            run(s, "fill " + (x - 7) + " " + (y - 1) + " " + (z - 13) + " "
                    + (x - 7) + " " + (y + 1) + " " + (z + 2) + " minecraft:stone");
            run(s, "fill " + (x + 7) + " " + (y - 1) + " " + (z - 13) + " "
                    + (x + 7) + " " + (y + 1) + " " + (z + 2) + " minecraft:stone");
            run(s, "fill " + (x - 6) + " " + (y - 1) + " " + (z - 12) + " "
                    + (x + 6) + " " + (y + 1) + " " + (z + 1) + " minecraft:water");
        }));
    }

    static void queueFishSchoolGifScene() {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();

        queuePool(x, y, z);

        // quality=legendary (or any quality) gives every member that quality, quality=mixed one of
        // each in turn: the trails are drawn in their qualities' tints.
        String quality = RenderSelfTest.param("quality", "").trim();
        grill24.fishtastic.component.FishQuality.Quality[] qualities = quality.isEmpty() ? null
                : quality.equals("mixed") ? grill24.fishtastic.component.FishQuality.Quality.values()
                : new grill24.fishtastic.component.FishQuality.Quality[]{
                        grill24.fishtastic.component.FishQuality.Quality.valueOf(quality.toUpperCase(java.util.Locale.ROOT))};
        queue(5, mc -> server(mc, s -> {
            String result = FishSchoolManager.get(s.getLevel(mc.level.dimension()))
                    .spawnForDebug(new BlockPos(x, y, z - 5), Fishtastic.id("bluegill"), qualities);
            check("fishschoolgif.spawn", result.startsWith("Spawned"), result);
        }));
        // Long enough for the next sync to send the setup and the copy to catch up.
        queue(40, mc -> {
            mc.options.hideGui = true;
            check("fishschoolgif.copy", !FishSchoolClientRuntime.debugState().startsWith("No tracked"),
                    FishSchoolClientRuntime.debugState());
        });

        queue(1, mc -> camera(mc, x, y + 0.35, z + 0.2, 180f, 1.5f));
        queue(35, mc -> {});
        captureSequence("broadside");
        queue(1, mc -> {
            checkTrackedCopy("fishschoolgif.broadside.copy");
            checkServerSchool(mc, "fishschoolgif.broadside.server");
        });

        queue(1, mc -> camera(mc, x, y + 0.35, z - 10.7, 0f, 1.5f));
        queue(25, mc -> {});
        captureSequence("reverse");
        queue(1, mc -> {
            checkTrackedCopy("fishschoolgif.reverse.copy");
            checkServerSchool(mc, "fishschoolgif.reverse.server");
        });

        queue(1, mc -> camera(mc, x + 4.1, y + 2.8, z - 0.2, 141f, 22f));
        queue(25, mc -> {});
        captureSequence("surface");
        queue(1, mc -> {
            checkTrackedCopy("fishschoolgif.surface.copy");
            checkServerSchool(mc, "fishschoolgif.surface.server");
        });

        captureCloseUp();

        queue(1, mc -> {
            // The copy must have matched every server fingerprint it reached.
            String state = FishSchoolClientRuntime.debugState();
            check("fishschoolgif.lockstep", !state.startsWith("No tracked fish schools.")
                            && !state.matches("(?s).*mismatches=[1-9].*"), state);
            mc.options.hideGui = false;
            check("fishschoolgif.fixture", true,
                    "captured " + (FRAME_COUNT * 3) + " real renderer frames from three views");
        });
    }

    /**
     * A server-sent surface goal: the fish swims up to its launch point and breaches, photographed
     * from arrival through apex and landing.
     */
    static void queueFishSchoolJumpScene() {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        java.util.concurrent.atomic.AtomicReference<java.util.UUID> schoolId =
                new java.util.concurrent.atomic.AtomicReference<>();

        queueJumpPool(x, y, z);
        queue(5, mc -> server(mc, s -> {
            String result = FishSchoolManager.get(s.getLevel(mc.level.dimension()))
                    .spawnForDebug(new BlockPos(x, y, z - 5), Fishtastic.id("bluegill"));
            check("fishschooljump.spawn", result.startsWith("Spawned"), result);
            if (result.startsWith("Spawned")) schoolId.set(spawnedId(result));
        }));
        queue(40, mc -> {
            mc.options.hideGui = true;
            check("fishschooljump.copy", !FishSchoolClientRuntime.debugState().startsWith("No tracked"),
                    FishSchoolClientRuntime.debugState());
        });
        queue(1, mc -> {
            Vec3 fish = FishSchoolClientRuntime.memberPositionForSelfTest(0);
            check("fishschooljump.camera_target", fish != null, String.valueOf(fish));
            // A block above the pool's surface (water fills y-1..y+1), looking across it at the fish.
            if (fish != null) camera(mc, fish.x, y + 2.9, fish.z + 5.5, 180f, 12f);
        });
        queue(1, mc -> server(mc, s -> {
            java.util.UUID id = schoolId.get();
            String result = id == null ? "School spawn did not produce an id."
                    : FishSchoolManager.get(s.getLevel(mc.level.dimension())).surfaceJumpForDebug(id);
            check("fishschooljump.start", result.startsWith("Sent"), result);
            FishSchoolClientRuntime.resetSelfTestCounters();
        }));
        // The fish has to swim up first; the goal is called off after 400 ticks.
        CompanionScenes.queueUntil("fishschooljump.visible", 450,
                mc -> FishSchoolClientRuntime.surfaceJumpActiveForSelfTest(), FishSchoolClientRuntime::debugState);
        // The peak is tracked over every frame: the glide's offset is negative, so a single
        // mid-jump sample can land after the flight and read nothing.
        float[] peak = {0f};
        // Side-on to the leap, level with the surface, centred on the flight: the lean reads here.
        queue(1, mc -> {
            double[] view = FishSchoolClientRuntime.surfaceJumpViewForSelfTest();
            if (view == null) return;
            double midX = view[0] + view[3] * 1.8, midZ = view[2] + view[4] * 1.8;
            double sideX = -view[4], sideZ = view[3];
            double camX = midX + sideX * 3.5, camZ = midZ + sideZ * 3.5;
            float yaw = (float) Math.toDegrees(Math.atan2(-(midX - camX), midZ - camZ));
            camera(mc, camX, y + 2.4, camZ, yaw, 5f);
        });
        for (int frame = 0; frame < 24; frame++) {
            int shot = frame;
            queue(2, mc -> {
                peak[0] = Math.max(peak[0], FishSchoolClientRuntime.maxSurfaceJumpOffsetForSelfTest(1f));
                screenshot(mc, "fishschooljump", "arc-" + String.format(java.util.Locale.ROOT, "%02d", shot));
            });
        }
        queue(1, mc -> check("fishschooljump.apex", peak[0] > 0.3f, "peak rendered offset=" + peak[0]));
        // Reaching the launch point is an engine touch, like a bobber's, but must not look like a bite.
        queue(1, mc -> check("fishschooljump.no_bite_burst", FishSchoolClientRuntime.biteBurstsForSelfTest() == 0,
                "bite bursts during the jump=" + FishSchoolClientRuntime.biteBurstsForSelfTest()));
        queue(2, mc -> {
            check("fishschooljump.landed", !FishSchoolClientRuntime.surfaceJumpActiveForSelfTest(),
                    FishSchoolClientRuntime.debugState());
            check("fishschooljump.lockstep", !FishSchoolClientRuntime.debugState().matches("(?s).*mismatches=[1-9].*"),
                    FishSchoolClientRuntime.debugState());
            mc.options.hideGui = false;
        });
    }

    /** The same real server-goal fixture with the Legendary presentation profile forced deterministically. */
    static void queueLegendaryBreachScene() {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        java.util.concurrent.atomic.AtomicReference<java.util.UUID> schoolId =
                new java.util.concurrent.atomic.AtomicReference<>();

        queueJumpPool(x, y, z);
        queue(5, mc -> server(mc, s -> {
            String result = FishSchoolManager.get(s.getLevel(mc.level.dimension()))
                    .spawnForDebug(new BlockPos(x, y, z - 5), Fishtastic.id("bluegill"),
                            new grill24.fishtastic.component.FishQuality.Quality[]{
                                    grill24.fishtastic.component.FishQuality.Quality.LEGENDARY});
            check("legendarybreach.spawn", result.startsWith("Spawned"), result);
            if (result.startsWith("Spawned")) schoolId.set(spawnedId(result));
        }));
        queue(40, mc -> {
            mc.options.hideGui = true;
            check("legendarybreach.copy", !FishSchoolClientRuntime.debugState().startsWith("No tracked"),
                    FishSchoolClientRuntime.debugState());
        });
        // Let the first-join recipe toast expire before capturing the deliberately visible HUD layers.
        queue(110, mc -> {});
        queue(1, mc -> {
            Vec3 fish = FishSchoolClientRuntime.memberPositionForSelfTest(0);
            check("legendarybreach.camera_target", fish != null, String.valueOf(fish));
            if (fish != null) camera(mc, fish.x, y + 2.9, fish.z + 5.5, 180f, 12f);
        });
        queue(1, mc -> server(mc, s -> {
            java.util.UUID id = schoolId.get();
            String result = id == null ? "School spawn did not produce an id."
                    : FishSchoolManager.get(s.getLevel(mc.level.dimension()))
                    .surfaceJumpForDebug(id);
            check("legendarybreach.start", result.startsWith("Sent"), result);
            FishSchoolClientRuntime.resetSelfTestCounters();
        }));
        CompanionScenes.queueUntil("legendarybreach.visible", 450,
                mc -> FishSchoolClientRuntime.surfaceJumpActiveForSelfTest(), FishSchoolClientRuntime::debugState);
        queue(1, mc -> mc.options.hideGui = false);
        float[] peak = {0f};
        int[] particlePeak = {0};
        boolean[] presentationSeen = {false};
        queue(1, mc -> {
            double[] view = FishSchoolClientRuntime.surfaceJumpViewForSelfTest();
            if (view == null) return;
            double midX = view[0] + view[3] * 1.8, midZ = view[2] + view[4] * 1.8;
            double sideX = -view[4], sideZ = view[3];
            double camX = midX + sideX * 3.5, camZ = midZ + sideZ * 3.5;
            float yaw = (float) Math.toDegrees(Math.atan2(-(midX - camX), midZ - camZ));
            camera(mc, camX, y + 2.4, camZ, yaw, 5f);
        });
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            int shot = frame;
            queue(2, mc -> {
                peak[0] = Math.max(peak[0], FishSchoolClientRuntime.maxSurfaceJumpOffsetForSelfTest(1f));
                particlePeak[0] = Math.max(particlePeak[0],
                        FishSchoolClientRuntime.legendaryBreachParticleCountForSelfTest());
                presentationSeen[0] |= FishSchoolClientRuntime.legendaryBreachActiveForSelfTest();
                screenshot(mc, "legendarybreach", "arc-" + String.format(java.util.Locale.ROOT, "%02d", shot));
            });
        }
        queue(1, mc -> {
            check("legendarybreach.apex", peak[0] > 0.3f, "peak rendered offset=" + peak[0]);
            check("legendarybreach.presentation", presentationSeen[0],
                    "live cinematic controller appeared in at least one capture=" + presentationSeen[0]);
            check("legendarybreach.particles", particlePeak[0] > 0,
                    "peak custom particles=" + particlePeak[0]);
        });
        queue(2, mc -> {
            check("legendarybreach.lockstep", !FishSchoolClientRuntime.debugState().matches("(?s).*mismatches=[1-9].*"),
                    FishSchoolClientRuntime.debugState());
            mc.options.hideGui = false;
        });
    }

    // ------------------------------------------------------------------ shapes

    /**
     * One visit of each shape in one pool (docs/in-world-fish-encounters-design-spec.md §3): a
     * lone pike, a bluegill pair, a loose group of clown loaches and a bluegill school. Each copy
     * must stay in lockstep with the server under its shape's flocking parameters; the frames are
     * for judging whether the shapes read differently in the water.
     */
    static void queueFishSchoolShapesScene() {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();

        queuePool(x, y, z);
        record Visit(String species, @org.jetbrains.annotations.Nullable grill24.fishtastic.school.VisitShape shape,
                     String expect, int dx, int dz) {}
        List<Visit> visits = List.of(
                new Visit("northern_pike", null, "solitary", -3, -9),
                new Visit("bluegill", grill24.fishtastic.school.VisitShape.PAIR, "pair", 3, -9),
                new Visit("clown_loach", null, "loose_group", -3, -3),
                new Visit("bluegill", null, "school", 3, -3));
        queue(5, mc -> server(mc, s -> {
            FishSchoolManager schools = FishSchoolManager.get(s.getLevel(mc.level.dimension()));
            for (Visit visit : visits) {
                String result = schools.spawnForDebug(new BlockPos(x + visit.dx(), y, z + visit.dz()),
                        Fishtastic.id(visit.species()), null, visit.shape());
                check("fishschoolshapes.spawn." + visit.expect(),
                        result.startsWith("Spawned") && result.contains(" as " + visit.expect() + " "), result);
            }
        }));
        queue(40, mc -> {
            mc.options.hideGui = true;
            check("fishschoolshapes.copies", FishSchoolClientRuntime.debugState().split("\n").length >= visits.size(),
                    FishSchoolClientRuntime.debugState());
        });

        queue(1, mc -> camera(mc, x, y + 6.5, z - 5.5, 0f, 89f));
        queue(60, mc -> {});
        for (int i = 0; i < 6; i++) {
            String shot = String.format(java.util.Locale.ROOT, "overhead-%02d", i);
            queue(20, mc -> screenshot(mc, "fishschoolshapes", shot));
        }
        queue(1, mc -> camera(mc, x + 4.1, y + 2.8, z - 0.2, 141f, 22f));
        queue(30, mc -> screenshot(mc, "fishschoolshapes", "surface"));

        queue(1, mc -> {
            // Every copy must have matched every server fingerprint it reached, whatever its shape.
            check("fishschoolshapes.lockstep", !FishSchoolClientRuntime.debugState().matches("(?s).*mismatches=[1-9].*"),
                    FishSchoolClientRuntime.debugState());
            mc.options.hideGui = false;
        });
    }

    /** The visit id at the end of a {@code spawnForDebug} result, "... (school <uuid>)." */
    private static java.util.UUID spawnedId(String result) {
        return java.util.UUID.fromString(result.substring(result.lastIndexOf("(school ") + 8, result.length() - 2));
    }

    // ------------------------------------------------------------------ classes

    /**
     * The non-swimming classes in one pool (encounter spec §4, §6): a garden eel colony, a
     * Portuguese man o' war group and a Japanese spider crab. Checks the arrival ripples showed, the
     * motionless eels still sparkle, and every copy holds lockstep; frames for judging the look.
     */
    static void queueFishSchoolClassesScene() {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();

        queuePool(x, y, z);
        record Visit(String species, int dx, int dz) {}
        List<Visit> visits = List.of(new Visit("garden_eel", -3, -9), new Visit("portuguese_man_o_war", 3, -9),
                new Visit("japanese_spider_crab", 0, -3));
        queue(5, mc -> {
            FishSchoolClientRuntime.resetSelfTestCounters();
            server(mc, s -> {
                FishSchoolManager schools = FishSchoolManager.get(s.getLevel(mc.level.dimension()));
                for (Visit visit : visits) {
                    String result = schools.spawnForDebug(new BlockPos(x + visit.dx(), y, z + visit.dz()),
                            Fishtastic.id(visit.species()));
                    check("fishschoolclasses.spawn." + visit.species(), result.startsWith("Spawned"), result);
                    if (result.startsWith("Spawned")) schools.ignoreHabitatForTest(spawnedId(result));
                }
            });
        });
        // Overhead, early enough to catch the arrival rings spreading on the surface.
        queue(1, mc -> {
            mc.options.hideGui = true;
            camera(mc, x, y + 6.5, z - 5.5, 0f, 89f);
        });
        for (int i = 0; i < 4; i++) {
            String shot = String.format(java.util.Locale.ROOT, "arrival-%02d", i);
            queue(5, mc -> screenshot(mc, "fishschoolclasses", shot));
        }
        queue(20, mc -> check("fishschoolclasses.arrivals", FishSchoolClientRuntime.arrivalsForSelfTest() >= visits.size(),
                FishSchoolClientRuntime.arrivalsForSelfTest() + " arrivals shown for " + visits.size() + " visits"));
        queue(120, mc -> {});
        queue(1, mc -> check("fishschoolclasses.stillglints", FishSchoolClientRuntime.stillGlintsForSelfTest() > 0,
                FishSchoolClientRuntime.stillGlintsForSelfTest() + " stationary glints"));
        queue(1, mc -> camera(mc, x - 3.5, y + 0.9, z - 5.2, 180f, 35f));
        queue(30, mc -> screenshot(mc, "fishschoolclasses", "eels"));
        queue(1, mc -> camera(mc, x + 3.5, y + 0.9, z - 5.2, 180f, 10f));
        queue(30, mc -> screenshot(mc, "fishschoolclasses", "drifters"));
        queue(1, mc -> camera(mc, x, y + 0.9, z + 0.3, 180f, 35f));
        queue(30, mc -> screenshot(mc, "fishschoolclasses", "crab"));
        queue(1, mc -> {
            check("fishschoolclasses.copies", FishSchoolClientRuntime.debugState().split("\n").length >= visits.size(),
                    FishSchoolClientRuntime.debugState());
            check("fishschoolclasses.lockstep", !FishSchoolClientRuntime.debugState().matches("(?s).*mismatches=[1-9].*"),
                    FishSchoolClientRuntime.debugState());
            mc.options.hideGui = false;
        });
    }

    // ------------------------------------------------------------------ bite

    /** Frames kept after the bite, one every {@link #CLOSE_UP_TICKS} ticks. */
    private static final int AFTER_BITE_FRAMES = 40;
    private static final int BITE_TIMEOUT_TICKS = 1200;
    /** The bite scene's progress, advanced by its tick probe. */
    private static final java.util.concurrent.atomic.AtomicReference<String> SERVER_SCHOOL =
            new java.util.concurrent.atomic.AtomicReference<>("");
    private static int biteFrame, framesTaken, afterBite, probeTicks;
    private static boolean filming;

    /**
     * A real cast at a real school: the player holds a Fishtastic rod, a bobber floats in the pool
     * beside the school, and a camera under the surface beside the bobber films from the moment a
     * fish is sent until {@link #AFTER_BITE_FRAMES} frames after it bites — the approach, the touch
     * and the bite burst, all from the server's engine and the client's copy.
     */
    static void queueFishSchoolBiteScene() {
        queueBiteScene("fishschoolbite", "bluegill", false);
    }

    /**
     * The bottom bite (encounter spec §4.4): a Japanese spider crab walks the pool's floor to
     * beneath a real bobber, which dips. Filmed by the same probe as {@code fishschoolbite}.
     */
    static void queueFishSchoolCrabScene() {
        queueBiteScene("fishschoolcrab", "japanese_spider_crab", true);
    }

    /** The scene {@link #filmBite} is filming for: its checks and frames are named after it. */
    private static String biteScene = "fishschoolbite";

    /**
     * @param ocean the species lives in the ocean, so its visit is judged without habitat: the
     *              self-test world is plains, and the approach is what is being filmed
     */
    private static void queueBiteScene(String scene, String species, boolean ocean) {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        double hookX = x + 0.5, hookZ = z - 4.5;

        queuePool(x, y, z);
        queue(5, mc -> server(mc, s -> {
            FishSchoolManager schools = FishSchoolManager.get(s.getLevel(mc.level.dimension()));
            // Every approach's start, bite and failure (with why) in the log, for reading a failed run.
            FishSchoolManager.logApproaches = true;
            String result = schools.spawnForDebug(new BlockPos(x, y, z - 6), Fishtastic.id(species));
            check(scene + ".spawn", result.startsWith("Spawned"), result);
            if (ocean && result.startsWith("Spawned")) schools.ignoreHabitatForTest(spawnedId(result));
        }));
        queue(40, mc -> {
            mc.options.hideGui = true;
            // view=above: where a fishing player stands, eyes ~2 blocks over the water, looking down
            // at the bobber. Default: just under the surface, beside the hook (tilted down to the
            // floor for a crawler, whose bite happens there).
            if ("above".equals(RenderSelfTest.param("view", "under"))) {
                camera(mc, hookX, y + 3.9, hookZ + 3.2, 180f, 32f);
            } else {
                camera(mc, hookX, y + 1.35, hookZ + 2.4, 180f, ocean ? 28f : -6f);
            }
        });
        queue(10, mc -> server(mc, s -> {
            net.minecraft.server.level.ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            // The camera helper leaves the player in spectator, and a spectator cannot hold a line:
            // FishingHook.shouldStopFishing removes its bobber at once. Cast as a creative player
            // hovering where the camera put it instead.
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new net.minecraft.world.item.ItemStack(grill24.fishtastic.FishtasticItems.COPPER_FISHING_ROD.value()));
            net.minecraft.world.entity.projectile.FishingHook hook =
                    new net.minecraft.world.entity.projectile.FishingHook(player, player.level(), 0, 0);
            hook.setPos(hookX, y + 1.6, hookZ);
            hook.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            player.level().addFreshEntity(hook);
            player.fishing = hook;
        }));
        queue(1, mc -> {
            biteScene = scene;
            biteFrame = -1;
            framesTaken = 0;
            afterBite = 0;
            probeTicks = 0;
            filming = false;
            SERVER_SCHOOL.set("");
            RenderSelfTest.tickProbe = FishSchoolScenes::filmBite;
        });
        queue(BITE_TIMEOUT_TICKS, mc -> {});
        queue(1, mc -> {
            RenderSelfTest.tickProbe = null;
            check(scene + ".approach", framesTaken > 0, "frames filmed: " + framesTaken);
            check(scene + ".bite", biteFrame >= 0, "bite at frame " + biteFrame + "; " + SERVER_SCHOOL.get());
            mc.options.hideGui = false;
        });
    }

    private static void filmBite(net.minecraft.client.Minecraft mc) {
        probeTicks++;
        // The server's view of the school, read on its own thread and picked up a tick later.
        server(mc, s -> {
            FishSchoolManager schools = FishSchoolManager.get(s.getLevel(mc.level.dimension()));
            SERVER_SCHOOL.set(schools.debugNearest(mc.player.position()) + " | "
                    + schools.hookReport(s.getPlayerList().getPlayers().get(0)));
        });
        String school = SERVER_SCHOOL.get();
        if (!filming && school.contains("APPROACHING")) filming = true;
        if (!filming || afterBite >= AFTER_BITE_FRAMES || probeTicks % CLOSE_UP_TICKS != 0) return;
        if (biteFrame < 0 && school.contains("BITE_READY")) biteFrame = framesTaken;
        screenshot(mc, biteScene, String.format(java.util.Locale.ROOT, "bite-%03d", framesTaken++));
        if (biteFrame >= 0) afterBite++;
    }

    /** Frames in the close-up, one every {@link #CLOSE_UP_TICKS} ticks. */
    private static final int CLOSE_UP_FRAMES = 60;
    private static final int CLOSE_UP_TICKS = 2;

    /**
     * A camera following one school fish from {@code 1.8} blocks south of it, so its sparkle trail
     * is large enough to judge. A fixed offset rather than one turned with the fish: the camera
     * never swings when the fish turns, and the trail shows whichever way it swims across. Each
     * frame photographs the view the previous frame set up, then moves the camera on.
     */
    private static void captureCloseUp() {
        queue(1, mc -> {
            FishSchoolClientRuntime.debugTrailMarkers = Boolean.parseBoolean(RenderSelfTest.param("markers", "false"));
            // tint=FFC850 FF78B4 draws the trail in those tints instead of the default cyans.
            String tint = RenderSelfTest.param("tint", "").trim();
            FishSchoolClientRuntime.setMoteTintOverride(tint.isEmpty() ? null
                    : java.util.Arrays.stream(tint.split("[\\s,]+")).mapToInt(h -> Integer.parseInt(h, 16)).toArray());
            checkTrackedCopy("fishschoolgif.closeup.begin");
            checkServerSchool(mc, "fishschoolgif.closeup.server");
            followFish(mc);
        });
        queue(10, mc -> {});
        for (int frame = 0; frame < CLOSE_UP_FRAMES; frame++) {
            String shot = "closeup-" + String.format(java.util.Locale.ROOT, "%02d", frame);
            queue(CLOSE_UP_TICKS, mc -> {
                screenshot(mc, "fishschoolgif", shot);
                followFish(mc);
            });
        }
        queue(1, mc -> {
            FishSchoolClientRuntime.debugTrailMarkers = false;
            FishSchoolClientRuntime.setMoteTintOverride(null);
            String state = FishSchoolClientRuntime.debugState();
            check("fishschoolgif.closeup", FishSchoolClientRuntime.memberPositionForSelfTest(0) != null,
                    "followed a school fish for " + CLOSE_UP_FRAMES + " frames; " + state);
        });
    }

    private static void followFish(net.minecraft.client.Minecraft mc) {
        net.minecraft.world.phys.Vec3 fish = FishSchoolClientRuntime.memberPositionForSelfTest(0);
        if (fish == null) return;
        camera(mc, fish.x, fish.y + 0.25, fish.z + 1.8, 180f, 8f);
    }

    private static void captureSequence(String view) {
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            String shot = view + "-" + String.format(java.util.Locale.ROOT, "%02d", frame);
            queue(3, mc -> screenshot(mc, "fishschoolgif", shot));
        }
    }

    // ------------------------------------------------------------------ reach

    /** Bobber trials in the reach scene, and how long each waits for a bite (the owner's 30 s). */
    private static final int REACH_TRIALS = 8, REACH_WAIT_TICKS = 600, REACH_REST_TICKS = 130;
    private static final java.util.concurrent.atomic.AtomicReference<String> REACH_STATUS =
            new java.util.concurrent.atomic.AtomicReference<>("");
    private static final List<String> REACH_RESULTS = new java.util.ArrayList<>();
    private static int reachTrial, reachWait;
    private static String reachPhase = "cast";

    /**
     * The owner's case, measured: a large, deep pool (25x25, 6 deep: bigger than a school's water
     * cap, like a natural lake), a school in the middle, and a bobber floating 1-3 blocks from a
     * free fish. First a sweep: for every free fish, 8 directions at 1, 2 and 3 blocks, is the
     * bobber's water cell in the school's water at all? Then {@link #REACH_TRIALS} real waits of up
     * to 30 s each with a still bobber 2 blocks from a fish; the server logs every approach.
     */
    static void queueFishSchoolReachScene() {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        Vec3 centre = new Vec3(x + 0.5, y, z + 0.5);
        queue(1, mc -> server(mc, s -> {
            run(s, "fill " + (x - 14) + " " + (y - 6) + " " + (z - 14) + " " + (x + 14) + " " + (y + 6) + " " + (z + 14)
                    + " minecraft:air");
            run(s, "fillbiome " + (x - 14) + " " + (y - 6) + " " + (z - 14) + " " + (x + 14) + " " + (y + 6) + " " + (z + 14)
                    + " minecraft:river");
            run(s, "fill " + (x - 13) + " " + (y - 5) + " " + (z - 13) + " " + (x + 13) + " " + (y + 1) + " " + (z + 13)
                    + " minecraft:stone");
            run(s, "fill " + (x - 12) + " " + (y - 4) + " " + (z - 12) + " " + (x + 12) + " " + (y + 1) + " " + (z + 12)
                    + " minecraft:water");
        }));
        queue(5, mc -> server(mc, s -> {
            FishSchoolManager.logApproaches = true;
            String result = FishSchoolManager.get(s.getLevel(mc.level.dimension()))
                    .spawnForDebug(new BlockPos(x, y, z), Fishtastic.id("bluegill"));
            check("fishschoolreach.spawn", result.startsWith("Spawned"), result);
        }));
        queue(20, mc -> camera(mc, x + 0.5, y + 9, z + 16, 180f, 35f));
        queue(10, mc -> server(mc, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(grill24.fishtastic.FishtasticItems.COPPER_FISHING_ROD.value()));
        }));
        // Let the school spread out before the sweep.
        queue(200, mc -> server(mc, s -> {
            ServerLevel level = s.getLevel(mc.level.dimension());
            FishSchoolManager schools = FishSchoolManager.get(level);
            List<Vec3> fish = schools.availableMembersForTest(centre);
            int[] water = new int[4], outside = new int[4];
            for (Vec3 f : fish) {
                for (int d = 1; d <= 3; d++) {
                    for (int k = 0; k < 8; k++) {
                        double angle = k * Math.PI / 4;
                        BlockPos cell = BlockPos.containing(f.x + Math.cos(angle) * d, y + 1.35, f.z + Math.sin(angle) * d);
                        if (!level.getFluidState(cell).isSource()) continue;
                        water[d]++;
                        if (!schools.inSchoolWaterForTest(centre, cell)) outside[d]++;
                    }
                }
            }
            check("fishschoolreach.sweep", fish.size() > 0, String.format(java.util.Locale.ROOT,
                    "%d free fish; bobber cells outside the school's water: 1 block %d/%d, 2 blocks %d/%d, 3 blocks %d/%d; fish at %s",
                    fish.size(), outside[1], water[1], outside[2], water[2], outside[3], water[3], fish));
        }));
        queue(1, mc -> {
            REACH_RESULTS.clear();
            reachTrial = 0;
            reachWait = 0;
            reachPhase = "cast";
            REACH_STATUS.set("");
            RenderSelfTest.tickProbe = mc2 -> reachProbe(mc2, centre, y);
        });
        queue(REACH_TRIALS * (REACH_WAIT_TICKS + REACH_REST_TICKS + 10), mc -> {});
        queue(1, mc -> {
            RenderSelfTest.tickProbe = null;
            server(mc, s -> {
                FishSchoolManager.logApproaches = false;
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                if (player.fishing != null) player.fishing.discard();
            });
            long bites = REACH_RESULTS.stream().filter(r -> r.contains("BITE")).count();
            check("fishschoolreach.bites", bites == REACH_RESULTS.size() && !REACH_RESULTS.isEmpty(),
                    bites + "/" + REACH_RESULTS.size() + " trials bit within 30 s: " + REACH_RESULTS);
        });
    }

    private static void reachProbe(net.minecraft.client.Minecraft mc, Vec3 centre, int y) {
        if (reachTrial >= REACH_TRIALS) return;
        reachWait++;
        switch (reachPhase) {
            case "cast" -> {
                int trial = reachTrial;
                REACH_STATUS.set("");
                server(mc, s -> {
                    ServerLevel level = s.getLevel(mc.level.dimension());
                    FishSchoolManager schools = FishSchoolManager.get(level);
                    List<Vec3> fish = schools.availableMembersForTest(centre);
                    if (fish.isEmpty()) {
                        REACH_STATUS.set("CAST no free fish");
                        return;
                    }
                    Vec3 f = fish.get(trial % fish.size());
                    for (int k = 0; k < 8; k++) {
                        double angle = (trial * 3 + k) * Math.PI / 4;
                        double hx = f.x + Math.cos(angle) * 2, hz = f.z + Math.sin(angle) * 2;
                        BlockPos cell = BlockPos.containing(hx, y + 1.35, hz);
                        if (!level.getFluidState(cell).isSource()) continue;
                        ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                        if (player.fishing != null) player.fishing.discard();
                        FishingHook hook = new FishingHook(player, level, 0, 0);
                        hook.setPos(hx, y + 1.85, hz);
                        hook.setDeltaMovement(Vec3.ZERO);
                        level.addFreshEntity(hook);
                        player.fishing = hook;
                        REACH_STATUS.set(String.format(java.util.Locale.ROOT, "CAST inSchoolWater=%s",
                                schools.inSchoolWaterForTest(centre, cell)));
                        return;
                    }
                    REACH_STATUS.set("CAST no water 2 blocks from the fish");
                });
                reachPhase = "wait";
                reachWait = 0;
            }
            case "wait" -> {
                String cast = REACH_STATUS.get();
                if (cast.startsWith("CAST no")) {
                    REACH_RESULTS.add("#" + reachTrial + " " + cast);
                    reachPhase = "rest";
                    reachWait = 0;
                    return;
                }
                if (reachWait % 5 == 0) {
                    server(mc, s -> {
                        FishSchoolManager schools = FishSchoolManager.get(s.getLevel(mc.level.dimension()));
                        String now = schools.debugNearest(centre) + " | "
                                + schools.hookReport(s.getPlayerList().getPlayers().get(0));
                        String head = REACH_STATUS.get().replaceAll(" \\| last:.*", "");
                        REACH_STATUS.set(now.contains("BITE_READY") ? head + " BITE" : head + " | last: " + now);
                    });
                }
                String status = REACH_STATUS.get();
                boolean bit = status.contains(" BITE");
                if (bit || reachWait >= REACH_WAIT_TICKS) {
                    REACH_RESULTS.add("#" + reachTrial + " " + (bit ? status + " at " + reachWait : "TIMEOUT " + status));
                    server(mc, s -> {
                        ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                        if (player.fishing != null) player.fishing.discard();
                        player.fishing = null;
                    });
                    reachPhase = "rest";
                    reachWait = 0;
                }
            }
            default -> {
                if (reachWait >= REACH_REST_TICKS) {
                    reachTrial++;
                    reachPhase = "cast";
                    reachWait = 0;
                }
            }
        }
    }

    // ------------------------------------------------------------------ perf

    /** Pool centres relative to the origin: six schools, the per-dimension maximum. */
    private static final int[][] PERF_POOLS = {{-34, 0}, {0, 0}, {34, 0}, {-34, -34}, {0, -34}, {34, -34}};
    /** Water reaches this far from a pool's centre (29x29) and this far below/above its middle (11 deep). */
    private static final int PERF_HALF = 14, PERF_DOWN = 5, PERF_UP = 5;
    private static final java.util.concurrent.atomic.AtomicInteger PERF_BITES = new java.util.concurrent.atomic.AtomicInteger();
    /** Server thread only: whether the fished school had a bite-ready member at the last poll. */
    private static boolean perfWasReady;
    private static int perfProbeTicks;
    /** Server thread only: what the bobber's hook check said at each poll, numbers blanked, and how often. */
    private static final java.util.Map<String, Integer> PERF_HOOK_REASONS = new java.util.TreeMap<>();

    /**
     * Performance plan step 0 (docs/interactive-fish-schools-performance-plan.md): six schools in
     * six large, deep pools, a player hovering between them, and one measured phase after another,
     * each after a counter reset. Idle, then fishing beside a fish, then fishing across a wall from
     * one (the longest path searches), then a late join, then the replay bench, then a freezing
     * pool. Every number goes to the log as a NOTE; the checks only guard that the fixture worked.
     */
    static void queueFishSchoolPerfScene() {
        int x = origin.getX();
        int z = origin.getZ();
        // The flat world's floor is y=-60, and fills below -64 silently fail: build well above it.
        int y0 = origin.getY() + 12;
        int surface = y0 + PERF_UP;
        int perfTicks = Integer.parseInt(RenderSelfTest.param("perfTicks", "600"));
        int wallX = x + 1;
        Vec3 fishedPool = new Vec3(x + 0.5, y0, z + 0.5);
        Vec3 wallPool = new Vec3(x + 0.5, y0, z - 34 + 0.5);
        Vec3 frozenPool = new Vec3(x + 34 + 0.5, y0, z + 0.5);

        for (int[] pool : PERF_POOLS) {
            int cx = x + pool[0], cz = z + pool[1];
            int h = PERF_HALF;
            queue(1, mc -> server(mc, s -> {
                run(s, "fill " + (cx - h - 1) + " " + (y0 - PERF_DOWN - 1) + " " + (cz - h - 1) + " "
                        + (cx + h + 1) + " " + (surface + 6) + " " + (cz + h + 1) + " minecraft:air");
                run(s, "fillbiome " + (cx - h - 1) + " " + (y0 - PERF_DOWN - 1) + " " + (cz - h - 1) + " "
                        + (cx + h + 1) + " " + (surface + 6) + " " + (cz + h + 1) + " minecraft:river");
                run(s, "fill " + (cx - h - 1) + " " + (y0 - PERF_DOWN - 1) + " " + (cz - h - 1) + " "
                        + (cx + h + 1) + " " + surface + " " + (cz + h + 1) + " minecraft:stone");
                run(s, "fill " + (cx - h) + " " + (y0 - PERF_DOWN) + " " + (cz - h) + " "
                        + (cx + h) + " " + surface + " " + (cz + h) + " minecraft:water");
            }));
        }
        // Finding 4's worst case: a wall across one pool, open only along the bottom, so a bobber
        // just over it from a fish is in the school's water but ~15 cells of path away. Built now
        // rather than mid-scene, so no phase is measuring a domain rebuild it caused itself.
        int wallZ = z - 34;
        queue(1, mc -> server(mc, s -> run(s, "fill " + wallX + " " + (y0 - 2) + " " + (wallZ - PERF_HALF) + " "
                + wallX + " " + surface + " " + (wallZ + PERF_HALF) + " minecraft:stone")));

        queue(5, mc -> server(mc, s -> {
            FishSchoolManager schools = FishSchoolManager.get(s.getLevel(mc.level.dimension()));
            int spawned = 0;
            StringBuilder results = new StringBuilder();
            for (int[] pool : PERF_POOLS) {
                // Seeded below the middle: a region spans its anchor -6..+4, so the surface (y0+5)
                // must be within 4 above it for a bobber to be in the school's water.
                String result = schools.spawnForDebug(new BlockPos(x + pool[0], y0 + 2, z + pool[1]),
                        Fishtastic.id("bluegill"));
                if (result.startsWith("Spawned")) spawned++;
                results.append(result).append(' ');
            }
            check("fishschoolperf.spawn", spawned == PERF_POOLS.length, spawned + "/" + PERF_POOLS.length + ": " + results);
        }));
        // Hover between the two fished pools, looking at the first: both bobbers stay within the
        // 32 blocks a line may reach, and all six schools are within tracking range.
        queue(20, mc -> camera(mc, x + 0.5, y0 + 14 + 1.62, z - 16.5, 0f, 30f));
        queue(10, mc -> server(mc, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(grill24.fishtastic.FishtasticItems.COPPER_FISHING_ROD.value()));
            FishSchoolPerf.enabled = true;
        }));
        // Let the schools spread and every copy catch up.
        queue(200, mc -> {
            FishSchoolClientRuntime.Perf.enabled = true;
            screenshot(mc, "fishschoolperf", "overview");
            RenderSelfTest.note("fishschoolperf.copies " + FishSchoolClientRuntime.debugState().replace('\n', ';'));
        });

        perfPhase("idle", perfTicks, null);
        perfPhase("fishing", perfTicks, mc -> perfFishingProbe(mc, fishedPool, surface, null));
        queue(1, mc -> {
            check("fishschoolperf.bites", PERF_BITES.get() > 0, PERF_BITES.get() + " bites while fishing beside a fish");
            server(mc, s -> {
                ServerPlayer player = s.getPlayerList().getPlayers().get(0);
                if (player.fishing != null) player.fishing.discard();
                player.fishing = null;
            });
        });
        perfPhase("wall", perfTicks, mc -> perfFishingProbe(mc, wallPool, surface, wallX));
        queue(1, mc -> server(mc, s -> {
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            if (player.fishing != null) player.fishing.discard();
            player.fishing = null;
        }));

        // Late join: every client copy is dropped by the server's bookkeeping and rebuilt from its
        // setup, replaying the whole epoch on the render thread.
        queue(20, mc -> {
            // Checked now too: the rebuild below starts every copy's mismatch count afresh.
            check("fishschoolperf.lockstep.before", !FishSchoolClientRuntime.debugState().matches("(?s).*mismatches=[1-9].*"),
                    FishSchoolClientRuntime.debugState().replace('\n', ';'));
            FishSchoolClientRuntime.Perf.reset();
            server(mc, s -> FishSchoolManager.get(s.getLevel(mc.level.dimension())).forgetTrackingForTest());
        });
        queue(60, mc -> {
            RenderSelfTest.note("fishschoolperf.latejoin client " + FishSchoolClientRuntime.Perf.report());
            RenderSelfTest.note("fishschoolperf.latejoin copies " + FishSchoolClientRuntime.debugState().replace('\n', ';'));
            String copies = FishSchoolClientRuntime.debugState();
            check("fishschoolperf.latejoin", copies.contains("caughtUp=true") && !copies.contains("caughtUp=false"),
                    FishSchoolClientRuntime.debugState().replace('\n', ';'));
        });

        // A whole default visit (12000 ticks) replayed at 2, 4 and 8 fish, twice for a warm JIT.
        for (int round = 1; round <= 2; round++) {
            for (int fish : new int[] {2, 4, 8}) {
                String label = "fishschoolperf.replay round=" + round + " ";
                queue(5, mc -> RenderSelfTest.note(label + FishSchoolClientRuntime.replayBenchForSelfTest(fish, 12000)));
            }
        }

        // Churn: one pool turned into a frozen river (still bluegill water, but cold enough to
        // freeze) with fast random ticks, counting the Water inputs its icing surface sends.
        int fx = x + 34;
        queue(1, mc -> {
            FishSchoolClientRuntime.Perf.reset();
            server(mc, s -> {
                FishSchoolPerf.reset();
                run(s, "fillbiome " + (fx - PERF_HALF) + " " + (y0 - PERF_DOWN) + " " + (z - PERF_HALF) + " "
                        + (fx + PERF_HALF) + " " + (surface + 6) + " " + (z + PERF_HALF) + " minecraft:frozen_river");
                run(s, "gamerule random_tick_speed 30");
            });
        });
        queue(1200, mc -> {
            RenderSelfTest.note("fishschoolperf.freeze client " + FishSchoolClientRuntime.Perf.report());
            server(mc, s -> {
                run(s, "gamerule random_tick_speed 3");
                ServerLevel level = s.getLevel(mc.level.dimension());
                int ice = 0;
                for (int dx = -PERF_HALF; dx <= PERF_HALF; dx++) {
                    for (int dz = -PERF_HALF; dz <= PERF_HALF; dz++) {
                        if (level.getBlockState(new BlockPos(fx + dx, surface, z + dz))
                                .is(net.minecraft.world.level.block.Blocks.ICE)) ice++;
                    }
                }
                RenderSelfTest.note("fishschoolperf.freeze server " + FishSchoolPerf.report());
                check("fishschoolperf.freeze", ice > 0 && FishSchoolPerf.waterInputs() > 0, ice + " surface cells iced; " + FishSchoolPerf.waterInputs()
                        + " Water inputs; " + FishSchoolManager.get(level).debugNearest(frozenPool));
            });
        });

        queue(5, mc -> {
            FishSchoolClientRuntime.Perf.enabled = false;
            server(mc, s -> FishSchoolPerf.enabled = false);
            check("fishschoolperf.lockstep", !FishSchoolClientRuntime.debugState().matches("(?s).*mismatches=[1-9].*"),
                    FishSchoolClientRuntime.debugState().replace('\n', ';'));
        });
    }

    /** One measured phase: counters reset, {@code ticks} of play (with {@code probe} each tick), then both reports. */
    private static void perfPhase(String name, int ticks, @org.jetbrains.annotations.Nullable java.util.function.Consumer<net.minecraft.client.Minecraft> probe) {
        queue(1, mc -> {
            FishSchoolClientRuntime.Perf.reset();
            server(mc, s -> {
                FishSchoolPerf.reset();
                PERF_HOOK_REASONS.clear();
            });
            PERF_BITES.set(0);
            perfWasReady = false;
            perfProbeTicks = 0;
            RenderSelfTest.tickProbe = probe;
        });
        queue(ticks, mc -> {
            RenderSelfTest.tickProbe = null;
            RenderSelfTest.note("fishschoolperf." + name + " client " + FishSchoolClientRuntime.Perf.report());
            server(mc, s -> RenderSelfTest.note("fishschoolperf." + name + " server " + FishSchoolPerf.report()
                    + " | bites=" + PERF_BITES.get() + (PERF_HOOK_REASONS.isEmpty() ? "" : " | hook checks " + PERF_HOOK_REASONS)));
        });
    }

    /**
     * Keeps a line in the pool at {@code centre} and counts its school's bites. Beside a fish
     * ({@code wallX} null) the bobber is recast 2 blocks from a free fish every 10 s; across the
     * wall it is recast every 2 s just over the wall from the free fish nearest it, so every hook
     * check searches a long path. Never recast while a fish is on its way or biting.
     */
    private static void perfFishingProbe(net.minecraft.client.Minecraft mc, Vec3 centre, int surface, @org.jetbrains.annotations.Nullable Integer wallX) {
        if (++perfProbeTicks % 5 != 0) return;
        boolean recastDue = perfProbeTicks % (wallX == null ? 200 : 40) == 5;
        server(mc, s -> {
            ServerLevel level = s.getLevel(mc.level.dimension());
            FishSchoolManager schools = FishSchoolManager.get(level);
            String state = schools.debugNearest(centre);
            boolean ready = state.contains("BITE_READY");
            if (ready && !perfWasReady) PERF_BITES.incrementAndGet();
            perfWasReady = ready;
            ServerPlayer player = s.getPlayerList().getPlayers().get(0);
            PERF_HOOK_REASONS.merge(schools.hookReport(player).replaceAll("[0-9a-f]{8}|-?[0-9][0-9.,]*", "#"), 1, Integer::sum);
            boolean lineOut = player.fishing != null && !player.fishing.isRemoved();
            if (ready || state.contains("APPROACHING") || (lineOut && !recastDue)) return;
            Vec3 at = perfBobberSpot(level, schools.availableMembersForTest(centre), surface, wallX);
            if (at == null) return;
            if (player.fishing != null) player.fishing.discard();
            FishingHook hook = new FishingHook(player, level, 0, 0);
            hook.setPos(at);
            hook.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(hook);
            player.fishing = hook;
        });
    }

    /** Where to float the bobber (its centre 0.85 over the surface cell), or null if no spot is water. */
    private static @org.jetbrains.annotations.Nullable Vec3 perfBobberSpot(ServerLevel level, List<Vec3> fish, int surface,
                                                                           @org.jetbrains.annotations.Nullable Integer wallX) {
        if (fish.isEmpty()) return null;
        if (wallX == null) {
            for (int k = 0; k < 8 * fish.size(); k++) {
                Vec3 f = fish.get(k / 8);
                double angle = k * Math.PI / 4;
                Vec3 at = new Vec3(f.x + Math.cos(angle) * 2, surface + 0.85, f.z + Math.sin(angle) * 2);
                if (level.getFluidState(BlockPos.containing(at.x, surface + 0.35, at.z)).isSource()) return at;
            }
            return null;
        }
        double plane = wallX + 0.5;
        Vec3 nearest = fish.stream().min(java.util.Comparator.comparingDouble(f -> Math.abs(f.x - plane))).orElseThrow();
        // The fish mirrored across the wall, but at most 3 blocks out so it stays within interest range.
        double side = Math.signum(nearest.x - plane);
        double offset = Math.max(1.0, Math.min(Math.abs(nearest.x - plane), 3.0));
        Vec3 at = new Vec3(plane - (side == 0 ? 1 : side) * offset, surface + 0.85, nearest.z);
        return level.getFluidState(BlockPos.containing(at.x, surface + 0.35, at.z)).isSource() ? at : null;
    }
}
