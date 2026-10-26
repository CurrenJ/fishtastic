package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.school.FishSchoolSim;
import grill24.fishtastic.school.FishSchoolWire;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.UUID;

/**
 * Everything a client needs to build its copy of one school's motion: the epoch's setup, every
 * input applied since, and the server's step when this was sent. Sent when a player starts
 * tracking the school and whenever a new epoch begins (docs/interactive-fish-schools-motion-plan.md).
 * {@code qualities} covers every member of the visit, in the water or not, so a fish that gets away
 * and rejoins keeps its trail colour. {@code ageTicks} is how long ago the visit arrived, so a client
 * that sees it arrive can show the arrival (encounter spec §6.3).
 */
public record FishSchoolSetupPacket(ResourceLocation dimension, UUID school, int epoch, ResourceLocation species,
                                    FishSchoolSim.Setup setup, List<FishSchoolSim.Stamped> inputs, long serverStep,
                                    List<MemberQuality> qualities, int ageTicks)
        implements FishtasticPayload {
    /** A member's quality, which its sparkle trail is drawn in. */
    public record MemberQuality(UUID member, FishQuality.Quality quality) {}

    private static final BufCodec<MemberQuality> MEMBER_QUALITY = BufCodec.of(
            (buf, q) -> {
                BufCodecs.UUID.encode(buf, q.member());
                BufCodecs.VAR_INT.encode(buf, q.quality().ordinal());
            },
            buf -> {
                UUID member = BufCodecs.UUID.decode(buf);
                int ordinal = BufCodecs.VAR_INT.decode(buf);
                FishQuality.Quality[] all = FishQuality.Quality.values();
                return new MemberQuality(member, ordinal >= 0 && ordinal < all.length
                        ? all[ordinal] : FishQuality.Quality.COMMON);
            });

    public static final FishtasticPayload.PayloadType<FishSchoolSetupPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("fish_school_setup"));

    public static final BufCodec<FishSchoolSetupPacket> STREAM_CODEC = BufCodec.composite(
            BufCodecs.RESOURCE_LOCATION, FishSchoolSetupPacket::dimension,
            BufCodecs.UUID, FishSchoolSetupPacket::school,
            BufCodecs.VAR_INT, FishSchoolSetupPacket::epoch,
            BufCodecs.RESOURCE_LOCATION, FishSchoolSetupPacket::species,
            FishSchoolWire.SETUP, FishSchoolSetupPacket::setup,
            FishSchoolWire.STAMPED.apply(BufCodecs.list(FishSchoolWire.MAX_INPUTS)), FishSchoolSetupPacket::inputs,
            BufCodecs.VAR_LONG, FishSchoolSetupPacket::serverStep,
            MEMBER_QUALITY.apply(BufCodecs.list(FishSchoolWire.MAX_MEMBERS)), FishSchoolSetupPacket::qualities,
            BufCodecs.VAR_INT, FishSchoolSetupPacket::ageTicks,
            FishSchoolSetupPacket::new);

    @Override public FishtasticPayload.PayloadType<?> type() { return TYPE; }

    public static void handleServerToClient(FishSchoolSetupPacket packet, FishtasticPacketHandling.IPacketContext context) {
        context.enqueueWork(() -> FishSchoolClientBridge.invoke("onSetup", FishSchoolSetupPacket.class, packet));
    }
}