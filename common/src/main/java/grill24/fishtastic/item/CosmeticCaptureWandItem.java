package grill24.fishtastic.item;

import grill24.fishtastic.command.CosmeticCaptureSession;
import grill24.fishtastic.network.CosmeticCaptureSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Debug tool for {@code /fishtastic cosmetic capture start}: right-click blocks to set the
 * bounding-box corners and anchor for a {@link grill24.fishtastic.fishtank.CosmeticStructure}
 * capture. Sneak while clicking to bypass a target block's own interaction (chests, doors, fish
 * tanks, etc.) — see {@code ServerPlayerGameMode#useItemOn}'s {@code suppressUsingBlock} check.
 */
public class CosmeticCaptureWandItem extends Item {
    public CosmeticCaptureWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }

        CosmeticCaptureSession session = CosmeticCaptureSession.get(player.getUUID());
        if (session == null) {
            player.sendSystemMessage(Component.literal(
                    "No active capture session. Run /fishtastic cosmetic capture start first.")
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        String message = session.recordClick(context.getClickedPos());
        player.sendSystemMessage(Component.literal(message).withStyle(ChatFormatting.AQUA));
        CosmeticCaptureSyncPacket.sendToPlayer(player, session);

        return InteractionResult.SUCCESS_SERVER;
    }
}
