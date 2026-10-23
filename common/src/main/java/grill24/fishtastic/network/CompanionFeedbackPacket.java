package grill24.fishtastic.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client result of a companion request (docs/fish-companions.md). The Companions tab
 * needs to tell "your bond request went through but the fish was gone" apart from silence — the
 * attachment sync alone can't say why nothing changed. Carries no data beyond the outcome; the UI
 * turns it into a line of text.
 */
public record CompanionFeedbackPacket(Outcome outcome) implements CustomPacketPayload {

    /** Outcomes the UI can explain; mirrors {@code Companions.BondResult} plus rename. */
    public enum Outcome {
        BONDED,
        NOT_A_FISH,
        LOCKED,
        NOT_CARRIED,
        RENAMED,
        NOTHING_TO_RENAME,
        INVENTORY_FULL,
        PARTY_FULL
    }

    /** Maps the server's bond result onto the wire outcome, exhaustively (no name() coupling). */
    public static Outcome of(grill24.fishtastic.companion.Companions.BondResult result) {
        return switch (result) {
            case BONDED -> Outcome.BONDED;
            case NOT_A_FISH -> Outcome.NOT_A_FISH;
            case LOCKED -> Outcome.LOCKED;
            case NOT_CARRIED -> Outcome.NOT_CARRIED;
            case INVENTORY_FULL -> Outcome.INVENTORY_FULL;
            case PARTY_FULL -> Outcome.PARTY_FULL;
        };
    }

    public static final CustomPacketPayload.Type<CompanionFeedbackPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.COMPANION_FEEDBACK_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionFeedbackPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT.map(i -> Outcome.values()[i], Outcome::ordinal),
                    CompanionFeedbackPacket::outcome,
                    CompanionFeedbackPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static ClientHandler clientHandler;

    public interface ClientHandler {
        void handle(CompanionFeedbackPacket packet);
    }

    public static void registerClientHandler(ClientHandler h) {
        clientHandler = h;
    }

    public static void handleServerToClient(CompanionFeedbackPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (clientHandler != null) clientHandler.handle(packet);
        });
    }
}
