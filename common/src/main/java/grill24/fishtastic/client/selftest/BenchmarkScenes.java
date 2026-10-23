package grill24.fishtastic.client.selftest;

import static grill24.fishtastic.client.selftest.RenderSelfTest.benchPlaceSpan;
import static grill24.fishtastic.client.selftest.RenderSelfTest.benchSpans;
import static grill24.fishtastic.client.selftest.RenderSelfTest.block;
import static grill24.fishtastic.client.selftest.RenderSelfTest.camera;
import static grill24.fishtastic.client.selftest.AmbientScenes.fill;
import static grill24.fishtastic.client.selftest.RenderSelfTest.finish;
import static grill24.fishtastic.client.selftest.RenderSelfTest.loader;
import static grill24.fishtastic.client.selftest.RenderSelfTest.origin;
import static grill24.fishtastic.client.selftest.RenderSelfTest.param;
import static grill24.fishtastic.client.selftest.RenderSelfTest.params;
import static grill24.fishtastic.client.selftest.RenderSelfTest.placeStructure;
import static grill24.fishtastic.client.selftest.RenderSelfTest.queue;
import static grill24.fishtastic.client.selftest.RenderSelfTest.run;
import static grill24.fishtastic.client.selftest.RenderSelfTest.scenes;
import static grill24.fishtastic.client.selftest.RenderSelfTest.screenshot;
import static grill24.fishtastic.client.selftest.RenderSelfTest.server;
import static grill24.fishtastic.client.selftest.RenderSelfTest.structure;
import static grill24.fishtastic.client.selftest.RenderSelfTest.tank;
import static grill24.fishtastic.client.selftest.RenderSelfTest.wait;
import static grill24.fishtastic.client.selftest.RenderSelfTest.waitFor;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import java.util.List;
import java.util.Locale;

/** Cosmetic bake, frame and scale benchmark scenes. */
final class BenchmarkScenes {

    /** One benchmark workload: builds its tanks and cosmetics, returns the tanks it made; and where to look from. */
    private record BenchScene(String name, java.util.function.Function<ServerLevel, List<BlockPos>> build,
                              double camX, double camY, double camZ, float yaw, float pitch) {}

