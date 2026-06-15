package grill24.fishtastic.tutorial;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

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
    COMPLETE;

    public static final Codec<TutorialStep> CODEC =
            Codec.INT.xmap(i -> values()[i], Enum::ordinal);

    public static final StreamCodec<ByteBuf, TutorialStep> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(i -> values()[i], Enum::ordinal);

    public boolean hasOverlay() {
        return this != COMPLETE;
    }

    public boolean pausesMinigame() {
        return this == MINIGAME_INTRO;
    }
}
