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
 * Inputs the server has just applied to a school's motion, sent the moment they happen so a
 * client copy running a few ticks behind has them before it reaches their step.
 */
public record FishSchoolInputPacket(ResourceLocation dimension, UUID school, int epoch, List<FishSchoolSim.Stamped> inputs)
        implements FishtasticPayload {
    public static final FishtasticPayload.PayloadType<FishSchoolInputPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("fish_school_input"));

    public static final BufCodec<FishSchoolInputPacket> STREAM_CODEC = BufCodec.composite(
            BufCodecs.RESOURCE_LOCATION, FishSchoolInputPacket::dimension,
            BufCodecs.UUID, FishSchoolInputPacket::school,
            BufCodecs.VAR_INT, FishSchoolInputPacket::epoch,
            FishSchoolWire.STAMPED.apply(BufCodecs.list(FishSchoolWire.MAX_INPUTS)), FishSchoolInputPacket::inputs,
            FishSchoolInputPacket::new);

    @Override public FishtasticPayload.PayloadType<?> type() { return TYPE; }

    public static void handleServerToClient(FishSchoolInputPacket packet, FishtasticPacketHandling.IPacketContext context) {
        context.enqueueWork(() -> FishSchoolClientBridge.invoke("onInputs", FishSchoolInputPacket.class, packet));
    }
}