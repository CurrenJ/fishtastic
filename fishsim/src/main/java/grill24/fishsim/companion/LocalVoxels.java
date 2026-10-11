package grill24.fishsim.companion;

import java.util.Arrays;

/**
 * A rolling window of blocks around the owner, plus the geometric questions the companion asks of
 * them (docs/fish-companions-sim-plan.md §3.1).
 *
 * <p>Cells are sampled lazily: the first question about a cell asks the {@link BlockQuery} and the
 * answer is kept until the window re-centres (the owner wandered far from its centre) or
 * {@link #invalidate} is called after a block change. Cells outside the window go straight to the
 * source, so a question is never wrong, only slower.
 */
public final class LocalVoxels implements BlockQuery {

    private static final int SIZE = 32;
    private static final int HALF = SIZE / 2;
    /** How far the owner may stray from the window centre before it re-centres, blocks. */
    private static final int RECENTER = 6;
    private static final int WORDS = SIZE * SIZE * SIZE / 64;

    private final long[] known = new long[WORDS];
    private final long[] solid = new long[WORDS];
    private final long[] wet = new long[WORDS];
    /** A solid cell's {@link BlockQuery#top} in sixteenths (16 = a full block); 0 for other cells. */
    private final byte[] tops = new byte[SIZE * SIZE * SIZE];
    private BlockQuery source = (x, y, z) -> false;
    private int minX, minY, minZ;
    private boolean placed;

    /** Points the window at {@code source} and re-centres it on {@code center} if needed. */
    public void update(BlockQuery source, Vec center) {
        int cx = floor(center.x()), cy = floor(center.y()), cz = floor(center.z());
        if (source != this.source || !placed
                || Math.abs(cx - (minX + HALF)) > RECENTER
                || Math.abs(cy - (minY + HALF)) > RECENTER
                || Math.abs(cz - (minZ + HALF)) > RECENTER) {
            this.source = source;
            minX = cx - HALF;
            minY = cy - HALF;
            minZ = cz - HALF;
            placed = true;
            invalidate();
        }
    }

    /** Forgets every sampled cell — call after a block change near the owner. */
    public void invalidate() {
        Arrays.fill(known, 0L);
        Arrays.fill(solid, 0L);
        Arrays.fill(wet, 0L);
        Arrays.fill(tops, (byte) 0);
    }

    @Override
    public boolean solid(int x, int y, int z) {
        int bit = bit(x, y, z);
        if (bit < 0) return source.solid(x, y, z);
        sample(bit, x, y, z);
        return (solid[bit >>> 6] & (1L << (bit & 63))) != 0;
    }

    @Override
    public boolean water(int x, int y, int z) {
        int bit = bit(x, y, z);
        if (bit < 0) return source.water(x, y, z);
        sample(bit, x, y, z);
        return (wet[bit >>> 6] & (1L << (bit & 63))) != 0;
    }

    /** Whether {@code p} is in water. */
    public boolean water(Vec p) {
        return water(floor(p.x()), floor(p.y()), floor(p.z()));
    }

    /** The cell's index in the window, or −1 outside it. */
    private int bit(int x, int y, int z) {
        int lx = x - minX, ly = y - minY, lz = z - minZ;
        if (lx < 0 || ly < 0 || lz < 0 || lx >= SIZE || ly >= SIZE || lz >= SIZE) return -1;
        return (lx * SIZE + ly) * SIZE + lz;
    }

    private void sample(int bit, int x, int y, int z) {
        int word = bit >>> 6;
        long mask = 1L << (bit & 63);
        if ((known[word] & mask) != 0) return;
        known[word] |= mask;
        if (source.solid(x, y, z)) {
            solid[word] |= mask;
            tops[bit] = (byte) Math.max(1, Math.min(16, Math.round(source.top(x, y, z) * 16)));
        } else if (source.water(x, y, z)) {
            wet[word] |= mask;
        }
    }

    @Override
    public float top(int x, int y, int z) {
        int bit = bit(x, y, z);
        if (bit < 0) return source.solid(x, y, z) ? source.top(x, y, z) : 0f;
        sample(bit, x, y, z);
        return tops[bit] / 16f;
    }

    /** How high a solid cell's collision reaches, blocks above its floor (1 for a full block). */
    private double topOf(int x, int y, int z) {
        int bit = bit(x, y, z);
        return bit < 0 ? source.top(x, y, z) : tops[bit] / 16.0;
    }

