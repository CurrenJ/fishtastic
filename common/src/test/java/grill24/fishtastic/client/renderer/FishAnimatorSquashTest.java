package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.data.FishAnimationConfig;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Squash-and-stretch on the locomotion drive (docs/fish-sim-locomotion.md §3.6) — the pose half,
 * where the engine's envelope becomes an actual deformation.
 *
 * <p>Two things here are easy to get wrong and invisible in review. The first is the <b>frame</b>:
 * these poses end with a −45° roll that stands a diagonally-painted texture upright, and a scale
 * written on the wrong side of it deforms along the canvas's diagonal instead of along the
 * creature — a squeeze that comes out slanted. The second is the <b>pivot</b>: a crawler scaled
 * about the item's centre sinks into the sand on the squash and floats above it on the stretch,
 * which is the Phase 1 floor-lift bug in a different guise.
 */
class FishAnimatorSquashTest {

    private static final float EPSILON = 1e-4f;
    /** The per-fish render scale the caller applies after the animator returns. */
    private static final float SCALE = 0.3f;

    private static FishAnimationConfig.UprightSit upright(boolean diagonal, float pivotFraction,
                                                          float squash) {
        return new FishAnimationConfig.UprightSit(0f, 8f, 0.004f, diagonal, pivotFraction, squash);
    }

    /**
     * The full model-space → tank-space transform for one crawler, including the uniform render
     * scale the renderer applies after the animator — the item's own geometry spans ±0.5 in model
     * units, so nothing about the pivot can be checked without it.
     */
    private static Matrix4f benthicPose(FishAnimationConfig config, float shapeDrive) {
        PoseStack poseStack = new PoseStack();
        // A fixed seed, because the pose's idle sway draws from it: two calls that differ only in
        // the shape drive have to differ only in the deformation.
        FishAnimator.applyBenthic(poseStack, config, new Random(7L), 0f, 0f, 0f, SCALE, false,
                shapeDrive);
        poseStack.scale(SCALE, SCALE, SCALE);
        return new Matrix4f(poseStack.last().pose());
    }

    private static Matrix4f driftPose(FishAnimationConfig config, float shapeDrive) {
        PoseStack poseStack = new PoseStack();
        FishAnimator.applyDrifting(poseStack, config, new Random(7L), 0f, 0f, SCALE, false, shapeDrive);
        poseStack.scale(SCALE, SCALE, SCALE);
        return new Matrix4f(poseStack.last().pose());
    }

    /** A model-space direction in the creature's <i>upright</i> frame, undoing the texture roll. */
    private static Vector3f bodyAxis(boolean diagonal, float x, float y) {
        if (!diagonal) return new Vector3f(x, y, 0f);
        float c = (float) Math.cos(Math.toRadians(45));
        float s = (float) Math.sin(Math.toRadians(45));
        return new Vector3f(x * c - y * s, x * s + y * c, 0f);
    }

    private static float lengthOfDirection(Matrix4f m, Vector3f dir) {
        return m.transformDirection(new Vector3f(dir)).length();
    }

    /**
     * The deformation is vertical <i>for the creature</i>, in both texture conventions. Written on
     * the wrong side of the roll this test still sees a deformation — of the same magnitude, even
     * — but pointed 45° off, which is why it measures the upright frame's axes rather than the
     * amount of change.
     */
    @Test
    void anUprightCreatureDeformsAlongItsOwnAxes() {
        for (boolean diagonal : new boolean[]{true, false}) {
            FishAnimationConfig.UprightSit cfg = upright(diagonal, 0.4f, 0.2f);
            Matrix4f relaxed = benthicPose(cfg, 0f);
            Matrix4f crouched = benthicPose(cfg, 1f);

            float sy = 1f - cfg.scuttleSquash();
            float horizontal = (float) (1.0 / Math.sqrt(sy));

            assertEquals(sy,
                    lengthOfDirection(crouched, bodyAxis(diagonal, 0f, 1f))
                            / lengthOfDirection(relaxed, bodyAxis(diagonal, 0f, 1f)),
                    EPSILON, "vertical factor, diagonal=" + diagonal);
            assertEquals(horizontal,
                    lengthOfDirection(crouched, bodyAxis(diagonal, 1f, 0f))
                            / lengthOfDirection(relaxed, bodyAxis(diagonal, 1f, 0f)),
                    EPSILON, "in-plane horizontal factor, diagonal=" + diagonal);
            assertEquals(horizontal,
                    lengthOfDirection(crouched, new Vector3f(0f, 0f, 1f))
                            / lengthOfDirection(relaxed, new Vector3f(0f, 0f, 1f)),
                    EPSILON, "across-plane horizontal factor, diagonal=" + diagonal);
        }
    }

