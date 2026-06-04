package grill24.fishtastic.util;

import grill24.fishtastic.FishtasticItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
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
        this.minigameState = new FishingMinigameState(LAYOUT.bobberSize());

        // Add some example targets
        Random random = new Random();
        minigameState.addTarget(new FishingTarget(List.of(new ItemStack(Items.STONE)), FishingTarget.TargetCategory.TREASURE, random, 0.3f));
        minigameState.addTarget(new FishingTarget(List.of(new ItemStack(Items.DIAMOND)), FishingTarget.TargetCategory.TREASURE, random, 0.6f));
        minigameState.addTarget(new FishingTarget(List.of(new ItemStack(Items.GOLD_INGOT)), FishingTarget.TargetCategory.TREASURE, random, 0.8f));
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
                    target.startCollectionAnimation(0, 0);
                    // Track this target as caught BEFORE it gets removed
                    if (!caughtTargetIndices.contains(targetIndex)) {
                        caughtTargetIndices.add(targetIndex);
                    }
                } else if (target.hasFailed()) {
                    target.startFailAnimation();
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
    public void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphics, float partialTick) {

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

    private void render(Minecraft minecraft, GuiGraphicsExtractor guiGraphics, float partialTick, float progress, int x, int y, int screenWidth, int screenHeight) {
        // ----- Render Fishing Bar + Bobber -----
        guiGraphics.pose().pushMatrix();
        renderFishingBar(minecraft, guiGraphics, partialTick, progress, x, y, screenHeight);
        renderTargets(guiGraphics, partialTick);
        guiGraphics.pose().popMatrix();
    }

    private void renderFishingBar(Minecraft minecraft, GuiGraphicsExtractor guiGraphics, float partialTick, float progress, int x, int y, int screenHeight) {
        float angle = 0f; // Can use to add a slight rotation effect if desired

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

        guiGraphics.pose().translate(x, y + verticalOffset);

        float scale = 2 * screenHeight / 3f;
        guiGraphics.pose().scale(scale, scale);

        renderItem(LAYOUT.bar(), guiGraphics, minecraft, angle, 0);

        guiGraphics.pose().pushMatrix();
        float normalizedBobberPosition = (isHiding || isIntro) ? minigameState.getBobberPosition() : minigameState.getInterpolatedBobberPosition(partialTick);
        float yOffset = normalizedBobberPosition * LAYOUT.bobberMaxYOffset();
        guiGraphics.pose().translate(0, -yOffset);
        renderItem(LAYOUT.bobber(), guiGraphics, minecraft, angle, 1);
        guiGraphics.pose().popMatrix();
    }

    private void renderTargets(GuiGraphicsExtractor guiGraphics, float partialTick) {
        // Render all targets from the minigame state
        final float itemMaxYOffset = LAYOUT.itemMaxYOffset();
        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;

        // Calculate bobber bounds for shake effect
        final float normalizedBobberMinY = minigameState.getBobberPosition();
        final float normalizedBobberMaxY = normalizedBobberMinY + minigameState.getBobberSize();

        // ----- Render Targets -----
        int zOffset = 2;
        for (FishingTarget target : minigameState.getTargets()) {
            guiGraphics.pose().pushMatrix();

            FishingTarget.TargetState targetState = target.getState();

            // Get the display item - use generic fish for FishtasticFish items during active/fail states
            ItemStack displayItem = target.getDisplayItemStack();

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

                guiGraphics.pose().translate(0, -targetYOffset);
                guiGraphics.pose().rotate((float) Math.toRadians(shakeAngle));
                guiGraphics.pose().scale(itemScale, itemScale);
                // setColor removed in 26.1 - render without tinting
                extension.fishtastic$renderItem(displayItem, 0, 0);

            } else if (targetState == FishingTarget.TargetState.ANIMATING_SUCCESS) {
                // Collection animation: physics-based movement with rotation on all axes
                // Render each reward item with its own physics simulation
                float targetPosition = target.getInterpolatedPosition(partialTick) - 0.5f;
                float targetYOffset = targetPosition * itemMaxYOffset;

                final float itemScale = (2 / 16f);

                for (PhysicsSimulation simulation : target.getPhysicsSimulations()) {
                    guiGraphics.pose().pushMatrix();

                    Vector2f successPhysSim = simulation.getInterpolatedPosition(partialTick);
                    Vector3f successRotation = simulation.getInterpolatedRotation(partialTick);

                    guiGraphics.pose().translate(successPhysSim.x(), -targetYOffset - successPhysSim.y());
                    // In 2D, only Z-axis rotation is meaningful
                    guiGraphics.pose().rotate((float) Math.toRadians(successRotation.z));
                    guiGraphics.pose().scale(itemScale, itemScale);
                    extension.fishtastic$renderItem(simulation.getItemStack(), 0, 0);

                    guiGraphics.pose().popMatrix();
                    zOffset++; // Each item gets its own z-level to prevent z-fighting
                }

            } else if (targetState == FishingTarget.TargetState.ANIMATING_FAIL) {
                // Fail animation: spin on Y-axis and shrink
                float targetPosition = target.getInterpolatedPosition(partialTick) - 0.5f;
                float targetYOffset = targetPosition * itemMaxYOffset;

                float spinAngle = target.getFailSpinAngle(partialTick);
                float collectScale = target.getCollectionScale(partialTick);
                final float itemScale = (2 / 16f) * collectScale;

                guiGraphics.pose().translate(0, -targetYOffset);
                // Y-axis spin doesn't apply in 2D - use scale-x for a flip effect
                float flipScale = (float) Math.cos(Math.toRadians(spinAngle));
                guiGraphics.pose().scale(itemScale * flipScale, itemScale);
                extension.fishtastic$renderItem(displayItem, 0, 0);
            }

            guiGraphics.pose().popMatrix();
            zOffset++; // Increment z-offset for each target so they don't z-fight
        }
    }

    private static void renderRewardsDisplay(GuiGraphicsExtractor guiGraphics, int x, int y, int screenWidth, int screenHeight) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(x - (screenHeight / 3f), y);

        float rewardDisplayScale = screenHeight / 3f;
        guiGraphics.pose().scale(rewardDisplayScale, rewardDisplayScale);

        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;
        extension.fishtastic$renderItem(new ItemStack(Blocks.BRICKS), 0, 0);
        guiGraphics.pose().popMatrix();
    }

    // -------------------------------------------------------------------------
    // Sprite layout — single source of truth for all sizing constants.
    // To resize or reshape the bar/bobber textures, only edit LAYOUT below.
    // -------------------------------------------------------------------------

    /**
     * Texture coordinates and associated item stack for one sprite.
     *
     * @param u        Left edge of the sprite content within the texture (px)
     * @param v        Top edge of the sprite content within the texture (px)
     * @param uw       Width of the sprite content (px)
     * @param vh       Height of the sprite content (px)
     * @param texWidth  Full texture width (px)
     * @param texHeight Full texture height (px)
     */
    public record GuiTextureItem(int u, int v, int uw, int vh, int texWidth, int texHeight, ItemStack itemStack, Vector2f localPivot) {
        public GuiTextureItem(int u, int v, int uw, int vh, int texWidth, int texHeight, ItemStack itemStack) {
            this(u, v, uw, vh, texWidth, texHeight, itemStack, calculateLocalPivot(u, v, uw, vh, texWidth, texHeight));
        }

        private static Vector2f calculateLocalPivot(int u, int v, int uw, int vh, int texWidth, int texHeight) {
            float x = (u + uw / 2f) / texWidth - 0.5f;
            float y = (v + vh / 2f) / texHeight - 0.5f;
            return new Vector2f(x, y);
        }
    }

    /**
     * All sizing parameters for the fishing bar overlay in one place.
     *
     * @param bar           Bar sprite definition
     * @param bobber        Bobber sprite definition
     * @param travelZonePx  Pixel height of the bobber's playable travel area within the bar
     *                      (texture height minus top + bottom decorative margins)
     * @param bobberHeightPx Pixel height of the bobber sprite within that travel zone
     * @param targetZonePx  Pixel height of the zone target icons may appear in
     *                      (typically slightly tighter than travelZonePx)
     */
    public record FishingBarLayout(
            GuiTextureItem bar,
            GuiTextureItem bobber,
            int travelZonePx,
            int bobberHeightPx,
            int targetZonePx
    ) {
        /** Fraction of bar texture height the bobber can travel — passed to the renderer. */
        public float bobberMaxYOffset() { return (float) travelZonePx / bar.texHeight(); }

        /** Bobber size as a fraction of the travel zone — passed to FishingMinigameState. */
        public float bobberSize()       { return (float) bobberHeightPx / travelZonePx; }

        /** Fraction of bar texture height target icons may travel — passed to the renderer. */
        public float itemMaxYOffset()   { return (float) targetZonePx / bar.texHeight(); }
    }

    public static final FishingBarLayout LAYOUT = new FishingBarLayout(
            new GuiTextureItem(0, 0, 8, 32, 32, 32, new ItemStack(FishtasticItems.FISHING_MINIGAME_ROD_BACKGROUND)),
            new GuiTextureItem(0, 0, 8, 32, 32, 32, new ItemStack(FishtasticItems.FISHING_MINIGAME_BOBBER)),
            28,  // travel zone: 32px texture minus ~2px margin at each end
            9,   // bobber height in pixels
            26   // target zone: slightly tighter margins than the bobber travel zone
    );

    private static void renderItem(GuiTextureItem guiTextureItem, GuiGraphicsExtractor guiGraphics, Minecraft minecraft, float angle, int zOffset) {
        guiGraphics.pose().pushMatrix();

        Vector2f pivot = guiTextureItem.localPivot();
        guiGraphics.pose().rotate((float) Math.toRadians(angle)); // Rotate around sprite center
        guiGraphics.pose().translate(-pivot.x(), -pivot.y()); // Center sprite content at screen origin

        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;
        extension.fishtastic$renderItem(guiTextureItem.itemStack(), 0, 0);

        guiGraphics.pose().popMatrix();
    }
}
