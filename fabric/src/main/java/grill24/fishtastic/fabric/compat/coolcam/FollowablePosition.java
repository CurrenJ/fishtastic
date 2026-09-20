package grill24.fishtastic.fabric.compat.coolcam;

import net.minecraft.world.phys.Vec3;

/**
 * A fishtastic-owned "thing with a live position" that can be handed to Cool Cam's camera-follow
 * API. Deliberately mirrors {@code grill24.coolcam.api.FollowTarget} without referencing it, so
 * this type can be used anywhere in fishtastic without a compile-time dependency on Cool Cam —
 * only {@link CoolCamFollowBridge} touches Cool Cam's classes directly.
 */
@FunctionalInterface
public interface FollowablePosition {
    Vec3 position(float partialTick);

    default boolean isValid() { return true; }
}
