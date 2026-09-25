package grill24.fishtastic.network.codec;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

/**
 * Mirrors the subset of {@code net.minecraft.network.codec.ByteBufCodecs} the tree uses. See
 * {@link BufCodec} for why this exists on 1.20.1 and why it landed with B2 instead of B3.
 */
public final class BufCodecs {
    private BufCodecs() {}

    public static final BufCodec<Float> FLOAT = BufCodec.of(ByteBuf::writeFloat, ByteBuf::readFloat);

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
                (buf, value) -> new FriendlyByteBuf(buf).writeVarInt(toId.applyAsInt(value)),
                buf -> byId.apply(new FriendlyByteBuf(buf).readVarInt())
        );
    }
}
