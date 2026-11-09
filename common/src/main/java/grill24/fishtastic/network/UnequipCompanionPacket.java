package grill24.fishtastic.network;

import grill24.fishtastic.companion.Companions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent CLIENT → SERVER from the companions screen to unequip the companion in {@code slot}
 * (docs/companion-ui-redesign/layout-spec.md §3.1). The fish goes back to the inventory; a full
 * inventory refuses and changes nothing ({@link Companions#unequipToInventory}).
 */
public record UnequipCompanionPacket(int slot) implements CustomPacketPayload {
    public static final Type<UnequipCompanionPacket> TYPE = new Type<>(FishtasticPackets.UNEQUIP_COMPANION_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, UnequipCompanionPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, UnequipCompanionPacket::slot, UnequipCompanionPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClientToServer(UnequipCompanionPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.getPlayer() instanceof ServerPlayer player)) return;
            CompanionFeedbackPacket.Outcome outcome = switch (Companions.unequipToInventory(player, packet.slot())) {
                case UNEQUIPPED -> CompanionFeedbackPacket.Outcome.UNEQUIPPED;
                case INVENTORY_FULL -> CompanionFeedbackPacket.Outcome.INVENTORY_FULL;
                case EMPTY -> CompanionFeedbackPacket.Outcome.NOTHING_TO_RENAME;
            };
            FishtasticTransport.sendToPlayer(player, new CompanionFeedbackPacket(outcome));
        });
    }
}
