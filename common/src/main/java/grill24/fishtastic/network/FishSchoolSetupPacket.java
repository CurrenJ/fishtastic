package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
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
 */
public record FishSchoolSetupPacket(ResourceLocation dimension, UUID school, int epoch, ResourceLocation species,
                                    FishSchoolSim.Setup setup, List<FishSchoolSim.Stamped> inputs, long serverStep)
        implements FishtasticPayload {
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
            FishSchoolSetupPacket::new);

    @Override public FishtasticPayload.PayloadType<?> type() { return TYPE; }

    public static void handleServerToClient(FishSchoolSetupPacket packet, FishtasticPacketHandling.IPacketContext context) {
        context.enqueueWork(() -> FishSchoolClientBridge.invoke("onSetup", FishSchoolSetupPacket.class, packet));
    }
}