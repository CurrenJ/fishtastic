package grill24.fishtastic.network;

import grill24.fishtastic.companion.CompanionFishingOutcome;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Server → client: "that player's fishing session just resolved — this way" (docs/fish-companions.md
 * §5, §11). The owner's own client derives the outcome from its minigame animation; everyone else
 * needs to be told, because the minigame bar is drawn client-side and they never see it. The
 * companion of the named player plays its catch wiggle or escape droop at the bobber.
 *
 * <p>Sent to players near the fisher — the companion is only ever visible up close, so a session
 * resolving across the world is not worth a packet.
 */
public record CompanionFishingEventPacket(UUID owner, CompanionFishingOutcome outcome) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CompanionFishingEventPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.COMPANION_FISHING_EVENT_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionFishingEventPacket> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, CompanionFishingEventPacket::owner,
                    ByteBufCodecs.VAR_INT.map(i -> i >= 0 && i < CompanionFishingOutcome.values().length
                                    ? CompanionFishingOutcome.values()[i] : CompanionFishingOutcome.NONE,
                            CompanionFishingOutcome::ordinal),
                    CompanionFishingEventPacket::outcome,
                    CompanionFishingEventPacket::new
            );

    /** How far the packet reaches, blocks — comfortably past where a companion can be seen. */
    private static final double REACH = 128.0;

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Tells everyone near {@code owner} (except the owner) how their session resolved. */
    public static void sendToNearbyPlayers(ServerLevel level, ServerPlayer owner, CompanionFishingOutcome outcome) {
        if (outcome == CompanionFishingOutcome.NONE) return;
        CompanionFishingEventPacket packet = new CompanionFishingEventPacket(owner.getUUID(), outcome);
        for (ServerPlayer player : level.players()) {
            if (player != owner && player.distanceToSqr(owner) < REACH * REACH) {
                player.connection.send(new ClientboundCustomPayloadPacket(packet));
            }
        }
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(CompanionFishingEventPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(CompanionFishingEventPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }
}
