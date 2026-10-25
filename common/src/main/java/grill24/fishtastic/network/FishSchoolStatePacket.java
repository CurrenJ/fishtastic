package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.school.FishSchoolWire;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * The schools a player is tracking, every 10 ticks: for each, the server's step and a fingerprint
 * of every fish's position at it (so a copy can tell it has drifted), each member's visual state,
 * and how long a departing visit has left to fade. A school missing from the list is no longer
 * tracked and its copy is dropped. Positions are never sent: the copies compute them.
 */
public record FishSchoolStatePacket(ResourceLocation dimension, List<School> schools) implements CustomPacketPayload {
    public static final int MAX_SCHOOLS = 32;
    public static final CustomPacketPayload.Type<FishSchoolStatePacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("fish_school_state"));

    /** Visual states, as the client draws them. */
    public static final int AVAILABLE = 0, APPROACHING = 1, BITE_READY = 2, RECOVERING = 3, DEPARTING = 4;

    public record Member(UUID member, int visual) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Member> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Member::member,
                ByteBufCodecs.VAR_INT, Member::visual,
                Member::new);
    }

    /**
     * @param fadeTicks ticks left before a departing visit is gone, or -1 while it is not departing
     */
    public record School(UUID school, int epoch, long serverStep, int checksum, int fadeTicks, List<Member> members) {
        public static final StreamCodec<RegistryFriendlyByteBuf, School> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, School::school,
                ByteBufCodecs.VAR_INT, School::epoch,
                ByteBufCodecs.VAR_LONG, School::serverStep,
                ByteBufCodecs.INT, School::checksum,
                ByteBufCodecs.INT, School::fadeTicks,
                Member.STREAM_CODEC.apply(ByteBufCodecs.list(FishSchoolWire.MAX_MEMBERS)), School::members,
                School::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, FishSchoolStatePacket> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, FishSchoolStatePacket::dimension,
            School.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_SCHOOLS)), FishSchoolStatePacket::schools,
            FishSchoolStatePacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServerToClient(FishSchoolStatePacket packet, FishtasticPackets.IPacketContext context) {
        context.enqueueWork(() -> FishSchoolClientBridge.invoke("onState", FishSchoolStatePacket.class, packet));
    }
}
