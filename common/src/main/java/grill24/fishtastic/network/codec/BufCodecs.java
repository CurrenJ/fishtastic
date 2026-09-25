package grill24.fishtastic.network.codec;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * Mirrors the subset of {@code net.minecraft.network.codec.ByteBufCodecs} the tree uses. See
 * {@link BufCodec} for why this exists on 1.20.1 and why it landed with B2 instead of B3.
 */
public final class BufCodecs {
    private BufCodecs() {}

    public static final BufCodec<Boolean> BOOL = BufCodec.of(ByteBuf::writeBoolean, ByteBuf::readBoolean);
    public static final BufCodec<Float> FLOAT = BufCodec.of(ByteBuf::writeFloat, ByteBuf::readFloat);
    public static final BufCodec<Integer> INT = BufCodec.of(ByteBuf::writeInt, ByteBuf::readInt);
    public static final BufCodec<Integer> VAR_INT =
            BufCodec.of((buf, v) -> new FriendlyByteBuf(buf).writeVarInt(v), buf -> new FriendlyByteBuf(buf).readVarInt());
    public static final BufCodec<Long> VAR_LONG =
            BufCodec.of((buf, v) -> new FriendlyByteBuf(buf).writeVarLong(v), buf -> new FriendlyByteBuf(buf).readVarLong());
    public static final BufCodec<String> STRING_UTF8 =
            BufCodec.of((buf, v) -> new FriendlyByteBuf(buf).writeUtf(v), buf -> new FriendlyByteBuf(buf).readUtf());
    public static final BufCodec<ResourceLocation> RESOURCE_LOCATION = BufCodec.of(
            (buf, v) -> new FriendlyByteBuf(buf).writeResourceLocation(v),
            buf -> new FriendlyByteBuf(buf).readResourceLocation()
    );
    public static final BufCodec<ItemStack> ITEM_STACK = BufCodec.of(
            (buf, v) -> new FriendlyByteBuf(buf).writeItem(v),
            buf -> new FriendlyByteBuf(buf).readItem()
    );
    public static final BufCodec<BlockPos> BLOCK_POS = BufCodec.of(
            (buf, v) -> new FriendlyByteBuf(buf).writeBlockPos(v),
            buf -> new FriendlyByteBuf(buf).readBlockPos()
    );
    public static final BufCodec<UUID> UUID = BufCodec.of(
            (buf, v) -> new FriendlyByteBuf(buf).writeUUID(v),
            buf -> new FriendlyByteBuf(buf).readUUID()
    );

    public static BufCodec<String> stringUtf8(int maxLength) {
        return BufCodec.of(
                (buf, v) -> new FriendlyByteBuf(buf).writeUtf(v, maxLength),
                buf -> new FriendlyByteBuf(buf).readUtf(maxLength)
        );
    }

    public static <T> BufCodec<Optional<T>> optional(BufCodec<T> elementCodec) {
        return BufCodec.of(
                (buf, value) -> {
                    BOOL.encode(buf, value.isPresent());
                    value.ifPresent(v -> elementCodec.encode(buf, v));
                },
                buf -> BOOL.decode(buf) ? Optional.of(elementCodec.decode(buf)) : Optional.empty()
        );
    }

    public static <T, C extends Collection<T>> BufCodec<C> collection(IntFunction<C> collectionFactory, BufCodec<T> elementCodec) {
        return BufCodec.of(
                (buf, value) -> {
                    VAR_INT.encode(buf, value.size());
                    for (T t : value) elementCodec.encode(buf, t);
                },
                buf -> {
                    int size = VAR_INT.decode(buf);
                    C collection = collectionFactory.apply(size);
                    for (int i = 0; i < size; i++) collection.add(elementCodec.decode(buf));
                    return collection;
                }
        );
    }

    public static <T> Function<BufCodec<T>, BufCodec<List<T>>> list() {
        return elementCodec -> BufCodec.of(
                (buf, value) -> {
                    VAR_INT.encode(buf, value.size());
                    for (T t : value) elementCodec.encode(buf, t);
                },
                buf -> {
                    int size = VAR_INT.decode(buf);
                    List<T> list = new ArrayList<>(size);
                    for (int i = 0; i < size; i++) list.add(elementCodec.decode(buf));
                    return list;
                }
        );
    }

    public static <T> Function<BufCodec<T>, BufCodec<List<T>>> list(int maxSize) {
        return list();
    }

    public static <K, V, M extends Map<K, V>> BufCodec<M> map(IntFunction<M> mapFactory, BufCodec<K> keyCodec, BufCodec<V> valueCodec) {
        return BufCodec.of(
                (buf, value) -> {
                    VAR_INT.encode(buf, value.size());
                    value.forEach((k, v) -> {
                        keyCodec.encode(buf, k);
                        valueCodec.encode(buf, v);
                    });
                },
                buf -> {
                    int size = VAR_INT.decode(buf);
                    M map = mapFactory.apply(size);
                    for (int i = 0; i < size; i++) {
                        K k = keyCodec.decode(buf);
                        V v = valueCodec.decode(buf);
                        map.put(k, v);
                    }
                    return map;
                }
        );
    }

    public static <K, V> BufCodec<Map<K, V>> map(BufCodec<K> keyCodec, BufCodec<V> valueCodec) {
        return BufCodec.of(
                (buf, value) -> {
                    VAR_INT.encode(buf, value.size());
                    value.forEach((k, v) -> {
                        keyCodec.encode(buf, k);
                        valueCodec.encode(buf, v);
                    });
                },
                buf -> {
                    int size = VAR_INT.decode(buf);
                    Map<K, V> map = new HashMap<>(size);
                    for (int i = 0; i < size; i++) {
                        K k = keyCodec.decode(buf);
                        V v = valueCodec.decode(buf);
                        map.put(k, v);
                    }
                    return map;
                }
        );
    }

    private static final String WRAPPER_KEY = "value";

    /** NBT round trip through the buffer, the same shape {@code ByteBufCodecs.fromCodec} produces on other branches. */
    public static <T> BufCodec<T> fromCodec(Codec<T> codec) {
        return BufCodec.of(
                (buf, value) -> {
                    Tag tag = codec.encodeStart(NbtOps.INSTANCE, value).result().orElseThrow();
                    net.minecraft.nbt.CompoundTag wrapper = new net.minecraft.nbt.CompoundTag();
                    wrapper.put(WRAPPER_KEY, tag);
                    new FriendlyByteBuf(buf).writeNbt(wrapper);
                },
                buf -> {
                    net.minecraft.nbt.CompoundTag wrapper = new FriendlyByteBuf(buf).readNbt(NbtAccounter.UNLIMITED);
                    Tag tag = wrapper.get(WRAPPER_KEY);
                    return codec.parse(NbtOps.INSTANCE, tag).result().orElseThrow();
                }
        );
    }

    public static <T> BufCodec<T> idMapper(IntFunction<T> byId, ToIntFunction<T> toId) {
        return BufCodec.of(
                (buf, value) -> VAR_INT.encode(buf, toId.applyAsInt(value)),
                buf -> byId.apply(VAR_INT.decode(buf))
        );
    }
}
