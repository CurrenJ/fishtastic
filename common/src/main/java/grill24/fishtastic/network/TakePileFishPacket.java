package grill24.fishtastic.network;

import grill24.fishtastic.menu.CompanionMenu;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Sent CLIENT → SERVER when the companion screen's pile strip is clicked (docs/fish-companions-party.md
 * §5.5). {@code fish} only describes the fish the strip showed; the server finds an identical one in
 * a Pile of Fish ({@link CompanionMenu#takeFromPile}) and puts it on the open menu's cursor. A stale
 * description answers {@link CompanionFeedbackPacket.Outcome#NOT_CARRIED}.
 */
public record TakePileFishPacket(ItemStack fish) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<TakePileFishPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.TAKE_PILE_FISH_ID);

    public static final BufCodec<TakePileFishPacket> STREAM_CODEC =
            BufCodec.composite(BufCodecs.ITEM_STACK, TakePileFishPacket::fish, TakePileFishPacket::new);

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(TakePileFishPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player && player.containerMenu instanceof CompanionMenu menu
                    && menu.takeFromPile(player, packet.fish()) == CompanionMenu.PileTake.NOT_CARRIED) {
                CompanionFeedbackPacket.sendToPlayer(player, CompanionFeedbackPacket.Outcome.NOT_CARRIED);
            }
        });
    }
}
