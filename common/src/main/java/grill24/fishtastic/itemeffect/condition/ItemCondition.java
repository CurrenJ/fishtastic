package grill24.fishtastic.itemeffect.condition;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.itemeffect.ItemEffectCondition;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record ItemCondition(ResourceLocation item) implements ItemEffectCondition {
    public static final MapCodec<ItemCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("item").forGetter(ItemCondition::item)
            ).apply(instance, ItemCondition::new)
    );

    @Override
    public boolean matches(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(item);
    }

    @Override
    public String getType() {
        return "item";
    }
}
