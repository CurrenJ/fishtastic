package grill24.fishtastic.client;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.network.FinishFishingMinigamePacket;
import grill24.fishtastic.network.StartFishingMinigamePacket;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.FishingTarget;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Client-side handler for fishing minigame packets
 */
public class FishingMinigameClientHandler {
    private static int currentSessionId = -1;
    private static FishingMinigameAnimation currentAnimation = null;

    /**
     * Handle start fishing minigame packet from server
     */
    public static void handleStartPacket(StartFishingMinigamePacket packet) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        currentSessionId = packet.sessionId();

        // Resolve equipped gear before constructing the animation — the bait can request a
        // smaller bobber (see BaitEffect.smallBobber), which is baked into the animation's
        // FishingMinigameState at construction time and can't change afterward.
        ItemStack rod = minecraft.player.getMainHandItem();
        if (!rod.is(FishtasticItems.COPPER_FISHING_ROD)) {
            rod = minecraft.player.getOffhandItem();
        }
        ItemStack bait = ItemStack.EMPTY;
        ItemStack hook = ItemStack.EMPTY;
        ItemStack charm = ItemStack.EMPTY;
        if (rod.is(FishtasticItems.COPPER_FISHING_ROD)) {
            bait = CopperFishingRod.getBait(rod);
            hook = CopperFishingRod.getHook(rod);
            charm = CopperFishingRod.getCharm(rod);
        }
        BaitEffect baitEffect = BaitEffect.fromStack(bait);

        // Create animation with server-provided targets
        FishingMinigameAnimation animation = new FishingMinigameAnimation(
                baitEffect != null && baitEffect.smallBobber()
                        ? FishingMinigameAnimation.LAYOUT_SMALL
                        : FishingMinigameAnimation.LAYOUT);

        // Apply charm input-force bonus if the player has one equipped
        if (!charm.isEmpty()) {
            CharmEffect charmEffect = charm.get(FishtasticDataComponents.CHARM_EFFECT.value());
            if (charmEffect != null) {
                animation.setInputForceMultiplier(charmEffect.inputForceMultiplier());
            }
            animation.setEquippedCharmEffect(charmEffect);
        }
        if (rod.is(FishtasticItems.COPPER_FISHING_ROD)) {
            animation.setEquippedGearStacks(bait, hook, charm);
        }

        // Clear existing targets and add server-provided ones
        animation.getMinigameState().getTargets().clear();
        Random random = new Random();

        for (StartFishingMinigamePacket.TargetData targetData : packet.targets()) {
            FishingTarget target = new FishingTarget(
                    targetData.rewardStacks(),
                    targetData.category(),
                    random,
                    targetData.initialPosition(),
                    targetData.difficulty(),
                    targetData.phases(),
                    resolveAffinityMultiplier(targetData, baitEffect, BaitEffect.FishGroupAffinity::targetSpeedMultiplier),
                    resolveAffinityMultiplier(targetData, baitEffect, BaitEffect.FishGroupAffinity::catchProgressMultiplier)
            );
            animation.getMinigameState().addTarget(target);
        }

        if (packet.isTutorial()) {
            animation.setTutorial(true);
        }

        animation.setTopWeightedFishPreviews(packet.topWeightedFishPreviews());
        animation.setCurrentZones(packet.zones());
        animation.setUndiscoveredSpecies(packet.undiscoveredSpecies());

        // Display the animation
        IGameRendererExtension gameRendererExt = (IGameRendererExtension) minecraft.gameRenderer;
        gameRendererExt.fishtastic$displayItemActivation(() -> animation);

        currentAnimation = animation;
    }

    /**
     * A type-preferenced bait's "challenge" counterweight for boosting a group's catch odds: a fish
     * reward belonging to one of the equipped bait's {@link BaitEffect.FishGroupAffinity} groups can
     * move faster ({@code targetSpeedMultiplier} — Frenzy Bait) and/or fill its catch bar slower
     * ({@code catchProgressMultiplier} — Trophy Bait) in the minigame. Reward stacks are already
     * visible to the client at session start (see {@code StartFishingMinigamePacket}), so both resolve
     * entirely client-side without any extra data from the server.
     */
    private static float resolveAffinityMultiplier(StartFishingMinigamePacket.TargetData targetData, @Nullable BaitEffect baitEffect,
                                                    java.util.function.ToDoubleFunction<BaitEffect.FishGroupAffinity> extractor) {
        if (baitEffect == null || targetData.category() != FishingTarget.TargetCategory.FISH
                || targetData.rewardStacks().isEmpty()) {
            return 1.0f;
        }
        ItemStack reward = targetData.rewardStacks().getFirst();
        float multiplier = 1.0f;
        for (BaitEffect.FishGroupAffinity affinity : baitEffect.fishGroupAffinities()) {
            if (reward.is(affinity.group())) {
                multiplier *= (float) extractor.applyAsDouble(affinity);
            }
        }
        return multiplier;
    }

    /**
     * Send minigame completion results to server
     * Call this when the client-side minigame finishes
     */
    public static void sendMinigameResults() {
        if (currentSessionId == -1 || currentAnimation == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        // Get caught indices from the animation (tracked before targets were removed)
        List<Integer> caughtIndices = currentAnimation.getCaughtTargetIndices();

        // Send packet to server with results
        minecraft.player.connection.send(
                new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(
                        new FinishFishingMinigamePacket(currentSessionId, caughtIndices)
                )
        );

        // Clean up
        currentSessionId = -1;
        currentAnimation = null;
    }

    /**
     * Get the current session ID (-1 if no active session)
     */
    public static int getCurrentSessionId() {
        return currentSessionId;
    }

    /**
     * Check if there's an active fishing minigame session
     */
    public static boolean hasActiveSession() {
        return currentSessionId != -1;
    }

    /**
     * Cancel the current minigame (without sending results)
     */
    public static void cancel() {
        currentSessionId = -1;
        currentAnimation = null;
    }
}
