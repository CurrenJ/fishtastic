package grill24.fishtastic.util;

import com.mojang.math.Axis;
import grill24.fishtastic.FishtasticItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.awt.*;
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

        // Use iterator to safely remove targets while iterating
        var iterator = minigameState.getTargets().iterator();
        boolean anyOngoing = false;
        while (iterator.hasNext()) {
            FishingTarget target = iterator.next();
            float targetPosition = target.getPosition();
            boolean isTargetWithinBobber =
                    targetPosition >= normalizedBobberMinY &&
                    targetPosition <= normalizedBobberMaxY;
            target.updateCatchProgress(isTargetWithinBobber);

            // Check if target was caught or failed
            if (target.isCaught()) {
                iterator.remove();
                // Target caught successfully - could add reward logic here
            } else if (target.hasFailed()) {
                iterator.remove();
            } else {
                anyOngoing = true;
            }
        }

        if(!anyOngoing && minigameState.getTargets().isEmpty()) {
            // End the animation if no targets are left
            tickCount = ANIMATION_DURATION;
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


        // Render all targets from the minigame state
        final float itemMaxYOffset = 26f / 32f; // Max Y offset in item texture units
        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;

        // Calculate bobber bounds for shake effect
        final float normalizedBobberMinY = minigameState.getBobberPosition();
        final float normalizedBobberMaxY = normalizedBobberMinY + minigameState.getBobberSize();

        int zOffset = 2;
        for (FishingTarget target : minigameState.getTargets()) {
            guiGraphics.pose().pushPose();

            // Get interpolated position for smooth rendering
            float targetPosition = target.getInterpolatedPosition(partialTick) - 0.5f; // -0.5 to 0.5 for correct position
            float targetYOffset = targetPosition * itemMaxYOffset;

            float catchProgress = target.getCatchProgress();

            // Add shake effect only if target is currently being caught (within bobber)
            float targetRawPosition = target.getInterpolatedPosition(partialTick);
            boolean isTargetWithinBobber = targetRawPosition >= normalizedBobberMinY && targetRawPosition <= normalizedBobberMaxY;
            float shakeAngle = 0f;
            if (isTargetWithinBobber) {
                float shakeOffset = target.getShakeOffset(partialTick);
                targetYOffset += shakeOffset;
                shakeAngle = (float) (Math.sin((tickCount + partialTick) * (0.25F + catchProgress)) * 10f - (2 * catchProgress)); // Shake angle
            }

            // Calculate scale based on catch progress
            // Scale grows from 0.5 (at progress 0) to 2.0 (at progress 1)
            // Base scale at 0.5 progress is 1.0
            float scaleMultiplier = 0.5f + (catchProgress * 0.5f); // Range: 0.5 (at progress=0) to 2.0 (at progress=1)
            final float itemScale = (2 / 16f) * scaleMultiplier;

            float prog = Math.max(0, (0.5f - catchProgress) * 2f); //
            Vector3f color = Utility.interpolateColor(
                    new Vector3f(1, 1, 1), // White at progress 0
                    new Vector3f(1, 0.25f, 0.25f), // Light green at progress 1
                    prog
            );


            guiGraphics.pose().translate(0, -targetYOffset, zOffset);
            guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(shakeAngle));
            guiGraphics.pose().scale(itemScale, itemScale, itemScale);
            guiGraphics.setColor(color.x, color.y, color.z, 1f);
            extension.fishtastic$renderItem(target.getItemStack(), 0, 0);
            guiGraphics.setColor(1f, 1f, 1f, 1f);

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
        renderItem(guiTextureItem, guiGraphics, minecraft, Axis.YP.rotationDegrees(angle), zOffset);
    }

    private static void renderItem(GuiTextureItem guiTextureItem, GuiGraphics guiGraphics, Minecraft minecraft, Quaternionf quaternion, int zOffset) {
        guiGraphics.pose().pushPose();

        Vector2f pivot = guiTextureItem.localPivot();
        guiGraphics.pose().translate(-pivot.x(), -pivot.y(), zOffset); // Adjust position so that pivot is center-origin

        guiGraphics.pose().translate(pivot.x(), pivot.y(), 0); // Translate to local pivot so that rotation occurs around pivot
        guiGraphics.pose().mulPose(quaternion); // Apply rotation
        guiGraphics.pose().translate(-pivot.x(), -pivot.y(), 0); // Translate back after rotation

        // Render the item
        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;
        extension.fishtastic$renderItem(guiTextureItem.itemStack(), 0, 0);

        guiGraphics.pose().popPose();
    }
}
