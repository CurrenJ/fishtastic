package grill24.fishtastic.network;

import grill24.fishtastic.blockentity.OrganizerSortMode;
import grill24.fishtastic.menu.ElectricFishOrganizerMenu;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent CLIENT → SERVER to change how the open Electric Fish Organizer groups and orders its
 * piles. The server applies it to the block entity (persisting it and re-triggering the sort)
 * and re-syncs the menu's data slots.
 */
public record SetOrganizerSortPacket(OrganizerSortMode mode, boolean ascending) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<SetOrganizerSortPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.SET_ORGANIZER_SORT_ID);

    public static final BufCodec<SetOrganizerSortPacket> STREAM_CODEC =
            BufCodec.composite(
                    OrganizerSortMode.STREAM_CODEC,
                    SetOrganizerSortPacket::mode,
                    BufCodecs.BOOL,
                    SetOrganizerSortPacket::ascending,
                    SetOrganizerSortPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(SetOrganizerSortPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.getPlayer() instanceof ServerPlayer player)) return;
            // Only applies while the player actually has the organizer menu open; a stale packet
            // from a closed screen is ignored.
            if (!(player.containerMenu instanceof ElectricFishOrganizerMenu menu)) return;

            menu.setSortState(packet.mode(), packet.ascending());
        });
    }
}
