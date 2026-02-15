package grill24.fishtastic.itemeffect.condition;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.itemeffect.ItemEffectCondition;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record ComponentCondition(ResourceLocation component) implements ItemEffectCondition {
    public static final MapCodec<ComponentCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("component").forGetter(ComponentCondition::component)
            ).apply(instance, ComponentCondition::new)
    );

    @Override
    public boolean matches(ItemStack stack) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(component);
        return type != null && stack.has(type);
    }

    @Override
    public String getType() {
        return "has_component";
    }
}
