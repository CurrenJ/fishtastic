package grill24.fishtastic.client;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.block.FishTankBlock;
import grill24.fishtastic.block.FishTankCustomizationMode;
import grill24.fishtastic.block.FishTankCustomizationModeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Client-side handler for fish tank customization mode cycling.
 */
public class FishTankCustomizationHandler {

    /**
     * Handle mouse scroll while player is crouching and looking at a fish tank.
     * Returns true if the event should be cancelled (scroll was handled).
     */
    public static boolean handleMouseScroll(double scrollDelta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || !player.isCrouching() || mc.level == null) {
            return false;
        }

        // Check if player is looking at a fish tank
        HitResult hitResult = mc.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHitResult)) {
            return false;
        }

        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }

        // Check if the targeted block is a fish tank
        if (!(mc.level.getBlockState(blockHitResult.getBlockPos()).getBlock() instanceof FishTankBlock)) {
            return false;
        }

        // Cycle the customization mode
        boolean forward = scrollDelta > 0;
        Fishtastic.LOGGER.info("[FishTankCustomizationHandler][CLIENT] Cycling mode, forward={}, scrollDelta={}", forward, scrollDelta);
        FishTankCustomizationMode newMode = FishTankCustomizationModeManager.cycleMode(player.getUUID(), forward);

        // Display feedback to player
        Fishtastic.LOGGER.info("[FishTankCustomizationHandler][CLIENT] New mode={}", newMode);
        player.sendSystemMessage(
            Component.literal("Fish Tank Customization: " + newMode.getDisplayName())
        );

        // Cancel the scroll event (prevent hotbar change)
        return true;
    }
}
