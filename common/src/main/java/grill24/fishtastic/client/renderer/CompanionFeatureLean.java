package grill24.fishtastic.client.renderer;

import grill24.fishsim.companion.Vec;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/** Projects attention into the actual perch frame, including head/body rotations and flips. */
public final class CompanionFeatureLean {
    private CompanionFeatureLean() {}
    public record Lean(float pitch, float roll) {}
    public static Lean toward(Matrix4fc localToWorld, Vec direction) {
        if (direction.lengthSqr() < 1e-8) return new Lean(0, 0);
        var local = new Matrix4f(localToWorld).invert().transformDirection(
                new Vector3f((float) direction.x(), (float) direction.y(), (float) direction.z()));
        if (!Float.isFinite(local.lengthSquared()) || local.lengthSquared() < 1e-8) return new Lean(0, 0);
        local.normalize();
        // Rotating +Y about +Z moves it toward -X; about +X moves it toward +Z.
        return new Lean(local.z * 18f, -local.x * 18f);
    }
}
