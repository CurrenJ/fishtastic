package grill24.fishtastic.component;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The 1.20.1 stand-in for 1.21.1's {@code DataComponentPatch} in quest/shop reward data
 * (docs/backport-pass2/track-b-1.20.1.md B2.5). Keeps the JSON shape byte-identical:
 * {@code "components": {"fishtastic:fish_tank_shape": "ornate", ...}}. Only
 * {@code fishtastic:fish_tank_shape} and {@code fishtastic:fish_tank_materials} ever appear in the
 * 94 data files that use this, so an unknown id is a hard decode error — the data is ours.
 * {@code "!id"} removal entries aren't supported (none are used).
 *
 * <p>{@link #CODEC} is hand-written rather than a combinator chain: which codec decodes each map
 * value depends on that entry's own key (looked up via {@link ComponentKey#byId}), so the values
 * can't be decoded generically before the keys are known.
 */
public final class FishtasticItemPatch {
    public static final FishtasticItemPatch EMPTY = new FishtasticItemPatch(Map.of());

    public static final Codec<FishtasticItemPatch> CODEC = new Codec<>() {
        @Override
        public <T> DataResult<Pair<FishtasticItemPatch, T>> decode(DynamicOps<T> ops, T input) {
            DataResult<MapLike<T>> mapResult = ops.getMap(input);
            if (mapResult.result().isEmpty()) {
                return DataResult.error(mapResult.error().orElseThrow()::message);
            }
            MapLike<T> map = mapResult.result().orElseThrow();

            Map<ComponentKey<?>, Object> decoded = new LinkedHashMap<>();
            var entries = map.entries().iterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                DataResult<ResourceLocation> idResult = ResourceLocation.CODEC.parse(ops, entry.getFirst());
                if (idResult.result().isEmpty()) {
                    return DataResult.error(idResult.error().orElseThrow()::message);
                }
                ResourceLocation id = idResult.result().orElseThrow();
                ComponentKey<?> key = ComponentKey.byId(id);
                if (key == null) {
                    return DataResult.error(() -> "Unknown component id in item patch: " + id);
                }
                DataResult<?> valueResult = key.codec.parse(ops, entry.getSecond());
                if (valueResult.result().isEmpty()) {
                    String message = valueResult.error().orElseThrow().message();
                    return DataResult.error(() -> "Failed to decode '" + id + "': " + message);
                }
                decoded.put(key, valueResult.result().orElseThrow());
            }

            return DataResult.success(Pair.of(new FishtasticItemPatch(decoded), ops.empty()));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> DataResult<T> encode(FishtasticItemPatch input, DynamicOps<T> ops, T prefix) {
            RecordBuilder<T> builder = ops.mapBuilder();
            for (Map.Entry<ComponentKey<?>, Object> entry : input.entries.entrySet()) {
                ComponentKey<Object> key = (ComponentKey<Object>) entry.getKey();
                T idEncoded = ResourceLocation.CODEC.encodeStart(ops, key.id).result().orElseThrow();
                T valueEncoded = key.codec.encodeStart(ops, entry.getValue()).result().orElseThrow();
                builder.add(idEncoded, valueEncoded);
            }
            return builder.build(prefix);
        }
    };

    private final Map<ComponentKey<?>, Object> entries;

    private FishtasticItemPatch(Map<ComponentKey<?>, Object> entries) {
        this.entries = entries;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(ComponentKey<T> key) {
        return (T) entries.get(key);
    }

    public void applyTo(ItemStack stack) {
        entries.forEach((key, value) -> setUnchecked(key, stack, value));
    }

    @SuppressWarnings("unchecked")
    private static <T> void setUnchecked(ComponentKey<T> key, ItemStack stack, Object value) {
        key.set(stack, (T) value);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<ComponentKey<?>, Object> entries = new LinkedHashMap<>();

        public <T> Builder set(ComponentKey<T> key, T value) {
            entries.put(key, value);
            return this;
        }

        public FishtasticItemPatch build() {
            return new FishtasticItemPatch(Map.copyOf(entries));
        }
    }
}
