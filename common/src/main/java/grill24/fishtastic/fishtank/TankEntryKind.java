package grill24.fishtastic.fishtank;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What kind of entry a {@code RemoveTankEntryPacket} targets within one tank segment. */
public enum TankEntryKind {
    FISH, COSMETIC, STRUCTURE_COSMETIC;

    public static final StreamCodec<ByteBuf, TankEntryKind> STREAM_CODEC = ByteBufCodecs.idMapper(
            i -> TankEntryKind.values()[i],
            TankEntryKind::ordinal
    );
}
