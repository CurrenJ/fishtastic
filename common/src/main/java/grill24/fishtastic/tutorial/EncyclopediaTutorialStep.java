package grill24.fishtastic.tutorial;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Independent FTUE for the Fish Encyclopedia screen — see {@link EncyclopediaTutorialManager}
 * for why this is a separate state machine from {@link TutorialStep} rather than an extension
 * of it.
 */
public enum EncyclopediaTutorialStep {
    NOT_STARTED,
    INTRO,
    ZONES_AND_REWARDS,
    COMPLETE;

    public static final Codec<EncyclopediaTutorialStep> CODEC =
            Codec.INT.xmap(i -> values()[i], Enum::ordinal);

    public static final StreamCodec<ByteBuf, EncyclopediaTutorialStep> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

    public boolean hasOverlay() {
        return this == INTRO || this == ZONES_AND_REWARDS;
    }
}
