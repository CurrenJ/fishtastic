package grill24.fishtastic.network;

import grill24.fishtastic.menu.CompanionMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Sent CLIENT → SERVER when the companion screen's pile strip is clicked (docs/fish-companions.md
 * §5.5). {@code fish} only describes the fish the strip showed; the server finds an identical one in
 * a Pile of Fish ({@link CompanionMenu#takeFromPile}) and puts it on the open menu's cursor. A stale
 * description answers {@link CompanionFeedbackPacket.Outcome#NOT_CARRIED}.
 */
public record TakePileFishPacket(ItemStack fish) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TakePileFishPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.TAKE_PILE_FISH_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, TakePileFishPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, TakePileFishPacket::fish,
                    TakePileFishPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(TakePileFishPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player && player.containerMenu instanceof CompanionMenu menu
                    && menu.takeFromPile(player, packet.fish()) == CompanionMenu.PileTake.NOT_CARRIED) {
                FishtasticTransport.sendToPlayer(player,
                        new CompanionFeedbackPacket(CompanionFeedbackPacket.Outcome.NOT_CARRIED));
            }
        });
    }
}
