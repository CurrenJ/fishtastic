package grill24.fishtastic.mcp.client;

import grill24.fishtastic.mcp.McpException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.File;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The only class in the MCP bridge feature that touches {@code Minecraft}/{@code Screenshot} - kept
 * under the same {@code grill24.fishtastic.client.*}-style convention the rest of the codebase uses for
 * client-only code (e.g. {@code FishtasticClientSetup}), so it's never reachable from a code path that
 * could run without a client present. Marshals onto the client thread via {@code Minecraft.submit(...)},
 * the client-side counterpart of the server-thread marshaling in {@link grill24.fishtastic.mcp.McpBridgeServer}.
 */
public final class McpScreenshotOps {
    private McpScreenshotOps() {}

    private static final long POLL_INTERVAL_MILLIS = 250;
    // Kept below McpBridgeServer's SCREENSHOT_TIMEOUT_SECONDS so this future always fails with a
    // clear McpException instead of racing the HTTP handler's own timeout.
    private static final long MAX_WAIT_MILLIS = 115_000;

    private static final ScheduledExecutorService POLLER = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "fishtastic-mcp-screenshot-poll");
        thread.setDaemon(true);
        return thread;
    });

    public static CompletableFuture<String> takeScreenshot() {
        CompletableFuture<String> future = new CompletableFuture<>();
        waitForNoScreen(future, System.currentTimeMillis());
        return future;
    }

    /**
     * A GUI (pause menu, inventory, chat, ...) covers the frame the player is actually looking at, so a
     * screenshot taken while one is open is useless for judging an in-world build. Poll on the client
     * thread until {@code Minecraft.screen} is null before grabbing.
     */
    private static void waitForNoScreen(CompletableFuture<String> future, long startMillis) {
        Minecraft mc = Minecraft.getInstance();

        mc.submit(() -> {
            if (mc.screen == null) {
                grab(mc, future);
                return;
            }
            if (System.currentTimeMillis() - startMillis >= MAX_WAIT_MILLIS) {
                future.completeExceptionally(new McpException(
                        "Timed out waiting for the player to close the open screen (" +
                                mc.screen.getClass().getSimpleName() + ") before taking a screenshot."));
                return;
            }
            POLLER.schedule(() -> waitForNoScreen(future, startMillis), POLL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
        });
    }

    private static void grab(Minecraft mc, CompletableFuture<String> future) {
        // A forced, deterministic name avoids parsing Screenshot's internal (private, racy)
        // name-collision logic just to learn the resulting path.
        String name = "mcp_" + System.currentTimeMillis() + ".png";
        File targetFile = new File(new File(mc.gameDirectory, "screenshots"), name);

        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), 1, component -> {
            if (isFailure(component)) {
                future.completeExceptionally(new McpException("Screenshot failed: " + component.getString()));
            } else {
                future.complete(targetFile.getAbsolutePath());
            }
        });
    }

    private static boolean isFailure(Component component) {
        return component.getContents() instanceof TranslatableContents tc
                && tc.getKey().equals("screenshot.failure");
    }
}
