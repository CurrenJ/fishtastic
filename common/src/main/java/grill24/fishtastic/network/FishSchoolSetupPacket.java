package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.school.FishSchoolSim;
import grill24.fishtastic.school.FishSchoolWire;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * Everything a client needs to build its copy of one school's motion: the epoch's setup, every
 * input applied since, and the server's step when this was sent. Sent when a player starts
 * tracking the school and whenever a new epoch begins (docs/interactive-fish-schools-motion-plan.md).
 * {@code qualities} covers every member of the visit, in the water or not, so a fish that gets away
 * and rejoins keeps its trail colour.
 */
public record FishSchoolSetupPacket(ResourceLocation dimension, UUID school, int epoch, ResourceLocation species,
                                    FishSchoolSim.Setup setup, List<FishSchoolSim.Stamped> inputs, long serverStep,
                                    List<MemberQuality> qualities)
        implements CustomPacketPayload {
    /** A member's quality, which its sparkle trail is drawn in. */
    public record MemberQuality(UUID member, FishQuality.Quality quality) {}

    private static final StreamCodec<RegistryFriendlyByteBuf, MemberQuality> MEMBER_QUALITY = StreamCodec.of(
            (buf, q) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, q.member());
                buf.writeVarInt(q.quality().ordinal());
            },
            buf -> {
                UUID member = UUIDUtil.STREAM_CODEC.decode(buf);
                int ordinal = buf.readVarInt();
                FishQuality.Quality[] all = FishQuality.Quality.values();
                return new MemberQuality(member, ordinal >= 0 && ordinal < all.length ? all[ordinal] : FishQuality.Quality.COMMON);
            });

    public static final CustomPacketPayload.Type<FishSchoolSetupPacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("fish_school_setup"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FishSchoolSetupPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ResourceLocation.STREAM_CODEC.encode(buf, p.dimension());
                UUIDUtil.STREAM_CODEC.encode(buf, p.school());
                buf.writeVarInt(p.epoch());
                ResourceLocation.STREAM_CODEC.encode(buf, p.species());
                FishSchoolWire.SETUP.encode(buf, p.setup());
                FishSchoolWire.writeList(buf, p.inputs(), FishSchoolWire.STAMPED);
                buf.writeVarLong(p.serverStep());
                FishSchoolWire.writeList(buf, p.qualities(), MEMBER_QUALITY);
            },
            buf -> new FishSchoolSetupPacket(ResourceLocation.STREAM_CODEC.decode(buf), UUIDUtil.STREAM_CODEC.decode(buf),
                    buf.readVarInt(), ResourceLocation.STREAM_CODEC.decode(buf), FishSchoolWire.SETUP.decode(buf),
                    FishSchoolWire.readList(buf, FishSchoolWire.STAMPED, FishSchoolWire.MAX_INPUTS), buf.readVarLong(),
                    FishSchoolWire.readList(buf, MEMBER_QUALITY, FishSchoolWire.MAX_MEMBERS)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServerToClient(FishSchoolSetupPacket packet, FishtasticPackets.IPacketContext context) {
        context.enqueueWork(() -> FishSchoolClientBridge.invoke("onSetup", FishSchoolSetupPacket.class, packet));
    }
}
