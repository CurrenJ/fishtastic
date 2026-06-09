package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record QuestReward(int questTokens, List<ItemStack> items) {
    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("quest_tokens", 0).forGetter(QuestReward::questTokens),
            ItemStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(QuestReward::items)
    ).apply(i, QuestReward::new));
}
