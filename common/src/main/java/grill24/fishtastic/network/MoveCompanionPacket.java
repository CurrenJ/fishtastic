package grill24.fishtastic.network;

import grill24.fishtastic.companion.Companions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent CLIENT → SERVER to swap two companion slots; "make lead" is a swap with slot 0
 * (docs/companion-ui-redesign/layout-spec.md §3.1). The server validates both slots.
 */
public record MoveCompanionPacket(int from, int to) implements CustomPacketPayload {
    public static final Type<MoveCompanionPacket> TYPE = new Type<>(FishtasticPackets.MOVE_COMPANION_ID);
    public static final StreamCodec<RegistryFriendlyByteBuf, MoveCompanionPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, MoveCompanionPacket::from,
                    ByteBufCodecs.VAR_INT, MoveCompanionPacket::to, MoveCompanionPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClientToServer(MoveCompanionPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player && Companions.swap(player, packet.from(), packet.to())) {
                FishtasticTransport.sendToPlayer(player, new CompanionFeedbackPacket(CompanionFeedbackPacket.Outcome.MOVED));
            }
        });
    }
}
