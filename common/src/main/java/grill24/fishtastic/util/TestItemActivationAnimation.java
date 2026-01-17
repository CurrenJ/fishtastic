package grill24.fishtastic.util;

import com.mojang.math.Axis;
import grill24.fishtastic.FishtasticItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector2f;

import java.util.Random;

public class TestItemActivationAnimation implements ItemActivationAnimation {
    private final ItemStack itemStack;
    private int tickCount = 0;
    private static final int ANIMATION_DURATION = 600; // 30 seconds at 20 TPS
    private final FishingMinigameState minigameState;

    public TestItemActivationAnimation(ItemStack itemStack) {
        this.itemStack = itemStack;
        this.minigameState = new FishingMinigameState();

        // Add some example targets
        Random random = new Random();
        minigameState.addTarget(new FishingTarget(new ItemStack(Items.STONE), random, 0.3f));
        minigameState.addTarget(new FishingTarget(new ItemStack(Items.DIAMOND), random, 0.6f));
        minigameState.addTarget(new FishingTarget(new ItemStack(Items.GOLD_INGOT), random, 0.8f));
    }

    @Override
    public boolean isActive() {
        return tickCount < ANIMATION_DURATION;
    }

    @Override
    public void tick() {
        tickCount++;
        minigameState.tick();

        // Update catch progress for all targets based on collision
        final float normalizedBobberMinY = minigameState.getBobberPosition();
        final float normalizedBobberMaxY = normalizedBobberMinY + minigameState.getBobberSize();

        for (FishingTarget target : minigameState.getTargets()) {
            float targetPosition = target.getPosition();
            boolean isTargetWithinBobber =
                    targetPosition >= normalizedBobberMinY &&
                    targetPosition <= normalizedBobberMaxY;
            target.updateCatchProgress(isTargetWithinBobber);
        }
    }

    /**
     * Applies an upward impulse to the bobber (player interaction)
     */
    public void applyPlayerImpulse() {
        minigameState.applyImpulse();
    }

    /**
     * Gets the current minigame state
     * @return The fishing minigame state object
     */
    public FishingMinigameState getMinigameState() {
        return minigameState;
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
        float angle = (float) (Math.sin(progress * (float)Math.PI * 2) * 12f); // Swing back and forth

        guiGraphics.pose().translate(x, y, 0);

        float scale = 2 * screenHeight / 3f;
        guiGraphics.pose().scale(scale, scale, scale);

        renderItem(BAR, guiGraphics, minecraft, angle, 0);

        guiGraphics.pose().pushPose();
        float bobberMaxYOffset = 28f / 32f; // Max Y offset in item texture units
        // Use physics-based bobber position from minigame state with interpolation for smooth rendering
        float normalizedBobberPosition = minigameState.getInterpolatedBobberPosition(partialTick); // 0 to 1
        float yOffset = normalizedBobberPosition * bobberMaxYOffset;
        guiGraphics.pose().translate(0, -yOffset, 0);
        renderItem(BOBBER, guiGraphics, minecraft, angle, 1);
        guiGraphics.pose().popPose();

//        guiGraphics.pose().pushPose();
//        final float itemMaxYOffset = 26f / 32f; // Max Y offset in item texture units
        // EXAMPLE OF RENDERING ITEM ON BAR
//        final float itemYOffset = (float) Math.sin(progress * Math.PI * 4) * 0.5f; // -0.5 to 0.5
//
//        // Calculate scale based on catch progress - item shrinks as it's being caught
//        float catchProgress = minigameState.getCatchProgress();
//        float scaleMultiplier = 1.0f - (catchProgress * 0.8f); // Shrinks to 20% of original size at full progress
//        final float itemScale = (2 / 16f) * scaleMultiplier;
//
//        guiGraphics.pose().translate(0, -itemYOffset * itemMaxYOffset, 2);
//        guiGraphics.pose().scale(itemScale, itemScale, itemScale);
//        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;
//        extension.fishtastic$renderItem(new ItemStack(Items.STONE), 0, 0);
//        guiGraphics.pose().popPose();

//        final float normalizedItemY = (itemYOffset + 0.5f) * (itemMaxYOffset / bobberMaxYOffset);
//        final float normalizedBobberMinY = normalizedBobberPosition;
//        final float normalizedBobberMaxY = normalizedBobberMinY + minigameState.getBobberSize();
//        boolean isItemWithinBobber =
//                normalizedItemY >= normalizedBobberMinY &&
//                normalizedItemY <= normalizedBobberMaxY;


        // Render all targets from the minigame state
        final float itemMaxYOffset = 26f / 32f; // Max Y offset in item texture units
        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;

        int zOffset = 2;
        for (FishingTarget target : minigameState.getTargets()) {
            guiGraphics.pose().pushPose();

            // Get interpolated position for smooth rendering
            float targetPosition = target.getInterpolatedPosition(partialTick) - 0.5f; // -0.5 to 0.5 for correct position
            float targetYOffset = targetPosition * itemMaxYOffset;

            // Calculate scale based on catch progress - item shrinks as it's being caught
            float catchProgress = target.getCatchProgress();
            float scaleMultiplier = 1.0f - (catchProgress * 0.8f); // Shrinks to 20% of original size at full progress
            final float itemScale = (2 / 16f) * scaleMultiplier;

            guiGraphics.pose().translate(0, -targetYOffset, zOffset);
            guiGraphics.pose().scale(itemScale, itemScale, itemScale);
            extension.fishtastic$renderItem(target.getItemStack(), 0, 0);

            guiGraphics.pose().popPose();
            zOffset++; // Increment z-offset for each target so they don't z-fight
        }

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
