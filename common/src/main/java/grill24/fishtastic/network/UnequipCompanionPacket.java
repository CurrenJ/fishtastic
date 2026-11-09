package grill24.fishtastic.network;

import grill24.fishtastic.companion.Companions;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent CLIENT → SERVER from the companions screen to unequip the companion in {@code slot}
 * (docs/companion-ui-redesign/layout-spec.md §3.1). The fish goes back to the inventory; a full
 * inventory refuses and changes nothing ({@link Companions#unequipToInventory}).
 */
public record UnequipCompanionPacket(int slot) implements FishtasticPayload {
    public static final FishtasticPayload.PayloadType<UnequipCompanionPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.UNEQUIP_COMPANION_ID);
    public static final BufCodec<UnequipCompanionPacket> STREAM_CODEC =
            BufCodec.composite(BufCodecs.VAR_INT, UnequipCompanionPacket::slot, UnequipCompanionPacket::new);

    @Override public FishtasticPayload.PayloadType<?> type() { return TYPE; }

    public static void handleClientToServer(UnequipCompanionPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.getPlayer() instanceof ServerPlayer player)) return;
            CompanionFeedbackPacket.Outcome outcome = switch (Companions.unequipToInventory(player, packet.slot())) {
                case UNEQUIPPED -> CompanionFeedbackPacket.Outcome.UNEQUIPPED;
                case INVENTORY_FULL -> CompanionFeedbackPacket.Outcome.INVENTORY_FULL;
                case EMPTY -> CompanionFeedbackPacket.Outcome.NOTHING_TO_RENAME;
            };
            CompanionFeedbackPacket.sendToPlayer(player, outcome);
        });
    }
}
