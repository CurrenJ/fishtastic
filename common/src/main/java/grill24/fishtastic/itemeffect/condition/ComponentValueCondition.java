package grill24.fishtastic.itemeffect.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.itemeffect.ItemEffectCondition;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
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
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(component);
        if (type == null) return false;

        Object componentValue = stack.get(type);
        if (componentValue == null) return false;

        return matchesComponentValue(type, componentValue);
    }

    @SuppressWarnings("unchecked")
    private <T> boolean matchesComponentValue(DataComponentType<T> type, Object componentValue) {
        Codec<T> codec = type.codec();
        if (codec == null) return false;

        try {
            JsonElement json = codec.encodeStart(JsonOps.INSTANCE, (T) componentValue)
                    .result().orElse(null);

            if (json == null) return false;

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
