package grill24.fishtastic.fabric.compat.coolcam;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

/**
 * Isolates every direct reference to Cool Cam's API (an optional, Fabric-only dependency — see
 * {@code fabric/build.gradle}) behind {@link #isAvailable()}. This class itself must never
 * reference {@code grill24.coolcam.*} — the JVM verifies an entire class file on first active use,
 * not per-method, so if a Cool-Cam-referencing method lived here, merely calling
 * {@link #isAvailable()} would force that method's bytecode to be verified too, which resolves
 * Cool Cam's types and throws {@code NoClassDefFoundError} when the jar isn't installed (this
 * happened in practice — see git history). All actual Cool Cam API calls live in
 * {@link CoolCamFollowBridgeImpl}, which is only ever loaded after a caller has confirmed
 * {@link #isAvailable()}.
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
        CoolCamFollowBridgeImpl.lookAt(target);
    }

    /** Points the camera at a real entity without moving it. */
    public static void lookAtEntity(Entity target) {
        if (!isAvailable()) return;
        CoolCamFollowBridgeImpl.lookAtEntity(target);
    }

    public static void stopFollowing() {
        if (!isAvailable()) return;
        CoolCamFollowBridgeImpl.stopFollowing();
    }

    public static boolean isFollowing() {
        return isAvailable() && CoolCamFollowBridgeImpl.isFollowing();
    }
}
