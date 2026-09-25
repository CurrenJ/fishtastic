package grill24.fishtastic.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.component.FishtasticItemPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record QuestReward(int questTokens, List<QuestReward.RewardItem> items) {
    /**
     * Plain {@code id`+`count} pair — deliberately does NOT construct an {@link ItemStack}
     * during decode. On 1.21.1, {@code ItemStack}'s constructor reads the item's default
     * {@code DataComponentMap} off its registry {@code Holder}, which isn't "bound" yet while this
     * quest datapack registry loads in parallel with the item registry during world creation; on
     * 1.20.1 there's no such bootstrap ordering hazard (components are plain NBT), but the shape is
     * kept identical across branches. Call {@link #toStack()} later, once actually granting the
     * reward to a player, not at parse time.
     */
    public record RewardItem(Item item, int count, FishtasticItemPatch components) {
        static final Codec<RewardItem> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("id").forGetter(RewardItem::item),
                Codec.INT.fieldOf("count").forGetter(RewardItem::count),
                // A raw component patch, not an ItemStack — see the class note above. The patch is
                // inert data at decode time, so it dodges any bootstrap-ordering problem while still
                // letting a quest hand out a configured item: most usefully a pre-built fish tank
                // carrying fishtastic:fish_tank_materials, which is how capstone quests award a
                // distinctive tank without needing a bespoke item.
                FishtasticItemPatch.CODEC.optionalFieldOf("components", FishtasticItemPatch.EMPTY)
                        .forGetter(RewardItem::components)
        ).apply(i, RewardItem::new));

        public RewardItem(Item item, int count) {
            this(item, count, FishtasticItemPatch.EMPTY);
        }

        public ItemStack toStack() {
            ItemStack stack = new ItemStack(item, count);
            FishtasticItemData.applyPatch(stack, components);
            return stack;
        }
    }

    public static final Codec<QuestReward> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.optionalFieldOf("quest_tokens", 0).forGetter(QuestReward::questTokens),
            RewardItem.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(QuestReward::items)
    ).apply(i, QuestReward::new));
}
