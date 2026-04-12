package grill24.fishtastic.item;

import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.FishingMinigameAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;


public class TestItem extends Item {
    public TestItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            // Client side - handle minigame display and input
            Minecraft minecraft = Minecraft.getInstance();
            IGameRendererExtension gameRendererExt = (IGameRendererExtension) minecraft.gameRenderer;

            // Check if there's an active fishing minigame animation
            var activeAnimation = gameRendererExt.fishtastic$getActiveAnimation();
            if (activeAnimation instanceof FishingMinigameAnimation animation) {
                // Apply impulse locally (no network packet needed!)
                animation.applyPlayerImpulse();
            }
        } else {
            // Server side - start the minigame session
            if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                grill24.fishtastic.server.FishingMinigameManager manager =
                        grill24.fishtastic.server.FishingMinigameManager.get(serverPlayer.level());
                manager.startSession(serverPlayer, 1.0f, false);
            }
        }

        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }
}
