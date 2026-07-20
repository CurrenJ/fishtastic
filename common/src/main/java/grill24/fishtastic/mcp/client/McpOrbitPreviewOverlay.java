package grill24.fishtastic.mcp.client;

import com.mojang.blaze3d.platform.NativeImage;
import grill24.fishtastic.Fishtastic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

/**
 * Briefly shows the stitched orbit sheet on the player's own screen after {@link McpOrbitOps} runs, so
 * the human sees the same contact sheet the agent is about to reason about instead of only watching
 * their character get flung around the build.
 *
 * <p>Purely a dev-tooling affordance - it draws nothing unless an orbit just finished.
 */
public final class McpOrbitPreviewOverlay {
    private McpOrbitPreviewOverlay() {}

    private static final Identifier TEXTURE_ID =
            Identifier.fromNamespaceAndPath(Fishtastic.MOD_ID, "mcp_orbit_preview");

    private static final long DISPLAY_MILLIS = 3000L;
    private static final long FADE_MILLIS = 400L;
    /** Fraction of the screen the preview is allowed to occupy. The sheet is very wide and short, so
     *  width is the binding constraint in practice. */
    private static final double MAX_WIDTH_FRACTION = 0.92;
    private static final double MAX_HEIGHT_FRACTION = 0.55;
    private static final int BORDER = 2;

    private static volatile boolean active;
    private static long expiresAt;
    private static int imageWidth;
    private static int imageHeight;

    /**
     * Takes ownership of {@code sheet} - it is handed to a {@link DynamicTexture}, which closes it when
     * the texture is released. Callers must not close or reuse the image afterwards.
     */
    public static void show(NativeImage sheet) {
        Minecraft mc = Minecraft.getInstance();
        // Texture creation uploads to the GPU, so it has to happen on the render thread.
        mc.submit(() -> {
            releaseTexture(mc);
            imageWidth = sheet.getWidth();
            imageHeight = sheet.getHeight();
            mc.getTextureManager().register(TEXTURE_ID,
                    new DynamicTexture(() -> "fishtastic mcp orbit preview", sheet));
            expiresAt = System.currentTimeMillis() + DISPLAY_MILLIS;
            active = true;
        });
    }

    /**
     * Releases the preview once it expires, independently of whether anything is being drawn.
     *
     * <p>Expiry must not be driven by {@link #render} alone: the HUD doesn't draw while a screen is
     * open or the window is minimised, so a render-driven release leaks the texture and its multi-megabyte
     * backing image for as long as the player happens to be sitting in a menu. Hook this from each
     * loader's client tick <em>outside</em> any {@code isPaused()} guard - being paused in a menu is
     * exactly the case this exists to cover.
     */
    public static void tick() {
        if (active && System.currentTimeMillis() >= expiresAt) {
            hide();
        }
    }

    /** Hooked from each loader's HUD render pass - see the client entrypoints. */
    public static void render(GuiGraphicsExtractor graphics) {
        if (!active) {
            return;
        }

        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0) {
            hide();
            return;
        }

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        double scale = Math.min(
                screenWidth * MAX_WIDTH_FRACTION / imageWidth,
                screenHeight * MAX_HEIGHT_FRACTION / imageHeight);
        int width = Math.max(1, (int) (imageWidth * scale));
        int height = Math.max(1, (int) (imageHeight * scale));
        int x = (screenWidth - width) / 2;
        int y = (screenHeight - height) / 2;

        int alpha = remaining >= FADE_MILLIS ? 255 : (int) (255L * remaining / FADE_MILLIS);

        graphics.fill(x - BORDER, y - BORDER, x + width + BORDER, y + height + BORDER,
                ARGB.color(alpha, 16, 16, 16));
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                TEXTURE_ID,
                x, y,
                0.0F, 0.0F,
                width, height,
                // Source rect is the whole sheet; dest rect is the scaled-to-fit box above.
                imageWidth, imageHeight,
                imageWidth, imageHeight,
                ARGB.color(alpha, 255, 255, 255));
    }

    private static void hide() {
        active = false;
        releaseTexture(Minecraft.getInstance());
    }

    private static void releaseTexture(Minecraft mc) {
        mc.getTextureManager().release(TEXTURE_ID);
    }
}
