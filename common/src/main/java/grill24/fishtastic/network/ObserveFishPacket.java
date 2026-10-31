package grill24.fishtastic.network;

import grill24.fishtastic.server.FishObservation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * "I held a spyglass on this fish for a second" (world fish exploration E16). The server checks the
 * claim itself ({@link FishObservation}); nothing here is trusted.
 *
 * <p>Two kinds, with different evidence:
 * <ul>
 *   <li>{@link Kind#VISIT}: a member of an interactive visit, named by its school and member
 *       UUIDs. The server holds the authoritative position.</li>
 *   <li>{@link Kind#AMBIENT}: a seeded ambient fish. Its ID is a hash, so the client sends the inputs
 *       the server needs to derive it again: chunk, slot and lifespan index (the ID is checked
 *       against them), plus the fish's home cell, which fixes the biome and height its species was
 *       drawn under.</li>
 * </ul>
 * Fields a kind does not use are not sent.
 */
public record ObserveFishPacket(Kind kind, ResourceLocation species, UUID school, UUID member, long fishId,
                                int chunkX, int chunkZ, int slot, long lifespanIndex, BlockPos home)
        implements CustomPacketPayload {

    public enum Kind {
        VISIT, AMBIENT
    }

    /** An all-zero UUID, for the fields a kind does not use. */
    private static final UUID NONE = new UUID(0L, 0L);

    public static final CustomPacketPayload.Type<ObserveFishPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.OBSERVE_FISH_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, ObserveFishPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind().ordinal());
                ResourceLocation.STREAM_CODEC.encode(buf, p.species());
                if (p.kind() == Kind.VISIT) {
                    UUIDUtil.STREAM_CODEC.encode(buf, p.school());
                    UUIDUtil.STREAM_CODEC.encode(buf, p.member());
                } else {
                    buf.writeLong(p.fishId());
                    buf.writeInt(p.chunkX());
                    buf.writeInt(p.chunkZ());
                    buf.writeVarInt(p.slot());
                    buf.writeLong(p.lifespanIndex());
                    buf.writeBlockPos(p.home());
                }
            },
            buf -> {
                int ordinal = buf.readUnsignedByte();
                if (ordinal >= Kind.values().length) throw new IllegalArgumentException("unknown observation kind " + ordinal);
                Kind kind = Kind.values()[ordinal];
                ResourceLocation species = ResourceLocation.STREAM_CODEC.decode(buf);
                if (kind == Kind.VISIT) {
                    return visit(species, UUIDUtil.STREAM_CODEC.decode(buf), UUIDUtil.STREAM_CODEC.decode(buf));
                }
                long fishId = buf.readLong();
                int chunkX = buf.readInt(), chunkZ = buf.readInt(), slot = buf.readVarInt();
                long index = buf.readLong();
                return ambient(species, fishId, chunkX, chunkZ, slot, index, buf.readBlockPos());
            });

    public static ObserveFishPacket visit(ResourceLocation species, UUID school, UUID member) {
        return new ObserveFishPacket(Kind.VISIT, species, school, member, 0L, 0, 0, 0, 0L, BlockPos.ZERO);
    }

    public static ObserveFishPacket ambient(ResourceLocation species, long fishId, int chunkX, int chunkZ, int slot,
                                            long lifespanIndex, BlockPos home) {
        return new ObserveFishPacket(Kind.AMBIENT, species, NONE, NONE, fishId, chunkX, chunkZ, slot, lifespanIndex, home);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(ObserveFishPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player) FishObservation.handle(player, packet);
        });
    }
}
