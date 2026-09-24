package grill24.fishtastic.itemeffect.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.itemeffect.ItemEffectCondition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record ComponentValueCondition(ResourceLocation component, String field, String value) implements ItemEffectCondition {
    public static final MapCodec<ComponentValueCondition> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    ResourceLocation.CODEC.fieldOf("component").forGetter(ComponentValueCondition::component),
                    Codec.STRING.fieldOf("field").forGetter(ComponentValueCondition::field),
                    Codec.STRING.fieldOf("value").forGetter(ComponentValueCondition::value)
            ).apply(instance, ComponentValueCondition::new)
    );

    @Override
    public boolean matches(ItemStack stack) {
        JsonElement json = FishtasticItemData.encodeById(stack, component);
        if (json == null) return false;

        try {
            if (json.isJsonObject()) {
                JsonObject obj = json.getAsJsonObject();
                if (obj.has(field)) {
                    return obj.get(field).getAsString().equals(value);
                }
            } else if (json.isJsonPrimitive() && field.isEmpty()) {
                return json.getAsString().equals(value);
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getType() {
        return "component_value";
    }
}
