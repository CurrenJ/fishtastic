package grill24.fishtastic.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Identifies which of the four leaderboard queries is being requested / returned.
 */
public enum LeaderboardType {
    /** Personal best size per fish type. Requires {@code targetPlayer}. */
    PERSONAL_BEST_SIZE,
    /** Global best size per fish type (one record-holder per fish). */
    GLOBAL_BEST_SIZE,
    /** Personal catch count per fish type. Requires {@code targetPlayer}. */
    PERSONAL_CATCH_COUNT,
    /** Global total catch count per player (across all species). */
    GLOBAL_CATCH_COUNT;

    public static final StreamCodec<ByteBuf, LeaderboardType> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(
                    i -> LeaderboardType.values()[i],
                    Enum::ordinal
            );
}
