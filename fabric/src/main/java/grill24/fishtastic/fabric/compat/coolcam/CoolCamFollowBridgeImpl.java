package grill24.fishtastic.fabric.compat.coolcam;

import net.minecraft.world.entity.Entity;

/**
 * The only class in fishtastic that references {@code grill24.coolcam.*} directly. Every call site
 * must go through {@link CoolCamFollowBridge}, which checks {@link CoolCamFollowBridge#isAvailable()}
 * before ever loading this class — loading it (which the JVM must fully verify, including the
 * anonymous {@code FollowTarget} below) is exactly what throws {@code NoClassDefFoundError} if Cool
 * Cam isn't installed. Keeping these methods out of {@link CoolCamFollowBridge} itself is the whole
 * point: that class's own bytecode must stay free of Cool Cam types so calling
 * {@code isAvailable()} never forces this class to resolve.
 */
final class CoolCamFollowBridgeImpl {
    private CoolCamFollowBridgeImpl() {}

    static void lookAt(FollowablePosition target) {
        grill24.coolcam.api.CameraFollowApi.lookAt(new grill24.coolcam.api.FollowTarget() {
            @Override
            public net.minecraft.world.phys.Vec3 position(float partialTick) {
                return target.position(partialTick);
            }

            @Override
            public boolean isValid() {
                return target.isValid();
            }
        });
    }

    static void lookAtEntity(Entity target) {
        grill24.coolcam.api.CameraFollowApi.lookAtEntity(target);
    }

    static void stopFollowing() {
        grill24.coolcam.api.CameraFollowApi.stopFollowing();
    }

    static boolean isFollowing() {
        return grill24.coolcam.api.CameraFollowApi.isFollowing();
    }
}
