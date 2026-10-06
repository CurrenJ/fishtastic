package grill24.fishsim.harness;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.render.FrameRenderer;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Headless verification export (docs/fish-sim-engine-plan.md §3.2): runs N ticks and writes PNG
 * trajectory plots, a frame strip, an animated GIF, and a metrics CSV — everything needed to
 * judge motion quality without a display or the game.
 *
 * <pre>
 *   ./gradlew :fishsim:runHeadless
 *   ./gradlew :fishsim:runHeadless -PsimArgs="--domain L --fish 12 --seed 42 --ticks 6000"
 *
 * <p>{@code --obstacles FILE} adds the solid parts of real cosmetics to the domain: one box per
 * line, {@code minL minY minD maxL maxY maxD} in the named domain's engine frame. This module cannot
 * read a structure, so the file comes from the mod's own derivation — see
 * {@code ObstacleExport} in the common tests ({@code ./gradlew :common:test --tests '*ObstacleExport*'
 * -PexportObstacles=DIR}).
 * </pre>
 *
 * Domains: {@code box} (the legacy single-tank path), {@code 1x1x1}, {@code 3x1x1}, {@code L},
 * {@code 2x2slab}, or {@code WxHxD} for an arbitrary full grid.
 */
public final class HeadlessRunner {

    private HeadlessRunner() {}

