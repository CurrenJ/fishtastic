package grill24.fishtastic.network;

import grill24.fishtastic.companion.CompanionSceneType;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;

import java.util.UUID;

/**
 * Server → client: "these two companions are playing this scene, starting at this game time"
 * (docs/fish-companions.md, §11). One of these per scene is the whole network cost: the
 * server schedules, and each receiving client plays the identical scene from an absolute start
 * time, so the two owners' views can't drift apart.
 *
 * <p>Sent to every player near either participant — the companions are only ever visible up
 * close, so a scene resolving across the world is not worth a packet.
 *
 * <p>A companion is (owner, party slot) (docs/fish-companions.md); a group takes part
 * through its leader's slot.
 */
public record CompanionScenePacket(CompanionSceneType scene, UUID first, int firstSlot, UUID second, int secondSlot,
                                   long startTime) implements FishtasticPayload {

    public static final FishtasticPayload.PayloadType<CompanionScenePacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.COMPANION_SCENE_ID);

    public static final BufCodec<CompanionScenePacket> STREAM_CODEC =
            BufCodec.composite(
                    CompanionSceneType.STREAM_CODEC, CompanionScenePacket::scene,
                    BufCodecs.UUID, CompanionScenePacket::first,
                    BufCodecs.VAR_INT, CompanionScenePacket::firstSlot,
                    BufCodecs.UUID, CompanionScenePacket::second,
                    BufCodecs.VAR_INT, CompanionScenePacket::secondSlot,
                    BufCodecs.VAR_LONG, CompanionScenePacket::startTime,
                    CompanionScenePacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(CompanionScenePacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(CompanionScenePacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }
}
