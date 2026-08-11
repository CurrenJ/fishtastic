package grill24.fishtastic.client;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.CharmEffect;
import grill24.fishtastic.item.CopperFishingRod;
import grill24.fishtastic.network.FinishFishingMinigamePacket;
import grill24.fishtastic.network.StartFishingMinigamePacket;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.FishingTarget;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

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

        // Create animation with server-provided targets
        FishingMinigameAnimation animation = new FishingMinigameAnimation();

        // Apply charm input-force bonus if the player has one equipped
        ItemStack rod = minecraft.player.getMainHandItem();
        if (!rod.is(FishtasticItems.COPPER_FISHING_ROD)) {
            rod = minecraft.player.getOffhandItem();
        }
        if (rod.is(FishtasticItems.COPPER_FISHING_ROD)) {
            ItemStack bait = CopperFishingRod.getBait(rod);
            ItemStack hook = CopperFishingRod.getHook(rod);
            ItemStack charm = CopperFishingRod.getCharm(rod);
            if (!charm.isEmpty()) {
                CharmEffect charmEffect = charm.get(FishtasticDataComponents.CHARM_EFFECT.value());
                if (charmEffect != null) {
                    animation.setInputForceMultiplier(charmEffect.inputForceMultiplier());
                }
                animation.setEquippedCharmEffect(charmEffect);
            }
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
                    targetData.phases()
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
