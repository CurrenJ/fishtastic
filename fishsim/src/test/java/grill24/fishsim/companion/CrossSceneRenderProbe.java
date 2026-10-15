package grill24.fishsim.companion;

import grill24.fishsim.harness.companion.TwoOwnerRun;
import grill24.fishsim.render.CompanionRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic: headless frames of cross-player scenes (docs/fish-companions-party.md §28) with the
 * viewer's own renderer: owner A's first-person view, beside a map with both scene fish's tracks
 * so far (A's leader as the followed fish, B's as marks). Written to build/scene-render/. Run with
 * -PsimStdout.
 */
@EnabledIfSystemProperty(named = "simStdout", matches = "true")
class CrossSceneRenderProbe {

    @Test
    void frames() throws IOException {
        File out = new File("build/scene-render");
        out.mkdirs();
        render(out, "ring", "same species", "pair_approach_stand", 2);
        render(out, "eight", "two free swimmers", "pair_far", 1);
        render(out, "eight_close", "two free swimmers", "pair_approach_stand", 1);
    }

    private static void render(File out, String name, String pairName, String script, long seed) throws IOException {
        TwoOwnerRun.Pair pair = TwoOwnerRun.PAIRS.stream().filter(p -> p.name().equals(pairName)).findFirst().orElseThrow();
        TwoOwnerRun.Sim sim = new TwoOwnerRun.Sim(pair, script, seed, CompanionTunables.defaults(), Temperament.BASELINE)
                .autoScenes(true);
        ArrayDeque<Vec> trailA = new ArrayDeque<>();
        List<CompanionRenderer.Mark> trackB = new ArrayList<>();
        LocalVoxels view = new LocalVoxels();
        int shot = 0;
        for (int k = 0; k < 2400 && shot < 6; k++) {
            sim.step();
            CrossScene s = sim.a.scene();
            if (s == null || !s.active()) continue;
            CompanionAgent a = sim.a.sceneUnit().leader(), b = sim.b.sceneUnit().leader();
            trailA.addLast(a.position());
            if (s.ticks() % 3 == 0) trackB.add(new CompanionRenderer.Mark(b.position(), b.yaw(), 0.12f, ""));
            int every = s.endTicks() / 5;
            if (s.ticks() > 0 && s.ticks() % every == 0) {
                view.update(sim.a.script.scene, sim.a.owner().eye());
                CompanionWorld w = withB(sim);
                BufferedImage fp = CompanionRenderer.firstPerson(640, 360, sim.a.owner(), view, a, false, w);
                CompanionRenderer.drawAgents(fp, sim.a.owner(), view, sim.b.sceneUnit().agents());
                BufferedImage top = CompanionRenderer.topView(360, 360, sim.a.owner(), view, a,
                        CompanionTunables.defaults(), trailA, 40, w);
                List<CompanionRenderer.Mark> marks = new ArrayList<>(trackB);
                marks.add(new CompanionRenderer.Mark(b.position(), b.yaw(), b.length(), "B"));
                CompanionRenderer.drawTopMarks(top, sim.a.owner(), 40, marks);
                File f = new File(out, String.format("%s-%d-t%03d.png", name, shot++, s.ticks()));
                ImageIO.write(CompanionRenderer.composite(fp, top), "png", f);
                System.out.println("RENDER " + f.getPath() + "  A " + a.goal() + "  B " + b.goal()
                        + (s.greeting() ? "  greeting" : ""));
            }
        }
    }

    /** Owner A's view of the world, with owner B always drawn. */
    private static CompanionWorld withB(TwoOwnerRun.Sim sim) {
        CompanionWorld w = sim.a.seen(0);
        List<PointOfInterest> pts = new ArrayList<>(w.points());
        if (pts.stream().noneMatch(p -> p.kind() == PointOfInterest.Kind.PLAYER)) {
            OwnerState b = sim.b.owner();
            pts.add(new PointOfInterest(PointOfInterest.Kind.PLAYER, TwoOwnerRun.OWNER_B, b.feet(), b.velocity(), 0.3));
        }
        return new CompanionWorld(w.owner(), w.blocks(), pts, w.held(), w.hurt());
    }
}
