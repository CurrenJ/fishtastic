package grill24.fishtastic.fabric.compat.coolcam;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

/**
 * Isolates every direct reference to Cool Cam's API (an optional, Fabric-only dependency — see
 * {@code fabric/build.gradle}) behind {@link #isAvailable()}. Nothing outside this class ever
 * imports {@code grill24.coolcam.*}, so the rest of fishtastic loads fine whether or not Cool Cam
 * is installed; only calling into this class when Cool Cam is missing (which every method here
 * guards against) would risk a {@code NoClassDefFoundError}.
 */
public final class CoolCamFollowBridge {
    private static final String MOD_ID = "coolcam";

    private CoolCamFollowBridge() {}

    public static boolean isAvailable() {
        return FabricLoader.getInstance().isModLoaded(MOD_ID);
    }

    /** Points the camera at any live-position source (e.g. a simulated fish) without moving it. */
    public static void lookAt(FollowablePosition target) {
        if (!isAvailable()) return;
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

    /** Points the camera at a real entity without moving it. */
    public static void lookAtEntity(Entity target) {
        if (!isAvailable()) return;
        grill24.coolcam.api.CameraFollowApi.lookAtEntity(target);
    }

    public static void stopFollowing() {
        if (!isAvailable()) return;
        grill24.coolcam.api.CameraFollowApi.stopFollowing();
    }

    public static boolean isFollowing() {
        return isAvailable() && grill24.coolcam.api.CameraFollowApi.isFollowing();
    }
}
