package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Sent <strong>server → client</strong> in response to an {@link AmbientContextRequestPacket}
 * (Fish in World, Phase 4): the authoritative species context for each water origin the server
 * accepted, produced by the shared {@code FishingPoolResolver} from the server's own equipment and
 * world state.
 *
 * <h2>Shape and bounds</h2>
 * The envelope echoes the request's {@code session} and {@code requestGeneration}. At most
 * {@link #MAX_ENTRIES} entries (one per requested origin) and {@link #MAX_CANDIDATES} candidates
 * per entry; an entry whose candidate list was cut to fit sets {@code truncated}. Every entry
 * echoes the origin's {@code patchKey}, {@code geometryGeneration} and {@code contextGeneration}
 * so the client can discard answers whose tokens no longer match its own state.
 *
 * <p>An entry is either {@link Status#OK} — {@code candidates} holds species ids and final raw
 * weights in pool order, {@code fingerprint} is the Phase 3 species-context fingerprint — or one of
 * the rejection statuses, in which case the client marks that patch invalid until its own geometry
 * changes. Rejections are answers, not errors: the client asked about water that has since changed
 * (or about a namespace it cannot see), and is told so without a packet storm.
 *
 * <p>There is no per-entry "no response" — the server either answers for an origin or the whole
 * request is dropped before any entry is built.
 *
 * <h2>Handling client-side</h2>
 * Register a listener via {@link #registerClientHandler} so the packet can reach client-only code
 * (the ambient context store) without a hard compile-time dependency from common code to client
 * classes.
 */
public record AmbientContextResponsePacket(
        long session,
        int requestGeneration,
        List<Entry> entries
) implements CustomPacketPayload {

    /** Entries per response — one per requested origin ({@link AmbientContextRequestPacket#MAX_ORIGINS}). */
    public static final int MAX_ENTRIES = AmbientContextRequestPacket.MAX_ORIGINS;
    /**
     * Candidates per entry. The standard-worms pool is well under this; a future datapack pool
     * larger than it is truncated from the bottom of the weight ranking, flagged in the entry.
     */
    public static final int MAX_CANDIDATES = 96;

    /** How one origin was answered. */
    public enum Status {
        /** The server resolved the origin and {@code candidates} is authoritative. */
        OK,
        /** The origin's dimension is not the player's current dimension. */
        REJECTED_DIMENSION,
        /** The origin is further from the player than a discovery window could reach. */
        REJECTED_FAR,
        /** No chunk is loaded at the origin; the server never loads one to answer. */
        REJECTED_UNLOADED,
        /**
         * A chunk is loaded there, but the origin cell no longer holds water. Also the wire
         * default for an id no version of this enum ever issued: see {@link #statusById(int)}.
         */
        REJECTED_NOT_WATER
    }

    /**
     * Decodes a status id off the wire, defaulting any unknown id to {@link Status#REJECTED_NOT_WATER}.
     * An unknown id is a malformed or hostile writer's doing, and it must not be a crash surface
     * reachable from the network (a raw array index would throw on it) nor a way to fake {@code OK}.
     * A rejection keeps the client's handling identical to a real refusal: "no known pool" for the
     * tokens the entry carries.
     */
    public static Status statusById(int id) {
        Status[] values = Status.values();
        return id >= 0 && id < values.length ? values[id] : Status.REJECTED_NOT_WATER;
    }

    /** One candidate species with its final raw weight, in pool order. */
    public record Candidate(Identifier species, int weight) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Candidate> STREAM_CODEC =
                StreamCodec.composite(
                        Identifier.STREAM_CODEC, Candidate::species,
                        ByteBufCodecs.VAR_INT, Candidate::weight,
                        Candidate::new
                );
    }

    /**
     * The answer for one origin. {@code sessionSourced} distinguishes a value served from the
     * player's active fishing session snapshot (Phase 3) — which takes precedence for its patch
     * until the session ends — from a fresh resolve. {@code heldRod} records which equipment lane
     * the answer used (a held supported rod vs the no-rod standard-worms baseline); diagnostics
     * for evidence logs, not a token.
     */
    public record Entry(
            long patchKey,
            int geometryGeneration,
            int contextGeneration,
            Status status,
            boolean sessionSourced,
            boolean heldRod,
            boolean truncated,
            long fingerprint,
            List<Candidate> candidates
    ) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.VAR_LONG, Entry::patchKey,
                        ByteBufCodecs.VAR_INT, Entry::geometryGeneration,
                        ByteBufCodecs.VAR_INT, Entry::contextGeneration,
                        ByteBufCodecs.idMapper(AmbientContextResponsePacket::statusById, Status::ordinal), Entry::status,
                        ByteBufCodecs.BOOL, Entry::sessionSourced,
                        ByteBufCodecs.BOOL, Entry::heldRod,
                        ByteBufCodecs.BOOL, Entry::truncated,
                        ByteBufCodecs.VAR_LONG, Entry::fingerprint,
                        Candidate.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CANDIDATES)), Entry::candidates,
                        Entry::new
                );

        /** An entry with no candidates, for one of the rejection statuses. */
        public static Entry rejected(long patchKey, int geometryGeneration, int contextGeneration,
                                     Status status) {
            return new Entry(patchKey, geometryGeneration, contextGeneration, status, false, false,
                    false, 0L, List.of());
        }
    }

    public static final CustomPacketPayload.Type<AmbientContextResponsePacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.AMBIENT_CONTEXT_RESPONSE_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, AmbientContextResponsePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, AmbientContextResponsePacket::session,
                    ByteBufCodecs.VAR_INT, AmbientContextResponsePacket::requestGeneration,
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_ENTRIES)), AmbientContextResponsePacket::entries,
                    AmbientContextResponsePacket::new
            );

    public AmbientContextResponsePacket {
        entries = List.copyOf(entries);
    }

    @Override
    public CustomPacketPayload.Type<AmbientContextResponsePacket> type() {
        return TYPE;
    }

    // -------------------------------------------------------------------------
    // Client handler registration
    // -------------------------------------------------------------------------

    /** Functional interface for receiving context responses on the client. */
    @FunctionalInterface
    public interface ClientHandler {
        void handle(AmbientContextResponsePacket packet);
    }

    private static ClientHandler clientHandler = null;

    /**
     * Register the client-side handler that receives context responses. Call this from your client
     * initialisation code (Fabric/NeoForge client entrypoint).
     */
    public static void registerClientHandler(ClientHandler handler) {
        clientHandler = handler;
    }

    /** Handles the packet on the client side. */
    public static void handleServerToClient(AmbientContextResponsePacket packet,
                                            FishtasticPackets.IPacketContext context) {
        context.enqueueWork(() -> {
            if (clientHandler != null) {
                clientHandler.handle(packet);
            } else {
                Fishtastic.LOGGER.warn("No client handler registered for AmbientContextResponsePacket");
            }
        });
    }
}
