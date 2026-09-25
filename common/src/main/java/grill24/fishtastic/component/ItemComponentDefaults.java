package grill24.fishtastic.component;

import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-item default values for {@link ComponentKey}s, the 1.20.1 stand-in for 1.21.1's
 * {@code Item.Properties#component(type, value)} prototype defaults. {@link grill24.fishtastic.FishtasticItems}
 * registers these once per item after the item itself is registered (properties can't carry them
 * pre-1.20.5), and {@link ComponentKey#get} falls back to them whenever a stack's NBT has no entry —
 * covering every path that produces a stack, not just {@code new ItemStack(item)}.
 */
final class ItemComponentDefaults {
    private static final Map<Item, Map<ComponentKey<?>, Object>> DEFAULTS = new HashMap<>();

    private ItemComponentDefaults() {}

    static <T> void register(Item item, ComponentKey<T> key, T value) {
        DEFAULTS.computeIfAbsent(item, i -> new HashMap<>()).put(key, value);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    static <T> T get(Item item, ComponentKey<T> key) {
        Map<ComponentKey<?>, Object> perItem = DEFAULTS.get(item);
        return perItem == null ? null : (T) perItem.get(key);
    }
}
