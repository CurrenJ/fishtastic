package grill24.fishtastic.itemeffect.condition;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.itemeffect.ItemEffectCondition;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public record AndCondition(List<ItemEffectCondition> conditions) implements ItemEffectCondition {
    public static final MapCodec<AndCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ItemEffectCondition.DISPATCH_CODEC.listOf().fieldOf("conditions").forGetter(AndCondition::conditions)
            ).apply(instance, AndCondition::new)
    );

    @Override
    public boolean matches(ItemStack stack) {
        return conditions.stream().allMatch(c -> c.matches(stack));
    }

    @Override
    public String getType() {
        return "and";
    }
}
