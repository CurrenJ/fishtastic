package grill24.fishtastic.mcp;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Embedded local HTTP bridge that lets a companion Node.js MCP shim (spawned by an MCP client such as
 * Claude Code, see {@code tools/mcp-bridge/}) place and inspect blocks in the running singleplayer world.
 * Dev-only, off by default, started explicitly via {@code /fishtastic mcp start} (see
 * {@link McpBridgeCommand}) - never reachable by a normal player who didn't ask for it.
 *
 * <p>Binds loopback-only and requires a shared-secret token (see {@link McpTokenAuth}) on every request.
 * Mutating endpoints ({@code place_block}/{@code fill_blocks}) are further restricted to a configured
 * {@link McpRegion}, as is {@code set_camera} - the one endpoint that moves the player rather than the
 * world (see {@link McpCameraOps}). World/command work is marshaled onto the owning server thread via
 * {@code MinecraftServer.submit(...)} (a {@code BlockableEventLoop}, the same mechanism vanilla uses
 * internally) - the HTTP handler thread blocks on the returned future with a short timeout rather than
 * touching world state directly.
 */
public final class McpBridgeServer {
    private static final Gson GSON = new Gson();
    private static final long TIMEOUT_SECONDS = 5;
    // Screenshot waits on the player closing whatever screen is open (see McpScreenshotOps), which is
    // unbounded on human timescales, unlike the other ops - give it its own, much longer allowance.
    private static final long SCREENSHOT_TIMEOUT_SECONDS = 120;

    private final HttpServer httpServer;
    private final ExecutorService executor;

    private McpBridgeServer(HttpServer httpServer, ExecutorService executor) {
        this.httpServer = httpServer;
        this.executor = executor;
    }

