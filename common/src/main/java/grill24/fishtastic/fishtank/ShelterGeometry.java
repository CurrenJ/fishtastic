package grill24.fishtastic.fishtank;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything about a shelter that is derived rather than authored (docs/fish-shelters.md §3.1):
 * its mouths, its hull and the run its interior offers, worked out from the interior cells and
 * the structure's parts in the structure's own build grid — the integer grid the parts were
 * captured on, before {@code scale}, facing south.
 *
 * <p>Pure integer geometry with no Minecraft types, so the rules an author can trip over (a sealed
 * hollow, a pocket that looks like a mouth) are testable without a game.
 *
 * <p><b>Mouths.</b> An interior cell's face is a mouth face when the cell on its other side is
 * neither a part nor interior and can reach the outside of the structure's bounding box by flood
 * fill through such cells. Coplanar mouth faces that touch merge into one rectangular mouth, so a
 * 2×2 hollow's open end is one mouth, not four.
 */
public final class ShelterGeometry {

    /** A cell of the build grid. */
    public record Cell(int x, int y, int z) {
        Cell plus(int[] dir) {
            return new Cell(x + dir[0], y + dir[1], z + dir[2]);
        }
    }

    /** The six face directions, in a fixed order: −x, +x, −y, +y, −z, +z. */
    static final int[][] DIRECTIONS = {
            {-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}};

    /**
     * One opening, in build cells. {@code outward} is the face direction from the interior into
     * open water; the mouth covers the faces of interior cells {@code min..max} (inclusive, equal
     * along the {@code outward} axis) on that side.
     */
    public record Mouth(int[] outward, Cell min, Cell max) {
        /** Number of faces along the axis, or 1 along the normal. */
        public int span(int axis) {
            return switch (axis) {
                case 0 -> max.x - min.x + 1;
                case 1 -> max.y - min.y + 1;
                default -> max.z - min.z + 1;
            };
        }

        public int normalAxis() {
            return outward[0] != 0 ? 0 : outward[1] != 0 ? 1 : 2;
        }
    }

    /**
     * The derived shape.
     *
     * @param interiorMin,interiorMax inclusive bounding cells of the interior
     * @param hullMin,hullMax         inclusive bounding cells of the parts touching the interior,
     *                                and of the interior itself
     * @param interiorRun             longest straight horizontal run of interior cells, in cells
     */
    public record Shape(Set<Cell> interior, List<Mouth> mouths, Cell interiorMin, Cell interiorMax,
                        Cell hullMin, Cell hullMax, int interiorRun) {}

    /** A derivation that either produced a {@link Shape} or says what is wrong with the hollow. */
    public record Result(Shape shape, String error) {
        public boolean ok() {
            return shape != null;
        }
    }

    private ShelterGeometry() {}

    /** The capacity a shelter gets when it doesn't name one: a fish per four interior cells, at least one. */
    public static int defaultCapacity(int interiorCells) {
        return Math.max(1, interiorCells / 4);
    }

    public static Result derive(Collection<Cell> interiorCells, Collection<Cell> partCells) {
        if (interiorCells.isEmpty()) return new Result(null, "shelter interior is empty");
        Set<Cell> interior = new HashSet<>(interiorCells);
        Set<Cell> parts = new HashSet<>(partCells);
        for (Cell cell : interior) {
            if (parts.contains(cell)) {
                return new Result(null, "shelter interior cell " + format(cell) + " is also a part");
            }
        }

        // Bounding box of everything, padded by one so the outside surrounds the structure.
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Collection<Cell> set : List.of(interior, parts)) {
            for (Cell c : set) {
                minX = Math.min(minX, c.x); maxX = Math.max(maxX, c.x);
                minY = Math.min(minY, c.y); maxY = Math.max(maxY, c.y);
                minZ = Math.min(minZ, c.z); maxZ = Math.max(maxZ, c.z);
            }
        }
        minX--; minY--; minZ--;
        maxX++; maxY++; maxZ++;

        // Flood the open water from a corner of the padded box: every cell that is neither part
        // nor interior and connects to the box's outside. The padding shell is all open, so one
        // seed reaches all of it.
        Set<Cell> outside = new HashSet<>();
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        Cell seed = new Cell(minX, minY, minZ);
        outside.add(seed);
        queue.add(seed);
        while (!queue.isEmpty()) {
            Cell c = queue.poll();
            for (int[] dir : DIRECTIONS) {
                Cell n = c.plus(dir);
                if (n.x < minX || n.x > maxX || n.y < minY || n.y > maxY || n.z < minZ || n.z > maxZ) continue;
                if (parts.contains(n) || interior.contains(n) || !outside.add(n)) continue;
                queue.add(n);
            }
        }

        // Mouth faces, grouped by direction and plane, then merged into touching rectangles.
        List<Mouth> mouths = new ArrayList<>();
        for (int d = 0; d < DIRECTIONS.length; d++) {
            int[] dir = DIRECTIONS[d];
            Set<Cell> faces = new HashSet<>();
            for (Cell c : interior) {
                if (outside.contains(c.plus(dir))) faces.add(c);
            }
            mouths.addAll(mergeFaces(dir, faces));
        }

        // Every separate hollow needs its own way out — a sealed one is an authoring mistake.
        for (Set<Cell> component : components(interior)) {
            boolean open = false;
            for (Mouth mouth : mouths) {
                if (component.contains(mouth.min())) {
                    open = true;
                    break;
                }
            }
            if (!open) {
                Cell any = component.iterator().next();
                return new Result(null, "shelter interior is sealed: the hollow at " + format(any)
                        + " has no face open to the outside");
            }
        }
        mouths.sort((a, b) -> compareCells(a.min(), b.min()) != 0 ? compareCells(a.min(), b.min())
                : Integer.compare(dirIndex(a.outward()), dirIndex(b.outward())));

