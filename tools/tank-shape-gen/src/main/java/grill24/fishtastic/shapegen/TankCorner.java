package grill24.fishtastic.shapegen;

import java.util.Set;

/**
 * The four corner posts of a tank's footprint, named by the two {@link TankFace} sides that meet
 * there. Free-standing (no ordinal-mirror constraint like {@link TankFace} has with
 * {@code net.minecraft.core.Direction}) since {@code Direction} has no diagonal equivalent.
 */
public enum TankCorner {
    NW(TankFace.NORTH, TankFace.WEST),
    NE(TankFace.NORTH, TankFace.EAST),
    SW(TankFace.SOUTH, TankFace.WEST),
    SE(TankFace.SOUTH, TankFace.EAST);

    private final TankFace faceA;
    private final TankFace faceB;

    TankCorner(TankFace faceA, TankFace faceB) {
        this.faceA = faceA;
        this.faceB = faceB;
    }

    public TankFace faceA() {
        return faceA;
    }

    public TankFace faceB() {
        return faceB;
    }

    /** 0 = west/north edge, 1 = east edge — the {@code cornerX} convention used throughout the
     * generators' {@code createSupportBox(cornerX, cornerZ, ...)}-style helpers. */
    public int xEdge() {
        return faceB == TankFace.EAST ? 1 : 0;
    }

    /** 0 = west/north edge, 1 = south edge — the {@code cornerZ} convention used throughout the
     * generators' {@code createSupportBox(cornerX, cornerZ, ...)}-style helpers. */
    public int zEdge() {
        return faceA == TankFace.SOUTH ? 1 : 0;
    }

    /** Whether both of this corner's orthogonal faces are open — the condition under which the
     * base per-permutation model omits this corner's post entirely, making it the one case where
     * a diagonal-empty corner fragment needs compositing back in. */
    public boolean isOrthogonallyEligible(Set<TankFace> openFaces) {
        return openFaces.contains(faceA) && openFaces.contains(faceB);
    }

    /** Encodes {@code (ceilingClosed, floorClosed)} as a 0-3 index for corner-fragment lookups. */
    public static int capState(boolean ceilingClosed, boolean floorClosed) {
        return (ceilingClosed ? 2 : 0) | (floorClosed ? 1 : 0);
    }
}
