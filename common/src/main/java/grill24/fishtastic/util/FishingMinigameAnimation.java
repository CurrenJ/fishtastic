package grill24.fishtastic.util;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.TutorialClientHandler;
import grill24.fishtastic.component.FishQuality;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class FishingMinigameAnimation implements ItemActivationAnimation {
    private int tickCount = 0;
    private final FishingMinigameState minigameState;
    private boolean isTutorial = false;
    private final Random sparkleRandom = new Random();

    private record SparkleBurst(List<SparkleParticle> particles, float targetYOffset) {}
    private final List<SparkleBurst> sparkleBursts = new ArrayList<>();

    // Intro animation state
    private boolean isIntro = true;
    private int introAnimationTick = 0;
    private static final int INTRO_ANIMATION_DURATION = 20; // 1 second at 20 TPS

    // Hide animation state
    private boolean isHiding = false;
    private int hideAnimationTick = 0;
    private static final int HIDE_ANIMATION_DURATION = 20; // 1 second at 20 TPS

    // Frame-rate physics tracking
    private float lastRenderTime = -1f;
    private boolean wasImpulseKeyDown = false;
    // Force applied per tick-unit while the impulse key is held.  Much smaller than the
    // tap impulse (0.04) so continuous hold doesn't instantly slam the bobber to the ceiling.
    private static final float HOLD_IMPULSE_STRENGTH = 0.012f;
    private float inputForceMultiplier = 1.0f;

    // Track caught targets BEFORE they get removed
    private final List<Integer> caughtTargetIndices = new ArrayList<>();

    public FishingMinigameAnimation() {
        this.minigameState = new FishingMinigameState(LAYOUT.bobberSize(), (1.0f - LAYOUT.bobberSize()) / 2f);

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

    private static int sparkleCountForTarget(FishingTarget target) {
        if (target.getCategory() == FishingTarget.TargetCategory.TREASURE) {
            return 16;
        }
        FishQuality.Quality best = target.getAllRewardItems().stream()
                .map(stack -> stack.get(FishtasticDataComponents.FISH_QUALITY.value()))
                .filter(q -> q != null)
                .map(FishQuality::quality)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(null);
        if (best == null) return 4; // vanilla fish
        return switch (best) {
            case COMMON    -> 0;
            case UNCOMMON  -> 3;
            case RARE      -> 12;
            case EPIC      -> 30;
            case LEGENDARY -> 75;
        };
    }

    private void tickSparkles() {
        for (SparkleBurst burst : sparkleBursts) {
            burst.particles().forEach(SparkleParticle::tick);
            burst.particles().removeIf(p -> !p.isAlive());
        }
        sparkleBursts.removeIf(burst -> burst.particles().isEmpty());
    }

    @Override
    public void tick() {
        tickCount++;
        tickSparkles();

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

        // Never remove targets from the list — doing so shifts indices and breaks the caught-index
        // tracking that is sent to the server.  Targets stay in their original slot forever; we
        // just stop updating them once they are no longer ACTIVE.
        boolean anyOngoing = false;
        List<FishingTarget> targets = minigameState.getTargets();
        for (int targetIndex = 0; targetIndex < targets.size(); targetIndex++) {
            FishingTarget target = targets.get(targetIndex);

            if (target.getState() == FishingTarget.TargetState.ACTIVE) {
                float targetPosition = target.getPosition();
                float bobberCenter = normalizedBobberMinY + minigameState.getBobberSize() * 0.5f;
                float bobberRadius = minigameState.getBobberSize() * 0.5f;
                float distToCenter = Math.abs(targetPosition - bobberCenter);
                float overlapQuality = distToCenter < bobberRadius ? 1f - distToCenter / bobberRadius : 0f;
                target.updateCatchProgress(overlapQuality);

                if (target.isCaught()) {
                    float targetYOffset = (target.getPosition() - 0.5f) * LAYOUT.itemMaxYOffset();
                    int sparkleCount = sparkleCountForTarget(target);
                    List<SparkleParticle> burst = new ArrayList<>();
                    for (int i = 0; i < sparkleCount; i++) {
                        burst.add(new SparkleParticle(0, 0, sparkleRandom, 20 + sparkleRandom.nextInt(15)));
                    }
                    sparkleBursts.add(new SparkleBurst(burst, targetYOffset));

                    target.startCollectionAnimation(0, 0);
                    caughtTargetIndices.add(targetIndex);
                } else if (target.hasFailed()) {
                    target.startFailAnimation();
                } else {
                    anyOngoing = true;
                }
            }
        }

        // Hide once every target has fully completed its animation
        boolean allComplete = !targets.isEmpty()
                && targets.stream().allMatch(FishingTarget::isAnimationComplete);
        if (!anyOngoing && allComplete) {
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

    public void setTutorial(boolean tutorial) {
        isTutorial = tutorial;
        minigameState.setNoCatchDrain(tutorial);
        for (FishingTarget target : minigameState.getTargets()) {
            target.setNoCatchDrain(tutorial);
        }
    }

    public boolean isTutorial() { return isTutorial; }

    public void setPaused(boolean paused) {
        minigameState.setPaused(paused);
    }

    public void setInputForceMultiplier(float multiplier) {
        this.inputForceMultiplier = multiplier;
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
        minigameState.applyImpulse(FishingMinigameState.IMPULSE_STRENGTH * inputForceMultiplier);
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
        // Compute delta time in game-tick units (1.0 = 50 ms tick; ~0.33 at 60 fps).
        // Using tickCount + partialTick as an absolute clock avoids any dependency on
        // wall-clock timing and stays perfectly in sync with the game's own time source.
        float absoluteTime = tickCount + partialTick;
        if (lastRenderTime < 0f) lastRenderTime = absoluteTime;
        float deltaTime = Math.min(absoluteTime - lastRenderTime, 3.0f); // clamp against lag spikes
        lastRenderTime = absoluteTime;

        // Per-frame input and physics — skip during intro / hide so the bobber is stable
        if (!isIntro && !isHiding) {
            // Poll both the dedicated keybind and vanilla's "use item" mapping (default: right
            // mouse button) every frame so right-click produces the same smooth, frame-rate
            // independent hold force as the keybind instead of vanilla's bursty click-repeat taps.
            boolean isImpulseDown = (FishtasticKeyBinds.fishingMinigameImpulse != null
                    && FishtasticKeyBinds.fishingMinigameImpulse.isDown())
                    || minecraft.options.keyUse.isDown();
            if (isImpulseDown) {
                // Apply a small continuous force each frame, scaled by deltaTime so the
                // accumulated velocity per second is frame-rate independent.
                minigameState.applyImpulse(HOLD_IMPULSE_STRENGTH * inputForceMultiplier * deltaTime);
                if (!wasImpulseKeyDown) {
                    // Fire the tutorial callback only on the initial press, not every frame.
                    TutorialClientHandler.onMinigameImpulse();
                }
            }
            wasImpulseKeyDown = isImpulseDown;

            minigameState.updatePhysics(deltaTime);
        }

        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();

        // Calculate progress (0.0 to 1.0)
        float progress = absoluteTime / 600f; // Generic progress for any animations

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
        renderSparkles(guiGraphics, partialTick);
        guiGraphics.pose().popMatrix();
    }

    private void renderSparkles(GuiGraphicsExtractor guiGraphics, float partialTick) {
        IGuiGraphicsExtension extension = (IGuiGraphicsExtension) guiGraphics;
        final float sparkleScale = 1f / 16f;

        for (SparkleBurst burst : sparkleBursts) {
            for (SparkleParticle sparkle : burst.particles()) {
                guiGraphics.pose().pushMatrix();
                Vector2f pos = sparkle.getInterpolatedPosition(partialTick);
                float rotZ = sparkle.getInterpolatedRotationZ(partialTick);
                float scale = sparkleScale * (1f - sparkle.getLifetimeProgress());
                guiGraphics.pose().translate(pos.x(), -burst.targetYOffset() - pos.y());
                guiGraphics.pose().rotate((float) Math.toRadians(rotZ));
                guiGraphics.pose().scale(scale, scale);
                extension.fishtastic$renderItem(sparkle.getItemStack(), 0, 0);
                guiGraphics.pose().popMatrix();
            }
        }
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
        float normalizedBobberPosition = minigameState.getBobberPosition();
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

                // Squash-and-stretch telegraph cues
                float dartCoil       = target.getDartCoilProgress();
                float dartBurst      = target.getDartBurstProgress();
                float lungeTelegraph = target.getLungeTelegraphProgress();
                float squashX, squashY;
                if (dartCoil > 0f) {
                    // DART coil: widen + flatten as tension builds
                    squashX = 1f + dartCoil * 0.35f;
                    squashY = 1f - dartCoil * 0.35f;
                } else if (dartBurst > 0f && dartBurst < 0.3f) {
                    // DART release: brief stretch in direction of travel
                    float t = dartBurst / 0.3f;
                    squashX = 1f - t * 0.30f;
                    squashY = 1f + t * 0.40f;
                } else if (lungeTelegraph > 0f) {
                    // LUNGE coil: compress horizontally, extend vertically
                    squashX = 1f - lungeTelegraph * 0.30f;
                    squashY = 1f + lungeTelegraph * 0.40f;
                } else {
                    squashX = 1f;
                    squashY = 1f;
                }

                guiGraphics.pose().translate(0, -targetYOffset);
                guiGraphics.pose().rotate((float) Math.toRadians(shakeAngle));
                guiGraphics.pose().scale(itemScale * squashX, itemScale * squashY);
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
                // Match the active-state scale (0.5 + catchProgress*0.5) so there's no pop on transition.
                // catchProgress is 0 at failure time, so this is always 0.5× — matching the smallest active size.
                float scaleMultiplier = 0.5f + (target.getCatchProgress() * 0.5f);
                final float itemScale = (2 / 16f) * scaleMultiplier * collectScale;

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
