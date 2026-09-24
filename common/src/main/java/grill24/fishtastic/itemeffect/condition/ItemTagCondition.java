package grill24.fishtastic.itemeffect.condition;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.itemeffect.ItemEffectCondition;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record ItemTagCondition(ResourceLocation tag) implements ItemEffectCondition {
    public static final MapCodec<ItemTagCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("tag").forGetter(ItemTagCondition::tag)
            ).apply(instance, ItemTagCondition::new)
    );

    @Override
    public boolean matches(ItemStack stack) {
        return stack.is(TagKey.create(Registries.ITEM, tag));
    }

    @Override
    public String getType() {
        return "item_tag";
    }
}
