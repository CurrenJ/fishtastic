package grill24.fishtastic.util;

import com.mojang.math.Axis;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.item.FishtasticFish;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FishingMinigameAnimation implements ItemActivationAnimation {
    private int tickCount = 0;
    private final FishingMinigameState minigameState;

    // Intro animation state
    private boolean isIntro = true;
    private int introAnimationTick = 0;
    private static final int INTRO_ANIMATION_DURATION = 20; // 1 second at 20 TPS

    // Hide animation state
    private boolean isHiding = false;
    private int hideAnimationTick = 0;
    private static final int HIDE_ANIMATION_DURATION = 20; // 1 second at 20 TPS

    // Track caught targets BEFORE they get removed
    private final List<Integer> caughtTargetIndices = new ArrayList<>();

    public FishingMinigameAnimation() {
        this.minigameState = new FishingMinigameState();

        // Add some example targets
        Random random = new Random();
        minigameState.addTarget(new FishingTarget(new ItemStack(Items.STONE), random, 0.3f));
        minigameState.addTarget(new FishingTarget(new ItemStack(Items.DIAMOND), random, 0.6f));
        minigameState.addTarget(new FishingTarget(new ItemStack(Items.GOLD_INGOT), random, 0.8f));
    }

    @Override
    public boolean isActive() {
        // Animation is active until hide animation completes
        return !isHiding || hideAnimationTick < HIDE_ANIMATION_DURATION;
    }

    @Override
    public void tick() {
        tickCount++;

        // If in intro, advance intro animation
        if (isIntro) {
            introAnimationTick++;
            if (introAnimationTick >= INTRO_ANIMATION_DURATION) {
                isIntro = false;
            }
            return; // Don't update minigame state or targets during intro animation
        }

        // If hiding, advance hide animation
        if (isHiding) {
            hideAnimationTick++;
            return; // Don't update minigame state or targets during hide animation
        }

        // Only update minigame state and targets when not in intro or hiding
        minigameState.tick();

        // Update catch progress for all targets based on collision
        final float normalizedBobberMinY = minigameState.getBobberPosition();
        final float normalizedBobberMaxY = normalizedBobberMinY + minigameState.getBobberSize();

        // Use iterator to safely remove targets while iterating
        var iterator = minigameState.getTargets().iterator();
        boolean anyOngoing = false;
        int targetIndex = 0;
        while (iterator.hasNext()) {
            FishingTarget target = iterator.next();

            // Only update active targets
            if (target.getState() == FishingTarget.TargetState.ACTIVE) {
                float targetPosition = target.getPosition();
                boolean isTargetWithinBobber =
                        targetPosition >= normalizedBobberMinY &&
                        targetPosition <= normalizedBobberMaxY;
                target.updateCatchProgress(isTargetWithinBobber);

                // Check if target was caught or failed
                if (target.isCaught()) {
                    target.startCollectionAnimation();
                    // Track this target as caught BEFORE it gets removed
                    if (!caughtTargetIndices.contains(targetIndex)) {
                        caughtTargetIndices.add(targetIndex);
                    }
                } else if (target.hasFailed()) {
                    target.startFailAnimation(0, 0);
                } else {
                    anyOngoing = true;
                }
            } else if (target.isAnimationComplete()) {
                // Remove only when animation is complete
                iterator.remove();
            }

            targetIndex++;
        }

        // If no ongoing targets and all targets are cleared, start hide animation
        if (!anyOngoing && minigameState.getTargets().isEmpty()) {
            if (!isHiding) {
                // Send results to server before hiding (via reflection to avoid client dependency)
                try {
                    Class<?> handlerClass = Class.forName("grill24.fishtastic.client.FishingMinigameClientHandler");
                    java.lang.reflect.Method sendMethod = handlerClass.getMethod("sendMinigameResults");
                    sendMethod.invoke(null);
                } catch (Exception e) {
                    // Silently fail - this is expected on server side
                }
            }
            isHiding = true;
        }
    }

    /**
     * Applies an upward impulse to the bobber (player interaction)
     * Does nothing if the minigame is hiding or in intro animation.
     */
    public void applyPlayerImpulse() {
        // Don't apply impulse during intro or hide animations
        if (isIntro || isHiding) {
            return;
        }
        minigameState.applyImpulse();
    }

    /**
     * Gets the list of caught target indices
     * @return List of indices of targets that were caught
     */
    public List<Integer> getCaughtTargetIndices() {
        return new ArrayList<>(caughtTargetIndices);
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
        float progress = (tickCount + partialTick) / 600f; // Generic progress for any animations

        // Center of the screen
        int x = screenWidth / 2;
        int y = screenHeight / 2;

        render(minecraft, guiGraphics, partialTick, progress, x, y, screenWidth, screenHeight);
//        renderRewardsDisplay(guiGraphics, x, y, screenWidth, screenHeight);
    }

    private void render(Minecraft minecraft, GuiGraphics guiGraphics, float partialTick, float progress, int x, int y, int screenWidth, int screenHeight) {
        // ----- Render Fishing Bar + Bobber -----
        guiGraphics.pose().pushPose();
        renderFishingBar(minecraft, guiGraphics, partialTick, progress, x, y, screenHeight);
        renderTargets(guiGraphics, partialTick);
        guiGraphics.pose().popPose();
    }

    private void renderFishingBar(Minecraft minecraft, GuiGraphics guiGraphics, float partialTick, float progress, int x, int y, int screenHeight) {
        float angle = (float) (Math.sin(progress * (float)Math.PI * 2) * 12f); // Swing back and forth

        // Calculate vertical offset for intro and hide animations
        float verticalOffset = 0f;

        // Intro animation: slide in from top
        if (isIntro) {
            float introProgress = (introAnimationTick + partialTick) / INTRO_ANIMATION_DURATION;
            introProgress = Math.min(1.0f, introProgress); // Clamp to 1.0
            // Ease-out function for smooth deceleration
            introProgress = 1.0f - (1.0f - introProgress) * (1.0f - introProgress);
            verticalOffset = -(1.0f - introProgress) * screenHeight; // Slide down from negative (top)
        }

        // Hide animation: slide out bottom
        if (isHiding) {
            float hideProgress = (hideAnimationTick + partialTick) / HIDE_ANIMATION_DURATION;
            hideProgress = Math.min(1.0f, hideProgress); // Clamp to 1.0
            // Ease-in function for smooth acceleration
            hideProgress = hideProgress * hideProgress;
            verticalOffset = hideProgress * screenHeight; // Slide down by screen height
        }

        guiGraphics.pose().translate(x, y + verticalOffset, 0);

        float scale = 2 * screenHeight / 3f;
        guiGraphics.pose().scale(scale, scale, scale);

        renderItem(BAR, guiGraphics, minecraft, angle, 0);

        guiGraphics.pose().pushPose();
        float bobberMaxYOffset = 28f / 32f; // Max Y offset in item texture units
        // Use physics-based bobber position from minigame state with interpolation for smooth rendering
        float normalizedBobberPosition = (isHiding || isIntro) ? minigameState.getBobberPosition() : minigameState.getInterpolatedBobberPosition(partialTick); // 0 to 1
        float yOffset = normalizedBobberPosition * bobberMaxYOffset;
        guiGraphics.pose().translate(0, -yOffset, 0);
        renderItem(BOBBER, guiGraphics, minecraft, angle, 1);
        guiGraphics.pose().popPose();
    }

    private void renderTargets(GuiGraphics guiGraphics, float partialTick) {
        // Render all targets from the minigame state
        final float itemMaxYOffset = 26f / 32f; // Max Y offset in item texture units
        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;

        // Calculate bobber bounds for shake effect
        final float normalizedBobberMinY = minigameState.getBobberPosition();
        final float normalizedBobberMaxY = normalizedBobberMinY + minigameState.getBobberSize();

        // ----- Render Targets -----
        int zOffset = 2;
        for (FishingTarget target : minigameState.getTargets()) {
            guiGraphics.pose().pushPose();

            FishingTarget.TargetState targetState = target.getState();

            // Get the display item - use generic fish for FishtasticFish items during active/fail states
            ItemStack displayItem = getDisplayItemStack(target);

            if (targetState == FishingTarget.TargetState.ACTIVE) {
                // Existing rendering logic for active targets
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
                    float baseFreq = 0.05f;
                    final float freqMultiplier = 0.2f;
                    final float baseAmplitude = 10f;
                    shakeAngle = target.getShakeAngle(partialTick, baseFreq, freqMultiplier, baseAmplitude);
                }

                // Calculate scale based on catch progress
                float scaleMultiplier = 0.5f + (catchProgress * 0.5f);
                final float itemScale = (2 / 16f) * scaleMultiplier;

                float prog = Math.max(0, (0.5f - catchProgress) * 2f);
                Vector3f color = Utility.interpolateColor(
                        new Vector3f(1, 1, 1), // White at progress 0
                        new Vector3f(1, 0.25f, 0.25f), // Red at progress 1
                        prog
                );

                guiGraphics.pose().translate(0, -targetYOffset, zOffset);
                guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(shakeAngle));
                guiGraphics.pose().scale(itemScale, itemScale, itemScale);
                guiGraphics.setColor(color.x, color.y, color.z, 1f);
                extension.fishtastic$renderItem(displayItem, 0, 0);
                guiGraphics.setColor(1f, 1f, 1f, 1f);

            } else if (targetState == FishingTarget.TargetState.ANIMATING_COLLECTION) {
                // Collection animation: spin on Y-axis and shrink
                float targetPosition = target.getInterpolatedPosition(partialTick) - 0.5f;
                float targetYOffset = targetPosition * itemMaxYOffset;

                float spinAngle = target.getCollectionSpinAngle(partialTick);
                float collectScale = target.getCollectionScale(partialTick);
                final float itemScale = (2 / 16f) * collectScale;

                guiGraphics.pose().translate(0, -targetYOffset, zOffset);
                guiGraphics.pose().mulPose(Axis.YP.rotationDegrees(spinAngle)); // Spin on Y-axis
                guiGraphics.pose().scale(itemScale, itemScale, itemScale);
                extension.fishtastic$renderItem(target.getItemStack(), 0, 0);

            } else if (targetState == FishingTarget.TargetState.ANIMATING_FAIL) {
                // Fail animation: physics-based movement with rotation on all axes
                float targetPosition = target.getInterpolatedPosition(partialTick) - 0.5f;
                float targetYOffset = targetPosition * itemMaxYOffset;

                Vector3f failRotation = target.getFailRotation(partialTick);
                float collectScale = target.getCollectionScale(partialTick);
                final float itemScale = (2 / 16f) * collectScale;

                Vector2f failPhysSim = target.getFailScreenPosition(partialTick);

                guiGraphics.pose().translate(failPhysSim.x(), -targetYOffset - failPhysSim.y(), zOffset);
                // Apply rotation on all three axes
                guiGraphics.pose().mulPose(Axis.XP.rotationDegrees(failRotation.x));
                guiGraphics.pose().mulPose(Axis.YP.rotationDegrees(failRotation.y));
                guiGraphics.pose().mulPose(Axis.ZP.rotationDegrees(failRotation.z));
                guiGraphics.pose().scale(itemScale, itemScale, itemScale);
                extension.fishtastic$renderItem(displayItem, 0, 0);

            }

            guiGraphics.pose().popPose();
            zOffset++; // Increment z-offset for each target so they don't z-fight
        }
    }

    /**
     * Gets the display ItemStack for a fishing target.
     * For FishtasticFish items in ACTIVE or ANIMATING_FAIL states, returns the generic fish item
     * to hide the actual reward until it's caught.
     * For caught items (ANIMATING_COLLECTION), returns the actual item to reveal the reward.
     *
     * @param target The fishing target to get the display item for
     * @return The ItemStack to display in the minigame
     */
    private ItemStack getDisplayItemStack(FishingTarget target) {
        ItemStack itemStack = target.getItemStack();
        FishingTarget.TargetState state = target.getState();

        // Only hide FishtasticFish items during ACTIVE and ANIMATING_FAIL states
        // Show the actual item during ANIMATING_COLLECTION (when caught)
        if ((state == FishingTarget.TargetState.ACTIVE || state == FishingTarget.TargetState.ANIMATING_FAIL)
            && (itemStack.is(ItemTags.FISHES) || itemStack.getItem() instanceof FishtasticFish)) {
            return new ItemStack(FishtasticItems.GENERIC_FISH);
        }

        return itemStack;
    }

    private static void renderRewardsDisplay(GuiGraphics guiGraphics, int x, int y, int screenWidth, int screenHeight) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(x - (screenHeight / 3f), y, 0);

        float rewardDisplayScale = screenHeight / 3f;
        guiGraphics.pose().scale(rewardDisplayScale, rewardDisplayScale, rewardDisplayScale);

        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;
        extension.fishtastic$renderItem(new ItemStack(Blocks.BRICKS), 0, 0);
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
