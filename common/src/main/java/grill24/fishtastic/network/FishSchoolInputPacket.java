package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
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
 * Inputs the server has just applied to a school's motion, sent the moment they happen so a
 * client copy running a few ticks behind has them before it reaches their step.
 */
public record FishSchoolInputPacket(ResourceLocation dimension, UUID school, int epoch, List<FishSchoolSim.Stamped> inputs)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FishSchoolInputPacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("fish_school_input"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FishSchoolInputPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                ResourceLocation.STREAM_CODEC.encode(buf, p.dimension());
                UUIDUtil.STREAM_CODEC.encode(buf, p.school());
                buf.writeVarInt(p.epoch());
                FishSchoolWire.writeList(buf, p.inputs(), FishSchoolWire.STAMPED);
            },
            buf -> new FishSchoolInputPacket(ResourceLocation.STREAM_CODEC.decode(buf), UUIDUtil.STREAM_CODEC.decode(buf),
                    buf.readVarInt(), FishSchoolWire.readList(buf, FishSchoolWire.STAMPED, FishSchoolWire.MAX_INPUTS)));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServerToClient(FishSchoolInputPacket packet, FishtasticPackets.IPacketContext context) {
        context.enqueueWork(() -> FishSchoolClientBridge.invoke("onInputs", FishSchoolInputPacket.class, packet));
    }
}
