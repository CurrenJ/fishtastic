package grill24.fishtastic.util;

import com.mojang.math.Axis;
import grill24.fishtastic.FishtasticItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;

public class TestItemActivationAnimation implements ItemActivationAnimation {
    private final ItemStack itemStack;
    private int tickCount = 0;
    private static final int ANIMATION_DURATION = 200; // 3 seconds at 20 ticks/second

    public TestItemActivationAnimation(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    @Override
    public boolean isActive() {
        return tickCount < ANIMATION_DURATION;
    }

    @Override
    public void tick() {
        tickCount++;

        // Recognize key press for debugging purposes
        // (In a real implementation, input handling would be elsewhere)
    }

    @Override
    public void render(Minecraft minecraft, GuiGraphics guiGraphics, float partialTick) {

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        // Calculate progress (0.0 to 1.0)
        float progress = (tickCount + partialTick) / ANIMATION_DURATION;

        // Simple test animation: move item from center to top-right corner
        int x = screenWidth / 2;
        int y = screenHeight / 2;

        guiGraphics.pose().pushPose();
        float angle = progress * 360.0f;

        guiGraphics.pose().translate(x, y, 0);

        float scale = screenHeight / 2f;
        guiGraphics.pose().scale(scale, scale, scale);

        renderItem(BAR, guiGraphics, minecraft, angle, 0);

        float maxYOffset = 19f / 32f; // Max Y offset in item texture units
        // sin wave for bobbing effect
        float yOffset = (float) Math.sin(progress * Math.PI * 4) * 0.5f + 0.5f; // Normalize to 0-1
        yOffset *= maxYOffset;
        guiGraphics.pose().translate(0, -yOffset, 0);
        renderItem(BOBBER, guiGraphics, minecraft, angle, 1);

        guiGraphics.pose().popPose();

    }

    /**
     * Helper record to store texture coordinates and associated item stack
     * @param u Starting pixel coordinate of gui tex within item texture
     * @param v Starting pixel coordinate of gui tex within item texture
     * @param uw Width of gui tex within item texture
     * @param vh Height of gui tex within item texture
     * @param texWidth Width of the full item texture
     * @param texHeight Height of the full item texture
     * @param itemStack
     */
    public static final GuiTextureItem BAR = new GuiTextureItem(0, 0, 8, 32, 32, 32, new ItemStack(FishtasticItems.FISHING_MINIGAME_ROD_BACKGROUND));
    public static final GuiTextureItem BOBBER = new GuiTextureItem(0, 0, 8, 32, 32, 32, new ItemStack(FishtasticItems.FISHING_MINIGAME_BOBBER));
    public record GuiTextureItem(int u, int v, int uw, int vh, int texWidth, int texHeight, ItemStack itemStack, Vector2f localPivot) {
        public GuiTextureItem(int u, int v, int uw, int vh, int texWidth, int texHeight, ItemStack itemStack) {
            this(u, v, uw, vh, texWidth, texHeight, itemStack, calculateLocalPivot(u, v, uw, vh, texWidth, texHeight));
        }
    }

    private static Vector2f calculateLocalPivot(int u, int v, int uw, int vh, int texWidth, int texHeight) {
        float x = (u + uw / 2f) / texWidth - 0.5f;
        float y = (v + vh / 2f) / texHeight - 0.5f;
        return new Vector2f(x, y);
    }

    private static void renderItem(GuiTextureItem guiTextureItem, GuiGraphics guiGraphics, Minecraft minecraft, float angle, int zOffset) {
        guiGraphics.pose().pushPose();

        Vector2f pivot = guiTextureItem.localPivot();
        guiGraphics.pose().translate(-pivot.x(), -pivot.y(), zOffset); // Adjust position so that pivot is center-origin

        guiGraphics.pose().translate(pivot.x(), pivot.y(), 0); // Translate to local pivot so that rotation occurs around pivot
        guiGraphics.pose().mulPose(Axis.YP.rotationDegrees(angle)); // Apply rotation
        guiGraphics.pose().translate(-pivot.x(), -pivot.y(), 0); // Translate back after rotation

        // Render the item
        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;
        extension.fishtastic$renderItem(guiTextureItem.itemStack(), 0, 0);

        guiGraphics.pose().popPose();
    }
}
