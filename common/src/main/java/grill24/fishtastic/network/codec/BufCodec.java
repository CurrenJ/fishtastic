package grill24.fishtastic.network.codec;

import io.netty.buffer.ByteBuf;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A 1.20.1 stand-in for 1.21.1's {@code StreamCodec<RegistryFriendlyByteBuf, T>}: MC 1.20.1 has no
 * {@code StreamCodec}, {@code ByteBufCodecs}, {@code CustomPacketPayload} or
 * {@code RegistryFriendlyByteBuf} (docs/backport-pass2/track-b-1.20.1.md, B3.1). Every field that
 * declares {@code StreamCodec<RegistryFriendlyByteBuf, X> STREAM_CODEC} on other branches declares
 * {@code BufCodec<X> STREAM_CODEC} here under the same field name, over a plain {@link ByteBuf}
 * (which {@link net.minecraft.network.FriendlyByteBuf} extends), so call sites don't change.
 *
 * <p><b>Pulled forward from B3.1:</b> the 9 item-data component records (B2) each declare a
 * {@code STREAM_CODEC} in the same file as their persistent {@code CODEC}, so this shim (and the
 * mechanical rename over those files) had to land with B2 rather than waiting for B3. Only the
 * subset those files use is implemented; B3 fills in the rest of the {@code ByteBufCodecs} surface
 * and the {@code FishtasticPayload} registrars.
 */
public interface BufCodec<T> {
    T decode(ByteBuf buf);
    void encode(ByteBuf buf, T value);

    default <R> BufCodec<R> map(Function<T, R> to, Function<R, T> from) {
        BufCodec<T> self = this;
        return new BufCodec<>() {
            @Override
            public R decode(ByteBuf buf) {
                return to.apply(self.decode(buf));
            }

            @Override
            public void encode(ByteBuf buf, R value) {
                self.encode(buf, from.apply(value));
            }
        };
    }

    static <T> BufCodec<T> of(BiConsumer<ByteBuf, T> encoder, Function<ByteBuf, T> decoder) {
        return new BufCodec<>() {
            @Override
            public T decode(ByteBuf buf) {
                return decoder.apply(buf);
            }

            @Override
            public void encode(ByteBuf buf, T value) {
                encoder.accept(buf, value);
            }
        };
    }

    static <T> BufCodec<T> unit(T value) {
        return of((buf, v) -> {}, buf -> value);
    }

    static <B, C1, T> BufCodec<T> composite(
            BufCodec<C1> codec1, Function<T, C1> getter1,
            Function<C1, T> factory
    ) {
        return of(
                (buf, value) -> codec1.encode(buf, getter1.apply(value)),
                buf -> factory.apply(codec1.decode(buf))
        );
    }

    static <C1, C2, T> BufCodec<T> composite(
            BufCodec<C1> codec1, Function<T, C1> getter1,
            BufCodec<C2> codec2, Function<T, C2> getter2,
            java.util.function.BiFunction<C1, C2, T> factory
    ) {
        return of(
                (buf, value) -> {
                    codec1.encode(buf, getter1.apply(value));
                    codec2.encode(buf, getter2.apply(value));
                },
                buf -> {
                    C1 c1 = codec1.decode(buf);
                    C2 c2 = codec2.decode(buf);
                    return factory.apply(c1, c2);
                }
        );
    }

    static <C1, C2, C3, T> BufCodec<T> composite(
            BufCodec<C1> codec1, Function<T, C1> getter1,
            BufCodec<C2> codec2, Function<T, C2> getter2,
            BufCodec<C3> codec3, Function<T, C3> getter3,
            Factory3<C1, C2, C3, T> factory
    ) {
        return of(
                (buf, value) -> {
                    codec1.encode(buf, getter1.apply(value));
                    codec2.encode(buf, getter2.apply(value));
                    codec3.encode(buf, getter3.apply(value));
                },
                buf -> {
                    C1 c1 = codec1.decode(buf);
                    C2 c2 = codec2.decode(buf);
                    C3 c3 = codec3.decode(buf);
                    return factory.create(c1, c2, c3);
                }
        );
    }

    interface Factory3<C1, C2, C3, T> {
        T create(C1 c1, C2 c2, C3 c3);
    }
}
