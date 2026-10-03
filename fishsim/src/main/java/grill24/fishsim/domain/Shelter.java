package grill24.fishsim.domain;

import java.util.List;

/**
 * A cosmetic a fish can swim into, rest in and leave — a hollow log, a clay pipe, a cave
 * (docs/fish-shelters.md §3). Expressed in the engine's local frame, ready to steer against: the
 * host derives it from the structure's authored hollow and maps it through the same frame the
 * fish are simulated in.
 *
 * <p>Both boxes are <i>oriented</i>: a lone tank's engine is turned by the tank's facing, so a
 * shelter that is axis-aligned in the block grid is not axis-aligned here. Rotation is about the
 * vertical only, because cosmetics only ever turn about the vertical.
 *
 * @param hull         the walls a fish not using this shelter must not pass through — the
 *                     bounding box of the structure's parts that touch the hollow, hollow included
 * @param interior     the box a fish occupies while inside
 * @param mouths       the openings from the interior to open water; never empty
 * @param capacity     how many fish may be inside at once
 * @param interiorRun  the longest straight horizontal run through the interior, in blocks — what
 *                     the length gate measures against (§5.2)
 * @param kind         what a visit there looks like (§12.1)
 * @param minLength    the shortest fish that may use it, in blocks; 0 for any. A seat kept for the
 *                     biggest fish in the tank (§12.9) is passed over by the small fry
 */
