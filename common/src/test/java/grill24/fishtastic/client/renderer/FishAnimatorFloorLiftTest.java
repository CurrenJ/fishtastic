package grill24.fishtastic.client.renderer;

import grill24.fishtastic.data.FishAnimationConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How far a floor-dwelling pose sits above the sand the engine reports under it.
 *
 * <p>Regression: when crawlers began rendering in group space, the group draw loop translated by
 * the engine's floor height alone, while the single-tank loop added the centre-pivot compensation
 * through {@code computeBaseY}. Upright creatures — nudibranchs, crabs — came out buried to their
 * midpoints in a group and correct in a lone tank. The lift is shared code now, and these are the
 * numbers both callers depend on.
 */
class FishAnimatorFloorLiftTest {

    private static final float EPSILON = 1e-6f;

    private static FishAnimationConfig.UprightSit upright(float floorOffset, float pivotFraction) {
        return new FishAnimationConfig.UprightSit(floorOffset, 8f, 0.004f, true, pivotFraction);
    }

    /**
     * An upright pose is pivoted about the item's centre, so standing it on the sand means lifting
     * it by the distance from that centre to the art's lowest visible pixel. Dropping the term
     * buries the creature; assuming it is always half the item floats art that doesn't reach the
     * bottom of its canvas.
     */
    @Test
    void anUprightPoseIsLiftedByItsMeasuredPivot() {
        for (float scale : new float[]{0.05f, 0.1f, 0.25f, 0.5f}) {
            assertEquals(FishAnimator.PLANTED_PIVOT_Y * scale,
                    FishAnimator.floorPoseLift(upright(0f, FishAnimator.PLANTED_PIVOT_Y), scale), EPSILON,
                    "full-canvas art at scale " + scale);
            // trapania_scurra's measured value: its art fills half the canvas vertically, so it
            // needs well under half the item — assuming the half floated it by the difference,
            // which on a creature that short is most of its visible height.
            assertEquals(0.1875f * scale, FishAnimator.floorPoseLift(upright(0f, 0.1875f), scale), EPSILON,
                    "half-canvas art at scale " + scale);
        }
    }

    /** Art that stops short of its canvas bottom is lifted less, never more. */
    @Test
    void shorterArtIsLiftedLess() {
        assertTrue(FishAnimator.floorPoseLift(upright(0f, 0.1875f), 0.3f)
                        < FishAnimator.floorPoseLift(upright(0f, 0.4062f), 0.3f),
                "art filling less of its canvas must sit lower");
    }

    /** An unmeasured species falls back to half the item — the pre-measurement behaviour. */
    @Test
    void anUnmeasuredSpeciesFallsBackToHalfTheItem() {
        assertEquals(FishAnimator.PLANTED_PIVOT_Y, FishAnimationConfig.UprightSit.DEFAULT_PIVOT_FRACTION, EPSILON);
        assertEquals(FishAnimator.PLANTED_PIVOT_Y * 0.3f,
                FishAnimator.floorPoseLift(FishAnimationConfig.UprightSit.DEFAULT, 0.3f), EPSILON);
    }

    /**
     * And it scales with the catch. A fixed lift would float small catches and sink large ones —
     * the bug this compensation was written for in the first place.
     */
    @Test
    void theUprightLiftScalesWithTheCatch() {
        FishAnimationConfig.UprightSit pose = upright(0f, 0.4062f);
        float small = FishAnimator.floorPoseLift(pose, 0.08f);
        float large = FishAnimator.floorPoseLift(pose, 0.40f);
        assertTrue(large > small, "a bigger catch must be lifted further");
        assertEquals(5f, large / small, 1e-4f, "the lift is proportional to the render scale");
    }

    /** A pose that lies flat on the sand is already resting on it and needs no lift. */
    @Test
    void aFlatPoseNeedsNoLift() {
        assertEquals(0f, FishAnimator.floorPoseLift(new FishAnimationConfig.FloorSit(0f, 8f, 0.004f), 0.3f),
                EPSILON);
    }

    /** The config's own manual nudge is carried on top, for both floor poses. */
    @Test
    void theConfigsOwnNudgeIsCarried() {
        assertEquals(0.02f, FishAnimator.floorPoseLift(new FishAnimationConfig.FloorSit(0.02f, 8f, 0.004f), 0.3f),
                EPSILON);
        assertEquals(0.02f + 0.4062f * 0.3f, FishAnimator.floorPoseLift(upright(0.02f, 0.4062f), 0.3f),
                EPSILON);
    }

    /** Poses the engine does not walk contribute nothing — they position themselves. */
    @Test
    void nonCrawlingPosesGetNoLift() {
        assertEquals(0f, FishAnimator.floorPoseLift(FishAnimationConfig.HorizontalSwim.DEFAULT, 0.3f), EPSILON);
        assertEquals(0f, FishAnimator.floorPoseLift(FishAnimationConfig.UprightFloat.DEFAULT, 0.3f), EPSILON);
        assertEquals(0f, FishAnimator.floorPoseLift(FishAnimationConfig.BellyDown.DEFAULT, 0.3f), EPSILON);
        // Planted is anchored rather than walked: its own Y is still pinned by the renderer, so a
        // lift here would double-count. Phase 4 is where that changes.
        assertEquals(0f, FishAnimator.floorPoseLift(FishAnimationConfig.Planted.DEFAULT, 0.3f), EPSILON);
    }
}
