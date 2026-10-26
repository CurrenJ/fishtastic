package grill24.fishtastic.school;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.domain.WaterDomain;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Wire forms of {@link FishSchoolSim}'s setup and inputs. Floats go over as floats, bit for bit:
 * a copy is only a copy if it is built from exactly the numbers the server used.
 */
public final class FishSchoolWire {
    private FishSchoolWire() {}

    /** A visit's water is at most a few hundred cells; this bounds a hostile or corrupt list. */
    public static final int MAX_COLUMNS = 1024;
    public static final int MAX_MEMBERS = 64;
    /** Inputs one packet may carry: a whole epoch's log, which the server keeps under this. */
    public static final int MAX_INPUTS = 4096;

    private static final Locomotion[] LOCOMOTIONS = Locomotion.values();
    private static final ShelterUse[] SHELTER_USES = ShelterUse.values();
    private static final VisitShape[] SHAPES = VisitShape.values();

    public static final StreamCodec<RegistryFriendlyByteBuf, WaterDomain.Column> COLUMN = StreamCodec.of(
            (buf, c) -> {
                buf.writeInt(c.worldX());
                buf.writeInt(c.worldZ());
                buf.writeVarInt(c.spans().length);
                for (float f : c.spans()) buf.writeFloat(f);
                buf.writeBoolean(c.truncated());
            },
            buf -> {
                int x = buf.readInt(), z = buf.readInt();
                int n = bounded(buf.readVarInt(), WaterDomain.MAX_SPANS * 2, "spans");
                float[] spans = new float[n];
                for (int i = 0; i < n; i++) spans[i] = buf.readFloat();
                return new WaterDomain.Column(x, z, spans, buf.readBoolean());
            });

