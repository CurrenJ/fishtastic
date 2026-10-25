package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.school.FishSchoolWire;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * The schools a player is tracking, every 10 ticks: for each, the server's step and a fingerprint
 * of every fish's position at it (so a copy can tell it has drifted), each member's visual state,
 * and how long a departing visit has left to fade. A school missing from the list is no longer
 * tracked and its copy is dropped. Positions are never sent: the copies compute them.
 */
public record FishSchoolStatePacket(ResourceLocation dimension, List<School> schools) implements FishtasticPayload {
    public static final int MAX_SCHOOLS = 32;
    public static final FishtasticPayload.PayloadType<FishSchoolStatePacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("fish_school_state"));

    /** Visual states, as the client draws them. */
    public static final int AVAILABLE = 0, APPROACHING = 1, BITE_READY = 2, RECOVERING = 3, DEPARTING = 4;

    public record Member(UUID member, int visual) {
        public static final BufCodec<Member> STREAM_CODEC = BufCodec.composite(
                BufCodecs.UUID, Member::member,
                BufCodecs.VAR_INT, Member::visual,
                Member::new);
    }

    /** @param fadeTicks ticks left before a departing visit is gone, or -1 while it is not departing */
    public record School(UUID school, int epoch, long serverStep, int checksum, int fadeTicks, List<Member> members) {
        public static final BufCodec<School> STREAM_CODEC = BufCodec.composite(
                BufCodecs.UUID, School::school,
                BufCodecs.VAR_INT, School::epoch,
                BufCodecs.VAR_LONG, School::serverStep,
                BufCodecs.INT, School::checksum,
                BufCodecs.INT, School::fadeTicks,
                Member.STREAM_CODEC.apply(BufCodecs.list(FishSchoolWire.MAX_MEMBERS)), School::members,
                School::new);
    }

    public static final BufCodec<FishSchoolStatePacket> STREAM_CODEC = BufCodec.composite(
            BufCodecs.RESOURCE_LOCATION, FishSchoolStatePacket::dimension,
            School.STREAM_CODEC.apply(BufCodecs.list(MAX_SCHOOLS)), FishSchoolStatePacket::schools,
            FishSchoolStatePacket::new);

    @Override public FishtasticPayload.PayloadType<?> type() { return TYPE; }

    public static void handleServerToClient(FishSchoolStatePacket packet, FishtasticPacketHandling.IPacketContext context) {
        context.enqueueWork(() -> FishSchoolClientBridge.invoke("onState", FishSchoolStatePacket.class, packet));
    }
}