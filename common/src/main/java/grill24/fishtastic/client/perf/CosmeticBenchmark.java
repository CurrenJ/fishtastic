package grill24.fishtastic.client.perf;

import com.mojang.blaze3d.systems.TimerQuery;
import grill24.fishtastic.Fishtastic;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dev-only performance harness for tank cosmetic rendering, driven by the {@code perfbench}
 * self-test scene. Two parts:
 *
 * <ul>
 *   <li>{@link #mode} — which path draws static cosmetics: {@link Mode#MESH} (the shipped path, baked
 *       into the tank's chunk mesh by {@code TankCosmeticMesh}), {@link Mode#PER_FRAME} (the previous
 *       path: the block entity renderer submits every cosmetic block every frame — same pieces,
 *       same poses), or {@link Mode#NONE} (no cosmetics at all, the baseline). Always {@code MESH}
 *       outside a benchmark run.</li>
 *   <li>A frame recorder fed by {@code MinecraftFrameBenchMixin}: per frame, the wall-clock frame
 *       duration, the CPU time of {@code renderFrame} up to present ({@link Minecraft#getFrameTimeNs}),
 *       and the GPU time of the frame from a timer query. Raw samples go to a CSV per measurement.</li>
 * </ul>
 */
public final class CosmeticBenchmark {

    public enum Mode { MESH, PER_FRAME, NONE }

    /** Which path draws static cosmetics. Read by TankCosmeticMesh and the tank renderer. */
    public static volatile Mode mode = Mode.MESH;

    /** Benchmark only: skip the per-frame scans of floor-structure parts (chest render, furnace particles), to measure them. */
    public static volatile boolean skipStructureScans = false;

    // Bake/snapshot cost accounting (TankCosmeticMesh), reset per scene.
    public static final AtomicLong snapshotNanos = new AtomicLong();
    public static final AtomicLong snapshotCount = new AtomicLong();
    public static final AtomicLong bakeNanos = new AtomicLong();
    public static final AtomicLong bakeCount = new AtomicLong();
    public static final AtomicLong bakedQuads = new AtomicLong();
    public static final AtomicLong bakedPieces = new AtomicLong();

    public static void resetBakeStats() {
        snapshotNanos.set(0); snapshotCount.set(0);
        bakeNanos.set(0); bakeCount.set(0); bakedQuads.set(0); bakedPieces.set(0);
    }

    // ── Frame recording ─────────────────────────────────────────────────────────

    private static boolean recording;
    private static int target;
    private static int count;
    private static long[] frameNs = new long[0];
    private static long[] cpuNs = new long[0];
    private static long[] gpuNs = new long[0];
    private static long lastFrameEnd;
    private static boolean gpuThisFrame;
    private static final ArrayDeque<long[]> pendingGpu = new ArrayDeque<>(); // [frameIndex]
    private static final ArrayDeque<TimerQuery.FrameProfile> pendingProfiles = new ArrayDeque<>();

    public static boolean isRecording() {
        return recording;
    }

    public static boolean isDone() {
        return !recording && count >= target && pendingProfiles.isEmpty();
    }

    /** Starts recording the next {@code frames} frames. */
    public static void start(int frames) {
        target = frames;
        count = 0;
        frameNs = new long[frames];
        cpuNs = new long[frames];
        gpuNs = new long[frames];
        Arrays.fill(gpuNs, -1);
        lastFrameEnd = 0;
        pendingGpu.clear();
        pendingProfiles.clear();
        recording = true;
    }

    /** {@code Minecraft.renderFrame} HEAD. */
    public static void frameStart() {
        pollGpu();
        gpuThisFrame = false;
        // 1.20.1: TimerQuery is optional and has no isRecording(); vanilla only runs its own query
        // while the F3 screen is up (Minecraft#runTick), so stay out of the way then.
        if (recording && lastFrameEnd != 0 && TimerQuery.getInstance().isPresent()
                && !Minecraft.getInstance().options.renderDebug) {
            TimerQuery.getInstance().get().beginProfile();
            gpuThisFrame = true;
        }
    }

    /** {@code Minecraft.renderFrame} TAIL. */
    public static void frameEnd(long cpuFrameNs) {
        long now = System.nanoTime();
        if (gpuThisFrame) {
            pendingProfiles.add(TimerQuery.getInstance().get().endProfile());
            pendingGpu.add(new long[]{count});
        }
        if (recording) {
            if (lastFrameEnd != 0 && count < target) {
                frameNs[count] = now - lastFrameEnd;
                cpuNs[count] = cpuFrameNs;
                count++;
                if (count >= target) recording = false;
            } else if (lastFrameEnd != 0) {
                recording = false;
            }
        }
        lastFrameEnd = recording ? now : 0;
    }

    private static void pollGpu() {
        while (!pendingProfiles.isEmpty() && pendingProfiles.peek().isDone()) {
            TimerQuery.FrameProfile profile = pendingProfiles.poll();
            long[] idx = pendingGpu.poll();
            long value = profile.get();
            if (idx != null && idx[0] < gpuNs.length) gpuNs[(int) idx[0]] = value;
        }
    }

    /** Lets outstanding GPU queries resolve after recording stops (call once per tick until {@link #isDone()}). */
    public static void drain() {
        pollGpu();
    }

    /** Summary of the last recording, and its raw samples written to {@code <run>/fishtastic_bench/<name>.csv}. */
    public static String finish(String name) {
        pollGpu();
        int n = count;
        long[] f = Arrays.copyOf(frameNs, n), c = Arrays.copyOf(cpuNs, n);
        long[] g = Arrays.stream(Arrays.copyOf(gpuNs, n)).filter(v -> v > 0).toArray();
        writeCsv(name, n);
        Arrays.sort(f);
        Arrays.sort(c);
        Arrays.sort(g);
        return String.format(Locale.ROOT,
                "frames=%d fps=%.0f frame_ms mean=%.3f p50=%.3f p95=%.3f p99=%.3f max=%.3f | cpu_ms mean=%.3f p50=%.3f p95=%.3f | gpu_ms mean=%.3f p50=%.3f p95=%.3f (n=%d)",
                n, n == 0 ? 0 : 1e9 / mean(f), ms(mean(f)), ms(pct(f, 50)), ms(pct(f, 95)), ms(pct(f, 99)), ms(n == 0 ? 0 : f[n - 1]),
                ms(mean(c)), ms(pct(c, 50)), ms(pct(c, 95)),
                ms(mean(g)), ms(pct(g, 50)), ms(pct(g, 95)), g.length);
    }

    private static void writeCsv(String name, int n) {
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("fishtastic_bench");
            Files.createDirectories(dir);
            try (Writer w = Files.newBufferedWriter(dir.resolve(name + ".csv"))) {
                w.write("frame,frame_ns,cpu_ns,gpu_ns\n");
                for (int i = 0; i < n; i++) w.write(i + "," + frameNs[i] + "," + cpuNs[i] + "," + gpuNs[i] + "\n");
            }
        } catch (IOException e) {
            Fishtastic.LOGGER.warn("[bench] could not write {}.csv", name, e);
        }
    }

    private static double mean(long[] a) {
        if (a.length == 0) return 0;
        double s = 0;
        for (long v : a) s += v;
        return s / a.length;
    }

    private static double pct(long[] sorted, int p) {
        if (sorted.length == 0) return 0;
        return sorted[Math.min(sorted.length - 1, (int) Math.floor(sorted.length * p / 100.0))];
    }

    private static double ms(double ns) {
        return ns / 1e6;
    }

    private CosmeticBenchmark() {}
}
