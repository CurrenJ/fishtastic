package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A ground walker's mood pitch must never take its nose or tail below the floor. */
class CompanionGroundHingeTest {
    private static final float HALF = 0.27f; // a 50 cm hellbender at render_calibration 1.07
    private static final float LIFT = 0.03f; // FloorSit default floor offset
    /** BEG, MILD, MOON_GAZE, WATCH, SLEEPY, DROOP, DIP and the dance extremes, degrees. */
    private static final float[] MOOD_PITCHES = {-15f, -5f, -28f, -12f, 23f, 22f, 32f, 30f, -30f};

    /** Heights above the floor of the nose (+z) and tail (−z) ends. */
    private static float[] ends(PoseStack poses) {
        Vector3f nose = poses.last().pose().transformPosition(new Vector3f(0, 0, HALF));
        Vector3f tail = poses.last().pose().transformPosition(new Vector3f(0, 0, -HALF));
        return new float[] {nose.y, tail.y};
    }

    private static PoseStack atCentre() {
        PoseStack poses = new PoseStack();
        poses.translate(0f, LIFT, 0f);
        return poses;
    }

    @Test
    void centrePivotSinksAnEndIntoTheFloor() {
        // The old behaviour, kept as the measured problem: DIP buries the nose ~0.11 blocks.
        PoseStack poses = atCentre();
        poses.mulPose(Axis.XP.rotationDegrees(32f));
        assertTrue(ends(poses)[0] < -0.1f);
    }

    @Test
    void hingeKeepsBothEndsOnOrAboveTheFloor() {
        for (float pitch : MOOD_PITCHES) {
            PoseStack poses = atCentre();
            CompanionRender.applyGroundHinge(poses, pitch, HALF, LIFT);
            float[] ends = ends(poses);
            assertTrue(ends[0] >= -1e-5f, "nose below floor at " + pitch + ": " + ends[0]);
            assertTrue(ends[1] >= -1e-5f, "tail below floor at " + pitch + ": " + ends[1]);
        }
    }

    @Test
    void noseUpLiftsTheHeadAndKeepsTheTailPlanted() {
        PoseStack poses = atCentre();
        CompanionRender.applyGroundHinge(poses, -28f, HALF, LIFT);
        float[] ends = ends(poses);
        assertTrue(ends[0] > LIFT + 0.2f, "head should lift: " + ends[0]);
        assertEquals(LIFT, ends[1], 1e-2f);
    }

    @Test
    void noseDownLeavesTheBodyFlat() {
        PoseStack poses = atCentre();
        CompanionRender.applyGroundHinge(poses, 32f, HALF, LIFT);
        float[] ends = ends(poses);
        assertEquals(LIFT, ends[0], 1e-6f);
        assertEquals(LIFT, ends[1], 1e-6f);
    }
}
