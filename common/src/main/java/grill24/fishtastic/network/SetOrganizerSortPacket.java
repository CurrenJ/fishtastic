package grill24.fishtastic.network;

import grill24.fishtastic.blockentity.OrganizerSortMode;
import grill24.fishtastic.menu.ElectricFishOrganizerMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent CLIENT → SERVER to change how the open Electric Fish Organizer groups and orders its
 * piles. The server applies it to the block entity (persisting it and re-triggering the sort)
 * and re-syncs the menu's data slots.
 */
public record SetOrganizerSortPacket(OrganizerSortMode mode, boolean ascending) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetOrganizerSortPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.SET_ORGANIZER_SORT_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SetOrganizerSortPacket> STREAM_CODEC =
            StreamCodec.composite(
                    OrganizerSortMode.STREAM_CODEC,
                    SetOrganizerSortPacket::mode,
                    ByteBufCodecs.BOOL,
                    SetOrganizerSortPacket::ascending,
                    SetOrganizerSortPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(SetOrganizerSortPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.getPlayer() instanceof ServerPlayer player)) return;
            // Only applies while the player actually has the organizer menu open; a stale packet
            // from a closed screen is ignored.
            if (!(player.containerMenu instanceof ElectricFishOrganizerMenu menu)) return;

            menu.setSortState(packet.mode(), packet.ascending());
        });
    }
}
