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

public record CharmEffect(float inputForceMultiplier) {

    public static final CharmEffect AMETHYST_CHARM = new CharmEffect(1.5f);

    public static final Codec<CharmEffect> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.optionalFieldOf("input_force_multiplier", 1.0f).forGetter(CharmEffect::inputForceMultiplier)
    ).apply(i, CharmEffect::new));

    public static final StreamCodec<ByteBuf, CharmEffect> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    public List<Component> tooltipLines() {
        List<Component> lines = new ArrayList<>();
        if (inputForceMultiplier != 1.0f) {
            lines.add(Component.translatable("tooltip.fishtastic.charm_effect.input_force_multiplier", inputForceMultiplier)
                    .withStyle(ChatFormatting.AQUA));
        }
        return lines;
    }
}
