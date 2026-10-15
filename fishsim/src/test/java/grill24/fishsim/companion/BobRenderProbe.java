package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.CompanionRun;
import grill24.fishsim.render.CompanionRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * Diagnostic: close-up stills of the man o' war (drift spec §6, §15) from a camera beside it, so its
 * pose (lean, rock, crest, tentacles) can be checked by eye. Swing viewer screenshots come out blank
 * on this machine. Written to build/bob-render/. Run with -PsimStdout.
 *
 * <pre>
 *   -Ptrace=walk  -PsweepSeeds=1 (the seed)  -PsweepTicks=600  -PtraceEvery=40
 * </pre>
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class BobRenderProbe {

    @Test
    void render() throws IOException {
        String script = System.getProperty("trace", "walk");
        long seed = Long.parseLong(System.getProperty("sweepSeeds", "1"));
        int ticks = Integer.parseInt(System.getProperty("sweepTicks", "600"));
        int every = Integer.parseInt(System.getProperty("traceEvery", "40"));
        File out = new File("build/bob-render");
        out.mkdirs();
        CompanionRun run = CompanionRun.drifter(script, seed, DriftStyle.BOB, Temperament.BASELINE,
                CompanionTunables.defaults(), true);
        LocalVoxels view = new LocalVoxels();
        for (int k = 0; k < ticks; k++) {
            run.run(1);
            if (k % every != 0) continue;
            CompanionAgent a = run.agent;
            a.interpolate(1f);
            // A camera 1.4 blocks off its right side, a little above, looking at it.
            Vec side = Vec.fromYawPitch(a.renderYaw() + 90, 0);
            Vec eye = a.renderPos().add(side.scale(1.4)).add(0, 0.35, 0);
            Vec look = a.renderPos().sub(eye);
            OwnerState cam = new OwnerState(eye, Vec.ZERO, (float) look.yawDeg(), (float) Math.toDegrees(Math.atan2(-look.y(), look.horizontalLength())),
                    false, false, true, 70f, false);
            view.update(run.world.blocks(), eye);
            BufferedImage img = CompanionRenderer.firstPerson(480, 320, cam, view, a, false);
            ImageIO.write(img, "png", new File(out, String.format("%s-t%03d.png", script, k)));
            System.out.printf("%s t%03d speed %.2f tilt %.1f bank %.1f squeeze %.2f flare %.2f%n", script, k,
                    a.velocity().horizontalLength(), a.renderPitch() + 90, a.renderBank(), a.renderSqueeze(), a.flare());
        }
    }
}
