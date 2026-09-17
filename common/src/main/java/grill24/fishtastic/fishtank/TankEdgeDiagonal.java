package grill24.fishtastic.fishtank;

import net.minecraft.core.Direction;

/**
 * The eight edge-diagonal neighbor cells of a tank — one for each combination of a horizontal
 * {@link Direction} (NORTH/SOUTH/EAST/WEST) and a vertical one (UP/DOWN), reached by moving one
 * step horizontally then one step vertically (e.g. {@code EAST_DOWN} = {@code EAST} then
 * {@code DOWN}). Distinct from {@link TankDiagonal}, which covers the four purely-horizontal
 * corner cells — this covers the horizontal×vertical "edge beam" gap instead (see the
 * edge-diagonal frame beam fix design doc).
 */
public enum TankEdgeDiagonal {
    NORTH_UP(Direction.NORTH, Direction.UP),
    NORTH_DOWN(Direction.NORTH, Direction.DOWN),
    SOUTH_UP(Direction.SOUTH, Direction.UP),
    SOUTH_DOWN(Direction.SOUTH, Direction.DOWN),
    EAST_UP(Direction.EAST, Direction.UP),
    EAST_DOWN(Direction.EAST, Direction.DOWN),
    WEST_UP(Direction.WEST, Direction.UP),
    WEST_DOWN(Direction.WEST, Direction.DOWN);

    private final Direction horizontal;
    private final Direction vertical;

    TankEdgeDiagonal(Direction horizontal, Direction vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public Direction horizontal() {
        return horizontal;
    }

    public Direction vertical() {
        return vertical;
    }

    /** The two horizontal-corner diagonals at either end of this edge's beam — where the beam's
     * frame ends and a perpendicular wall's glass pane picks up instead, restored (when this edge's
     * cell is filled, so the beam itself doesn't render) by a small glass-fill fragment. See
     * {@link grill24.fishtastic.fishtank.FishTankCompositeModelData#getEdgeDiagonalGlassFillMask()}. */
    public TankDiagonal[] endDiagonals() {
        return switch (horizontal) {
            case NORTH -> new TankDiagonal[]{TankDiagonal.NORTHWEST, TankDiagonal.NORTHEAST};
            case SOUTH -> new TankDiagonal[]{TankDiagonal.SOUTHWEST, TankDiagonal.SOUTHEAST};
            case WEST -> new TankDiagonal[]{TankDiagonal.NORTHWEST, TankDiagonal.SOUTHWEST};
            case EAST -> new TankDiagonal[]{TankDiagonal.NORTHEAST, TankDiagonal.SOUTHEAST};
            default -> throw new IllegalStateException("Not a horizontal face: " + horizontal);
        };
    }

    /** The "wall" face of {@code diagonal} relative to this edge — the corner's other face, which
     * is closed (and so carries a glass pane) precisely because this edge's own horizontal face is
     * the open one. {@code diagonal} must be one of {@link #endDiagonals()}. */
    public Direction wallFace(TankDiagonal diagonal) {
        return diagonal.first() == horizontal ? diagonal.second() : diagonal.first();
    }
}
