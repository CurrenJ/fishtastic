package grill24.fishtastic.component;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import grill24.fishtastic.util.Ids;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A 1.20.1-only stand-in for a 1.21.1 {@code DataComponentType<T>}: identifies one root-level key
 * in an {@link ItemStack}'s NBT tag, with a {@link Codec} to encode/decode the value through
 * {@link NbtOps#INSTANCE}. {@link grill24.fishtastic.FishtasticDataComponents} declares one
 * {@code ComponentKey<T>} per field, under the same field names the 1.21.1 branch uses, so every
 * call site elsewhere in the tree (routed through {@link grill24.fishtastic.FishtasticItemData})
 * is unchanged.
 *
 * <p>Semantics mirror vanilla data components: {@link #get} falls back to the item's registered
 * default ({@link ItemComponentDefaults}) when the tag has no entry, and {@link #set} drops the key
 * entirely when the value being set equals that default (normalization), so stacking
 * ({@code isSameItemSameTags}) behaves the same as {@code isSameItemSameComponents} would.
 */
public final class ComponentKey<T> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ResourceLocation, ComponentKey<?>> BY_ID = new LinkedHashMap<>();

    public final ResourceLocation id;
    /** The root-level NBT key this component is stored under: {@code id.toString()}. */
    public final String nbtKey;
    public final Codec<T> codec;

    private ComponentKey(ResourceLocation id, Codec<T> codec) {
        this.id = id;
        this.nbtKey = id.toString();
        this.codec = codec;
    }

    public static <T> ComponentKey<T> of(String path, Codec<T> codec) {
        ComponentKey<T> key = new ComponentKey<>(Ids.of("fishtastic", path), codec);
        BY_ID.put(key.id, key);
        return key;
    }

    @Nullable
    public static ComponentKey<?> byId(ResourceLocation id) {
        return BY_ID.get(id);
    }

    /** Registers {@code value} as {@code item}'s prototype default for this component (B2.1). */
    public void registerDefault(Item item, T value) {
        ItemComponentDefaults.register(item, this, value);
    }

    /** The stored value, the item's default if the tag has no entry, or null if neither exists. */
    @Nullable
    public T get(ItemStack stack) {
        if (stack.isEmpty()) return null;
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(nbtKey)) {
            T decoded = decode(tag.get(nbtKey));
            if (decoded != null) return decoded;
        }
        return ItemComponentDefaults.get(stack.getItem(), this);
    }

    public T getOrDefault(ItemStack stack, T fallback) {
        T value = get(stack);
        return value != null ? value : fallback;
    }

    public boolean has(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return (tag != null && tag.contains(nbtKey)) || ItemComponentDefaults.get(stack.getItem(), this) != null;
    }

    /** Sets the value, dropping the key if it equals the item's default (normalization). Returns the previous value. */
    @Nullable
    public T set(ItemStack stack, @Nullable T value) {
        T previous = get(stack);
        if (value == null) {
            remove(stack);
            return previous;
        }

        T itemDefault = ItemComponentDefaults.get(stack.getItem(), this);
        if (value.equals(itemDefault)) {
            remove(stack);
            return previous;
        }

        Tag encoded = encode(value);
        if (encoded != null) {
            stack.getOrCreateTag().put(nbtKey, encoded);
        }
        return previous;
    }

    public void remove(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return;
        tag.remove(nbtKey);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    @Nullable
    private T decode(Tag nbt) {
        DataResult<T> result = codec.parse(NbtOps.INSTANCE, nbt);
        if (result.result().isEmpty()) {
            LOGGER.warn("Failed to decode component '{}': {}", id, result.error().orElseThrow().message());
            return null;
        }
        return result.result().orElse(null);
    }

    @Nullable
    private Tag encode(T value) {
        DataResult<Tag> result = codec.encodeStart(NbtOps.INSTANCE, value);
        if (result.result().isEmpty()) {
            LOGGER.warn("Failed to encode component '{}': {}", id, result.error().orElseThrow().message());
            return null;
        }
        return result.result().orElse(null);
    }
}