    /**
     * Measures cosmetic rendering cost (see {@link grill24.fishtastic.client.perf.CosmeticBenchmark}).
     * For each workload, under each mode — NONE (no cosmetics: the baseline), PER_FRAME (the previous
     * path: every cosmetic block submitted every frame) and MESH (baked into the chunk mesh) — in a
     * rotating order over {@code rounds=} rounds: switch mode, re-mesh every section and wait until
     * all are built, warm up, then record {@code frames=} frames uncapped with vsync off. Logs one
     * {@code [bench] RESULT} line per measurement and one {@code [bench] BAKE} line per mesh build;
     * raw per-frame samples go to {@code <run>/fishtastic_bench/}. Marker params: {@code frames=},
     * {@code rounds=}, {@code label=} (e.g. the shader setting, for the file names).
     */
    static void queuePerfBenchScene() {
        int frames = Integer.parseInt(param("frames", "3000"));
        int rounds = Integer.parseInt(param("rounds", "2"));
        String label = param("label", "default");
        int x0 = origin.getX() - 9, y = origin.getY(), z0 = origin.getZ() - 4;
        String[] structures = {"castle_ruin", "coral_reef_1", "oak_tree", "birch_tree", "spruce_gazebo", "mossy_boulder"};

        List<BenchScene> scenes = List.of(
                new BenchScene("typical", level -> {
                    List<BlockPos> tanks = new java.util.ArrayList<>();
                    for (int i = 0; i < 24; i++) {
                        BlockPos pos = new BlockPos(x0 + (i % 6) * 2, y, z0 + (i / 6) * 2);
                        level.setBlockAndUpdate(pos, grill24.fishtastic.FishtasticBlocks.FISH_TANK.value().defaultBlockState());
                        FishTankBlockEntity t = tank(level, pos);
                        if (t == null) continue;
                        t.setCosmetic(new CosmeticGridCell(0, 0), new PlacedCosmetic(Blocks.KELP.defaultBlockState(), 3));
                        t.setCosmetic(new CosmeticGridCell(2, 0), new PlacedCosmetic(Blocks.SEAGRASS.defaultBlockState()));
                        t.setCosmetic(new CosmeticGridCell(0, 2), new PlacedCosmetic(Blocks.SEA_PICKLE.defaultBlockState().setValue(SeaPickleBlock.PICKLES, 3)));
                        t.setCosmetic(new CosmeticGridCell(2, 2), new PlacedCosmetic(Blocks.TUBE_CORAL_FAN.defaultBlockState()));
                        t.setCosmetic(new CosmeticGridCell(1, 0), new PlacedCosmetic(Blocks.BRAIN_CORAL_FAN.defaultBlockState()));
                        t.setCeilingCosmetic(new CosmeticGridCell(2, 1), new PlacedCosmetic(Blocks.LANTERN.defaultBlockState(), 2));
                        placeStructure(t, structures[i % structures.length], 1, 1);
                        tanks.add(pos);
                    }
                    return tanks;
                }, x0 + 5.5, y + 5.0, z0 - 4.5, 0f, 35f),
                new BenchScene("setpieces", level -> benchSpans(level, x0, y, z0, 1),
                        x0 + 4.0, y + 2.6, z0 - 6.0, 0f, 12f),
                new BenchScene("stress", level -> benchSpans(level, x0, y, z0, 4),
                        x0 + 9.5, y + 3.2, z0 - 9.0, 0f, 12f));

        grill24.fishtastic.client.perf.CosmeticBenchmark.Mode[] modes = grill24.fishtastic.client.perf.CosmeticBenchmark.Mode.values();
        int[] savedLimit = new int[1];
        boolean[] savedVsync = new boolean[1];
        queue(1, mc -> {
            savedLimit[0] = mc.options.framerateLimit().get();
            savedVsync[0] = mc.options.enableVsync().get();
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
            // (26.1.2 also lifts the AFK frame limiter here; 1.21.1 has none.)
            mc.options.hideGui = true;
            Fishtastic.LOGGER.info("[bench] START loader={} label={} frames={} rounds={} window={}x{}", loader, label, frames, rounds,
                    mc.getWindow().getWidth(), mc.getWindow().getHeight());
        });

        for (BenchScene scene : scenes) {
            List<BlockPos> tanks = new java.util.ArrayList<>();
            queue(1, mc -> server(mc, s -> run(s, "fill " + (x0 - 2) + " " + (y - 1) + " " + (z0 - 2) + " " + (x0 + 22) + " " + (y + 4) + " " + (z0 + 12) + " minecraft:air")));
            queue(5, mc -> server(mc, s -> { tanks.clear(); tanks.addAll(scene.build().apply(s.overworld())); }));
            queue(20, mc -> camera(mc, scene.camX(), scene.camY(), scene.camZ(), scene.yaw(), scene.pitch()));
            queue(20, mc -> screenshot(mc, "perfbench", scene.name()));
            for (int round = 0; round < rounds; round++) {
                for (int k = 0; k < modes.length; k++) {
                    var mode = modes[(k + round) % modes.length];   // rotate the order each round
                    String tag = loader + "_" + label + "_" + scene.name() + "_" + mode.name().toLowerCase(Locale.ROOT) + "_r" + (round + 1);
                    queue(1, mc -> {
                        grill24.fishtastic.client.perf.CosmeticBenchmark.mode = mode;
                        grill24.fishtastic.client.compositemodel.TankCosmeticMesh.clearCache();
                        grill24.fishtastic.client.perf.CosmeticBenchmark.resetBakeStats();
                        for (BlockPos pos : tanks) {
                            if (mc.level.getBlockEntity(pos) instanceof FishTankBlockEntity t) {
                                grill24.fishtastic.architectury.RegistrationApiSided.getInstance().requestModelDataUpdate(t);
                            }
                        }
                        mc.levelRenderer.allChanged();
                    });
                    long[] rebuildStart = new long[1];
                    queue(1, mc -> rebuildStart[0] = System.nanoTime());
                    waitFor(1, 600, mc -> mc.levelRenderer.hasRenderedAllSections(), mc -> {
                        var b = grill24.fishtastic.client.perf.CosmeticBenchmark.class;
                        Fishtastic.LOGGER.info(String.format(Locale.ROOT,
                                "[bench] BAKE %s rebuild_wait_ms=%.0f snapshots=%d snapshot_ms_total=%.2f bakes=%d bake_ms_total=%.2f pieces=%d quads=%d",
                                tag, (System.nanoTime() - rebuildStart[0]) / 1e6,
                                grill24.fishtastic.client.perf.CosmeticBenchmark.snapshotCount.get(),
                                grill24.fishtastic.client.perf.CosmeticBenchmark.snapshotNanos.get() / 1e6,
                                grill24.fishtastic.client.perf.CosmeticBenchmark.bakeCount.get(),
                                grill24.fishtastic.client.perf.CosmeticBenchmark.bakeNanos.get() / 1e6,
                                grill24.fishtastic.client.perf.CosmeticBenchmark.bakedPieces.get(),
                                grill24.fishtastic.client.perf.CosmeticBenchmark.bakedQuads.get()));
                    });
                    queue(40, mc -> grill24.fishtastic.client.perf.CosmeticBenchmark.start(frames));
                    waitFor(1, 4000, mc -> {
                        grill24.fishtastic.client.perf.CosmeticBenchmark.drain();
                        return grill24.fishtastic.client.perf.CosmeticBenchmark.isDone();
                    }, mc -> Fishtastic.LOGGER.info("[bench] RESULT {} {}", tag, grill24.fishtastic.client.perf.CosmeticBenchmark.finish(tag)));
                }
            }
        }
        queue(1, mc -> {
            grill24.fishtastic.client.perf.CosmeticBenchmark.mode = grill24.fishtastic.client.perf.CosmeticBenchmark.Mode.MESH;
            mc.options.framerateLimit().set(savedLimit[0]);
            mc.options.enableVsync().set(savedVsync[0]);
            mc.options.hideGui = false;
            mc.levelRenderer.allChanged();
            Fishtastic.LOGGER.info("[bench] DONE");
        });
    }

