package grill24.fishsim;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.render.FrameRenderer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Renders the floor walk from above with each crawler's path drawn over it, so the behaviour can
 * be looked at rather than only asserted (docs/fish-sim-locomotion.md §6, "headless first").
 * Deliberately a {@code main} and not a test: it produces a picture, and a picture has no pass
 * condition — {@link BenthicTest} holds the properties that do.
 *
 * <p>{@code java -cp <main>:<test> grill24.fishsim.BenthicProbe out.png [ticks]}
 */
public final class BenthicProbe {

    private BenthicProbe() {}

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "benthic-probe.png";
        int ticks = args.length > 1 ? Integer.parseInt(args[1]) : 3_000;

        // Three tanks in a row, with cosmetics filling a block-and-a-half in the middle: the only
        // way from one end to the other is around them.
        boolean[][][] occupancy = new boolean[3][1][1];
        for (boolean[][] column : occupancy) column[0][0] = true;

        int cellsD = FloorCells.PER_BLOCK;
        boolean[] blocked = new boolean[3 * FloorCells.PER_BLOCK * cellsD];
        // Middle tank: block its whole north two rows, leaving a one-cell corridor to the south.
        for (int il = 3; il < 7; il++) {
            for (int id = 0; id < 2; id++) blocked[il * cellsD + id] = true;
        }
        VoxelDomain domain = new VoxelDomain(occupancy, VoxelDomain.DEFAULT_INSET, 0.125f, blocked);

        FishSpec[] specs = {
                new FishSpec(0.11f, Locomotion.BENTHIC, false, 0),
                new FishSpec(0.09f, Locomotion.BENTHIC, true, 1),
                new FishSpec(0.13f, Locomotion.BENTHIC, false, 2),
                new FishSpec(0.10f, Locomotion.FREE_SWIM, false, 3),
                new FishSpec(0.10f, Locomotion.FREE_SWIM, true, 3),
        };

        FlockEngine engine = new FlockEngine(Tunables.GROUP);
        engine.rebuild(specs, 4242L, 0f, 20f, domain);

        int scale = 160;
        FrameRenderer renderer = new FrameRenderer(scale, false, false);
        float[][] trailL = new float[specs.length][ticks];
        float[][] trailD = new float[specs.length][ticks];
        for (int tick = 0; tick < ticks; tick++) {
            engine.step();
            for (int i = 0; i < specs.length; i++) {
                trailL[i][tick] = engine.posL()[i];
                trailD[i][tick] = engine.posD()[i];
            }
        }
        engine.interpolate(1f);

        BufferedImage image = renderer.render(engine, Tunables.GROUP, FrameRenderer.View.TOP);
        Graphics2D g = image.createGraphics();
        Color[] trailColors = {
                new Color(120, 230, 160), new Color(230, 200, 110), new Color(220, 130, 200),
                new Color(90, 150, 220), new Color(90, 150, 220),
        };
        for (int i = 0; i < specs.length; i++) {
            g.setColor(trailColors[i]);
            for (int tick = 0; tick < ticks; tick += 2) {
                float x = renderer.mapX(domain, trailL[i][tick]);
                float y = renderer.mapY(domain, FrameRenderer.View.TOP, 0f, trailD[i][tick]);
                g.fill(new Ellipse2D.Float(x - 1f, y - 1f, 2f, 2f));
            }
        }
        g.dispose();

        ImageIO.write(image, "png", new File(out));
        System.out.println("wrote " + out + " (" + image.getWidth() + "x" + image.getHeight() + ")");
        System.out.println("floor area: " + domain.floor().area() + " blocks²");
        for (int i = 0; i < specs.length; i++) {
            System.out.printf("fish %d %-10s path=%.2f blocks%n", i, engine.locomotion[i],
                    pathLength(trailL[i], trailD[i]));
        }
    }

    private static float pathLength(float[] l, float[] d) {
        float total = 0f;
        for (int i = 1; i < l.length; i++) {
            float dl = l[i] - l[i - 1], dd = d[i] - d[i - 1];
            total += (float) Math.sqrt(dl * dl + dd * dd);
        }
        return total;
    }

    private static final class FloorCells {
        static final int PER_BLOCK = grill24.fishsim.domain.FloorField.SUBCELLS;
    }
}
