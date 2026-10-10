package grill24.fishtastic.companion;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The pet-to-pet scenes of v1 (docs/fish-companions.md §10): G1 (two companions of the same
 * species loop together, with a bubble heart) and G2 (two free swimmers fly a figure-eight around
 * both owners, one lobe centred on each). The server picks one — see
 * {@code CompanionSceneManager} — and names it in a {@code CompanionScenePacket}; every client
 * plays exactly that scene, so both owners see the same thing.
 *
 * <p>This enum is also where a scene's shared timing lives: the server holds both companions busy
 * for {@link #durationTicks()}, and every client derives its playback phase from the packet's
 * start time and the same tick numbers. That shared arithmetic is what keeps the two owners'
 * views in sync without a single per-tick packet.
 */
public enum CompanionSceneType {
    /** G1: same species — a synchronized loop plus a bubble heart. */
    G1_SAME_SPECIES(120, 40),
    /** G2: two free swimmers — a figure-eight around both owners. */
    G2_FREE_SWIM_EIGHT(200, 100);

    private final int durationTicks;
    private final int pathTicks;

    CompanionSceneType(int durationTicks, int pathTicks) {
        this.durationTicks = durationTicks;
        this.pathTicks = pathTicks;
    }

    /** How long the scene plays — and how long the server marks both companions busy for it. */
    public int durationTicks() {
        return durationTicks;
    }

    /** Ticks for one full pass of the scene's path (one loop, or the whole eight). */
    public int pathTicks() {
        return pathTicks;
    }

    /**
     * Defensive decode: this packet is only ever sent by our own server, so an out-of-range value
     * means a version mismatch — fall back to the gentlest scene rather than crashing the client.
     */
    public static final StreamCodec<ByteBuf, CompanionSceneType> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            i -> i >= 0 && i < values().length ? values()[i] : G1_SAME_SPECIES,
            CompanionSceneType::ordinal);
}
