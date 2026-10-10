package grill24.fishtastic.tutorial;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The one-step FTUE for fish companions (docs/fish-companions.md §8.4, R7.7): the first time any
 * bond becomes available, a side panel in the encyclopedia introduces the Companions tab; opening
 * that tab completes the step. Kept separate from the other tutorial state machines, like
 * {@link EncyclopediaTutorialStep}.
 */
public enum CompanionTutorialStep {
    NOT_STARTED,
    INTRO,
    COMPLETE;

    public static final Codec<CompanionTutorialStep> CODEC =
            Codec.INT.xmap(i -> values()[i], Enum::ordinal);

    public static final StreamCodec<ByteBuf, CompanionTutorialStep> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

    public boolean hasOverlay() {
        return this == INTRO;
    }
}
