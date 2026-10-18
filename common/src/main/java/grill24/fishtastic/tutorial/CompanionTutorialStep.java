package grill24.fishtastic.tutorial;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Companion lessons. Append new steps: existing saves encode NOT_STARTED/INTRO/COMPLETE as 0/1/2.
 */
public enum CompanionTutorialStep {
    NOT_STARTED,
    INTRO,
    COMPLETE,
    EQUIP,
    RETURN,
    REEQUIP,
    LEAD,
    RENAME,
    GROUPS,
    PILES;

    public static final Codec<CompanionTutorialStep> CODEC =
            Codec.INT.xmap(i -> values()[i], Enum::ordinal);

    public static final StreamCodec<ByteBuf, CompanionTutorialStep> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

    public boolean hasOverlay() {
        return this != NOT_STARTED && this != COMPLETE;
    }

    /** These explanations may be acknowledged without requiring extra fish or a custom name. */
    public boolean canContinue() {
        return this == LEAD || this == RENAME || this == GROUPS || this == PILES;
    }

    public int lessonNumber() {
        return switch (this) {
            case EQUIP -> 1;
            case RETURN -> 2;
            case REEQUIP -> 3;
            case LEAD -> 4;
            case RENAME -> 5;
            case GROUPS -> 6;
            case PILES -> 7;
            default -> 0;
        };
    }
}
