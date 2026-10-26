package grill24.fishtastic.school;

import com.mojang.serialization.Codec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.Tunables;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * How an interactive visit arrives: one fish, a pair, a loose group or a tight school
 * (docs/in-world-fish-encounters-design-spec.md §3). Each species authors its shape in its
 * profile's {@code world_encounter}; {@link #fallback} covers a species that has not.
 */
public enum VisitShape implements StringRepresentable {
    SOLITARY(1, 1, 0f),
    PAIR(2, 2, 1.5f),
    LOOSE_GROUP(2, 4, 2.0f),
    SCHOOL(3, 6, 1.0f);

    public static final Codec<VisitShape> CODEC = Codec.STRING.comapFlatMap(name -> {
        for (VisitShape shape : values()) {
            if (shape.getSerializedName().equals(name)) return com.mojang.serialization.DataResult.success(shape);
        }
        return com.mojang.serialization.DataResult.error(() -> "Unknown visit shape: " + name);
    }, VisitShape::getSerializedName);

    /**
     * Loose groups barely flock: no heading or speed matching and a short perception range, so
     * each fish wanders on its own while weak cohesion keeps the group in the same stretch of water.
     */
    private static final Tunables LOOSE = Tunables.GROUP
            .withAlignmentWeight(0f)
            .withAlignHeadingWeight(0f)
            .withSpeedMatchWeight(0f)
            .withCohesionSpeed(0.02f)
            .withNeighborRange(2.5f);

    /**
     * A pair keeps company: a long perception range so the two find each other across a pond, and
     * weak heading matching so they travel together without moving as one.
     */
    private static final Tunables PAIRED = Tunables.GROUP
            .withAlignHeadingWeight(0.02f)
            .withCohesionSpeed(0.04f)
            .withNeighborRange(3.0f);

    private final int minMembers;
    private final int maxMembers;
    private final float spacing;

    VisitShape(int minMembers, int maxMembers, float spacing) {
        this.minMembers = minMembers;
        this.maxMembers = maxMembers;
        this.spacing = spacing;
    }

    public int minMembers() { return minMembers; }
    public int maxMembers() { return maxMembers; }
    /** Minimum distance, in blocks, between two members where the visit forms. */
    public float spacing() { return spacing; }

    /**
     * The engine parameters a visit of this shape runs with. A school keeps the set every school
     * has always run with, so the shipped schools behave exactly as before.
     */
    public Tunables tunables() {
        return switch (this) {
            case SCHOOL, SOLITARY -> Tunables.GROUP;
            case PAIR -> PAIRED;
            case LOOSE_GROUP -> LOOSE;
        };
    }

    /** {@code shape}, unless a creature of {@code locomotion} cannot hold it: only free swimmers school. */
    public static VisitShape allowedFor(VisitShape shape, Locomotion locomotion) {
        return shape == SCHOOL && locomotion != Locomotion.FREE_SWIM ? LOOSE_GROUP : shape;
    }

    /** The shape for a species that authored none, from its rarity (spec §3.1). */
    public static VisitShape fallback(int baseWeight) {
        if (baseWeight >= 25) return SCHOOL;
        if (baseWeight >= 8) return LOOSE_GROUP;
        return SOLITARY;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