    public static McpBridgeServer start(MinecraftServer server, int port) throws IOException {
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        // Named + daemon so an abandoned pool is identifiable in a thread dump rather than showing up as
        // an anonymous "pool-N", and so a stuck handler can never hold the JVM open on its own.
        ExecutorService handlerPool = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "fishtastic-mcp-http");
            thread.setDaemon(true);
            return thread;
        });
        http.setExecutor(handlerPool);

        McpBridgeServer bridge = new McpBridgeServer(http, handlerPool);
        String token = UUID.randomUUID().toString().replace("-", "");

        http.createContext("/place_block", McpTokenAuth.wrap(exchange ->
                safeHandle(exchange, () -> bridge.handlePlaceBlock(server, exchange))));
        http.createContext("/fill_blocks", McpTokenAuth.wrap(exchange ->
                safeHandle(exchange, () -> bridge.handleFillBlocks(server, exchange))));
        http.createContext("/get_block", McpTokenAuth.wrap(exchange ->
                safeHandle(exchange, () -> bridge.handleGetBlock(server, exchange))));
        http.createContext("/get_context", McpTokenAuth.wrap(exchange ->
                safeHandle(exchange, () -> bridge.handleGetContext(server, exchange))));
        http.createContext("/run_command", McpTokenAuth.wrap(exchange ->
                safeHandle(exchange, () -> bridge.handleRunCommand(server, exchange))));
        http.createContext("/screenshot", McpTokenAuth.wrap(exchange ->
                safeHandle(exchange, () -> bridge.handleScreenshot(exchange))));
        http.createContext("/set_camera", McpTokenAuth.wrap(exchange ->
                safeHandle(exchange, () -> bridge.handleSetCamera(server, exchange))));
        http.createContext("/orbit_screenshot", McpTokenAuth.wrap(exchange ->
                safeHandle(exchange, () -> bridge.handleOrbitScreenshot(server, exchange))));

        http.start();
        McpBridgeState.setActive(bridge, server, token, port);
        return bridge;
    }

    public void stop() {
        httpServer.stop(0);
        // HttpServer.stop() does NOT shut down an executor supplied via setExecutor - without this the
        // pool and its threads outlive every start/stop cycle, which (being process-scoped) survives
        // quitting to title and only clears on a full game restart.
        executor.shutdownNow();
        McpBridgeState.clearActive();
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private void handlePlaceBlock(MinecraftServer server, HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        BlockPos pos = readPos(body);
        String blockState = requireString(body, "block_state");
        String dimension = optString(body, "dimension", "minecraft:overworld");

        runOnServerThread(exchange, server, () -> {
            ServerLevel level = resolveLevel(server, dimension);
            JsonObject response = new JsonObject();
            response.addProperty("block", McpBlockOps.placeBlock(level, pos, blockState));
            return response;
        });
    }

    private void handleFillBlocks(MinecraftServer server, HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        BlockPos from = readPos(body.getAsJsonObject("from"));
        BlockPos to = readPos(body.getAsJsonObject("to"));
        String blockState = requireString(body, "block_state");
        String mode = optString(body, "mode", "replace");
        String dimension = optString(body, "dimension", "minecraft:overworld");

        runOnServerThread(exchange, server, () -> {
            ServerLevel level = resolveLevel(server, dimension);
            JsonObject response = new JsonObject();
            response.addProperty("placed", McpBlockOps.fillBlocks(level, from, to, blockState, mode));
            return response;
        });
    }

    private void handleGetBlock(MinecraftServer server, HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        BlockPos pos = readPos(body);
        String dimension = optString(body, "dimension", "minecraft:overworld");

        runOnServerThread(exchange, server, () -> {
            ServerLevel level = resolveLevel(server, dimension);
            JsonObject response = new JsonObject();
            response.addProperty("block", McpBlockOps.getBlock(level, pos));
            return response;
        });
    }

    private void handleGetContext(MinecraftServer server, HttpExchange exchange) throws IOException {
        runOnServerThread(exchange, server, () -> McpContextOps.getContext(server));
    }

    private void handleRunCommand(MinecraftServer server, HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        String command = requireString(body, "command");

        runOnServerThread(exchange, server, () -> {
            JsonObject response = new JsonObject();
            response.addProperty("feedback", McpCommandRunner.run(server, command));
            return response;
        });
    }

    private void handleSetCamera(MinecraftServer server, HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);

        if (body.has("restore") && body.get("restore").getAsBoolean()) {
            runOnServerThread(exchange, server, () -> McpCameraOps.restore(server));
            return;
        }

        Vec3 pos = optVec(body, "x", "y", "z");
        Vec3 lookAt = body.has("look_at")
                ? readVec(body.getAsJsonObject("look_at"), "x", "y", "z")
                : null;
        Float yaw = optFloat(body, "yaw");
        Float pitch = optFloat(body, "pitch");

        runOnServerThread(exchange, server, () -> McpCameraOps.setCamera(server, pos, yaw, pitch, lookAt));
    }

    /**
     * Unlike every other handler this one deliberately does <em>not</em> go through
     * {@link #runOnServerThread} - it is a long, multi-step sequence that has to interleave server-thread
     * teleports with client-thread frame grabs, so it orchestrates from the HTTP handler thread and
     * marshals each individual step itself (see {@link grill24.fishtastic.mcp.client.McpOrbitOps}).
     * Occupying the server thread for the whole orbit would freeze the very ticking it depends on.
     */
    private void handleOrbitScreenshot(MinecraftServer server, HttpExchange exchange) throws IOException {
        JsonObject body = readJson(exchange);
        Vec3 center = readVec(body.getAsJsonObject("center"), "x", "y", "z");
        double radius = body.has("radius") ? body.get("radius").getAsDouble() : 12.0;
        double height = body.has("height") ? body.get("height").getAsDouble() : center.y + 5.0;
        int shots = body.has("shots") ? body.get("shots").getAsInt() : 8;
        long settleMillis = body.has("settle_ms") ? body.get("settle_ms").getAsLong() : 400L;

        try {
            grill24.fishtastic.mcp.client.McpOrbitOps.Result result =
                    grill24.fishtastic.mcp.client.McpOrbitOps.orbit(server, center, radius, height, shots, settleMillis);

            JsonObject response = new JsonObject();
            response.addProperty("path", result.path());
            response.addProperty("layout", "Row-major, " + Math.min(4, shots) + " per row.");
            JsonArray labels = new JsonArray();
            result.angleLabels().forEach(labels::add);
            response.add("tile_angles", labels);
            sendJson(exchange, 200, GSON.toJson(response));
        } catch (McpException e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        }
    }

    private void handleScreenshot(HttpExchange exchange) throws IOException {
        try {
            String path = grill24.fishtastic.mcp.client.McpScreenshotOps.takeScreenshot().get(SCREENSHOT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            JsonObject response = new JsonObject();
            response.addProperty("path", path);
            sendJson(exchange, 200, GSON.toJson(response));
        } catch (TimeoutException e) {
            sendJson(exchange, 504, errorJson("Screenshot timed out - is the game window focused, and was an open screen (menu/inventory/chat) closed within 2 minutes?"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendJson(exchange, 500, errorJson("Interrupted."));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof McpException mcpEx) {
                sendJson(exchange, 400, errorJson(mcpEx.getMessage()));
            } else {
                sendJson(exchange, 500, errorJson(String.valueOf(cause)));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private interface ServerOp {
        JsonElement run();
    }

    private interface ExchangeOp {
        void run() throws IOException;
    }

    /** Top-level safety net so no handler ever lets an exception escape without a JSON response
     *  (JSON-body parsing errors, missing fields, etc. - anything happening before/outside
     *  {@link #runOnServerThread}, which has its own future-specific handling). */
    private static void safeHandle(HttpExchange exchange, ExchangeOp op) throws IOException {
        try {
            op.run();
        } catch (McpException e) {
            sendJson(exchange, 400, errorJson(e.getMessage()));
        } catch (Exception e) {
            sendJson(exchange, 500, errorJson(String.valueOf(e)));
        }
    }

    private void runOnServerThread(HttpExchange exchange, MinecraftServer server, ServerOp op) throws IOException {
        CompletableFuture<JsonElement> future = server.submit(op::run);
        try {
            JsonElement result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            sendJson(exchange, 200, GSON.toJson(result));
        } catch (TimeoutException e) {
            sendJson(exchange, 504, errorJson("Operation timed out - is the game window focused?"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendJson(exchange, 500, errorJson("Interrupted."));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof McpException mcpEx) {
                sendJson(exchange, 400, errorJson(mcpEx.getMessage()));
            } else {
                sendJson(exchange, 500, errorJson(String.valueOf(cause)));
            }
        }
    }

    private static ServerLevel resolveLevel(MinecraftServer server, String dimensionId) {
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimensionId));
        ServerLevel level = server.getLevel(key);
        if (level == null) {
            throw new McpException("Unknown dimension: " + dimensionId);
        }
        return level;
    }

    private static BlockPos readPos(JsonObject obj) {
        return new BlockPos(obj.get("x").getAsInt(), obj.get("y").getAsInt(), obj.get("z").getAsInt());
    }

    private static Vec3 readVec(JsonObject obj, String xKey, String yKey, String zKey) {
        return new Vec3(obj.get(xKey).getAsDouble(), obj.get(yKey).getAsDouble(), obj.get(zKey).getAsDouble());
    }

    /** All-or-nothing: a partially specified position is a caller mistake, not a defaulting opportunity. */
    private static Vec3 optVec(JsonObject obj, String xKey, String yKey, String zKey) {
        boolean any = obj.has(xKey) || obj.has(yKey) || obj.has(zKey);
        if (!any) {
            return null;
        }
        if (!(obj.has(xKey) && obj.has(yKey) && obj.has(zKey))) {
            throw new McpException("Camera position needs all of x, y, z (or none, to keep the current position).");
        }
        return readVec(obj, xKey, yKey, zKey);
    }

    private static Float optFloat(JsonObject obj, String key) {
        return obj.has(key) ? obj.get(key).getAsFloat() : null;
    }

    private static JsonObject readJson(HttpExchange exchange) throws IOException {
        try (InputStream is = exchange.getRequestBody()) {
            String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return new JsonObject();
            }
            return JsonParser.parseString(body).getAsJsonObject();
        }
    }

    private static String requireString(JsonObject obj, String key) {
        if (!obj.has(key)) {
            throw new McpException("Missing required field '" + key + "'.");
        }
        return obj.get(key).getAsString();
    }

    private static String optString(JsonObject obj, String key, String fallback) {
        return obj.has(key) ? obj.get(key).getAsString() : fallback;
    }

    static void sendJson(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String errorJson(String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("error", message);
        return GSON.toJson(obj);
    }
}
