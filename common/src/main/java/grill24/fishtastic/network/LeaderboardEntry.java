package grill24.fishtastic.network;

import grill24.fishtastic.component.FishQuality;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A single row in any leaderboard response.
 *
 * <p>Fields are present or absent depending on the {@link LeaderboardType}:
 * <ul>
 *   <li>{@code fishType}   — present for per-fish-type leaderboards (PERSONAL/GLOBAL best size,
 *                             PERSONAL catch count)</li>
 *   <li>{@code playerUuid} / {@code playerName} — present for global leaderboards
 *                             (GLOBAL best size, GLOBAL catch count), used to fetch the player head</li>
 *   <li>{@code size}       — meaningful for size leaderboards (0 otherwise)</li>
 *   <li>{@code catchCount} — meaningful for count leaderboards (0 otherwise)</li>
 *   <li>{@code quality}    — meaningful for size leaderboards (COMMON otherwise)</li>
 *   <li>{@code recentCatches} — the player's latest individual catches, newest first. Only filled
 *                             in for the GLOBAL catch-count podium (the top three rows), which
 *                             renders them as a Fish Pile; empty everywhere else.</li>
 * </ul>
 */
public record LeaderboardEntry(
        Optional<ResourceLocation> fishType,
        Optional<UUID> playerUuid,
        Optional<String> playerName,
        float size,
        int catchCount,
        FishQuality.Quality quality,
        List<RecentCatch> recentCatches
) {
    private static final BufCodec<FishQuality.Quality> QUALITY_CODEC =
            BufCodecs.VAR_INT.map(
                    i -> FishQuality.Quality.values()[i],
                    Enum::ordinal
            );

    public static final BufCodec<LeaderboardEntry> STREAM_CODEC = BufCodec.composite(
            BufCodecs.optional(BufCodecs.RESOURCE_LOCATION),
            LeaderboardEntry::fishType,
            BufCodecs.optional(BufCodecs.UUID),
            LeaderboardEntry::playerUuid,
            BufCodecs.optional(BufCodecs.stringUtf8(256)),
            LeaderboardEntry::playerName,
            BufCodecs.FLOAT,
            LeaderboardEntry::size,
            BufCodecs.VAR_INT,
            LeaderboardEntry::catchCount,
            QUALITY_CODEC,
            LeaderboardEntry::quality,
            RecentCatch.STREAM_CODEC.apply(BufCodecs.list()),
            LeaderboardEntry::recentCatches,
            LeaderboardEntry::new
    );

    // ---- Convenience constructors ----

    public static LeaderboardEntry personalBestSize(ResourceLocation fishType, float size, FishQuality.Quality quality) {
        return new LeaderboardEntry(Optional.of(fishType), Optional.empty(), Optional.empty(), size, 0, quality, List.of());
    }

    public static LeaderboardEntry globalBestSize(ResourceLocation fishType, UUID playerUuid, String playerName,
                                                   float size, FishQuality.Quality quality) {
        return new LeaderboardEntry(Optional.of(fishType), Optional.of(playerUuid), Optional.of(playerName), size, 0, quality, List.of());
    }

    public static LeaderboardEntry personalCatchCount(ResourceLocation fishType, int count) {
        return new LeaderboardEntry(Optional.of(fishType), Optional.empty(), Optional.empty(), 0f, count, FishQuality.Quality.COMMON, List.of());
    }

    public static LeaderboardEntry globalCatchCount(UUID playerUuid, String playerName, int count,
                                                    List<RecentCatch> recentCatches) {
        return new LeaderboardEntry(Optional.empty(), Optional.of(playerUuid), Optional.of(playerName), 0f, count,
                FishQuality.Quality.COMMON, recentCatches);
    }
}
