package grill24.fishtastic.mcp;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Read-only orientation info for the MCP bridge - player pose, active region, world time. */
public final class McpContextOps {
    private McpContextOps() {}

    public static JsonObject getContext(MinecraftServer server) {
        JsonObject json = new JsonObject();

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (!players.isEmpty()) {
            ServerPlayer player = players.get(0);
            JsonObject playerJson = new JsonObject();
            playerJson.addProperty("x", player.getX());
            playerJson.addProperty("y", player.getY());
            playerJson.addProperty("z", player.getZ());
            playerJson.addProperty("yaw", player.getYRot());
            playerJson.addProperty("pitch", player.getXRot());
            playerJson.addProperty("dimension", player.level().dimension().identifier().toString());
            playerJson.addProperty("gamemode", player.gameMode.getGameModeForPlayer().name());
            json.add("player", playerJson);
        } else {
            json.add("player", JsonNull.INSTANCE);
        }

        McpRegion region = McpBridgeState.getRegion();
        if (region != null) {
            JsonObject regionJson = new JsonObject();
            regionJson.addProperty("min", region.min().toShortString());
            regionJson.addProperty("max", region.max().toShortString());
            regionJson.addProperty("dimension", region.dimension().identifier().toString());
            json.add("region", regionJson);
        } else {
            json.add("region", JsonNull.INSTANCE);
        }

        long gameTime = server.overworld().getGameTime();
        json.addProperty("gameTime", gameTime);
        json.addProperty("dayTime", gameTime % 24000L);

        return json;
    }
}
