package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.command.CosmeticCaptureSession;
import net.minecraft.core.BlockPos;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import grill24.fishtastic.network.FishtasticPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Sent <strong>server → client</strong> whenever a player's cosmetic-capture wand session
 * changes, so the client can render a gizmo preview of the current selection.
 * {@code active = false} clears the preview (session cancelled/finished).
 */
public record CosmeticCaptureSyncPacket(
        boolean active,
        CosmeticCaptureSession.Mode mode,
        Optional<BlockPos> corner1,
        Optional<BlockPos> corner2,
        Optional<BlockPos> anchor
) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<CosmeticCaptureSyncPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("cosmetic_capture_sync"));

    public static final BufCodec<CosmeticCaptureSyncPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.BOOL, CosmeticCaptureSyncPacket::active,
                    CosmeticCaptureSession.Mode.STREAM_CODEC, CosmeticCaptureSyncPacket::mode,
                    BufCodecs.optional(BufCodecs.BLOCK_POS), CosmeticCaptureSyncPacket::corner1,
                    BufCodecs.optional(BufCodecs.BLOCK_POS), CosmeticCaptureSyncPacket::corner2,
                    BufCodecs.optional(BufCodecs.BLOCK_POS), CosmeticCaptureSyncPacket::anchor,
                    CosmeticCaptureSyncPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void sendToPlayer(ServerPlayer player, CosmeticCaptureSession session) {
        player.connection.send(new ClientboundCustomPayloadPacket(new CosmeticCaptureSyncPacket(
                true,
                session.mode(),
                Optional.ofNullable(session.corner1()),
                Optional.ofNullable(session.corner2()),
                Optional.ofNullable(session.anchor())
        )));
    }

    public static void sendClear(ServerPlayer player) {
        player.connection.send(new ClientboundCustomPayloadPacket(new CosmeticCaptureSyncPacket(
                false, CosmeticCaptureSession.Mode.CORNER_1, Optional.empty(), Optional.empty(), Optional.empty()
        )));
    }

    @FunctionalInterface
    public interface ClientHandler {
        void handle(CosmeticCaptureSyncPacket packet);
    }

    private static ClientHandler clientHandler = null;

    public static void registerClientHandler(ClientHandler handler) {
        clientHandler = handler;
    }

    public static void handleServerToClient(CosmeticCaptureSyncPacket packet, FishtasticPackets.IPacketContext context) {
        context.enqueueWork(() -> {
            if (clientHandler != null) {
                clientHandler.handle(packet);
            }
        });
    }
}
