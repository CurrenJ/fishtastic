package grill24.fishsim.companion;

import java.util.List;

/**
 * Everything a companion can perceive on one tick (docs/fish-companions.md): built
 * once per 20 Hz tick and handed to every agent. In game the adapter fills it from Minecraft;
 * headless, a scripted owner, a voxel scene and scripted points of interest do. The agent can't
 * tell the difference.
 *
 * @param owner  the owner's body, view and flags
 * @param blocks the blocks around the owner
 * @param points things nearby worth reacting to (hostiles, cats, dropped items, companions, player feet)
 * @param held   what the owner holds in their main hand, as the companion understands it (spec §3.1)
 * @param hurt    the owner took damage this tick
 * @param ambient biome, weather, time, moon and warmth for moods (Phase 5); {@link Ambient#NEUTRAL} for none
 */
public record CompanionWorld(OwnerState owner, BlockQuery blocks, List<PointOfInterest> points, Held held,
                             boolean hurt, Ambient ambient, List<WorldFeature> features, long gameTick) {

    /** A held item's reaction category (spec §3.1). The adapter maps item stacks to these. */
    public enum Held {
        NONE,
        /** A fishing rod: excited, watches the rod tip. */
        ROD,
        /** Bait: begs. */
        BAIT,
        /** A fish of the companion's own species: nose to the item. */
        SAME_SPECIES,
        /** A fish from {@code #predator_fish}: hides behind the owner's head. */
        PREDATOR,
        /** A name tag: tilts its head. */
        NAME_TAG,
        /** Food: mild interest. */
        FOOD
    }

    public CompanionWorld {
        points = List.copyOf(points);
        features = List.copyOf(features);
        if (ambient == null) ambient = Ambient.NEUTRAL;
    }

    public CompanionWorld(OwnerState owner, BlockQuery blocks, List<PointOfInterest> points, Held held, boolean hurt, Ambient ambient) {
        this(owner, blocks, points, held, hurt, ambient, List.of(), 0);
    }
    public CompanionWorld withFeatures(List<WorldFeature> features) {
        return new CompanionWorld(owner, blocks, points, held, hurt, ambient, features, gameTick);
    }
    public CompanionWorld withGameTick(long tick) {
        return new CompanionWorld(owner, blocks, points, held, hurt, ambient, features, tick);
    }
    /** Compatibility view for the established bask/hold rules; production warmth lives in features. */
    Ambient moodAmbient() {
        if (features.stream().noneMatch(f -> f.kind() == WorldFeature.Kind.WARMTH)) return ambient;
        java.util.ArrayList<Vec> warm = new java.util.ArrayList<>(ambient.warmth());
        for (WorldFeature feature : features) if (feature.kind() == WorldFeature.Kind.WARMTH) warm.add(feature.pos());
        return ambient.withWarmth(warm);
    }

    /** No ambient: no moods, as before Phase 5. */
    public CompanionWorld(OwnerState owner, BlockQuery blocks, List<PointOfInterest> points, Held held, boolean hurt) {
        this(owner, blocks, points, held, hurt, Ambient.NEUTRAL);
    }

    /** The same tick seen with other points, keeping everything else (the ambient included). */
    public CompanionWorld withPoints(List<PointOfInterest> points) {
        return new CompanionWorld(owner, blocks, points, held, hurt, ambient, features, gameTick);
    }

    public CompanionWorld withAmbient(Ambient ambient) {
        return new CompanionWorld(owner, blocks, points, held, hurt, ambient, features, gameTick);
    }

    /**
     * Where the held item draws a companion's eye: a rod's tip, out in front and up, or the item in
     * the owner's main hand, low right in the view as first person draws it. Null when empty-handed.
     */
    public Vec heldPoint() {
        if (held == Held.NONE) return null;
        if (held == Held.ROD) {
            Vec look = owner.look();
            Vec right = look.cross(Vec.UP).normalize();
            if (right.lengthSqr() < 1e-9) right = owner.flatRight();
            Vec up = right.cross(look);
            return owner.eye().add(look.scale(1.7)).add(right.scale(0.45)).add(up.scale(0.25));
        }
        return PerchBody.slotPoint(owner, PerchBody.Slot.RIGHT_HAND);
    }

    /** An owner and their blocks, with nothing else going on. */
    public static CompanionWorld of(OwnerState owner, BlockQuery blocks) {
        return new CompanionWorld(owner, blocks, List.of(), Held.NONE, false, Ambient.NEUTRAL);
    }
}