    // ── bakebench / scalebench: what a cosmetic structure costs (client/perf/CosmeticCostBench) ──

    /**
     * Geometry and cold-cache bake time of every shipped structure ({@code [bakebench] STRUCT} lines),
     * with the vertex size of each chunk layer. Marker param: {@code reps=} timed bakes per structure.
     */
    static void queueBakeBenchScene() {
        int reps = Integer.parseInt(param("reps", "21"));
        queue(40, mc -> {
            var registry = mc.level.registryAccess().registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
            List<String> names = registry.keySet().stream().map(id -> id.getPath()).sorted().toList();
            grill24.fishtastic.client.perf.CosmeticCostBench.run(names, n -> registry.getOptional(structure(n)).orElse(null), reps);
            Fishtastic.LOGGER.info("[bakebench] DONE");
        });
    }

    /**
     * Steady-state frame cost of cosmetic structures as their amount grows. For each {@code structure=}
     * (repeatable) and each copy count ({@code spanCounts=} boxes for spans, laid out in a square;
     * {@code floorCounts=} tanks for floor structures), the same tanks are measured with cosmetics
     * off (NONE) and baked into the mesh (MESH), in pairs whose order alternates by round. The
     * pseudo-structure {@code empty} is the A/A control: tanks with nothing in them, where MESH and
     * NONE draw the same thing. RESULT lines carry the scene's total cosmetic quads; raw frames go
     * to {@code <run>/fishtastic_bench/scale_<loader>_<label>_<structure>_<count>_<mode>_r<N>.csv}.
     */
    static void queueScaleBenchScene() {
        int frames = Integer.parseInt(param("frames", "3000"));
        int rounds = Integer.parseInt(param("rounds", "5"));
        String label = param("label", "default");
        int[] spanCounts = java.util.Arrays.stream(param("spanCounts", "1,4,9").split(",")).mapToInt(Integer::parseInt).toArray();
        int[] floorCounts = java.util.Arrays.stream(param("floorCounts", "9,36").split(",")).mapToInt(Integer::parseInt).toArray();
        List<String> names = params("structure").isEmpty() ? List.of("empty", "sunken_ziggurat") : params("structure");
        // ab=mesh (default): NONE against MESH. ab=scan: both halves MESH, the per-frame floor-structure
        // part scans on against skipped (CosmeticBenchmark.skipStructureScans).
        boolean scanAb = param("ab", "mesh").equals("scan");
        int x0 = origin.getX() - 9, y = origin.getY(), z0 = origin.getZ() - 4;
        var tankState = grill24.fishtastic.FishtasticBlocks.FISH_TANK.value().defaultBlockState();
        int[] savedLimit = new int[1];
        boolean[] savedVsync = new boolean[1];
        queue(1, mc -> {
            savedLimit[0] = mc.options.framerateLimit().get();
            savedVsync[0] = mc.options.enableVsync().get();
            mc.options.framerateLimit().set(260);
            mc.options.enableVsync().set(false);
            mc.options.hideGui = true;
            // fullscreen=true: frame times depend on the framebuffer size, and a launch can come up windowed
            // at the 854x480 default even with fullscreen set in options. The START line records the size.
            if (param("fullscreen", "false").equals("true") && !mc.getWindow().isFullscreen()) mc.getWindow().toggleFullScreen();
        });
        queue(40, mc -> Fishtastic.LOGGER.info("[scale] START loader={} label={} frames={} rounds={} window={}x{}", loader, label, frames, rounds,
                mc.getWindow().getWidth(), mc.getWindow().getHeight()));
        for (String name : names) {
            boolean[] isSpan = new boolean[1];
            int[] box = {1, 1, 1};
            queue(1, mc -> {
                var registry = mc.level.registryAccess().registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
                var st = name.equals("empty") ? null : registry.getOptional(structure(name)).orElse(null);
                isSpan[0] = st != null && st.span().isPresent();
                if (isSpan[0]) { box[0] = st.span().get().x(); box[1] = st.span().get().y(); box[2] = st.span().get().z(); }
            });
            // Whether a structure is a span is only known once the registry is up, so both count
            // lists are queued and the one that doesn't apply is skipped when the scene runs.
            // The empty control runs the floor counts.
            java.util.List<int[]> plan = new java.util.ArrayList<>();
            for (int c : spanCounts) plan.add(new int[]{1, c});
            for (int c : floorCounts) plan.add(new int[]{0, c});
            for (int[] entry : plan) {
                boolean spanCount = entry[0] == 1;
                int count = entry[1];
                List<BlockPos> tanks = new java.util.ArrayList<>();
                boolean[] skip = new boolean[1];
                int[] sceneQuads = new int[1];
                double[] cam = new double[4];
                queue(1, mc -> {
                    skip[0] = name.equals("empty") ? spanCount : spanCount != isSpan[0];
                    if (skip[0]) return;
                    server(mc, s -> {
                        run(s, "fill " + (x0 - 3) + " " + (y - 1) + " " + (z0 - 3) + " " + (x0 + 24) + " " + (y + 5) + " " + (z0 + 16) + " minecraft:air");
                        tanks.clear();
                        var level = s.overworld();
                        int k = (int) Math.ceil(Math.sqrt(count));
                        if (isSpan[0]) {
                            var registry = level.registryAccess().registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY);
                            for (int i = 0; i < count; i++) {
                                BlockPos min = new BlockPos(x0 + (i % k) * (box[0] + 1), y, z0 + (i / k) * (box[2] + 1));
                                for (BlockPos p : BlockPos.betweenClosed(min, min.offset(box[0] - 1, box[1] - 1, box[2] - 1))) {
                                    level.setBlockAndUpdate(p, tankState);
                                    tanks.add(p.immutable());
                                }
                                benchPlaceSpan(level, registry, min, name);
                            }
                            cam[0] = x0 + (k * (box[0] + 1) - 1) / 2.0;
                            cam[1] = k * (box[0] + 1);
                            cam[2] = k * (box[2] + 1);
                            cam[3] = box[1];
                        } else {
                            for (int i = 0; i < count; i++) {
                                BlockPos p = new BlockPos(x0 + (i % k) * 2, y, z0 + (i / k) * 2);
                                level.setBlockAndUpdate(p, tankState);
                                FishTankBlockEntity t = tank(level, p);
                                if (t != null && !name.equals("empty")) placeStructure(t, name, 1, 1);
                                tanks.add(p);
                            }
                            cam[0] = x0 + (k * 2 - 1) / 2.0;
                            cam[1] = k * 2;
                            cam[2] = k * 2;
                            cam[3] = 1;
                        }
                    });
                });
                queue(20, mc -> {
                    if (skip[0]) return;
                    double dist = Math.max(4.0, 0.62 * cam[1] + 1.5);
                    camera(mc, cam[0], y + cam[3] + 0.45 * dist, z0 - dist, 0f, 28f);
                });
                queue(30, mc -> {
                    if (skip[0]) return;
                    screenshot(mc, "scalebench", label + "_" + name + "_" + count);
                    int q = 0;
                    for (BlockPos pos : tanks) {
                        if (mc.level.getBlockEntity(pos) instanceof FishTankBlockEntity t) {
                            var snap = grill24.fishtastic.client.compositemodel.TankCosmeticMesh.compute(t);
                            var baked = grill24.fishtastic.client.compositemodel.TankCosmeticMesh.bake(snap);
                            q += baked.untinted().size() + baked.tinted().size();
                        }
                    }
                    sceneQuads[0] = q;
                });
                for (int round = 0; round < rounds; round++) {
                    for (int half = 0; half < 2; half++) {
                        boolean first = (half + round) % 2 == 0;
                        var mode = (first && !scanAb) ? grill24.fishtastic.client.perf.CosmeticBenchmark.Mode.NONE
                                : grill24.fishtastic.client.perf.CosmeticBenchmark.Mode.MESH;
                        boolean skipScans = scanAb && !first;
                        String arm = scanAb ? (skipScans ? "noscan" : "scan") : mode.name().toLowerCase(Locale.ROOT);
                        String tag = "scale_" + loader + "_" + label + "_" + name + "_" + count + "_" + arm + "_r" + (round + 1);
                        queue(1, mc -> {
                            if (skip[0]) return;
                            grill24.fishtastic.client.perf.CosmeticBenchmark.mode = mode;
                            grill24.fishtastic.client.perf.CosmeticBenchmark.skipStructureScans = skipScans;
                            grill24.fishtastic.client.compositemodel.TankCosmeticMesh.clearCache();
                            grill24.fishtastic.client.perf.CosmeticBenchmark.resetBakeStats();
                            for (BlockPos pos : tanks) {
                                if (mc.level.getBlockEntity(pos) instanceof FishTankBlockEntity t) {
                                    grill24.fishtastic.architectury.RegistrationApiSided.getInstance().requestModelDataUpdate(t);
                                }
                            }
                            mc.levelRenderer.allChanged();
                        });
                        long[] rebuildStart = new long[1];
                        queue(1, mc -> rebuildStart[0] = System.nanoTime());
                        waitFor(1, 600, mc -> skip[0] || mc.levelRenderer.hasRenderedAllSections(), mc -> {
                            if (skip[0]) return;
                            Fishtastic.LOGGER.info(String.format(Locale.ROOT,
                                    "[scale] BAKE %s rebuild_wait_ms=%.0f snapshots=%d snapshot_ms_total=%.2f bakes=%d bake_ms_total=%.2f quads=%d",
                                    tag, (System.nanoTime() - rebuildStart[0]) / 1e6,
                                    grill24.fishtastic.client.perf.CosmeticBenchmark.snapshotCount.get(),
                                    grill24.fishtastic.client.perf.CosmeticBenchmark.snapshotNanos.get() / 1e6,
                                    grill24.fishtastic.client.perf.CosmeticBenchmark.bakeCount.get(),
                                    grill24.fishtastic.client.perf.CosmeticBenchmark.bakeNanos.get() / 1e6,
                                    grill24.fishtastic.client.perf.CosmeticBenchmark.bakedQuads.get()));
                        });
                        queue(40, mc -> { if (!skip[0]) grill24.fishtastic.client.perf.CosmeticBenchmark.start(frames); });
                        waitFor(1, 4000, mc -> {
                            if (skip[0]) return true;
                            grill24.fishtastic.client.perf.CosmeticBenchmark.drain();
                            return grill24.fishtastic.client.perf.CosmeticBenchmark.isDone();
                        }, mc -> {
                            if (skip[0]) return;
                            Fishtastic.LOGGER.info("[scale] RESULT {} tanks={} scene_quads={} {}", tag, tanks.size(), sceneQuads[0],
                                    grill24.fishtastic.client.perf.CosmeticBenchmark.finish(tag));
                        });
                    }
                }
            }
        }
        queue(1, mc -> {
            grill24.fishtastic.client.perf.CosmeticBenchmark.mode = grill24.fishtastic.client.perf.CosmeticBenchmark.Mode.MESH;
            grill24.fishtastic.client.perf.CosmeticBenchmark.skipStructureScans = false;
            mc.options.framerateLimit().set(savedLimit[0]);
            mc.options.enableVsync().set(savedVsync[0]);
            mc.options.hideGui = false;
            mc.levelRenderer.allChanged();
            Fishtastic.LOGGER.info("[scale] DONE");
        });
    }

}
