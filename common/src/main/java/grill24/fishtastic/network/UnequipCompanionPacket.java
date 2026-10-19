package grill24.fishtastic.network;

import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.Companions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Sent CLIENT → SERVER to give one party slot's companion back (docs/fish-companions-party.md §5.1). */
public record UnequipCompanionPacket(int slot) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<UnequipCompanionPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.UNEQUIP_COMPANION_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, UnequipCompanionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, UnequipCompanionPacket::slot,
                    UnequipCompanionPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(UnequipCompanionPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player && CompanionParty.validSlot(packet.slot())) {
                Companions.unequip(player, packet.slot());
            }
        });
    }
}
