package grill24.fishtastic.shapegen;

import java.util.Set;

/**
 * Plain (Minecraft-free) mirror of {@code grill24.fishtastic.fishtank.TankEdgeDiagonal} — the
 * eight edge-diagonal neighbor cells of a tank, one per combination of a horizontal
 * {@link TankFace} (NORTH/SOUTH/EAST/WEST) and a vertical one (UP/DOWN). Distinct from
 * {@link TankCorner}, which covers the four purely-horizontal corner cells — this covers the
 * horizontal×vertical "edge beam" gap instead (see the edge-diagonal frame beam fix design doc).
 */
public enum TankEdge {
    NORTH_UP(TankFace.NORTH, TankFace.UP),
    NORTH_DOWN(TankFace.NORTH, TankFace.DOWN),
    SOUTH_UP(TankFace.SOUTH, TankFace.UP),
    SOUTH_DOWN(TankFace.SOUTH, TankFace.DOWN),
    EAST_UP(TankFace.EAST, TankFace.UP),
    EAST_DOWN(TankFace.EAST, TankFace.DOWN),
    WEST_UP(TankFace.WEST, TankFace.UP),
    WEST_DOWN(TankFace.WEST, TankFace.DOWN);

    private final TankFace horizontal;
    private final TankFace vertical;

    TankEdge(TankFace horizontal, TankFace vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public TankFace horizontal() {
        return horizontal;
    }

    public TankFace vertical() {
        return vertical;
    }

    /** Eligible (its beam is missing from the base per-permutation bake) when both this edge's
     * horizontal and vertical faces are open — see {@code FishTankCompositeModelData#getEdgeDiagonalOverrideMask}. */
    public boolean isEligible(Set<TankFace> openFaces) {
        return openFaces.contains(horizontal) && openFaces.contains(vertical);
    }

    /** The two corners at either end of this edge's beam — where the beam's own frame ends and a
     * perpendicular wall's glass pane (or corner post) picks up instead. See
     * {@code TaperedGlassGeometryGenerator#generateEdgeGlassFillFragment}. */
    public TankCorner[] endCorners() {
        return switch (horizontal) {
            case NORTH -> new TankCorner[]{TankCorner.NW, TankCorner.NE};
            case SOUTH -> new TankCorner[]{TankCorner.SW, TankCorner.SE};
            case WEST -> new TankCorner[]{TankCorner.NW, TankCorner.SW};
            case EAST -> new TankCorner[]{TankCorner.NE, TankCorner.SE};
            default -> throw new IllegalStateException("Not a horizontal face: " + horizontal);
        };
    }
}
