package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.render.CompanionRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayDeque;

/** Visual: top views with long trails, tiled, to judge path shape. Run with -PsimStdout. */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class TrailSnapshotProbe {
    @Test
    void snapshots() throws Exception {
        String[] scripts = {"idle_fixed", "idle_looking", "walk", "walk_turns", "doorway", "forest"};
        int w = 360, h = 360;
        BufferedImage sheet = new BufferedImage(w * 3, h * 2, BufferedImage.TYPE_INT_RGB);
        var g = sheet.createGraphics();
        for (int k = 0; k < scripts.length; k++) {
            CompanionRun run = new CompanionRun(scripts[k], 2, CompanionTunables.defaults(), false);
            ArrayDeque<Vec> trail = new ArrayDeque<>();
            for (int i = 0; i < 900; i++) {
                run.tick();
                trail.addLast(run.agent.position());
                if (trail.size() > 400) trail.removeFirst();
            }
            run.agent.interpolate(1f);
            BufferedImage img = CompanionRenderer.topView(w, h, run.owner, run.scene, run.agent, run.tunables, trail, 26);
            g.drawImage(img, (k % 3) * w, (k / 3) * h, null);
            g.setColor(java.awt.Color.BLACK);
            g.drawString(scripts[k] + " (last 20 s)", (k % 3) * w + 6, (k / 3) * h + 14);
        }
        g.dispose();
        File out = new File("build/companion-export/trails.png");
        out.getParentFile().mkdirs();
        ImageIO.write(sheet, "png", out);
        System.out.println("wrote " + out.getAbsolutePath());
    }
}
