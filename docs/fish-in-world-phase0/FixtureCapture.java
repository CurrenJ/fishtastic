import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.FlockEngine;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import grill24.fishsim.domain.VoxelDomain;
import grill24.fishsim.render.FrameRenderer;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Phase 0 diagnostic export; no Minecraft sprites, visual-parity claim, or runtime changes. */
class FixtureCapture {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        Files.createDirectories(out);
        for (Locomotion mode : new Locomotion[]{Locomotion.FREE_SWIM, Locomotion.GLIDE,
                Locomotion.DRIFT, Locomotion.BENTHIC}) {
            boolean[][][] cells = new boolean[4][2][3];
            for (boolean[][] column : cells) for (boolean[] row : column) Arrays.fill(row, true);
            FlockEngine engine = new FlockEngine(Tunables.GROUP);
            engine.rebuild(new FishSpec[]{new FishSpec(0.26f, mode, false, 1),
                    new FishSpec(0.30f, mode, true, 1), new FishSpec(0.22f, mode, false, 2)},
                    42L, 0f, 20f, new VoxelDomain(cells));
            FrameRenderer renderer = new FrameRenderer(80, false, true);
            StringBuilder trace = new StringBuilder("tick,id,declared,effective,l,y,d,yaw,phase,shape\n");
            BufferedImage first = renderer.render(engine, Tunables.GROUP, FrameRenderer.View.SIDE);
            BufferedImage sheet = new BufferedImage(first.getWidth() * 3, first.getHeight() * 2,
                    BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = sheet.createGraphics();
            int captures = 0;
            for (int tick = 0; tick <= 1200; tick++) {
                if (tick > 0) engine.step();
                engine.interpolate(1f);
                if (tick % 40 == 0) for (int i = 0; i < engine.count(); i++) {
                    trace.append(String.format(Locale.ROOT, "%d,%d,%s,%s,%.9f,%.9f,%.9f,%.9f,%.9f,%.9f%n",
                            tick, i, mode, engine.locomotion[i], engine.renderX[i], engine.renderY[i],
                            engine.renderZ[i], engine.renderYaw[i], engine.renderPhase[i], engine.renderShape[i]));
                }
                if (tick % 240 == 0) {
                    graphics.drawImage(renderer.render(engine, Tunables.GROUP, FrameRenderer.View.SIDE),
                            (captures % 3) * first.getWidth(), (captures / 3) * first.getHeight(), null);
                    captures++;
                }
            }
            graphics.dispose();
            String name = mode.name().toLowerCase(Locale.ROOT);
            ImageIO.write(sheet, "png", out.resolve(name + "-strip.png").toFile());
            ImageIO.write(renderer.render(engine, Tunables.GROUP, FrameRenderer.View.TOP), "png",
                    out.resolve(name + "-top.png").toFile());
            Files.writeString(out.resolve(name + "-trace.csv"), trace);
            System.out.println(name + ": 1200 ticks, 3 fish, effective="
                    + Arrays.toString(engine.locomotion) + ", backstops=" + engine.backstopEngagements());
        }
    }
}
