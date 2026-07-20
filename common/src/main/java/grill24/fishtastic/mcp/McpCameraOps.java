package grill24.fishtastic.mcp;

import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Set;

/**
 * Moves the singleplayer owner's viewpoint so the MCP bridge can screenshot a build from a chosen angle
 * without asking the human to walk there (see {@link grill24.fishtastic.mcp.client.McpScreenshotOps},
 * which always shoots from wherever the player currently is).
 *
 * <p>This is the one bridge op that moves the human's own character rather than world blocks, so it is
 * deliberately fenced in: it only reaches {@value #MAX_DISTANCE_FROM_REGION} blocks beyond the configured
 * sandbox region, and it snapshots the player's pre-move pose on the first call of a session so
 * {@code restore} can always put them back where they were standing.
 */
public final class McpCameraOps {
    private McpCameraOps() {}

    /** How far outside the sandbox region the camera may be placed. Enough to frame a region from a
     *  comfortable distance, not enough to turn this into a teleport-anywhere tool. */
    private static final int MAX_DISTANCE_FROM_REGION = 128;

    public static JsonObject setCamera(MinecraftServer server, Vec3 pos, Float yaw, Float pitch, Vec3 lookAt) {
        ServerPlayer player = requirePlayer(server);
        rememberHome(player);

        Vec3 target = pos != null ? pos : player.position();
        checkInBounds(server, target);

        float finalYaw;
        float finalPitch;
        if (lookAt != null) {
            // Aim from the eye, not the feet - otherwise every shot tilts down by the player's eye height.
            Vec3 eye = target.add(0.0, player.getEyeHeight(), 0.0);
            Vec3 delta = lookAt.subtract(eye);
            double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
            finalYaw = (float) (Math.toDegrees(Math.atan2(-delta.x, delta.z)));
            finalPitch = (float) (-Math.toDegrees(Math.atan2(delta.y, horizontal)));
        } else {
            finalYaw = yaw != null ? yaw : player.getYRot();
            finalPitch = pitch != null ? pitch : player.getXRot();
        }

        return apply(player, target, finalYaw, finalPitch);
    }

    public static JsonObject restore(MinecraftServer server) {
        ServerPlayer player = requirePlayer(server);
        Pose home = McpBridgeState.getCameraHome();
        if (home == null) {
            throw new McpException("No saved camera home - set_camera hasn't moved the player this session.");
        }
        JsonObject response = apply(player, home.pos(), home.yaw(), home.pitch());
        McpBridgeState.setCameraHome(null);
        response.addProperty("restored", true);
        return response;
    }

    // -------------------------------------------------------------------------

    private static JsonObject apply(ServerPlayer player, Vec3 pos, float yaw, float pitch) {
        // Set.of() = every coordinate absolute (the same shape /tp uses); resetCamera false so we don't
        // yank a player who is spectating something else.
        player.teleportTo((ServerLevel) player.level(), pos.x, pos.y, pos.z, Set.<Relative>of(), yaw, pitch, false);

        // Without this a creative player placed mid-air immediately starts falling, and the screenshot
        // taken a moment later is from several blocks lower than requested.
        if (player.getAbilities().mayfly && !player.getAbilities().flying) {
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }

        JsonObject response = new JsonObject();
        response.addProperty("x", pos.x);
        response.addProperty("y", pos.y);
        response.addProperty("z", pos.z);
        response.addProperty("yaw", yaw);
        response.addProperty("pitch", pitch);
        return response;
    }

    private static ServerPlayer requirePlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new McpException("No player online to move.");
        }
        return players.get(0);
    }

    /** Snapshot the player's own pose the first time we move them, so restore() is always available. */
    private static void rememberHome(ServerPlayer player) {
        if (McpBridgeState.getCameraHome() == null) {
            McpBridgeState.setCameraHome(new Pose(player.position(), player.getYRot(), player.getXRot()));
        }
    }

    private static void checkInBounds(MinecraftServer server, Vec3 target) {
        McpRegion region = McpBridgeState.getRegion();
        if (region == null) {
            throw new McpException("No MCP region configured - run /fishtastic mcp region set <from> <to> first.");
        }
        ServerPlayer player = requirePlayer(server);
        if (!region.dimension().equals(player.level().dimension())) {
            throw new McpException("Player is not in the region's dimension (" + region.dimension().identifier() + ").");
        }
        BlockPos min = region.min();
        BlockPos max = region.max();
        double dx = axisDistance(target.x, min.getX(), max.getX() + 1);
        double dy = axisDistance(target.y, min.getY(), max.getY() + 1);
        double dz = axisDistance(target.z, min.getZ(), max.getZ() + 1);
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) > MAX_DISTANCE_FROM_REGION) {
            throw new McpException("Camera position is more than " + MAX_DISTANCE_FROM_REGION
                    + " blocks from the sandbox region - set_camera only frames the region, it isn't a general teleport.");
        }
    }

    private static double axisDistance(double value, double min, double max) {
        if (value < min) {
            return min - value;
        }
        if (value > max) {
            return value - max;
        }
        return 0.0;
    }

    /** The player's own pose before the bridge first moved them. */
    public record Pose(Vec3 pos, float yaw, float pitch) {}
}
