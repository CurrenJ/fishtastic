package grill24.fishsim.harness.companion;

import grill24.fishsim.companion.CompanionTunables;
import grill24.fishsim.companion.Vec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.harness.GifSequenceWriter;
import grill24.fishsim.render.CompanionRenderer;

import javax.imageio.ImageIO;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless companion export (docs/fish-companions-sim-plan.md §4.3). For each owner script it
 * writes:
 * <ul>
 *   <li>{@code <script>.gif} — first person beside the top view, 10 fps;</li>
 *   <li>{@code <script>-sheet.png} — a contact sheet of first-person frames, one every 3 s;</li>
 *   <li>a metrics line on stdout and a row in {@code metrics.csv}.</li>
 * </ul>
 *
 * <pre>
 *   ./gradlew :fishsim:runCompanionHeadless
 *   ./gradlew :fishsim:runCompanionHeadless -PsimArgs="--script walk --seed 3 --ticks 2400"
 *   ./gradlew :fishsim:runCompanionHeadless -PsimArgs="--class GLIDE"   (files prefixed glide_)
 * </pre>
 */
public final class CompanionHeadless {

    private static final int FP_W = 512, FP_H = 288, TOP = 288;
    private static final int TRAIL = 120;

    private CompanionHeadless() {}

    public static void main(String[] args) throws IOException {
        String scriptArg = "all";
        long seed = 1;
        Locomotion locomotion = Locomotion.FREE_SWIM;
        int ticks = 1200;
        int gifTicks = 1200;
        Path out = Path.of("build", "companion-export");
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--script" -> scriptArg = args[++i];
                case "--seed" -> seed = Long.parseLong(args[++i]);
                case "--ticks" -> ticks = Integer.parseInt(args[++i]);
                case "--gif-ticks" -> gifTicks = Integer.parseInt(args[++i]);
                case "--out" -> out = Path.of(args[++i]);
                case "--class" -> locomotion = Locomotion.valueOf(args[++i].toUpperCase(java.util.Locale.ROOT));
                default -> throw new IllegalArgumentException("unknown option " + args[i]);
            }
        }
        Files.createDirectories(out);
        List<String> scripts = scriptArg.equals("all") ? OwnerScript.NAMES : List.of(scriptArg.split(","));
        String prefix = locomotion == Locomotion.FREE_SWIM ? "" : locomotion.name().toLowerCase(java.util.Locale.ROOT) + "_";
        try (PrintWriter csv = new PrintWriter(Files.newBufferedWriter(out.resolve(prefix + "metrics.csv")))) {
            csv.println("script,seed,ticks,summary");
            for (String script : scripts) {
                CompanionRun.Metrics m = export(script, seed, locomotion, prefix, ticks, Math.min(gifTicks, ticks), out);
                String line = m.summary();
                System.out.printf("%-13s %s%n", script, line);
                csv.printf("%s,%d,%d,\"%s\"%n", script, seed, ticks, line);
            }
        }
        System.out.println("Wrote " + out.toAbsolutePath());
    }

    static CompanionRun.Metrics export(String script, long seed, Locomotion locomotion, String prefix, int ticks,
                                       int gifTicks, Path out) throws IOException {
        CompanionTunables t = CompanionTunables.defaults();
        CompanionRun run = new CompanionRun(script, seed, locomotion, t, true);
        ArrayDeque<Vec> trail = new ArrayDeque<>();
        List<BufferedImage> sheet = new ArrayList<>();
        Path gifPath = out.resolve(prefix + script + ".gif");
        Files.deleteIfExists(gifPath);
        try (FileImageOutputStream gifOut = gifTicks > 0 ? new FileImageOutputStream(gifPath.toFile()) : null) {
            GifSequenceWriter gif = null;
            for (int tick = 0; tick < ticks; tick++) {
                run.tick();
                trail.addLast(run.agent.position());
                if (trail.size() > TRAIL) trail.removeFirst();
                boolean gifFrame = tick < gifTicks && tick % 2 == 0;
                boolean sheetFrame = tick % 60 == 0 && sheet.size() < 20;
                if (!gifFrame && !sheetFrame) continue;
                run.agent.interpolate(1f);
                BufferedImage fp = CompanionRenderer.firstPerson(FP_W, FP_H, run.owner, run.scene, run.agent, true);
                if (sheetFrame) sheet.add(label(fp, String.format("%s%s t=%.0fs", prefix, script, tick / 20.0)));
                if (gifFrame) {
                    BufferedImage top = CompanionRenderer.topView(TOP, TOP, run.owner, run.scene, run.agent, t, trail, 22);
                    BufferedImage frame = CompanionRenderer.composite(fp, top);
                    if (gif == null) gif = new GifSequenceWriter(gifOut, frame.getType(), 100);
                    gif.writeFrame(frame);
                }
            }
            if (gif != null) gif.close();
        }
        writeSheet(sheet, out.resolve(prefix + script + "-sheet.png"));
        return run.metrics();
    }

    private static BufferedImage label(BufferedImage img, String text) {
        Graphics2D g = img.createGraphics();
        g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        g.setColor(new Color(0, 0, 0, 140));
        g.fillRect(img.getWidth() - 220, img.getHeight() - 20, 220, 20);
        g.setColor(Color.WHITE);
        g.drawString(text, img.getWidth() - 216, img.getHeight() - 6);
        g.dispose();
        return img;
    }

    private static void writeSheet(List<BufferedImage> frames, Path path) throws IOException {
        if (frames.isEmpty()) return;
        int cols = 4, rows = (frames.size() + cols - 1) / cols;
        int w = frames.get(0).getWidth() / 2, h = frames.get(0).getHeight() / 2;
        BufferedImage sheet = new BufferedImage(w * cols, h * rows, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        for (int i = 0; i < frames.size(); i++) {
            g.drawImage(frames.get(i), (i % cols) * w, (i / cols) * h, w, h, null);
        }
        g.dispose();
        ImageIO.write(sheet, "png", path.toFile());
    }
}
