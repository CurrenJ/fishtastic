package grill24.fishtastic.item;

import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class CopperFishingRod extends FishingRodItem {
    public CopperFishingRod(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        super.use(level, player, hand);

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
        }

        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }
}
