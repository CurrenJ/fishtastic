package grill24.fishtastic.itemeffect.condition;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.itemeffect.ItemEffectCondition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public record ComponentCondition(Identifier component) implements ItemEffectCondition {
    public static final MapCodec<ComponentCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Identifier.CODEC.fieldOf("component").forGetter(ComponentCondition::component)
            ).apply(instance, ComponentCondition::new)
    );

    @Override
    public boolean matches(ItemStack stack) {
        return FishtasticItemData.hasById(stack, component);
    }

    @Override
    public String getType() {
        return "has_component";
    }
}