    public static final StreamCodec<RegistryFriendlyByteBuf, FishSpec> SPEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeFloat(s.length());
                buf.writeVarInt(s.locomotion().ordinal());
                buf.writeBoolean(s.mirrored());
                buf.writeVarInt(s.species());
                buf.writeVarInt(s.shelterUse().ordinal());
            },
            buf -> new FishSpec(buf.readFloat(), LOCOMOTIONS[bounded(buf.readVarInt(), LOCOMOTIONS.length - 1, "locomotion")],
                    buf.readBoolean(), buf.readVarInt(),
                    SHELTER_USES[bounded(buf.readVarInt(), SHELTER_USES.length - 1, "shelter use")]));

    public static final StreamCodec<RegistryFriendlyByteBuf, FishSchoolSim.Placement> PLACEMENT = StreamCodec.of(
            (buf, p) -> {
                UUIDUtil.STREAM_CODEC.encode(buf, p.member());
                buf.writeFloat(p.l());
                buf.writeFloat(p.y());
                buf.writeFloat(p.d());
            },
            buf -> new FishSchoolSim.Placement(UUIDUtil.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readFloat(),
                    buf.readFloat()));

    public static final StreamCodec<RegistryFriendlyByteBuf, FishSchoolSim.Setup> SETUP = StreamCodec.of(
            (buf, s) -> {
                BlockPos.STREAM_CODEC.encode(buf, s.origin());
                writeList(buf, s.columns(), COLUMN);
                SPEC.encode(buf, s.spec());
                buf.writeLong(s.seed());
                writeList(buf, s.members(), PLACEMENT);
                buf.writeVarInt(s.shape().ordinal());
            },
            buf -> new FishSchoolSim.Setup(BlockPos.STREAM_CODEC.decode(buf), readList(buf, COLUMN, MAX_COLUMNS),
                    SPEC.decode(buf), buf.readLong(), readList(buf, PLACEMENT, MAX_MEMBERS),
                    SHAPES[bounded(buf.readVarInt(), SHAPES.length - 1, "visit shape")]));

    private static final int LEAVE = 0, REJOIN = 1, APPROACH = 2, WITHDRAW = 3, WATER = 4, WATCHER = 5;

    public static final StreamCodec<RegistryFriendlyByteBuf, FishSchoolSim.Input> INPUT = StreamCodec.of(
            (buf, input) -> {
                switch (input) {
                    case FishSchoolSim.Leave leave -> {
                        buf.writeByte(LEAVE);
                        UUIDUtil.STREAM_CODEC.encode(buf, leave.member());
                    }
                    case FishSchoolSim.Rejoin rejoin -> {
                        buf.writeByte(REJOIN);
                        UUIDUtil.STREAM_CODEC.encode(buf, rejoin.member());
                        buf.writeFloat(rejoin.l());
                        buf.writeFloat(rejoin.y());
                        buf.writeFloat(rejoin.d());
                    }
                    case FishSchoolSim.Approach a -> {
                        buf.writeByte(APPROACH);
                        UUIDUtil.STREAM_CODEC.encode(buf, a.anchor());
                        UUIDUtil.STREAM_CODEC.encode(buf, a.member());
                        buf.writeFloat(a.l());
                        buf.writeFloat(a.y());
                        buf.writeFloat(a.d());
                        buf.writeFloat(a.speedScale());
                        buf.writeFloat(a.holdSeconds());
                    }
                    case FishSchoolSim.Withdraw withdraw -> {
                        buf.writeByte(WITHDRAW);
                        UUIDUtil.STREAM_CODEC.encode(buf, withdraw.anchor());
                    }
                    case FishSchoolSim.Water water -> {
                        buf.writeByte(WATER);
                        writeList(buf, water.columns(), COLUMN);
                    }
                    case FishSchoolSim.Watcher watcher -> {
                        buf.writeByte(WATCHER);
                        buf.writeBoolean(watcher.near());
                        buf.writeFloat(watcher.l());
                        buf.writeFloat(watcher.y());
                        buf.writeFloat(watcher.d());
                    }
                }
            },
            buf -> switch (buf.readByte()) {
                case LEAVE -> new FishSchoolSim.Leave(UUIDUtil.STREAM_CODEC.decode(buf));
                case REJOIN -> new FishSchoolSim.Rejoin(UUIDUtil.STREAM_CODEC.decode(buf), buf.readFloat(),
                        buf.readFloat(), buf.readFloat());
                case APPROACH -> new FishSchoolSim.Approach(UUIDUtil.STREAM_CODEC.decode(buf),
                        UUIDUtil.STREAM_CODEC.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat(), buf.readFloat());
                case WITHDRAW -> new FishSchoolSim.Withdraw(UUIDUtil.STREAM_CODEC.decode(buf));
                case WATER -> new FishSchoolSim.Water(readList(buf, COLUMN, MAX_COLUMNS));
                case WATCHER -> new FishSchoolSim.Watcher(buf.readBoolean(), buf.readFloat(), buf.readFloat(),
                        buf.readFloat());
                default -> throw new DecoderException("Unknown fish school input");
            });

    public static final StreamCodec<RegistryFriendlyByteBuf, FishSchoolSim.Stamped> STAMPED = StreamCodec.of(
            (buf, s) -> {
                buf.writeVarLong(s.step());
                INPUT.encode(buf, s.input());
            },
            buf -> new FishSchoolSim.Stamped(buf.readVarLong(), INPUT.decode(buf)));

    public static <T> void writeList(RegistryFriendlyByteBuf buf, List<T> list, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        buf.writeVarInt(list.size());
        for (T t : list) codec.encode(buf, t);
    }

    public static <T> List<T> readList(RegistryFriendlyByteBuf buf, StreamCodec<RegistryFriendlyByteBuf, T> codec, int max) {
        int n = bounded(buf.readVarInt(), max, "list");
        List<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(codec.decode(buf));
        return out;
    }

    private static int bounded(int value, int max, String what) {
        if (value < 0 || value > max) throw new DecoderException("Fish school " + what + " out of bounds: " + value);
        return value;
    }
}
