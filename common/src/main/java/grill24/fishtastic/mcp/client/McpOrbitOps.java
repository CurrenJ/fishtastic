package grill24.fishtastic.mcp.client;

import com.mojang.blaze3d.platform.NativeImage;
import grill24.fishtastic.mcp.McpCameraOps;
import grill24.fishtastic.mcp.McpException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Takes a turntable-style orbit of screenshots around a build and stitches them into one contact sheet.
 *
 * <p>Exists because a single screenshot is a bad basis for judging a fish tank cosmetic: the tank places
 * a structure at any of 4 rotations, so a silhouette that reads from the angle it was built facing can
 * collapse into an ambiguous lump from the side. Reviewing that one view at a time is both slow and easy
 * to skip, so this does the whole orbit in one call and hands back a single image - the agent gets every
 * angle whether or not it thought to ask for them.
 *
 * <p>Also takes care of the two things that silently ruin an automated screenshot run: an open GUI
 * covering the frame, and the singleplayer game pausing itself the moment the window loses focus (which
 * is exactly what happens while the human is reading the agent's output). Both are handled here rather
 * than being pushed onto the human as "please tab back in".
 */
public final class McpOrbitOps {
    private McpOrbitOps() {}

    /** Width each shot is downscaled to in the sheet. 4 across keeps the sheet a sane size for a vision
     *  model while leaving each tile big enough to judge a silhouette. */
    private static final int TILE_WIDTH = 400;
    private static final int TILES_PER_ROW = 4;
    /** 1px gutter so tile boundaries are unambiguous in the stitched image. */
    private static final int GUTTER = 2;
    private static final int GUTTER_ARGB = 0xFF202020;

    private static final long CLIENT_OP_TIMEOUT_SECONDS = 30;

    /** Compass label per 45° step, indexed by shot number (0° = camera due south of the subject). */
    private static final String[] LABELS_45 = {"S", "SE", "E", "NE", "N", "NW", "W", "SW"};

    public static Result orbit(MinecraftServer server, Vec3 center, double radius, double height, int shots, long settleMillis) {
        if (shots < 1 || shots > 16) {
            throw new McpException("shots must be between 1 and 16.");
        }

        Minecraft mc = Minecraft.getInstance();
        boolean[] previousOptions = onClient(mc, () -> {
            boolean[] previous = {mc.options.pauseOnLostFocus, mc.options.hideGui};
            // Without this the game re-pauses itself every tick while the window is unfocused - i.e. the
            // whole time the human is reading the agent's output - and the orbit deadlocks on a frozen
            // client that never applies the teleports.
            mc.options.pauseOnLostFocus = false;
            // The screenshot is of the composited frame, HUD included, so without this every tile burns
            // space on a hotbar and crosshair instead of the build. It also stops a previous orbit's
            // preview overlay (itself a HUD element) being captured into this orbit's tiles.
            mc.options.hideGui = true;
            closeOpenScreen(mc);
            return previous;
        });

        List<NativeImage> tiles = new ArrayList<>();
        List<String> angles = new ArrayList<>();
        boolean optionsRestored = false;
        try {
            for (int i = 0; i < shots; i++) {
                double theta = Math.toRadians(360.0 * i / shots);
                Vec3 cameraPos = new Vec3(
                        center.x + radius * Math.sin(theta),
                        height,
                        center.z + radius * Math.cos(theta));

                Vec3 finalPos = cameraPos;
                awaitServer(server, () -> McpCameraOps.setCamera(server, finalPos, null, null, center));

                // The teleport reaches the client as a packet; without a beat to apply it (and to stream
                // in whatever chunks just came into view) the shot is taken from the previous angle.
                sleep(settleMillis);

                NativeImage shot = grabImage(mc);
                try {
                    tiles.add(downscale(shot));
                } finally {
                    shot.close();
                }
                angles.add(label(i, shots));
            }

            // Restore the client options *before* showing the preview, not in the finally below. The
            // preview is itself a HUD element, so leaving hideGui set would suppress the very thing we
            // just captured. Capture is finished by this point, so nothing can contaminate the tiles.
            restoreOptions(mc, previousOptions);
            optionsRestored = true;

            String path = writeSheet(mc, tiles);
            return new Result(path, angles);
        } finally {
            for (NativeImage tile : tiles) {
                tile.close();
            }
            // Only reached when the orbit failed before the restore above. Leaving hideGui set would
            // strand the player with no HUD at all, so this path must not be skipped on error.
            if (!optionsRestored) {
                restoreOptions(mc, previousOptions);
            }
            // Leaving the player parked on the last orbit position would be a surprising side effect of
            // what is, to the caller, just "take a preview".
            awaitServer(server, () -> McpCameraOps.restore(server));
        }
    }

    private static void restoreOptions(Minecraft mc, boolean[] previous) {
        onClient(mc, () -> {
            mc.options.pauseOnLostFocus = previous[0];
            mc.options.hideGui = previous[1];
            return null;
        });
    }

    /** Stitched sheet path plus the compass label of each tile, in row-major order. */
    public record Result(String path, List<String> angleLabels) {}

    // -------------------------------------------------------------------------

    private static void closeOpenScreen(Minecraft mc) {
        if (mc.screen == null) {
            return;
        }
        // A container screen owns server-side state, so it has to be closed through the player (which
        // sends the close packet) rather than by nulling the screen out from under it.
        if (mc.screen instanceof AbstractContainerScreen<?> && mc.player != null) {
            mc.player.closeContainer();
        } else {
            mc.setScreen(null);
        }
    }

    private static NativeImage grabImage(Minecraft mc) {
        CompletableFuture<NativeImage> future = new CompletableFuture<>();
        mc.submit(() -> Screenshot.takeScreenshot(mc.getMainRenderTarget(), 1, future::complete));
        try {
            return future.get(CLIENT_OP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            // The capture may still land after we've given up waiting; nobody else holds a reference to
            // it, so without this the image's native memory is lost for the life of the process.
            future.thenAccept(NativeImage::close);
            throw new McpException("Timed out capturing a frame - is the game still rendering?");
        } catch (InterruptedException e) {
            future.thenAccept(NativeImage::close);
            Thread.currentThread().interrupt();
            throw new McpException("Interrupted while capturing a frame.");
        } catch (ExecutionException e) {
            throw new McpException("Failed to capture a frame: " + e.getCause());
        }
    }

    private static NativeImage downscale(NativeImage source) {
        int tileHeight = Math.max(1, TILE_WIDTH * source.getHeight() / source.getWidth());
        NativeImage tile = new NativeImage(TILE_WIDTH, tileHeight, false);
        source.resizeSubRectTo(0, 0, source.getWidth(), source.getHeight(), tile);
        return tile;
    }

    private static String writeSheet(Minecraft mc, List<NativeImage> tiles) {
        if (tiles.isEmpty()) {
            throw new McpException("No frames captured.");
        }
        int tileWidth = tiles.get(0).getWidth();
        int tileHeight = tiles.get(0).getHeight();
        int columns = Math.min(TILES_PER_ROW, tiles.size());
        int rows = (tiles.size() + columns - 1) / columns;

        int sheetWidth = columns * tileWidth + (columns - 1) * GUTTER;
        int sheetHeight = rows * tileHeight + (rows - 1) * GUTTER;

        NativeImage sheet = new NativeImage(sheetWidth, sheetHeight, false);
        try {
            sheet.fillRect(0, 0, sheetWidth, sheetHeight, GUTTER_ARGB);
            for (int i = 0; i < tiles.size(); i++) {
                int column = i % columns;
                int row = i / columns;
                tiles.get(i).copyRect(sheet, 0, 0,
                        column * (tileWidth + GUTTER), row * (tileHeight + GUTTER),
                        tileWidth, tileHeight, false, false);
            }

            File directory = new File(mc.gameDirectory, Screenshot.SCREENSHOT_DIR);
            directory.mkdirs();
            File target = new File(directory, "mcp_orbit_" + System.currentTimeMillis() + ".png");
            sheet.writeToFile(target);

            // Hands ownership of the image to the overlay, which closes it with its texture - so this
            // is the one path that must not close the sheet itself.
            McpOrbitPreviewOverlay.show(sheet);
            return target.getAbsolutePath();
        } catch (IOException e) {
            sheet.close();
            throw new McpException("Failed to write the stitched sheet: " + e.getMessage());
        } catch (RuntimeException e) {
            sheet.close();
            throw e;
        }
    }

    private static String label(int index, int shots) {
        double degrees = 360.0 * index / shots;
        String compass = shots == 8 ? LABELS_45[index] : null;
        String rounded = Math.rint(degrees) == degrees
                ? String.valueOf((long) degrees)
                : String.format("%.1f", degrees);
        return compass != null ? rounded + "° (" + compass + ")" : rounded + "°";
    }

    private static <T> T onClient(Minecraft mc, Supplier<T> op) {
        try {
            return mc.submit(op::get).get(CLIENT_OP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new McpException("Timed out waiting on the client thread.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpException("Interrupted waiting on the client thread.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof McpException mcpEx) {
                throw mcpEx;
            }
            throw new McpException(String.valueOf(cause));
        }
    }

    private static void awaitServer(MinecraftServer server, Runnable op) {
        try {
            server.submit(op).get(CLIENT_OP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new McpException("Timed out waiting on the server thread - is the game ticking?");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpException("Interrupted waiting on the server thread.");
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof McpException mcpEx) {
                throw mcpEx;
            }
            throw new McpException(String.valueOf(cause));
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpException("Interrupted between orbit shots.");
        }
    }
}
