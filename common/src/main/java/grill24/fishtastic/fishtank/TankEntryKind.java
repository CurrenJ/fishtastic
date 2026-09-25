package grill24.fishtastic.fishtank;

import io.netty.buffer.ByteBuf;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.network.codec.BufCodec;

/** What kind of entry a {@code RemoveTankEntryPacket} targets within one tank segment. */
public enum TankEntryKind {
    FISH, COSMETIC, STRUCTURE_COSMETIC;

    public static final BufCodec<TankEntryKind> STREAM_CODEC = BufCodecs.idMapper(
            i -> TankEntryKind.values()[i],
            TankEntryKind::ordinal
    );
}
