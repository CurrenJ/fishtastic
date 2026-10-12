package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import grill24.fishtastic.data.FishAnimationConfig;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A flat-lying walker faces where it walks (owner playtest 2026-10-12: the plaice walked 45° off
 * its heading). The chain is the companion's: its yaw, then the floor pose with the companion's
 * nose-forward base, then the FIXED display of {@code item/generated} (a half-turn about Y), on
 * an item quad whose texture top-right is model (+x, +y).
 */
class FishAnimatorFloorHeadingTest {

    private static final float NOSE_FORWARD = 90f;

    /** Where a model-space direction on the sprite points on the floor, for a walker at yaw 0. */
    private static Vector3f onFloor(FishAnimationConfig.FloorSit cfg, Vector3f spriteDir, float ripplePhase) {
        PoseStack stack = new PoseStack();
        // A rotation amplitude of 0 keeps the idle sway out of the heading.
        FishAnimator.applyBenthic(stack, cfg, new Random(1), 0f, NOSE_FORWARD, NOSE_FORWARD, 0.3f, false, 0f, ripplePhase);
        stack.mulPose(Axis.YP.rotationDegrees(180f)); // FIXED display of item/generated
        Matrix4f m = stack.last().pose();
        Vector3f d = m.transformDirection(new Vector3f(spriteDir));
        return new Vector3f(d.x, 0f, d.z).normalize();
    }

    private static FishAnimationConfig.FloorSit flat(boolean diagonal, float ripple) {
        return new FishAnimationConfig.FloorSit(0.03f, 0f, 0.004f, 0.05f, diagonal, ripple);
    }

    /** The heading at yaw 0 in the companion frame: local +z (CompanionRender's "+z is the heading"). */
    private static final Vector3f HEADING = new Vector3f(0f, 0f, 1f);

    @Test
    void aDiagonalSpriteLeadsWithItsHead() {
        Vector3f head = onFloor(flat(true, 0f), new Vector3f(1f, 1f, 0f), Float.NaN);
        assertEquals(1f, head.dot(HEADING), 1e-4f, "head corner should point along the heading, was " + head);
    }

    @Test
    void withoutTheFlagTheSpriteWasFortyFiveDegreesOff() {
        // The bug, kept as a record: the uncorrected head corner sits 45° off the heading.
        Vector3f head = onFloor(flat(false, 0f), new Vector3f(1f, 1f, 0f), Float.NaN);
        assertEquals(Math.cos(Math.toRadians(45)), head.dot(HEADING), 1e-4f);
    }

    @Test
    void theRippleWagsAboutTheHeadingAndSettlesOnIt() {
        Vector3f peak = onFloor(flat(true, 6f), new Vector3f(1f, 1f, 0f), (float) (Math.PI / 2));
        double off = Math.toDegrees(Math.acos(Math.min(1f, peak.dot(HEADING))));
        assertEquals(6.0, off, 0.05, "the wag's peak");
        Vector3f zero = onFloor(flat(true, 6f), new Vector3f(1f, 1f, 0f), 0f);
        assertEquals(1f, zero.dot(HEADING), 1e-4f, "on the heading where the wag crosses zero");
        Vector3f none = onFloor(flat(true, 6f), new Vector3f(1f, 1f, 0f), Float.NaN);
        assertTrue(none.dot(HEADING) > 0.9999f, "no ripple clock, no wag");
    }
}
