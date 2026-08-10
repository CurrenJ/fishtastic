package grill24.fishtastic.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

public record HookEffect(
        float qualityBias,
        float trashChanceDelta
) {
    public static final HookEffect HOOK = new HookEffect(0.5f, -0.04f);
    public static final HookEffect OLD_COPPER_HOOK = new HookEffect(-0.5f, 0.08f);

    public static final Codec<HookEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("quality_bias", 0.0f).forGetter(HookEffect::qualityBias),
            Codec.FLOAT.optionalFieldOf("trash_chance_delta", 0.0f).forGetter(HookEffect::trashChanceDelta)
    ).apply(i, HookEffect::new));

    public static final StreamCodec<ByteBuf, HookEffect> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    // Qualitative, same reasoning as BaitEffect's tooltip lines — sign is what the player
    // actually experiences, the exact magnitude isn't something they can reason about in play.
    public List<Component> tooltipLines() {
        List<Component> lines = new ArrayList<>();
        if (qualityBias != 0f) {
            String key = qualityBias > 0
                    ? "tooltip.fishtastic.hook_effect.quality_bias_positive"
                    : "tooltip.fishtastic.hook_effect.quality_bias_negative";
            lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
        if (trashChanceDelta != 0f) {
            String key = trashChanceDelta > 0
                    ? "tooltip.fishtastic.hook_effect.trash_chance_delta_more"
                    : "tooltip.fishtastic.hook_effect.trash_chance_delta_less";
            lines.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
        return lines;
    }
}
