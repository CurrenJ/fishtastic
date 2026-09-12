package grill24.fishtastic.blockentity;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How {@link ElectricFishOrganizerBlockEntity} groups the fish it holds into piles: by species
 * (the original, and still default, behavior), quality tier, size bucket, or fishing zone. Mirrors
 * the {@code FishTankShape}-style enum (serialized name, {@link #next()}, a translatable display
 * name) used elsewhere in this codebase for small persisted/synced GUI choices.
 */
public enum OrganizerSortMode implements StringRepresentable {
    SPECIES, QUALITY, SIZE, ZONE;

    public static final StreamCodec<ByteBuf, OrganizerSortMode> STREAM_CODEC = ByteBufCodecs.idMapper(
            i -> OrganizerSortMode.values()[i],
            OrganizerSortMode::ordinal
    );

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The next mode in cycle order (wrapping) — what the GUI's cycle button steps to. */
    public OrganizerSortMode next() {
        OrganizerSortMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /** Localized display name, e.g. "Species" / "Rarity" / "Size" / "Zone". */
    public Component getDisplayName() {
        return Component.translatable("gui.fishtastic.electric_fish_organizer.sort_mode." + getSerializedName());
    }

    public static OrganizerSortMode bySerializedName(String name) {
        for (OrganizerSortMode mode : values()) {
            if (mode.getSerializedName().equals(name)) {
                return mode;
            }
        }
        return SPECIES;
    }
}
