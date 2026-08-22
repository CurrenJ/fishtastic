package grill24.fishsim.harness;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
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

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--domain" -> domainName = args[++i];
                case "--fish" -> fish = Integer.parseInt(args[++i]);
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--ticks" -> ticks = Integer.parseInt(args[++i]);
                case "--gif-ticks" -> gifTicks = Integer.parseInt(args[++i]);
                case "--out" -> outDir = Path.of(args[++i]);
                case "--no-heatmap" -> heatmap = false;
                default -> throw new IllegalArgumentException("Unknown arg: " + args[i]);
            }
        }
        Files.createDirectories(outDir);

        // Box = the single-tank parity set; voxel domains = the multi-tank canonical set.
        Tunables tunables = domainName.equals("box") ? Tunables.DEFAULT : Tunables.GROUP;
        FlockEngine engine = new FlockEngine(tunables);
        FishSpec[] specs = Scenarios.specs(fish, seed);
        if (domainName.equals("box")) {
            engine.rebuild(specs, seed, 0f, 3, 0.35f, 0.3f, 20f);
        } else {
            engine.rebuild(specs, seed, 0f, 20f, new VoxelDomain(Scenarios.occupancy(domainName)));
        }

        FrameRenderer renderer = new FrameRenderer(120, heatmap, true);
        Metrics metrics = new Metrics(engine, tunables, 200);

        String tag = domainName + "-n" + fish + "-s" + seed;

        // Trajectory capture (positions every tick) + strip frames + tail-end GIF frames.
        float[][] trailL = new float[engine.count()][ticks];
        float[][] trailY = new float[engine.count()][ticks];
        float[][] trailD = new float[engine.count()][ticks];
        java.util.List<BufferedImage> stripFrames = new java.util.ArrayList<>();
        int gifStart = Math.max(0, ticks - gifTicks);

        File gifFile = outDir.resolve(tag + ".gif").toFile();
        try (FileImageOutputStream gifOut = new FileImageOutputStream(gifFile)) {
            GifSequenceWriter gif = null;
            for (int t = 0; t < ticks; t++) {
                engine.step();
                metrics.sample();
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
        int w = frames.getFirst().getWidth();
        int h = frames.getFirst().getHeight();
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

    private static void writePng(BufferedImage image, Path path) throws IOException {
        ImageIO.write(image, "png", path.toFile());
    }
}