    /**
     * The one that would have caught the floor-lift bug in its new guise: the point the creature
     * stands on does not move through the whole envelope, at any measured pivot. Scaling about the
     * item's centre instead passes every other assertion here and buries the crab.
     */
    @Test
    void aCrawlersContactPointHoldsStillThroughTheWholeSquash() {
        for (float pivotFraction : new float[]{0.19f, 0.34f, 0.5f}) {
            FishAnimationConfig.UprightSit cfg = upright(true, pivotFraction, 0.2f);
            Vector3f contact = bodyAxis(true, 0f, -pivotFraction);
            Vector3f relaxed = benthicPose(cfg, 0f).transformPosition(new Vector3f(contact));
            for (float drive = 0f; drive <= 1f; drive += 0.1f) {
                Vector3f moved = benthicPose(cfg, drive).transformPosition(new Vector3f(contact));
                assertEquals(relaxed.y, moved.y, EPSILON,
                        "the contact point moved at drive " + drive + ", pivot " + pivotFraction);
            }
        }
    }

    /**
     * Volume is held to within a hair across the whole envelope, for every class that deforms. It
     * is what makes the effect read as flexing rather than as the animal changing size — which
     * matters more here than in most games, since {@code render_calibration} exists precisely to
     * draw these species true-to-scale.
     */
    @Test
    void everyDeformationPreservesVolume() {
        FishAnimationConfig.UprightSit crawler = upright(true, 0.34f, 0.2f);
        FishAnimationConfig.UprightFloat bell =
                new FishAnimationConfig.UprightFloat(0.05f, 0.03f, 0.4f, true, 0.3f);
        FishAnimationConfig.FloorSit flat = new FishAnimationConfig.FloorSit(0.03f, 8f, 0.004f, 0.2f);

        float crawlerBase = benthicPose(crawler, 0f).determinant();
        float bellBase = driftPose(bell, 0f).determinant();
        float flatBase = benthicPose(flat, 0f).determinant();
        for (float drive = 0f; drive <= 1f; drive += 0.1f) {
            assertEquals(1f, benthicPose(crawler, drive).determinant() / crawlerBase, 1e-3f,
                    "an upright crawler changed volume at drive " + drive);
            assertEquals(1f, driftPose(bell, drive).determinant() / bellBase, 1e-3f,
                    "a bell changed volume at drive " + drive);
            assertEquals(1f, benthicPose(flat, drive).determinant() / flatBase, 1e-3f,
                    "a flat crawler changed area at drive " + drive);
        }
    }

    /**
     * A flat-lying creature deforms in its own plane, not through its own zero thickness. The
     * vertical squash that is right for an upright crawler shows literally nothing here, so this
     * asserts the plane it actually happens in.
     */
    @Test
    void aFlatCreatureDeformsInThePlaneItLiesIn() {
        FishAnimationConfig.FloorSit cfg = new FishAnimationConfig.FloorSit(0.03f, 8f, 0.004f, 0.2f);
        Matrix4f relaxed = benthicPose(cfg, 0f);
        Matrix4f pushed = benthicPose(cfg, 1f);

        float along = 1f - cfg.scuttleSquash();
        assertEquals(along,
                lengthOfDirection(pushed, new Vector3f(1f, 0f, 0f))
                        / lengthOfDirection(relaxed, new Vector3f(1f, 0f, 0f)),
                EPSILON, "along the body");
        assertEquals(1f / along,
                lengthOfDirection(pushed, new Vector3f(0f, 1f, 0f))
                        / lengthOfDirection(relaxed, new Vector3f(0f, 1f, 0f)),
                EPSILON, "across the body");
        assertEquals(1f,
                lengthOfDirection(pushed, new Vector3f(0f, 0f, 1f))
                        / lengthOfDirection(relaxed, new Vector3f(0f, 0f, 1f)),
                EPSILON, "through the sprite, where nothing should happen");
    }

    /**
     * The amplitude a data file authors is the amplitude the creature gets, and a species that
     * opts out with zero is bit-identical to one drawn with no drive at all — which is what lets
     * this ship on defaults without touching any of the {@code fish_profile} files.
     */
    @Test
    void theConfiguredAmplitudeBoundsTheDeformation() {
        FishAnimationConfig.UprightSit off = upright(true, 0.34f, 0f);
        assertEquals(benthicPose(off, 0f), benthicPose(off, 1f),
                "a species that opted out still deformed");

        for (float squash : new float[]{0.02f, 0.05f, 0.2f}) {
            FishAnimationConfig.UprightSit cfg = upright(true, 0.34f, squash);
            Matrix4f relaxed = benthicPose(cfg, 0f);
            Vector3f up = bodyAxis(true, 0f, 1f);
            float base = lengthOfDirection(relaxed, up);
            for (float drive = 0f; drive <= 1f; drive += 0.1f) {
                float factor = lengthOfDirection(benthicPose(cfg, drive), up) / base;
                assertTrue(factor <= 1f + EPSILON && factor >= 1f - squash - EPSILON,
                        "deformation " + factor + " left the configured bound at squash " + squash);
            }
        }
    }
}
