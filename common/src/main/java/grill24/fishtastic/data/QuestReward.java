package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record QuestReward(int questTokens, List<QuestReward.RewardItem> items) {
    /**
     * Plain {@code id`+`count} pair — deliberately does NOT construct an {@link ItemStack}
     * during decode. {@code ItemStack}'s constructor reads the item's default
     * {@link net.minecraft.core.component.DataComponentMap} off its registry {@code Holder},
     * which isn't "bound" yet (see {@code Holder.Reference#bindComponents}) while this quest
     * datapack registry loads in parallel with the item registry during world creation — it
     * throws "Components not bound yet". Call {@link #toStack()} later, once actually granting
     * the reward to a player (well after bootstrap), not at parse time.
     */
    public record RewardItem(Item item, int count) {
        static final Codec<RewardItem> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(RewardItem::item),
                Codec.INT.fieldOf("count").forGetter(RewardItem::count)
        ).apply(i, RewardItem::new));

        public ItemStack toStack() {
            return new ItemStack(item, count);
        }
    }

    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("quest_tokens", 0).forGetter(QuestReward::questTokens),
            RewardItem.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(QuestReward::items)
    ).apply(i, QuestReward::new));
}