    /** Whether a sphere of radius {@code r} at {@code p} touches no solid block. */
    public boolean sphereClear(Vec p, double r) {
        int x0 = floor(p.x() - r), x1 = floor(p.x() + r);
        int y0 = floor(p.y() - r), y1 = floor(p.y() + r);
        int z0 = floor(p.z() - r), z1 = floor(p.z() + r);
        double r2 = r * r;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (solid(x, y, z) && boxDistanceSqr(p, x, y, z, topOf(x, y, z)) < r2) return false;
                }
            }
        }
        return true;
    }

    /** Whether a sphere of radius {@code r} can sweep from {@code a} to {@code b} without touching a block. */
    public boolean segmentClear(Vec a, Vec b, double r) {
        double len = a.distanceTo(b);
        int steps = Math.max(1, (int) Math.ceil(len / 0.2));
        for (int i = 0; i <= steps; i++) {
            if (!sphereClear(a.lerp(b, i / (double) steps), r)) return false;
        }
        return true;
    }

    /**
     * The push away from nearby blocks: each solid cell within {@code margin} contributes along
     * the line from its nearest point to {@code p}, weighted by (1 − d/margin)², so the push
     * grows smoothly from zero at the margin. Length is at most 1.
     */
    public Vec avoidance(Vec p, double margin) {
        int x0 = floor(p.x() - margin), x1 = floor(p.x() + margin);
        int y0 = floor(p.y() - margin), y1 = floor(p.y() + margin);
        int z0 = floor(p.z() - margin), z1 = floor(p.z() + margin);
        double sx = 0, sy = 0, sz = 0;
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    if (!solid(x, y, z)) continue;
                    double top = topOf(x, y, z);
                    double qx = clamp(p.x(), x, x + 1), qy = clamp(p.y(), y, y + top), qz = clamp(p.z(), z, z + 1);
                    double dx = p.x() - qx, dy = p.y() - qy, dz = p.z() - qz;
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d >= margin) continue;
                    if (d < 1e-6) {
                        // Inside the block: push out from its centre.
                        dx = p.x() - (x + 0.5);
                        dy = p.y() - (y + top * 0.5);
                        dz = p.z() - (z + 0.5);
                        d = Math.max(1e-6, Math.sqrt(dx * dx + dy * dy + dz * dz));
                    }
                    double w = 1 - Math.min(d, margin) / margin;
                    w *= w;
                    sx += dx / d * w;
                    sy += dy / d * w;
                    sz += dz / d * w;
                }
            }
        }
        return new Vec(sx, sy, sz).clampLength(1);
    }

    private static double boxDistanceSqr(Vec p, int x, int y, int z, double top) {
        double dx = p.x() - clamp(p.x(), x, x + 1);
        double dy = p.y() - clamp(p.y(), y, y + top);
        double dz = p.z() - clamp(p.z(), z, z + 1);
        return dx * dx + dy * dy + dz * dz;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    // ── The ground, for things that walk on it ────────────────────────────────────────────────

    /**
     * The surface something standing at ({@code x}, {@code z}) would stand on: the highest top of a
     * solid cell at or below {@code fromY} (+ a hair), no more than {@code depth} below it, with
     * room above it for a body of radius {@code r}. NaN if there is none — open air too deep, or
     * a block over the surface (a wall: the floor found is under something solid).
     */
    public double floorBelow(double x, double z, double fromY, double depth, double r) {
        int bx = floor(x), bz = floor(z);
        int y0 = floor(fromY + 1e-6);
        int y1 = floor(fromY - depth);
        for (int y = y0; y >= y1; y--) {
            if (!solid(bx, y, bz)) continue;
            double s = y + topOf(bx, y, bz);
            if (s > fromY + 1e-6) return Double.NaN; // its top is above where we stand: a wall
            if (s < fromY - depth) return Double.NaN;
            return sphereClear(new Vec(x, s + r + GROUND_LIFT, z), r) ? s : Double.NaN;
        }
        return Double.NaN;
    }

    /**
     * The surface at the top of column ({@code bx}, {@code bz}) at or below {@code fromY} (+ a hair),
     * no more than {@code depth} below it: NaN if there is none (too deep), or if the first solid
     * cell met reaches above {@code fromY} (a wall).
     */
    public double columnFloor(int bx, int bz, double fromY, double depth) {
        int y0 = floor(fromY + 1e-6);
        int y1 = floor(fromY - depth);
        for (int y = y0; y >= y1; y--) {
            if (!solid(bx, y, bz)) continue;
            double s = y + topOf(bx, y, bz);
            if (s > fromY + 1e-6 || s < fromY - depth) return Double.NaN;
            return s;
        }
        return Double.NaN;
    }

    /** How far above the floor a standing body's centre sits beyond its radius: never quite touching. */
    public static final double GROUND_LIFT = 0.002;

    static int floor(double v) {
        return (int) Math.floor(v);
    }
}