    public static void main(String[] args) throws IOException {
        String domainName = "L";
        int fish = 12;
        long seed = 12345L;
        int ticks = 6_000;
        int gifTicks = 600;      // 30 s of sim in the GIF
        int stripEvery = 1_000;  // one strip frame per N ticks
        Path outDir = Path.of("build", "sim-export");
        boolean heatmap = true;
        String cast = "mixed";
        String watcher = "none";
        FrameRenderer.View filmView = FrameRenderer.View.SIDE;
        Path obstacles = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--domain" -> domainName = args[++i];
                case "--fish" -> fish = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--ticks" -> ticks = Integer.parseInt(args[++i]);
                case "--gif-ticks" -> gifTicks = Integer.parseInt(args[++i]);
                case "--out" -> outDir = Path.of(args[++i]);
                case "--no-heatmap" -> heatmap = false;
                case "--cast" -> cast = args[++i];
                // pace: a player walking up to the glass at +lateral and away again every 30 s.
                // hover: one who stays by the tank, stepping between 3.5 and 1.1 blocks every 10 s.
                case "--watcher" -> watcher = args[++i];
                // The contact sheets' view: top for a shelter passed through along the depth axis,
                // like the Whale Fall's skull from eye to eye.
                case "--film-view" -> filmView = FrameRenderer.View.valueOf(args[++i].toUpperCase());
                case "--obstacles" -> obstacles = Path.of(args[++i]);
                default -> throw new IllegalArgumentException("Unknown arg: " + args[i]);
            }
        }
        Files.createDirectories(outDir);

        // Box = the single-tank parity set; voxel domains = the multi-tank canonical set.
        Tunables tunables = domainName.equals("box") ? Tunables.DEFAULT : Tunables.GROUP;
        FlockEngine engine = new FlockEngine(tunables);
        FishSpec[] specs = Scenarios.cast(cast, fish, seed);
        if (domainName.equals("box")) {
            engine.rebuild(specs, seed, 0f, 3, 0.35f, 0.3f, 20f);
        } else {
            grill24.fishsim.domain.VoxelDomain domain = Scenarios.domain(domainName);
            if (obstacles != null) domain.rebuildObstacles(Scenarios.obstacles(obstacles));
            engine.rebuild(specs, seed, 0f, 20f, domain);
        }

        FrameRenderer renderer = new FrameRenderer(120, heatmap, true);
        Metrics metrics = new Metrics(engine, tunables, 200);

        String tag = domainName + (obstacles == null ? "" : "+" + obstacles.getFileName().toString().replace(".txt", ""))
                + (cast.equals("mixed") ? "" : "-" + cast) + "-n" + fish + "-s" + seed;

        // Trajectory capture (positions every tick) + strip frames + tail-end GIF frames.
        float[][] trailL = new float[engine.count()][ticks];
        float[][] trailY = new float[engine.count()][ticks];
        float[][] trailD = new float[engine.count()][ticks];
        java.util.List<BufferedImage> stripFrames = new java.util.ArrayList<>();
        int gifStart = Math.max(0, ticks - gifTicks);

        // The first fish to make a whole shelter visit, filmed from the start of its approach to
        // the end of its exit (docs/fish-shelters.md §10) — a sheet to read, where the GIF is to
        // watch. Empty when the domain has no shelters or nobody visits.
        boolean watched = !watcher.equals("none");
        VisitFilm visit = new VisitFilm(engine.count(), watched, filmView);
        // The first lunge that sends the shoal every way at once: some dash for cover, the rest
        // flinch (docs/fish-shelters.md §5.3.2).
        StartleFilm startle = new StartleFilm(engine.count());
        float watcherAt = 7.5f;

        File gifFile = outDir.resolve(tag + ".gif").toFile();
        try (FileImageOutputStream gifOut = new FileImageOutputStream(gifFile)) {
            GifSequenceWriter gif = null;
            for (int t = 0; t < ticks; t++) {
                if (watched) {
                    // Walks, at a player's 4.3 blocks/s, rather than teleporting: the startle
                    // reads the watcher's approach over the last second.
                    float target = watcher.equals("hover")
                            ? ((t / 200) % 2 == 1 ? 1.1f : 3.5f)
                            : ((t / 600) % 2 == 1 ? 1.1f : 7.5f);
                    float step = 4.3f * tunables.dt();
                    watcherAt += Math.max(-step, Math.min(step, target - watcherAt));
                    engine.setWatcher(true, domain(engine) + watcherAt, 0f, 0f);
                }
                engine.step();
                metrics.sample();
                visit.observe(engine, renderer, tunables, t);
                if (watched) startle.observe(engine, renderer, tunables, t);
                for (int i = 0; i < engine.count(); i++) {
                    trailL[i][t] = engine.posL()[i];
                    trailY[i][t] = engine.posY()[i];
                    trailD[i][t] = engine.posD()[i];
                }
                if (t % stripEvery == 0) {
                    stripFrames.add(renderer.render(engine, tunables, FrameRenderer.View.SIDE));
                }
                if (t >= gifStart) {
                    BufferedImage frame = renderer.render(engine, tunables, FrameRenderer.View.SIDE);
                    if (gif == null) gif = new GifSequenceWriter(gifOut, frame.getType(), 50);
                    gif.writeFrame(frame);
                }
            }
            if (gif != null) gif.close();
        }

        writePng(trajectoryPlot(renderer, engine, tunables, FrameRenderer.View.SIDE, trailL, trailY, trailD),
                outDir.resolve(tag + "-trajectory-side.png"));
        writePng(trajectoryPlot(renderer, engine, tunables, FrameRenderer.View.TOP, trailL, trailY, trailD),
                outDir.resolve(tag + "-trajectory-top.png"));
        writePng(strip(stripFrames), outDir.resolve(tag + "-strip.png"));
        if (visit.done()) {
            writePng(visit.sheet(), outDir.resolve(tag + "-visit.png"));
            System.out.println("Visit sheet: fish " + visit.fish + ", " + visit.frames.size() + " frames");
        }
        if (startle.done()) {
            writePng(startle.sheet(), outDir.resolve(tag + "-startle.png"));
            System.out.println("Startle sheet: from tick " + startle.start + ", " + startle.frames.size() + " frames");
        }
        Files.writeString(outDir.resolve(tag + "-metrics.csv"),
                Metrics.csvHeader() + "\n" + metrics.csvRow() + "\n");

        System.out.println("Exported to " + outDir.toAbsolutePath() + " (" + tag + ")");
        System.out.println(Metrics.csvHeader());
        System.out.println(metrics.csvRow());
    }

    /** The domain rendered once, with every fish's full path overlaid (older segments fade out). */
    private static BufferedImage trajectoryPlot(FrameRenderer renderer, FlockEngine engine, Tunables tunables,
                                                FrameRenderer.View view, float[][] l, float[][] y, float[][] d) {
        BufferedImage image = renderer.render(engine, tunables, view);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            g.setStroke(new BasicStroke(1.2f));
            int ticks = l[0].length;
            for (int i = 0; i < engine.count(); i++) {
                if (!engine.swimmers[i]) continue;
                // Same hue mapping as FrameRenderer's fish bodies — trails read as species too.
                Color base = Color.getHSBColor((engine.species[i] % 6) / 6f * 0.85f + 0.05f, 0.65f, 0.95f);
                for (int t = 1; t < ticks; t += 2) {
                    int alpha = 30 + (int) (120f * t / ticks);
                    g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
                    g.draw(new java.awt.geom.Line2D.Float(
                            renderer.mapX(engine.domain(), l[i][t - 1]),
                            renderer.mapY(engine.domain(), view, y[i][t - 1], d[i][t - 1]),
                            renderer.mapX(engine.domain(), l[i][t]),
                            renderer.mapY(engine.domain(), view, y[i][t], d[i][t])));
                }
            }
        } finally {
            g.dispose();
        }
        return image;
    }

    private static BufferedImage strip(java.util.List<BufferedImage> frames) {
        int w = frames.get(0).getWidth();
        int h = frames.get(0).getHeight();
        BufferedImage out = new BufferedImage(w * frames.size(), h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            for (int i = 0; i < frames.size(); i++) {
                g.drawImage(frames.get(i), i * w, 0, null);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    /**
     * Films one whole visit: the first fish to go from ROAMING into APPROACH and on through to
     * INSIDE, a frame every {@link #EVERY} ticks until it is ROAMING again. An approach that
     * times out is discarded and the next one filmed instead.
     */
    private static final class VisitFilm {
        static final int EVERY = 10, DASH_EVERY = 3, COLUMNS = 6, MAX_FRAMES = 60;
        final int[] prev;
        final java.util.List<BufferedImage> frames = new java.util.ArrayList<>();
        final java.util.List<String> labels = new java.util.ArrayList<>();
        int fish = -1;
        boolean reachedInside, finished;
        /** Film only a startle — with a pacing watcher, that is the visit worth reading. */
        final boolean startledOnly;
        final FrameRenderer.View view;

        VisitFilm(int n, boolean startledOnly, FrameRenderer.View view) {
            prev = new int[n];
            this.startledOnly = startledOnly;
            this.view = view;
        }

        boolean done() {
            return finished;
        }

        void observe(FlockEngine engine, FrameRenderer renderer, Tunables tunables, int tick) {
            if (finished) return;
            for (int i = 0; i < engine.count(); i++) {
                int st = engine.shelterState(i);
                if (fish < 0 && st == FlockEngine.SHELTER_APPROACH && prev[i] == FlockEngine.SHELTER_ROAMING
                        && (!startledOnly || engine.startled(i))) {
                    fish = i;
                }
                prev[i] = st;
            }
            if (fish < 0) return;
            int st = engine.shelterState(fish);
            if (st == FlockEngine.SHELTER_INSIDE) reachedInside = true;
            if (st == FlockEngine.SHELTER_ROAMING) {
                if (reachedInside) {
                    finished = true;
                } else { // timed out on the way: film the next one
                    fish = -1;
                    frames.clear();
                    labels.clear();
                }
                return;
            }
            // A dash lasts a few seconds, so it is filmed three times as densely as a visit.
            if (tick % (engine.dashing(fish) ? DASH_EVERY : EVERY) == 0 && frames.size() < MAX_FRAMES) {
                BufferedImage frame = renderer.render(engine, tunables, view);
                // Ring the filmed fish: in a crowded tank the label alone doesn't say which it is.
                Graphics2D g = frame.createGraphics();
                try {
                    float x = renderer.mapX(engine.domain(), engine.posL()[fish]);
                    float y = renderer.mapY(engine.domain(), view, engine.posY()[fish], engine.posD()[fish]);
                    g.setColor(new Color(255, 80, 80));
                    g.setStroke(new java.awt.BasicStroke(2f));
                    g.drawOval(Math.round(x) - 14, Math.round(y) - 14, 28, 28);
                } finally {
                    g.dispose();
                }
                frames.add(frame);
                float v = (float) Math.sqrt(engine.velL()[fish] * engine.velL()[fish]
                        + engine.velY()[fish] * engine.velY()[fish] + engine.velD()[fish] * engine.velD()[fish]);
                labels.add("t=" + tick + " " + new String[]{"roam", "approach", "enter", "inside", "exit", "rest"}[st]
                        + (engine.startled(fish) ? " (startled)" : "") + String.format(" v=%.2f", v));
            }
        }

        BufferedImage sheet() {
            int w = frames.get(0).getWidth(), h = frames.get(0).getHeight();
            int rows = (frames.size() + COLUMNS - 1) / COLUMNS;
            BufferedImage out = new BufferedImage(w * COLUMNS, h * rows, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            try {
                for (int k = 0; k < frames.size(); k++) {
                    int x = (k % COLUMNS) * w, y = (k / COLUMNS) * h;
                    g.drawImage(frames.get(k), x, y, null);
                    g.setColor(new Color(255, 230, 120));
                    g.drawString("fish " + fish + " · " + labels.get(k), x + 6, y + h - 6);
                }
            } finally {
                g.dispose();
            }
            return out;
        }
    }

    /**
     * The whole tank from the first tick at least {@link #MIN_FLINCHERS} fish start flinching
     * together: dashing fish ringed cyan, flinching ones orange, every {@link #EVERY} ticks.
     */
    private static final class StartleFilm {
        static final int EVERY = 2, FRAMES = 42, COLUMNS = 6, MIN_FLINCHERS = 3;
        final boolean[] wasFlinching;
        final java.util.List<BufferedImage> frames = new java.util.ArrayList<>();
        final java.util.List<String> labels = new java.util.ArrayList<>();
        int start = -1;

        StartleFilm(int n) {
            wasFlinching = new boolean[n];
        }

        boolean done() {
            return frames.size() >= FRAMES;
        }

        void observe(FlockEngine engine, FrameRenderer renderer, Tunables tunables, int tick) {
            if (done()) return;
            if (start < 0) {
                int started = 0;
                for (int i = 0; i < engine.count(); i++) {
                    if (engine.flinching(i) && !wasFlinching[i]) started++;
                    wasFlinching[i] = engine.flinching(i);
                }
                if (started < MIN_FLINCHERS) return;
                start = tick;
            }
            if ((tick - start) % EVERY != 0) return;
            BufferedImage frame = renderer.render(engine, tunables, FrameRenderer.View.SIDE);
            Graphics2D g = frame.createGraphics();
            int dashing = 0, flinching = 0;
            try {
                g.setStroke(new java.awt.BasicStroke(2f));
                for (int i = 0; i < engine.count(); i++) {
                    boolean d = engine.dashing(i), f = engine.flinching(i);
                    if (!d && !f) continue;
                    if (d) dashing++;
                    if (f) flinching++;
                    g.setColor(d ? new Color(80, 220, 255) : new Color(255, 150, 40));
                    float x = renderer.mapX(engine.domain(), engine.posL()[i]);
                    float y = renderer.mapY(engine.domain(), FrameRenderer.View.SIDE, engine.posY()[i], engine.posD()[i]);
                    g.drawOval(Math.round(x) - 12, Math.round(y) - 12, 24, 24);
                }
            } finally {
                g.dispose();
            }
            frames.add(frame);
            labels.add(String.format("t+%.1fs  dashing %d  flinching %d", (tick - start) * tunables.dt(), dashing, flinching));
        }

        BufferedImage sheet() {
            int w = frames.get(0).getWidth(), h = frames.get(0).getHeight();
            int rows = (frames.size() + COLUMNS - 1) / COLUMNS;
            BufferedImage out = new BufferedImage(w * COLUMNS, h * rows, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = out.createGraphics();
            try {
                for (int k = 0; k < frames.size(); k++) {
                    int x = (k % COLUMNS) * w, y = (k / COLUMNS) * h;
                    g.drawImage(frames.get(k), x, y, null);
                    g.setColor(new Color(255, 230, 120));
                    g.drawString(labels.get(k), x + 6, y + h - 6);
                }
            } finally {
                g.dispose();
            }
            return out;
        }
    }

    /** The domain's high-lateral edge, which the pacing watcher walks up to. */
    private static float domain(FlockEngine engine) {
        return engine.domain().maxLateral();
    }

    private static void writePng(BufferedImage image, Path path) throws IOException {
        ImageIO.write(image, "png", path.toFile());
    }
}
