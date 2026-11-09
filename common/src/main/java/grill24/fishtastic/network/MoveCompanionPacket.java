package grill24.fishtastic.network;

import grill24.fishtastic.companion.Companions;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent CLIENT → SERVER to swap two companion slots; "make lead" is a swap with slot 0
 * (docs/companion-ui-redesign/layout-spec.md §3.1). The server validates both slots.
 */
public record MoveCompanionPacket(int from, int to) implements FishtasticPayload {
    public static final FishtasticPayload.PayloadType<MoveCompanionPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.MOVE_COMPANION_ID);
    public static final BufCodec<MoveCompanionPacket> STREAM_CODEC =
            BufCodec.composite(BufCodecs.VAR_INT, MoveCompanionPacket::from,
                    BufCodecs.VAR_INT, MoveCompanionPacket::to, MoveCompanionPacket::new);

    @Override public FishtasticPayload.PayloadType<?> type() { return TYPE; }

    public static void handleClientToServer(MoveCompanionPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player && Companions.swap(player, packet.from(), packet.to())) {
                CompanionFeedbackPacket.sendToPlayer(player, CompanionFeedbackPacket.Outcome.MOVED);
            }
        });
    }
}
