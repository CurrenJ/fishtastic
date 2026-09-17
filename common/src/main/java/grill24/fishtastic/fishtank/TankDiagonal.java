package grill24.fishtastic.fishtank;

import net.minecraft.core.Direction;

/**
 * The four horizontal diagonal neighbor cells of a tank, each carrying the two orthogonal
 * {@link Direction}s that reach it (e.g. {@code NORTHWEST} = {@code NORTH} then {@code WEST}),
 * so {@link grill24.fishtastic.blockentity.FishTankBlockEntity} can loop generically over all
 * four instead of four hand-written lookups.
 */
public enum TankDiagonal {
    NORTHWEST(Direction.NORTH, Direction.WEST),
    NORTHEAST(Direction.NORTH, Direction.EAST),
    SOUTHWEST(Direction.SOUTH, Direction.WEST),
    SOUTHEAST(Direction.SOUTH, Direction.EAST);

    private final Direction first;
    private final Direction second;

    TankDiagonal(Direction first, Direction second) {
        this.first = first;
        this.second = second;
    }

    public Direction first() {
        return first;
    }

    public Direction second() {
        return second;
    }
}
