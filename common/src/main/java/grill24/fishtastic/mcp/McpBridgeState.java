package grill24.fishtastic.mcp;

import net.minecraft.server.MinecraftServer;

import java.time.Instant;

/**
 * Single static holder for the MCP bridge's session state (dev-only tooling - see class docs on
 * {@link McpBridgeServer}). Intentionally in-memory only, not persisted {@code SavedData}: the region
 * and the running bridge share the same session-scoped lifetime, so a stale region can never silently
 * survive a restart.
 */
public final class McpBridgeState {
    private McpBridgeState() {}

    private static volatile McpBridgeServer activeServer;
    private static volatile MinecraftServer server;
    private static volatile String token;
    private static volatile int port;
    private static volatile Instant startedAt;
    private static volatile McpRegion region;
    private static volatile McpCameraOps.Pose cameraHome;

    public static synchronized void setActive(McpBridgeServer bridgeServer, MinecraftServer minecraftServer, String newToken, int boundPort) {
        activeServer = bridgeServer;
        server = minecraftServer;
        token = newToken;
        port = boundPort;
        startedAt = Instant.now();
    }

    public static synchronized void clearActive() {
        activeServer = null;
        server = null;
        token = null;
        port = 0;
        startedAt = null;
        cameraHome = null;
    }

    public static boolean isRunning() {
        return activeServer != null;
    }

    public static McpBridgeServer getActiveServer() {
        return activeServer;
    }

    public static MinecraftServer getServer() {
        return server;
    }

    public static String getToken() {
        return token;
    }

    public static int getPort() {
        return port;
    }

    public static Instant getStartedAt() {
        return startedAt;
    }

    public static void setRegion(McpRegion newRegion) {
        region = newRegion;
    }

    public static McpRegion getRegion() {
        return region;
    }

    /** The player's pose before {@code set_camera} first moved them this session, or null if it hasn't. */
    public static void setCameraHome(McpCameraOps.Pose pose) {
        cameraHome = pose;
    }

    public static McpCameraOps.Pose getCameraHome() {
        return cameraHome;
    }
}
