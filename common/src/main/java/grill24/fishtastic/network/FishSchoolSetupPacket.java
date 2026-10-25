package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.school.FishSchoolSim;
import grill24.fishtastic.school.FishSchoolWire;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.UUID;

/**
 * Everything a client needs to build its copy of one school's motion: the epoch's setup, every
 * input applied since, and the server's step when this was sent. Sent when a player starts
 * tracking the school and whenever a new epoch begins (docs/interactive-fish-schools-motion-plan.md).
 */
public record FishSchoolSetupPacket(Identifier dimension, UUID school, int epoch, Identifier species,
                                    FishSchoolSim.Setup setup, List<FishSchoolSim.Stamped> inputs, long serverStep)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FishSchoolSetupPacket> TYPE =
            new CustomPacketPayload.Type<>(Fishtastic.id("fish_school_setup"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FishSchoolSetupPacket> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                Identifier.STREAM_CODEC.encode(buf, p.dimension());
                UUIDUtil.STREAM_CODEC.encode(buf, p.school());
                buf.writeVarInt(p.epoch());
                Identifier.STREAM_CODEC.encode(buf, p.species());
                FishSchoolWire.SETUP.encode(buf, p.setup());
                FishSchoolWire.writeList(buf, p.inputs(), FishSchoolWire.STAMPED);
                buf.writeVarLong(p.serverStep());
            },
            buf -> new FishSchoolSetupPacket(Identifier.STREAM_CODEC.decode(buf), UUIDUtil.STREAM_CODEC.decode(buf),
                    buf.readVarInt(), Identifier.STREAM_CODEC.decode(buf), FishSchoolWire.SETUP.decode(buf),
                    FishSchoolWire.readList(buf, FishSchoolWire.STAMPED, FishSchoolWire.MAX_INPUTS), buf.readVarLong()));

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleServerToClient(FishSchoolSetupPacket packet, FishtasticPackets.IPacketContext context) {
        context.enqueueWork(() -> FishSchoolClientBridge.invoke("onSetup", FishSchoolSetupPacket.class, packet));
    }
}
