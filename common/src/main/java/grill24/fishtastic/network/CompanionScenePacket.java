package grill24.fishtastic.network;

import grill24.fishtastic.companion.CompanionSceneType;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/**
 * Server → client: "these two companions are playing this scene, starting at this game time"
 * (docs/fish-companions.md §10, §11). One of these per scene is the whole network cost: the
 * server schedules, and each receiving client plays the identical scene from an absolute start
 * time, so the two owners' views can't drift apart.
 *
 * <p>Sent to every player near either participant — the companions are only ever visible up
 * close, so a scene resolving across the world is not worth a packet.
 *
 * <p>A companion is (owner, party slot) (docs/fish-companions-party.md §5.2); a group takes part
 * through its leader's slot.
 */
public record CompanionScenePacket(CompanionSceneType scene, UUID first, int firstSlot, UUID second, int secondSlot,
                                   long startTime) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CompanionScenePacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.COMPANION_SCENE_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionScenePacket> STREAM_CODEC =
            StreamCodec.composite(
                    CompanionSceneType.STREAM_CODEC, CompanionScenePacket::scene,
                    UUIDUtil.STREAM_CODEC, CompanionScenePacket::first,
                    ByteBufCodecs.VAR_INT, CompanionScenePacket::firstSlot,
                    UUIDUtil.STREAM_CODEC, CompanionScenePacket::second,
                    ByteBufCodecs.VAR_INT, CompanionScenePacket::secondSlot,
                    ByteBufCodecs.VAR_LONG, CompanionScenePacket::startTime,
                    CompanionScenePacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(CompanionScenePacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(CompanionScenePacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }
}
