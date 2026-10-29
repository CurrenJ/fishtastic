package grill24.fishtastic.school;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.domain.WaterDomain;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.core.BlockPos;

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
    private static final SurfaceJumpPlaybackProfile[] PLAYBACK_PROFILES = SurfaceJumpPlaybackProfile.values();

    public static final BufCodec<WaterDomain.Column> COLUMN = BufCodec.of(
            (buf, c) -> {
                buf.writeInt(c.worldX());
                buf.writeInt(c.worldZ());
                BufCodecs.VAR_INT.encode(buf, c.spans().length);
                for (float f : c.spans()) buf.writeFloat(f);
                buf.writeBoolean(c.truncated());
            },
            buf -> {
                int x = buf.readInt(), z = buf.readInt();
                int n = bounded(BufCodecs.VAR_INT.decode(buf), WaterDomain.MAX_SPANS * 2, "spans");
                float[] spans = new float[n];
                for (int i = 0; i < n; i++) spans[i] = buf.readFloat();
                return new WaterDomain.Column(x, z, spans, buf.readBoolean());
            });

    public static final BufCodec<FishSpec> SPEC = BufCodec.of(
            (buf, s) -> {
                buf.writeFloat(s.length());
                BufCodecs.VAR_INT.encode(buf, s.locomotion().ordinal());
                buf.writeBoolean(s.mirrored());
                BufCodecs.VAR_INT.encode(buf, s.species());
                BufCodecs.VAR_INT.encode(buf, s.shelterUse().ordinal());
            },
            buf -> new FishSpec(buf.readFloat(), LOCOMOTIONS[bounded(BufCodecs.VAR_INT.decode(buf), LOCOMOTIONS.length - 1, "locomotion")],
                    buf.readBoolean(), BufCodecs.VAR_INT.decode(buf),
                    SHELTER_USES[bounded(BufCodecs.VAR_INT.decode(buf), SHELTER_USES.length - 1, "shelter use")]));

    public static final BufCodec<FishSchoolSim.Placement> PLACEMENT = BufCodec.of(
            (buf, p) -> {
                BufCodecs.UUID.encode(buf, p.member());
                buf.writeFloat(p.l());
                buf.writeFloat(p.y());
                buf.writeFloat(p.d());
            },
            buf -> new FishSchoolSim.Placement(BufCodecs.UUID.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat()));

    public static final BufCodec<FishSchoolSim.Setup> SETUP = BufCodec.of(
            (buf, s) -> {
                BufCodecs.BLOCK_POS.encode(buf, s.origin());
                writeList(buf, s.columns(), COLUMN);
                SPEC.encode(buf, s.spec());
                buf.writeLong(s.seed());
                writeList(buf, s.members(), PLACEMENT);
                BufCodecs.VAR_INT.encode(buf, s.shape().ordinal());
            },
            buf -> new FishSchoolSim.Setup(BufCodecs.BLOCK_POS.decode(buf), readList(buf, COLUMN, MAX_COLUMNS),
                    SPEC.decode(buf), buf.readLong(), readList(buf, PLACEMENT, MAX_MEMBERS),
                    SHAPES[bounded(BufCodecs.VAR_INT.decode(buf), SHAPES.length - 1, "visit shape")]));

    private static final int LEAVE = 0, REJOIN = 1, APPROACH = 2, WITHDRAW = 3, WATER = 4, WATCHER = 5,
            SURFACE_JUMP = 6, SURFACE_GOAL = 7;

    public static final BufCodec<FishSchoolSim.Input> INPUT = BufCodec.of(
            (buf, input) -> {
                if (input instanceof FishSchoolSim.Leave leave) {
                    buf.writeByte(LEAVE);
                    BufCodecs.UUID.encode(buf, leave.member());
                } else if (input instanceof FishSchoolSim.Rejoin rejoin) {
                    buf.writeByte(REJOIN);
                    BufCodecs.UUID.encode(buf, rejoin.member());
                    buf.writeFloat(rejoin.l());
                    buf.writeFloat(rejoin.y());
                    buf.writeFloat(rejoin.d());
                } else if (input instanceof FishSchoolSim.Approach approach) {
                    buf.writeByte(APPROACH);
                    BufCodecs.UUID.encode(buf, approach.anchor());
                    BufCodecs.UUID.encode(buf, approach.member());
                    buf.writeFloat(approach.l());
                    buf.writeFloat(approach.y());
                    buf.writeFloat(approach.d());
                    buf.writeFloat(approach.speedScale());
                    buf.writeFloat(approach.holdSeconds());
                } else if (input instanceof FishSchoolSim.Withdraw withdraw) {
                    buf.writeByte(WITHDRAW);
                    BufCodecs.UUID.encode(buf, withdraw.anchor());
                } else if (input instanceof FishSchoolSim.Water water) {
                    buf.writeByte(WATER);
                    writeList(buf, water.columns(), COLUMN);
                } else if (input instanceof FishSchoolSim.SurfaceJump jump) {
                    buf.writeByte(SURFACE_JUMP);
                    BufCodecs.UUID.encode(buf, jump.member());
                    BufCodecs.VAR_INT.encode(buf, jump.durationTicks());
                    buf.writeFloat(jump.height());
                    buf.writeByte(jump.playbackProfile().ordinal());
                } else if (input instanceof FishSchoolSim.SurfaceGoal goal) {
                    buf.writeByte(SURFACE_GOAL);
                    BufCodecs.UUID.encode(buf, goal.goal());
                    BufCodecs.UUID.encode(buf, goal.member());
                    buf.writeFloat(goal.l());
                    buf.writeFloat(goal.y());
                    buf.writeFloat(goal.d());
                    BufCodecs.VAR_INT.encode(buf, goal.durationTicks());
                    buf.writeFloat(goal.height());
                    buf.writeByte(goal.playbackProfile().ordinal());
                } else if (input instanceof FishSchoolSim.Watcher watcher) {
                    buf.writeByte(WATCHER);
                    buf.writeBoolean(watcher.near());
                    buf.writeFloat(watcher.l());
                    buf.writeFloat(watcher.y());
                    buf.writeFloat(watcher.d());
                } else {
                    throw new IllegalArgumentException("Unknown fish school input " + input.getClass().getName());
                }
            },
            FishSchoolWire::readInput);

    private static FishSchoolSim.Input readInput(ByteBuf buf) {
        switch (buf.readByte()) {
            case LEAVE: return new FishSchoolSim.Leave(BufCodecs.UUID.decode(buf));
            case REJOIN: return new FishSchoolSim.Rejoin(BufCodecs.UUID.decode(buf), buf.readFloat(), buf.readFloat(), buf.readFloat());
            case APPROACH: return new FishSchoolSim.Approach(BufCodecs.UUID.decode(buf), BufCodecs.UUID.decode(buf),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat());
            case WITHDRAW: return new FishSchoolSim.Withdraw(BufCodecs.UUID.decode(buf));
            case WATER: return new FishSchoolSim.Water(readList(buf, COLUMN, MAX_COLUMNS));
            case WATCHER: return new FishSchoolSim.Watcher(buf.readBoolean(), buf.readFloat(), buf.readFloat(), buf.readFloat());
            case SURFACE_JUMP: return new FishSchoolSim.SurfaceJump(BufCodecs.UUID.decode(buf),
                    bounded(BufCodecs.VAR_INT.decode(buf), FishSchoolSim.MAX_SURFACE_JUMP_TICKS, "surface jump duration"),
                    buf.readFloat(), PLAYBACK_PROFILES[bounded(buf.readUnsignedByte(), PLAYBACK_PROFILES.length - 1,
                            "surface jump playback profile")]);
            case SURFACE_GOAL: return new FishSchoolSim.SurfaceGoal(BufCodecs.UUID.decode(buf), BufCodecs.UUID.decode(buf),
                    buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    bounded(BufCodecs.VAR_INT.decode(buf), FishSchoolSim.MAX_SURFACE_JUMP_TICKS, "surface goal duration"),
                    buf.readFloat(), PLAYBACK_PROFILES[bounded(buf.readUnsignedByte(), PLAYBACK_PROFILES.length - 1,
                            "surface goal playback profile")]);
            default: throw new DecoderException("Unknown fish school input");
        }
    }

    public static final BufCodec<FishSchoolSim.Stamped> STAMPED = BufCodec.of(
            (buf, stamped) -> {
                BufCodecs.VAR_LONG.encode(buf, stamped.step());
                INPUT.encode(buf, stamped.input());
            },
            buf -> new FishSchoolSim.Stamped(BufCodecs.VAR_LONG.decode(buf), INPUT.decode(buf)));

    public static <T> void writeList(ByteBuf buf, List<T> list, BufCodec<T> codec) {
        BufCodecs.VAR_INT.encode(buf, list.size());
        for (T value : list) codec.encode(buf, value);
    }

    public static <T> List<T> readList(ByteBuf buf, BufCodec<T> codec, int max) {
        int size = bounded(BufCodecs.VAR_INT.decode(buf), max, "list");
        List<T> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) out.add(codec.decode(buf));
        return out;
    }

    private static int bounded(int value, int max, String what) {
        if (value < 0 || value > max) throw new DecoderException("Fish school " + what + " out of bounds: " + value);
        return value;
    }
}
