package grill24.fishtastic.tutorial;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;

public enum TutorialStep {
    BAIT_LOAD,
    WAITING_FOR_CAST,
    HOOK_IN_WATER,
    MINIGAME_INTRO,
    MINIGAME_CONTROL,
    MINIGAME_CATCH,
    CATCH_RESULT,
    QUEST_INTRO,
    QUEST_CLAIM,
    SHOP_BROWSE,
    COMPLETE,
    // Append only: saved tutorials and packets use the original ordinal values.
    NOT_STARTED,
    ENCYCLOPEDIA_OFFER,
    ENCYCLOPEDIA_OPEN;

    public static final Codec<TutorialStep> CODEC =
            Codec.INT.xmap(i -> values()[i], Enum::ordinal);

    public static final BufCodec<TutorialStep> STREAM_CODEC =
            BufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

    public boolean hasOverlay() {
        return this != COMPLETE && this != NOT_STARTED;
    }

    public boolean pausesMinigame() {
        return this == MINIGAME_INTRO || this == MINIGAME_CONTROL;
    }
}