        Cell interiorMin = minOf(interior), interiorMax = maxOf(interior);

        // The hull: parts touching the interior (any of the 26 neighbours, so edge and corner
        // blocks of the wall count) together with the interior itself.
        Set<Cell> hullCells = new HashSet<>(interior);
        for (Cell c : interior) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Cell n = new Cell(c.x + dx, c.y + dy, c.z + dz);
                        if (parts.contains(n)) hullCells.add(n);
                    }
                }
            }
        }

        int run = 0;
        for (Cell c : interior) {
            if (!interior.contains(new Cell(c.x - 1, c.y, c.z))) {
                int len = 0;
                while (interior.contains(new Cell(c.x + len, c.y, c.z))) len++;
                run = Math.max(run, len);
            }
            if (!interior.contains(new Cell(c.x, c.y, c.z - 1))) {
                int len = 0;
                while (interior.contains(new Cell(c.x, c.y, c.z + len))) len++;
                run = Math.max(run, len);
            }
        }

        return new Result(new Shape(Set.copyOf(interior), List.copyOf(mouths), interiorMin, interiorMax,
                minOf(hullCells), maxOf(hullCells), run), null);
    }

    /**
     * Splits one direction's mouth faces into rectangles: each connected patch in the plane, grown
     * greedily — first along one in-plane axis, then the other while the whole row is present.
     */
    private static List<Mouth> mergeFaces(int[] dir, Set<Cell> faces) {
        List<Mouth> out = new ArrayList<>();
        int normal = dir[0] != 0 ? 0 : dir[1] != 0 ? 1 : 2;
        int a = normal == 0 ? 1 : 0;           // first in-plane axis
        int b = normal == 2 ? 1 : 2;           // second in-plane axis
        List<Cell> ordered = new ArrayList<>(faces);
        ordered.sort(ShelterGeometry::compareCells);
        Set<Cell> used = new HashSet<>();
        for (Cell start : ordered) {
            if (used.contains(start)) continue;
            int lenA = 1;
            while (true) {
                Cell next = offset(start, a, lenA, b, 0);
                if (!faces.contains(next) || used.contains(next)) break;
                lenA++;
            }
            int lenB = 1;
            grow:
            while (true) {
                for (int i = 0; i < lenA; i++) {
                    Cell next = offset(start, a, i, b, lenB);
                    if (!faces.contains(next) || used.contains(next)) break grow;
                }
                lenB++;
            }
            for (int i = 0; i < lenA; i++) {
                for (int j = 0; j < lenB; j++) used.add(offset(start, a, i, b, j));
            }
            out.add(new Mouth(dir.clone(), start, offset(start, a, lenA - 1, b, lenB - 1)));
        }
        return out;
    }

    private static Cell offset(Cell c, int axisA, int da, int axisB, int db) {
        int[] v = {c.x, c.y, c.z};
        v[axisA] += da;
        v[axisB] += db;
        return new Cell(v[0], v[1], v[2]);
    }

    private static List<Set<Cell>> components(Set<Cell> cells) {
        List<Set<Cell>> out = new ArrayList<>();
        Set<Cell> seen = new HashSet<>();
        List<Cell> ordered = new ArrayList<>(cells);
        ordered.sort(ShelterGeometry::compareCells);
        for (Cell start : ordered) {
            if (!seen.add(start)) continue;
            Set<Cell> component = new HashSet<>();
            ArrayDeque<Cell> queue = new ArrayDeque<>();
            component.add(start);
            queue.add(start);
            while (!queue.isEmpty()) {
                Cell c = queue.poll();
                for (int[] dir : DIRECTIONS) {
                    Cell n = c.plus(dir);
                    if (cells.contains(n) && seen.add(n)) {
                        component.add(n);
                        queue.add(n);
                    }
                }
            }
            out.add(component);
        }
        return out;
    }

    private static int dirIndex(int[] dir) {
        for (int i = 0; i < DIRECTIONS.length; i++) {
            if (DIRECTIONS[i][0] == dir[0] && DIRECTIONS[i][1] == dir[1] && DIRECTIONS[i][2] == dir[2]) return i;
        }
        return -1;
    }

    private static int compareCells(Cell p, Cell q) {
        if (p.x != q.x) return Integer.compare(p.x, q.x);
        if (p.y != q.y) return Integer.compare(p.y, q.y);
        return Integer.compare(p.z, q.z);
    }

    private static Cell minOf(Set<Cell> cells) {
        int x = Integer.MAX_VALUE, y = Integer.MAX_VALUE, z = Integer.MAX_VALUE;
        for (Cell c : cells) { x = Math.min(x, c.x); y = Math.min(y, c.y); z = Math.min(z, c.z); }
        return new Cell(x, y, z);
    }

    private static Cell maxOf(Set<Cell> cells) {
        int x = Integer.MIN_VALUE, y = Integer.MIN_VALUE, z = Integer.MIN_VALUE;
        for (Cell c : cells) { x = Math.max(x, c.x); y = Math.max(y, c.y); z = Math.max(z, c.z); }
        return new Cell(x, y, z);
    }

    private static String format(Cell c) {
        return "(" + c.x + ", " + c.y + ", " + c.z + ")";
    }
}
