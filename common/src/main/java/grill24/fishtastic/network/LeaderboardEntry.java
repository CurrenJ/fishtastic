package grill24.fishtastic.network;

import grill24.fishtastic.component.FishQuality;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

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
 * </ul>
 */
public record LeaderboardEntry(
        Optional<ResourceLocation> fishType,
        Optional<UUID> playerUuid,
        Optional<String> playerName,
        float size,
        int catchCount,
        FishQuality.Quality quality
) {
    private static final StreamCodec<ByteBuf, FishQuality.Quality> QUALITY_CODEC =
            ByteBufCodecs.VAR_INT.map(
                    i -> FishQuality.Quality.values()[i],
                    Enum::ordinal
            );

    public static final StreamCodec<ByteBuf, LeaderboardEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC),
            LeaderboardEntry::fishType,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
            LeaderboardEntry::playerUuid,
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(256)),
            LeaderboardEntry::playerName,
            ByteBufCodecs.FLOAT,
            LeaderboardEntry::size,
            ByteBufCodecs.VAR_INT,
            LeaderboardEntry::catchCount,
            QUALITY_CODEC,
            LeaderboardEntry::quality,
            LeaderboardEntry::new
    );

    // ---- Convenience constructors ----

    public static LeaderboardEntry personalBestSize(ResourceLocation fishType, float size, FishQuality.Quality quality) {
        return new LeaderboardEntry(Optional.of(fishType), Optional.empty(), Optional.empty(), size, 0, quality);
    }

    public static LeaderboardEntry globalBestSize(ResourceLocation fishType, UUID playerUuid, String playerName,
                                                   float size, FishQuality.Quality quality) {
        return new LeaderboardEntry(Optional.of(fishType), Optional.of(playerUuid), Optional.of(playerName), size, 0, quality);
    }

    public static LeaderboardEntry personalCatchCount(ResourceLocation fishType, int count) {
        return new LeaderboardEntry(Optional.of(fishType), Optional.empty(), Optional.empty(), 0f, count, FishQuality.Quality.COMMON);
    }

    public static LeaderboardEntry globalCatchCount(UUID playerUuid, String playerName, int count) {
        return new LeaderboardEntry(Optional.empty(), Optional.of(playerUuid), Optional.of(playerName), 0f, count, FishQuality.Quality.COMMON);
    }
}
