package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Fishtastic's own moon phase, serialized in fish profile {@code moon_weights}.
 *
 * <p>Mirrors 26.1's {@code MoonPhase}: the same constant names, serialized names and indices
 * (0 = full moon, as in vanilla's moon cycle). It exists because {@code MoonPhase} is new in 26.1,
 * while 1.21.1 and 1.20.1 only have {@code Level#getMoonPhase()} returning that index as an
 * {@code int}. With this enum, {@link FishProfile} and the fish profile JSONs are identical on every
 * MC version, and only {@link #at} differs per branch.
 */
public enum FishMoonPhase implements StringRepresentable {
    FULL_MOON("full_moon"),
    WANING_GIBBOUS("waning_gibbous"),
    THIRD_QUARTER("third_quarter"),
    WANING_CRESCENT("waning_crescent"),
    NEW_MOON("new_moon"),
    WAXING_CRESCENT("waxing_crescent"),
    FIRST_QUARTER("first_quarter"),
    WAXING_GIBBOUS("waxing_gibbous");

    public static final Codec<FishMoonPhase> CODEC = StringRepresentable.fromEnum(FishMoonPhase::values);

    private static final FishMoonPhase[] BY_INDEX = values();

    private final String serializedName;

    FishMoonPhase(String serializedName) {
        this.serializedName = serializedName;
    }

    /** The phase index in vanilla's cycle, 0 (full moon) to 7. */
    public int index() {
        return ordinal();
    }

    /** Maps a vanilla moon phase index (what {@code Level#getMoonPhase()} returns before 26.1). */
    public static FishMoonPhase fromIndex(int index) {
        return BY_INDEX[Math.floorMod(index, BY_INDEX.length)];
    }

    /** The moon phase at {@code pos}. The only version-specific method: 1.21.1 and 1.20.1 read {@code level.getMoonPhase()}. */
    public static FishMoonPhase at(Level level, BlockPos pos) {
        return fromIndex(level.getMoonPhase());
    }

    @Override
    public @NotNull String getSerializedName() {
        return serializedName;
    }
}
