package grill24.fishtastic.itemeffect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import grill24.fishtastic.itemeffect.condition.AndCondition;
import grill24.fishtastic.itemeffect.condition.ComponentCondition;
import grill24.fishtastic.itemeffect.condition.ComponentValueCondition;
import grill24.fishtastic.itemeffect.condition.ItemTagCondition;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public interface ItemEffectCondition {
    // Lazy initialization to avoid circular dependency
    class Codecs {
        private static final Map<String, Supplier<MapCodec<? extends ItemEffectCondition>>> CONDITION_TYPE_SUPPLIERS = Map.of(
                "item_tag", () -> ItemTagCondition.CODEC,
                "has_component", () -> ComponentCondition.CODEC,
                "component_value", () -> ComponentValueCondition.CODEC,
                "and", () -> AndCondition.CODEC
        );

        private static final Map<String, MapCodec<? extends ItemEffectCondition>> CONDITION_TYPES = new ConcurrentHashMap<>();

        private static MapCodec<? extends ItemEffectCondition> getConditionCodec(String type) {
            // Avoid ConcurrentHashMap.computeIfAbsent — Java 17+ throws
            // "Recursive update" if a concurrent thread modifies the same hash
            // bin while the mapping function runs (as happens with ParallelMapTransform).
            // get → compute → putIfAbsent is safe because the computation is
            // idempotent and codec objects are stateless.
            MapCodec<? extends ItemEffectCondition> cached = CONDITION_TYPES.get(type);
            if (cached != null) return cached;

            Supplier<MapCodec<? extends ItemEffectCondition>> supplier = CONDITION_TYPE_SUPPLIERS.get(type);
            if (supplier == null) {
                throw new IllegalArgumentException("Unknown item effect condition type: " + type);
            }
            MapCodec<? extends ItemEffectCondition> computed = supplier.get();
            MapCodec<? extends ItemEffectCondition> winner = CONDITION_TYPES.putIfAbsent(type, computed);
            return winner != null ? winner : computed;
        }

        public static final Codec<ItemEffectCondition> DISPATCH_CODEC = Codec.STRING.dispatch(
                "type",
                ItemEffectCondition::getType,
                Codecs::getConditionCodec
        );
    }

    Codec<ItemEffectCondition> DISPATCH_CODEC = Codecs.DISPATCH_CODEC;

    boolean matches(ItemStack stack);

    String getType();
}
