package grill24.fishtastic.network;

import grill24.fishtastic.server.FishObservation;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Client claim that a spyglass was held on a fish; the server validates all evidence. */
public record ObserveFishPacket(Kind kind, ResourceLocation species, UUID school, UUID member, long fishId,
                                int chunkX, int chunkZ, int slot, long lifespanIndex, BlockPos home)
        implements FishtasticPayload {

    public enum Kind { VISIT, AMBIENT }

    private static final UUID NONE = new UUID(0L, 0L);

    public static final FishtasticPayload.PayloadType<ObserveFishPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.OBSERVE_FISH_ID);

    public static final BufCodec<ObserveFishPacket> STREAM_CODEC = BufCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind().ordinal());
                BufCodecs.RESOURCE_LOCATION.encode(buf, p.species());
                if (p.kind() == Kind.VISIT) {
                    BufCodecs.UUID.encode(buf, p.school());
                    BufCodecs.UUID.encode(buf, p.member());
                } else {
                    buf.writeLong(p.fishId());
                    buf.writeInt(p.chunkX());
                    buf.writeInt(p.chunkZ());
                    BufCodecs.VAR_INT.encode(buf, p.slot());
                    buf.writeLong(p.lifespanIndex());
                    BufCodecs.BLOCK_POS.encode(buf, p.home());
                }
            },
            buf -> {
                int ordinal = buf.readUnsignedByte();
                if (ordinal >= Kind.values().length) throw new IllegalArgumentException("unknown observation kind " + ordinal);
                Kind kind = Kind.values()[ordinal];
                ResourceLocation species = BufCodecs.RESOURCE_LOCATION.decode(buf);
                if (kind == Kind.VISIT) {
                    return visit(species, BufCodecs.UUID.decode(buf), BufCodecs.UUID.decode(buf));
                }
                long fishId = buf.readLong();
                int chunkX = buf.readInt(), chunkZ = buf.readInt(), slot = BufCodecs.VAR_INT.decode(buf);
                long index = buf.readLong();
                return ambient(species, fishId, chunkX, chunkZ, slot, index, BufCodecs.BLOCK_POS.decode(buf));
            });

    public static ObserveFishPacket visit(ResourceLocation species, UUID school, UUID member) {
        return new ObserveFishPacket(Kind.VISIT, species, school, member, 0L, 0, 0, 0, 0L, BlockPos.ZERO);
    }

    public static ObserveFishPacket ambient(ResourceLocation species, long fishId, int chunkX, int chunkZ, int slot,
                                            long lifespanIndex, BlockPos home) {
        return new ObserveFishPacket(Kind.AMBIENT, species, NONE, NONE, fishId, chunkX, chunkZ, slot, lifespanIndex, home);
    }

    @Override
    public FishtasticPayload.PayloadType<?> type() { return TYPE; }

    public static void handleClientToServer(ObserveFishPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player) FishObservation.handle(player, packet);
        });
    }
}