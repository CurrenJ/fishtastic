package grill24.fishtastic.network.codec;

import io.netty.buffer.ByteBuf;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * A 1.20.1 stand-in for 1.21.1's {@code StreamCodec<RegistryFriendlyByteBuf, T>}: MC 1.20.1 has no
 * {@code StreamCodec}, {@code ByteBufCodecs}, {@code CustomPacketPayload} or
 * {@code RegistryFriendlyByteBuf} (docs/backport-pass2/track-b-1.20.1.md, B3.1). Every field that
 * declares {@code StreamCodec<RegistryFriendlyByteBuf, X> STREAM_CODEC} on other branches declares
 * {@code BufCodec<X> STREAM_CODEC} here under the same field name, over a plain {@link ByteBuf}
 * (which {@link net.minecraft.network.FriendlyByteBuf} extends), so call sites don't change.
 *
 * <p>The {@code composite} overloads go to 9 fields (the tree's widest record, {@code
 * StartFishingMinigamePacket}, has 7) to also absorb {@code util/StreamCodecs}' 7-9 field
 * overloads — 1.21.1 needed that helper because vanilla {@code StreamCodec.composite} stops at 6;
 * this shim doesn't have that ceiling, so the helper's call sites become {@code BufCodec.composite}
 * directly and the helper itself is dropped.
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

    /** Mirrors {@code StreamCodec#apply}, e.g. {@code FOO.apply(BufCodecs.list())}. */
    default <R> BufCodec<R> apply(Function<BufCodec<T>, BufCodec<R>> operation) {
        return operation.apply(this);
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

    static <C1, T> BufCodec<T> composite(
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
            BiFunction<C1, C2, T> factory
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

    static <C1, C2, C3, C4, T> BufCodec<T> composite(
            BufCodec<C1> codec1, Function<T, C1> getter1,
            BufCodec<C2> codec2, Function<T, C2> getter2,
            BufCodec<C3> codec3, Function<T, C3> getter3,
            BufCodec<C4> codec4, Function<T, C4> getter4,
            Factory4<C1, C2, C3, C4, T> factory
    ) {
        return of(
                (buf, value) -> {
                    codec1.encode(buf, getter1.apply(value));
                    codec2.encode(buf, getter2.apply(value));
                    codec3.encode(buf, getter3.apply(value));
                    codec4.encode(buf, getter4.apply(value));
                },
                buf -> {
                    C1 c1 = codec1.decode(buf);
                    C2 c2 = codec2.decode(buf);
                    C3 c3 = codec3.decode(buf);
                    C4 c4 = codec4.decode(buf);
                    return factory.create(c1, c2, c3, c4);
                }
        );
    }

    static <C1, C2, C3, C4, C5, T> BufCodec<T> composite(
            BufCodec<C1> codec1, Function<T, C1> getter1,
            BufCodec<C2> codec2, Function<T, C2> getter2,
            BufCodec<C3> codec3, Function<T, C3> getter3,
            BufCodec<C4> codec4, Function<T, C4> getter4,
            BufCodec<C5> codec5, Function<T, C5> getter5,
            Factory5<C1, C2, C3, C4, C5, T> factory
    ) {
        return of(
                (buf, value) -> {
                    codec1.encode(buf, getter1.apply(value));
                    codec2.encode(buf, getter2.apply(value));
                    codec3.encode(buf, getter3.apply(value));
                    codec4.encode(buf, getter4.apply(value));
                    codec5.encode(buf, getter5.apply(value));
                },
                buf -> {
                    C1 c1 = codec1.decode(buf);
                    C2 c2 = codec2.decode(buf);
                    C3 c3 = codec3.decode(buf);
                    C4 c4 = codec4.decode(buf);
                    C5 c5 = codec5.decode(buf);
                    return factory.create(c1, c2, c3, c4, c5);
                }
        );
    }

    static <C1, C2, C3, C4, C5, C6, T> BufCodec<T> composite(
            BufCodec<C1> codec1, Function<T, C1> getter1,
            BufCodec<C2> codec2, Function<T, C2> getter2,
            BufCodec<C3> codec3, Function<T, C3> getter3,
            BufCodec<C4> codec4, Function<T, C4> getter4,
            BufCodec<C5> codec5, Function<T, C5> getter5,
            BufCodec<C6> codec6, Function<T, C6> getter6,
            Factory6<C1, C2, C3, C4, C5, C6, T> factory
    ) {
        return of(
                (buf, value) -> {
                    codec1.encode(buf, getter1.apply(value));
                    codec2.encode(buf, getter2.apply(value));
                    codec3.encode(buf, getter3.apply(value));
                    codec4.encode(buf, getter4.apply(value));
                    codec5.encode(buf, getter5.apply(value));
                    codec6.encode(buf, getter6.apply(value));
                },
                buf -> {
                    C1 c1 = codec1.decode(buf);
                    C2 c2 = codec2.decode(buf);
                    C3 c3 = codec3.decode(buf);
                    C4 c4 = codec4.decode(buf);
                    C5 c5 = codec5.decode(buf);
                    C6 c6 = codec6.decode(buf);
                    return factory.create(c1, c2, c3, c4, c5, c6);
                }
        );
    }

    static <C1, C2, C3, C4, C5, C6, C7, T> BufCodec<T> composite(
            BufCodec<C1> codec1, Function<T, C1> getter1,
            BufCodec<C2> codec2, Function<T, C2> getter2,
            BufCodec<C3> codec3, Function<T, C3> getter3,
            BufCodec<C4> codec4, Function<T, C4> getter4,
            BufCodec<C5> codec5, Function<T, C5> getter5,
            BufCodec<C6> codec6, Function<T, C6> getter6,
            BufCodec<C7> codec7, Function<T, C7> getter7,
            Factory7<C1, C2, C3, C4, C5, C6, C7, T> factory
    ) {
        return of(
                (buf, value) -> {
                    codec1.encode(buf, getter1.apply(value));
                    codec2.encode(buf, getter2.apply(value));
                    codec3.encode(buf, getter3.apply(value));
                    codec4.encode(buf, getter4.apply(value));
                    codec5.encode(buf, getter5.apply(value));
                    codec6.encode(buf, getter6.apply(value));
                    codec7.encode(buf, getter7.apply(value));
                },
                buf -> {
                    C1 c1 = codec1.decode(buf);
                    C2 c2 = codec2.decode(buf);
                    C3 c3 = codec3.decode(buf);
                    C4 c4 = codec4.decode(buf);
                    C5 c5 = codec5.decode(buf);
                    C6 c6 = codec6.decode(buf);
                    C7 c7 = codec7.decode(buf);
                    return factory.create(c1, c2, c3, c4, c5, c6, c7);
                }
        );
    }

    static <C1, C2, C3, C4, C5, C6, C7, C8, T> BufCodec<T> composite(
            BufCodec<C1> codec1, Function<T, C1> getter1,
            BufCodec<C2> codec2, Function<T, C2> getter2,
            BufCodec<C3> codec3, Function<T, C3> getter3,
            BufCodec<C4> codec4, Function<T, C4> getter4,
            BufCodec<C5> codec5, Function<T, C5> getter5,
            BufCodec<C6> codec6, Function<T, C6> getter6,
            BufCodec<C7> codec7, Function<T, C7> getter7,
            BufCodec<C8> codec8, Function<T, C8> getter8,
            Factory8<C1, C2, C3, C4, C5, C6, C7, C8, T> factory
    ) {
        return of(
                (buf, value) -> {
                    codec1.encode(buf, getter1.apply(value));
                    codec2.encode(buf, getter2.apply(value));
                    codec3.encode(buf, getter3.apply(value));
                    codec4.encode(buf, getter4.apply(value));
                    codec5.encode(buf, getter5.apply(value));
                    codec6.encode(buf, getter6.apply(value));
                    codec7.encode(buf, getter7.apply(value));
                    codec8.encode(buf, getter8.apply(value));
                },
                buf -> {
                    C1 c1 = codec1.decode(buf);
                    C2 c2 = codec2.decode(buf);
                    C3 c3 = codec3.decode(buf);
                    C4 c4 = codec4.decode(buf);
                    C5 c5 = codec5.decode(buf);
                    C6 c6 = codec6.decode(buf);
                    C7 c7 = codec7.decode(buf);
                    C8 c8 = codec8.decode(buf);
                    return factory.create(c1, c2, c3, c4, c5, c6, c7, c8);
                }
        );
    }

    static <C1, C2, C3, C4, C5, C6, C7, C8, C9, T> BufCodec<T> composite(
            BufCodec<C1> codec1, Function<T, C1> getter1,
            BufCodec<C2> codec2, Function<T, C2> getter2,
            BufCodec<C3> codec3, Function<T, C3> getter3,
            BufCodec<C4> codec4, Function<T, C4> getter4,
            BufCodec<C5> codec5, Function<T, C5> getter5,
            BufCodec<C6> codec6, Function<T, C6> getter6,
            BufCodec<C7> codec7, Function<T, C7> getter7,
            BufCodec<C8> codec8, Function<T, C8> getter8,
            BufCodec<C9> codec9, Function<T, C9> getter9,
            Factory9<C1, C2, C3, C4, C5, C6, C7, C8, C9, T> factory
    ) {
        return of(
                (buf, value) -> {
                    codec1.encode(buf, getter1.apply(value));
                    codec2.encode(buf, getter2.apply(value));
                    codec3.encode(buf, getter3.apply(value));
                    codec4.encode(buf, getter4.apply(value));
                    codec5.encode(buf, getter5.apply(value));
                    codec6.encode(buf, getter6.apply(value));
                    codec7.encode(buf, getter7.apply(value));
                    codec8.encode(buf, getter8.apply(value));
                    codec9.encode(buf, getter9.apply(value));
                },
                buf -> {
                    C1 c1 = codec1.decode(buf);
                    C2 c2 = codec2.decode(buf);
                    C3 c3 = codec3.decode(buf);
                    C4 c4 = codec4.decode(buf);
                    C5 c5 = codec5.decode(buf);
                    C6 c6 = codec6.decode(buf);
                    C7 c7 = codec7.decode(buf);
                    C8 c8 = codec8.decode(buf);
                    C9 c9 = codec9.decode(buf);
                    return factory.create(c1, c2, c3, c4, c5, c6, c7, c8, c9);
                }
        );
    }

    interface Factory3<C1, C2, C3, T> {
        T create(C1 c1, C2 c2, C3 c3);
    }

    interface Factory4<C1, C2, C3, C4, T> {
        T create(C1 c1, C2 c2, C3 c3, C4 c4);
    }

    interface Factory5<C1, C2, C3, C4, C5, T> {
        T create(C1 c1, C2 c2, C3 c3, C4 c4, C5 c5);
    }

    interface Factory6<C1, C2, C3, C4, C5, C6, T> {
        T create(C1 c1, C2 c2, C3 c3, C4 c4, C5 c5, C6 c6);
    }

    interface Factory7<C1, C2, C3, C4, C5, C6, C7, T> {
        T create(C1 c1, C2 c2, C3 c3, C4 c4, C5 c5, C6 c6, C7 c7);
    }

    interface Factory8<C1, C2, C3, C4, C5, C6, C7, C8, T> {
        T create(C1 c1, C2 c2, C3 c3, C4 c4, C5 c5, C6 c6, C7 c7, C8 c8);
    }

    interface Factory9<C1, C2, C3, C4, C5, C6, C7, C8, C9, T> {
        T create(C1 c1, C2 c2, C3 c3, C4 c4, C5 c5, C6 c6, C7 c7, C8 c8, C9 c9);
    }
}
