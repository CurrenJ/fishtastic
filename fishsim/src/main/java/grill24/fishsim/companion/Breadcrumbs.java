package grill24.fishsim.companion;

/**
 * The owner's recent path at chest height. Somewhere the owner just walked is somewhere the fish
 * can swim, so when a wall stands between them the fish follows the trail instead of pathfinding —
 * which is also what a pet does: it goes the way you went.
 */
final class Breadcrumbs {

    private static final int CAPACITY = 64;
    /** Spacing between crumbs, blocks. */
    private static final double SPACING = 0.6;

    private final Vec[] ring = new Vec[CAPACITY];
    private int head = -1, size;

    void record(Vec chest) {
        if (size > 0 && ring[head].distanceTo(chest) < SPACING) return;
        head = (head + 1) % CAPACITY;
        ring[head] = chest;
        if (size < CAPACITY) size++;
    }

    void clear() {
        head = -1;
        size = 0;
    }

    int size() {
        return size;
    }

    /** The {@code i}-th newest crumb (0 = newest). */
    Vec get(int i) {
        return ring[((head - i) % CAPACITY + CAPACITY) % CAPACITY];
    }

    /** The newest crumb the fish can swim straight to from {@code from}, or null. */
    Vec newestReachable(Vec from, LocalVoxels voxels, double radius) {
        for (int i = 0; i < size; i++) {
            Vec c = get(i);
            if (voxels.segmentClear(from, c, radius)) return c;
        }
        return null;
    }

    /** The point {@code back} blocks back along the trail from the newest crumb, or null. */
    Vec trailPoint(Vec chest, double back) {
        Vec prev = chest;
        double walked = 0;
        for (int i = 0; i < size; i++) {
            Vec c = get(i);
            double seg = prev.distanceTo(c);
            if (walked + seg >= back) return prev.lerp(c, (back - walked) / Math.max(seg, 1e-9));
            walked += seg;
            prev = c;
        }
        return size > 0 ? prev : null;
    }
}
