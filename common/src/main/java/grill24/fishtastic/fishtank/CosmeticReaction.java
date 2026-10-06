package grill24.fishtastic.fishtank;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Something a structure does when a fish sets it off (docs/fish-shelters.md §12.13): a clam that
 * opens when a fish noses its lip, a portcullis that rises to let one through. Client-only and
 * purely visual: nothing is stored or synced, and two players may see different moments.
 * <p>
 * <b>What sets it off</b> is one of two things:
 * <ul>
 *   <li>a {@code nose} anchor: a box of build cells on the structure's surface, and the side a fish
 *       comes from ({@code facing}, authored facing south). A fish swims up, touches it, holds a
 *       moment and backs off; the reaction starts at the touch.</li>
 *   <li>a {@code gate}: the index of one of the structure's gate shelters, which is <i>locked</i>
 *       while the reaction is at rest. Only the fish the structure sends may swim through it; the
 *       reaction starts as that fish sets off, and holds until it is through.</li>
 * </ul>
 * Either way, the structure's own clock decides when ({@code mean_seconds}, §12.2: rare clocks run
 * per structure, one moment at a time per tank).
 * <p>
 * <b>What it does.</b> Parts carry an optional {@code group} name. A {@code motion} swings a group
 * on a hinge ({@code pivot} in build units, {@code axis}, {@code degrees} at full open) or slides
 * it ({@code offset} in build units). A {@code glow} group is drawn lit and full bright while open.
 * {@code bursts} release tank bubbles at build-unit points when it opens. Sounds are quiet cues
 * played where the structure stands. Timing is in ticks: it opens over {@code open_ticks}, holds
 * {@code hold_ticks} (after the touch, or after a gate's fish is through), and closes over
 * {@code close_ticks}.
 */
public record CosmeticReaction(Optional<Nose> nose, Optional<Integer> gate, float meanSeconds, float holdSeconds,
                               int openTicks, int holdTicks, int closeTicks, List<Motion> motions,
                               List<String> glows, List<Burst> bursts, Optional<Sound> openSound,
                               Optional<Sound> closeSound) {

    /** Mean seconds between firings of a rare clock (§12.2). */
    public static final float DEFAULT_MEAN_SECONDS = 240f;

    public CosmeticReaction {
        motions = List.copyOf(motions);
        glows = List.copyOf(glows);
        bursts = List.copyOf(bursts);
    }

    private static final Codec<List<Float>> VEC3 = Codec.FLOAT.listOf().comapFlatMap(
            list -> list.size() == 3 ? DataResult.success(List.copyOf(list)) : DataResult.error(() -> "expected 3 numbers, got " + list),
            list -> list);

    private static final Codec<ShelterGeometry.Cell> CELL_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.INT.fieldOf("x").forGetter(ShelterGeometry.Cell::x),
            Codec.INT.fieldOf("y").forGetter(ShelterGeometry.Cell::y),
            Codec.INT.fieldOf("z").forGetter(ShelterGeometry.Cell::z)
    ).apply(i, ShelterGeometry.Cell::new));

    /** A nose anchor: build cells {@code min..max} on the surface, touched from the {@code facing} side. */
    public record Nose(ShelterGeometry.Cell min, ShelterGeometry.Cell max, Direction facing) {
        public static final Codec<Nose> CODEC = RecordCodecBuilder.create(i -> i.group(
                CELL_CODEC.fieldOf("min").forGetter(Nose::min),
                CELL_CODEC.fieldOf("max").forGetter(Nose::max),
                Direction.CODEC.fieldOf("facing").forGetter(Nose::facing)
        ).apply(i, Nose::new));

        /** Every build cell of the anchor box. */
        public List<ShelterGeometry.Cell> cells() {
            List<ShelterGeometry.Cell> out = new ArrayList<>();
            for (int x = Math.min(min.x(), max.x()); x <= Math.max(min.x(), max.x()); x++) {
                for (int y = Math.min(min.y(), max.y()); y <= Math.max(min.y(), max.y()); y++) {
                    for (int z = Math.min(min.z(), max.z()); z <= Math.max(min.z(), max.z()); z++) {
                        out.add(new ShelterGeometry.Cell(x, y, z));
                    }
                }
            }
            return out;
        }

        /**
         * The anchor as a shelter shape, so it is placed in a tank exactly as a hollow is
         * ({@code TankShelters}): its cells are the interior and the hull, and its one mouth is
         * their face on the {@code facing} side.
         */
        public ShelterGeometry.Shape shape() {
            ShelterGeometry.Cell lo = new ShelterGeometry.Cell(Math.min(min.x(), max.x()), Math.min(min.y(), max.y()), Math.min(min.z(), max.z()));
            ShelterGeometry.Cell hi = new ShelterGeometry.Cell(Math.max(min.x(), max.x()), Math.max(min.y(), max.y()), Math.max(min.z(), max.z()));
            int[] out = {facing.getStepX(), facing.getStepY(), facing.getStepZ()};
            ShelterGeometry.Cell faceMin = new ShelterGeometry.Cell(out[0] > 0 ? hi.x() : lo.x(), lo.y(), out[2] > 0 ? hi.z() : lo.z());
            ShelterGeometry.Cell faceMax = new ShelterGeometry.Cell(out[0] < 0 ? lo.x() : hi.x(), hi.y(), out[2] < 0 ? lo.z() : hi.z());
            int run = facing.getAxis() == Direction.Axis.X ? hi.x() - lo.x() + 1 : hi.z() - lo.z() + 1;
            return new ShelterGeometry.Shape(new HashSet<>(cells()), List.of(new ShelterGeometry.Mouth(out, faceMin, faceMax)),
                    lo, hi, lo, hi, run);
        }
    }

    /**
     * A group that moves while the reaction is open: a hinge (a {@code pivot} point in build
     * units, an {@code axis} through it, and the turn at full open in {@code degrees}), or a slide
     * by {@code offset} build units at full open. A hinge if it names a pivot, else a slide. With
     * {@code sway_ticks} a hinge swings to and fro with that period instead of holding open, as a
     * bell does on its rope, swinging less as the reaction closes.
     */
    public record Motion(String group, Optional<List<Float>> pivot, Direction.Axis axis, float degrees,
                         Optional<List<Float>> offset, int swayTicks) {
        public static final Codec<Motion> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("group").forGetter(Motion::group),
                VEC3.optionalFieldOf("pivot").forGetter(Motion::pivot),
                Direction.Axis.CODEC.optionalFieldOf("axis", Direction.Axis.X).forGetter(Motion::axis),
                Codec.FLOAT.optionalFieldOf("degrees", 0f).forGetter(Motion::degrees),
                VEC3.optionalFieldOf("offset").forGetter(Motion::offset),
                Codec.intRange(0, 200).optionalFieldOf("sway_ticks", 0).forGetter(Motion::swayTicks)
        ).apply(i, Motion::new));

        public boolean hinge() {
            return pivot.isPresent();
        }
    }

    /** Tank bubbles let out at a build-unit point as the reaction opens. */
    public record Burst(List<Float> at, int count) {
        public static final Codec<Burst> CODEC = RecordCodecBuilder.create(i -> i.group(
                VEC3.fieldOf("at").forGetter(Burst::at),
                Codec.intRange(1, 32).optionalFieldOf("count", 6).forGetter(Burst::count)
        ).apply(i, Burst::new));
    }

    /** A quiet sound cue: a sound event id, its volume (kept low: these are decorations) and pitch. */
    public record Sound(ResourceLocation id, float volume, float pitch) {
        public static final Codec<Sound> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("id").forGetter(Sound::id),
                Codec.floatRange(0f, 1f).optionalFieldOf("volume", 0.2f).forGetter(Sound::volume),
                Codec.floatRange(0.5f, 2f).optionalFieldOf("pitch", 1f).forGetter(Sound::pitch)
        ).apply(i, Sound::new));
    }

    public static final Codec<CosmeticReaction> CODEC = RecordCodecBuilder.<CosmeticReaction>create(i -> i.group(
            Nose.CODEC.optionalFieldOf("nose").forGetter(CosmeticReaction::nose),
            Codec.intRange(0, 63).optionalFieldOf("gate").forGetter(CosmeticReaction::gate),
            Codec.floatRange(1f, 3600f).optionalFieldOf("mean_seconds", DEFAULT_MEAN_SECONDS).forGetter(CosmeticReaction::meanSeconds),
            Codec.floatRange(0f, 30f).optionalFieldOf("hold_seconds", 2.5f).forGetter(CosmeticReaction::holdSeconds),
            Codec.intRange(1, 200).optionalFieldOf("open_ticks", 12).forGetter(CosmeticReaction::openTicks),
            Codec.intRange(0, 400).optionalFieldOf("hold_ticks", 30).forGetter(CosmeticReaction::holdTicks),
            Codec.intRange(1, 200).optionalFieldOf("close_ticks", 16).forGetter(CosmeticReaction::closeTicks),
            Motion.CODEC.listOf().optionalFieldOf("motions", List.of()).forGetter(CosmeticReaction::motions),
            Codec.STRING.listOf().optionalFieldOf("glows", List.of()).forGetter(CosmeticReaction::glows),
            Burst.CODEC.listOf().optionalFieldOf("bursts", List.of()).forGetter(CosmeticReaction::bursts),
            Sound.CODEC.optionalFieldOf("open_sound").forGetter(CosmeticReaction::openSound),
            Sound.CODEC.optionalFieldOf("close_sound").forGetter(CosmeticReaction::closeSound)
    ).apply(i, CosmeticReaction::new)).validate(r -> r.nose.isPresent() == r.gate.isPresent()
            ? DataResult.error(() -> "a reaction is set off by exactly one of a nose anchor or a gate")
            : r.nose.isPresent() && r.nose.get().facing().getAxis() == Direction.Axis.Y
            ? DataResult.error(() -> "a nose anchor faces sideways: fish swim level")
            : DataResult.success(r));

    /** Every group this reaction moves or lights. */
    public Set<String> groups() {
        Set<String> out = new HashSet<>(glows);
        for (Motion motion : motions) out.add(motion.group());
        return out;
    }

    /** The groups that move, and so leave the space they stood in at rest. */
    public Set<String> movingGroups() {
        Set<String> out = new HashSet<>();
        for (Motion motion : motions) out.add(motion.group());
        return out;
    }

    /**
     * The engine's handle on one placed structure's reaction: the tank it stands in, its anchor
     * cell, and which of its reactions. Rebuilt identically on every shelter rebuild, so an event
     * still finds its structure after one.
     */
    public record Key(BlockPos tank, CosmeticGridCell anchor, int reaction) {}
}