public record Shelter(OrientedBox hull, OrientedBox interior, List<Mouth> mouths, int capacity,
                      float interiorRun, Kind kind, float minLength) {

    public Shelter {
        mouths = List.copyOf(mouths);
    }

    /** A {@link Kind#HOLLOW} — every shelter before §12.4's gates. */
    public Shelter(OrientedBox hull, OrientedBox interior, List<Mouth> mouths, int capacity, float interiorRun) {
        this(hull, interior, mouths, capacity, interiorRun, Kind.HOLLOW);
    }

    /** A shelter any fish that fits may use, whatever its size. */
    public Shelter(OrientedBox hull, OrientedBox interior, List<Mouth> mouths, int capacity, float interiorRun, Kind kind) {
        this(hull, interior, mouths, capacity, interiorRun, kind, 0f);
    }

    /** What a visit looks like (docs/fish-shelters.md §12.1, §12.4). */
    public enum Kind {
        /**
         * A hollow fish hide in (the Hollow Log, the Clay Pipe): opt-in species only, counted
         * against the hidden budget, cover for a startle and a home for a lurker.
         */
        HOLLOW,
        /**
         * A space fish visit on show (the Spruce Gazebo's floor, under its roof): visited like a
         * hollow, but a fish in it is in plain view, so any swimmer that fits may go, it never
         * counts as hidden, and it is neither cover nor a home.
         */
        OPEN,
        /**
         * An opening swum straight through without stopping (a fence arch, the Torii Gate): taken
         * only by a fish already heading through it, by any swimmer that fits, with no length gate
         * and no dwell. It has no hull: its posts are obstacles, and the opening is open water.
         */
        GATE;

        /** Whether a fish in it is out of sight — what the hidden budget counts (§5.4). */
        public boolean hides() {
            return this == HOLLOW;
        }
    }

    /**
     * A box turned about the vertical. {@code (cos, sin)} is the direction of the box's own
     * lateral axis in the engine's (lateral, depth) plane; its depth axis is that turned a quarter,
     * {@code (−sin, cos)}. Half-extents are along the box's own axes.
     */
    public record OrientedBox(float centerL, float centerY, float centerD,
                              float halfL, float halfY, float halfD,
                              float cos, float sin) {

        /** An unrotated box from its min and max corners. */
        public static OrientedBox ofBounds(float minL, float minY, float minD,
                                           float maxL, float maxY, float maxD) {
            return new OrientedBox((minL + maxL) * 0.5f, (minY + maxY) * 0.5f, (minD + maxD) * 0.5f,
                    (maxL - minL) * 0.5f, (maxY - minY) * 0.5f, (maxD - minD) * 0.5f, 1f, 0f);
        }

        /** Writes this point in the box's own frame, relative to its centre, into {@code out[0..2]}. */
        public void toBox(float l, float y, float d, float[] out) {
            float dl = l - centerL, dd = d - centerD;
            out[0] = dl * cos + dd * sin;
            out[1] = y - centerY;
            out[2] = -dl * sin + dd * cos;
        }

        /** Whether the point lies inside the box (boundary included). */
        public boolean contains(float l, float y, float d) {
            float dl = l - centerL, dd = d - centerD;
            float u = dl * cos + dd * sin;
            float w = -dl * sin + dd * cos;
            return Math.abs(u) <= halfL && Math.abs(y - centerY) <= halfY && Math.abs(w) <= halfD;
        }

        /**
         * Signed distance from the point to the box surface — positive outside, negative inside —
         * with the outward direction of that distance written to {@code grad[0..2]} in the engine
         * frame. Outside, the direction points from the nearest surface point to the point (corner
         * and edge regions included, so a fish skirting a corner is pushed round it, not into
         * it). Inside, it is the normal of the nearest face, the shortest way out.
         */
        public float signedDistance(float l, float y, float d, float[] grad) {
            float dl = l - centerL, dd = d - centerD;
            float u = dl * cos + dd * sin;
            float v = y - centerY;
            float w = -dl * sin + dd * cos;
            float eu = Math.abs(u) - halfL, ev = Math.abs(v) - halfY, ew = Math.abs(w) - halfD;
            float gu, gv, gw, dist;
            if (eu > 0f || ev > 0f || ew > 0f) {
                float ou = Math.max(eu, 0f), ov = Math.max(ev, 0f), ow = Math.max(ew, 0f);
                dist = (float) Math.sqrt(ou * ou + ov * ov + ow * ow);
                gu = Math.copySign(ou, u) / dist;
                gv = Math.copySign(ov, v) / dist;
                gw = Math.copySign(ow, w) / dist;
            } else if (eu >= ev && eu >= ew) {
                dist = eu;
                gu = u < 0f ? -1f : 1f; gv = 0f; gw = 0f;
            } else if (ev >= ew) {
                dist = ev;
                gu = 0f; gv = v < 0f ? -1f : 1f; gw = 0f;
            } else {
                dist = ew;
                gu = 0f; gv = 0f; gw = w < 0f ? -1f : 1f;
            }
            grad[0] = gu * cos - gw * sin;
            grad[1] = gv;
            grad[2] = gu * sin + gw * cos;
            return dist;
        }

        /** The engine-frame corner {@code (±halfL, ±halfY, ±halfD)} picked by the sign bits of {@code corner}. */
        public void corner(int corner, float[] out) {
            float u = (corner & 1) != 0 ? halfL : -halfL;
            float v = (corner & 2) != 0 ? halfY : -halfY;
            float w = (corner & 4) != 0 ? halfD : -halfD;
            out[0] = centerL + u * cos - w * sin;
            out[1] = centerY + v;
            out[2] = centerD + u * sin + w * cos;
        }
    }

    /**
     * One opening: a flat rectangle on the interior's boundary. {@code normal} points <i>into</i>
     * the shelter; {@code tangent} is one in-plane axis and the other is {@code normal × tangent}.
     * Half-sizes are along those two axes.
     */
    public record Mouth(float centerL, float centerY, float centerD,
                        float normalL, float normalY, float normalD,
                        float tangentL, float tangentY, float tangentD,
                        float halfTangent, float halfBitangent) {

        /** The narrower half-opening — what a fish's height has to fit through (§5.2). */
        public float halfSize() {
            return Math.min(halfTangent, halfBitangent);
        }

        /** The second in-plane axis, {@code normal × tangent}, into {@code out[0..2]}. */
        public void bitangent(float[] out) {
            out[0] = normalY * tangentD - normalD * tangentY;
            out[1] = normalD * tangentL - normalL * tangentD;
            out[2] = normalL * tangentY - normalY * tangentL;
        }
    }
}
