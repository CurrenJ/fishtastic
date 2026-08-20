package grill24.fishtastic.client.renderer;

/**
 * The volume fish are simulated within, expressed in tank-local coordinates:
 *
 * <ul>
 *   <li><b>lateral</b> (X) — perpendicular to the tank's facing; the fish's swim axis.</li>
 *   <li><b>vertical</b> (Y) — block units above the tank's item-position baseline.</li>
 *   <li><b>depth</b> (Z) — along the tank's facing; the front-to-back axis.</li>
 * </ul>
 *
 * Phase 1 implements a single axis-aligned box (one tank). Phase 2 turns this into a
 * union of AABBs for a player-locked multiblock aquarium — the reason it is an interface
 * rather than a concrete class baked into the simulation.
 */
public interface FlockDomain {
    float minLateral();
    float maxLateral();
    float minVertical();
    float maxVertical();
    float minDepth();
    float maxDepth();

    /** Depth planes fish drift back toward — one per depth layer. */
    float[] layerDepths();

    /** Shortest usable straight-line run, in blocks — the size-gate's "room to swim" measure. */
    float shortestRun();

    /** A single tank: an axis-aligned box in local (lateral, vertical, depth) space. */
    record Box(float halfExtent, float verticalHalf, float[] layerDepths) implements FlockDomain {
        @Override public float minLateral() { return -halfExtent; }
        @Override public float maxLateral() { return halfExtent; }
        @Override public float minVertical() { return -verticalHalf; }
        @Override public float maxVertical() { return verticalHalf; }
        @Override public float minDepth() { return -halfExtent; }
        @Override public float maxDepth() { return halfExtent; }
        @Override public float[] layerDepths() { return layerDepths; }
        @Override public float shortestRun() { return halfExtent * 2f; }
    }
}
