package grill24.fishtastic.item;

import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.TestItemActivationAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;


public class TestItem extends Item {
    public TestItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        // Only execute on client side
        if (level.isClientSide) {
            Minecraft minecraft = Minecraft.getInstance();
            IGameRendererExtension gameRendererExt = (IGameRendererExtension) minecraft.gameRenderer;

            // Check if there's an active animation
            if (gameRendererExt.fishtastic$getActiveAnimation() != null) {
                // If the active animation is a TestItemActivationAnimation, apply impulse to it
                if (gameRendererExt.fishtastic$getActiveAnimation() instanceof TestItemActivationAnimation testAnimation) {
                    testAnimation.applyPlayerImpulse();
                } else {
                    // Cancel other animation types
                    gameRendererExt.fishtastic$cancelCurrentAnimation();
                }
            } else {
                // Start a new animation
                TestItemActivationAnimation animation = new TestItemActivationAnimation(itemStack.copy());
                gameRendererExt.fishtastic$displayItemActivation(animation);
            }
        }

        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide);
    }
}
