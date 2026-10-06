package grill24.fishsim.harness;

import grill24.fishsim.core.FishSpec;
import grill24.fishsim.core.Locomotion;
import grill24.fishsim.core.ShelterUse;
import grill24.fishsim.domain.Shelter;
import grill24.fishsim.domain.VoxelDomain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Shared scenario factories for the headless runner and the viewer. */
public final class Scenarios {

    private Scenarios() {}

    /** Deterministic mixed swimmer population, matching the invariant tests' distribution. */
    public static FishSpec[] specs(int n, long seed) {
        Random r = new Random(seed * 31 + n);
        FishSpec[] specs = new FishSpec[n];
        for (int i = 0; i < n; i++) {
            specs[i] = new FishSpec(0.06f + r.nextFloat() * 0.2f, Locomotion.FREE_SWIM, r.nextBoolean(), r.nextInt(3));
        }
        return specs;
    }

    /**
     * A shoal of clown loaches (0.26 blocks, give or take a little) that visit shelters — the
     * Phase 2 acceptance cast (docs/fish-shelters.md §9).
     */
    public static FishSpec[] loaches(int n, long seed) {
        Random r = new Random(seed * 31 + n);
        FishSpec[] specs = new FishSpec[n];
        for (int i = 0; i < n; i++) {
            specs[i] = new FishSpec(0.24f + r.nextFloat() * 0.04f, Locomotion.FREE_SWIM, r.nextBoolean(), 1,
                    ShelterUse.VISITOR);
        }
        return specs;
    }

    /** {@link #specs}, every fish a shelter visitor — sizes from a goby's to a loach's. */
    public static FishSpec[] visitors(int n, long seed) {
        FishSpec[] specs = specs(n, seed);
        for (int i = 0; i < n; i++) specs[i] = specs[i].withShelterUse(ShelterUse.VISITOR);
        return specs;
    }

    /** {@link #loaches} that bolt for cover when the watcher walks up — think neon tetras. */
    public static FishSpec[] skittish(int n, long seed) {
        FishSpec[] specs = loaches(n, seed);
        for (int i = 0; i < n; i++) specs[i] = specs[i].withShelterUse(ShelterUse.SKITTISH);
        return specs;
    }

    /**
     * A bichir-sized lurker (0.30 blocks, a little shorter than the real one so it clears the Hollow
     * Log's mouth) among {@code n − 1} visiting loaches — the Phase 4 cast.
     */
    public static FishSpec[] lurker(int n, long seed) {
        FishSpec[] specs = loaches(n, seed);
        specs[0] = new FishSpec(0.30f, Locomotion.FREE_SWIM, false, 2, ShelterUse.LURKER);
        return specs;
    }

    /** A named cast: {@code mixed} ({@link #specs}), {@code visitors}, {@code loaches}, {@code skittish} or {@code lurker}. */
    public static FishSpec[] cast(String name, int n, long seed) {
        return switch (name) {
            case "mixed" -> specs(n, seed);
            case "visitors" -> visitors(n, seed);
            case "loaches" -> loaches(n, seed);
            case "skittish" -> skittish(n, seed);
            case "lurker" -> lurker(n, seed);
            default -> throw new IllegalArgumentException("Unknown cast: " + name);
        };
    }

    /** Named domain occupancies: {@code L}, {@code 2x2slab}, or {@code WxHxD} full grids. */
    public static boolean[][][] occupancy(String name) {
        if (name.equals("L")) {
            boolean[][][] g = new boolean[3][1][3];
            for (int ix = 0; ix < 3; ix++) g[ix][0][0] = true;
            g[2][0][1] = true;
            g[2][0][2] = true;
            return g;
        }
        String dims = name.equals("2x2slab") ? "2x1x2" : name;
        String[] parts = dims.split("x");
        if (parts.length != 3) throw new IllegalArgumentException("Unknown domain: " + name);
        boolean[][][] g = new boolean[Integer.parseInt(parts[0])][Integer.parseInt(parts[1])][Integer.parseInt(parts[2])];
        for (boolean[][] a : g) for (boolean[] b : a) Arrays.fill(b, true);
        return g;
    }

    /**
     * Height of the sand above a block's own bottom face — the mod's {@code CosmeticGridCell.FLOOR_Y}
     * (2/16), restated here because this module cannot see it.
     */
    public static final float SAND_SURFACE = 2f / 16f;

    /**
     * A voxel domain by name: {@link #occupancy} names, optionally followed by {@code +log} to put
     * a {@link #hollowLog} on the sand, centred in the bottom-storey block nearest the grid's
     * centre (the centre itself may be outside an L), or {@code +logpair} for two of them one
     * block either side of it, a block of sand between (the owner's 5x2x1 test tank), or
     * {@code +pipe} for a {@link #clayPipe} there, or {@code +arch} for a {@link #fenceArch}, or
     * {@code +skull} for the {@link #whaleSkull} (in a 4x2x2, the Whale Fall's own box).
     */
    public static VoxelDomain domain(String name) {
        String[] parts = name.split("\\+");
        boolean[][][] occupancy = occupancy(parts[0]);
        VoxelDomain domain = new VoxelDomain(occupancy);
        List<Shelter> shelters = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].equals("skull")) {
                shelters.add(whaleSkull(occupancy.length, occupancy[0].length, occupancy[0][0].length));
                continue;
            }
            boolean pair = parts[i].equals("logpair"), pipe = parts[i].equals("pipe"), arch = parts[i].equals("arch");
            if (!pair && !pipe && !arch && !parts[i].equals("log")) throw new IllegalArgumentException("Unknown shelter: " + parts[i]);
            int sx = occupancy.length, sy = occupancy[0].length, sz = occupancy[0][0].length;
            float bestL = 0f, bestD = 0f, best = Float.MAX_VALUE;
            for (int ix = 0; ix < sx; ix++) {
                for (int iz = 0; iz < sz; iz++) {
                    if (!occupancy[ix][0][iz]) continue;
                    float l = -sx / 2f + ix + 0.5f, d = -sz / 2f + iz + 0.5f;
                    if (l * l + d * d < best) {
                        best = l * l + d * d;
                        bestL = l;
                        bestD = d;
                    }
                }
            }
            if (arch) {
                shelters.add(fenceArch(bestL, -sy / 2f + SAND_SURFACE, bestD));
            } else if (pipe) {
                shelters.add(clayPipe(bestL, -sy / 2f + SAND_SURFACE, bestD));
            } else if (pair) {
                shelters.add(hollowLog(bestL - 1f, -sy / 2f + SAND_SURFACE, bestD));
                shelters.add(hollowLog(bestL + 1f, -sy / 2f + SAND_SURFACE, bestD));
            } else {
                shelters.add(hollowLog(bestL, -sy / 2f + SAND_SURFACE, bestD));
            }
        }
        domain.rebuildShelters(shelters);
        return domain;
    }

    /**
     * The shipped Hollow Log (cosmetic_structure/hollow_log.json) as the mod maps it, unturned:
     * built at scale 0.09 on a grid of 6 × 4 × 4 cells lying along lateral, with a 5 × 2 × 2 hollow
     * closed at the low-lateral end and open at the high one. Hand-placed rather than derived,
     * because the derivation lives on the Minecraft side; {@code TankSheltersTest} checks the real
     * mapping.
     *
     * @param centerL,centerD where the hull's centre stands
     * @param floorY          the sand the log lies on
     */
    /**
     * The Whale Fall's skull shelter (cosmetic_structure/whale_fall.json, from
     * tools/span-structure-gen/gen.py) as the mod maps it, unturned, in a group whose bounding box
     * is {@code sx × sy × sz} with the whale's box at its min corner. Build cell {@code (bx, by, bz)}
     * is a block of 0.125 centred {@code (bx + 0.5) × 0.125} in from the box's interior west and
     * north walls (1/16 thick), standing on the sand. The hollow is cells 5..7 x 1..2 x 4..10, the
     * hull 4..8 x 0..3 x 3..11, and the eye sockets open at x = 6 on either side, each one cell wide
     * and two tall. Hand-placed, like {@link #hollowLog}; {@code TankSheltersTest} checks the real
     * mapping.
     */
    public static Shelter whaleSkull(int sx, int sy, int sz) {
        float u = 0.125f, wall = 1f / 16f;
        float ox = wall - sx / 2f, oy = SAND_SURFACE - sy / 2f, oz = wall - sz / 2f;
        Shelter.OrientedBox hull = Shelter.OrientedBox.ofBounds(
                ox + 4 * u, oy, oz + 3 * u, ox + 9 * u, oy + 4 * u, oz + 12 * u);
        Shelter.OrientedBox interior = Shelter.OrientedBox.ofBounds(
                ox + 5 * u, oy + u, oz + 4 * u, ox + 8 * u, oy + 3 * u, oz + 11 * u);
        Shelter.Mouth north = new Shelter.Mouth(
                ox + 6.5f * u, oy + 2 * u, oz + 4 * u,
                0f, 0f, 1f,
                1f, 0f, 0f,
                0.5f * u, u);
        Shelter.Mouth south = new Shelter.Mouth(
                ox + 6.5f * u, oy + 2 * u, oz + 11 * u,
                0f, 0f, -1f,
                1f, 0f, 0f,
                0.5f * u, u);
        return new Shelter(hull, interior, List.of(north, south), 3, 7 * u);
    }

    /**
     * A Clay Pipe: the {@link #hollowLog}'s body with both ends open, so its hollow runs the full six
     * cells and has a mouth at each end (cosmetic_structure/clay_pipe.json).
     */
    public static Shelter clayPipe(float centerL, float floorY, float centerD) {
        float u = 0.09f;
        float hullMinL = centerL - 3f * u;
        float hullMinD = centerD - 2f * u;
        Shelter.OrientedBox hull = Shelter.OrientedBox.ofBounds(
                hullMinL, floorY, hullMinD, hullMinL + 6f * u, floorY + 4f * u, hullMinD + 4f * u);
        Shelter.OrientedBox interior = Shelter.OrientedBox.ofBounds(
                hullMinL, floorY + u, hullMinD + u, hullMinL + 6f * u, floorY + 3f * u, hullMinD + 3f * u);
        Shelter.Mouth high = new Shelter.Mouth(
                hullMinL + 6f * u, floorY + 2f * u, centerD,
                -1f, 0f, 0f,
                0f, 1f, 0f,
                u, u);
        Shelter.Mouth low = new Shelter.Mouth(
                hullMinL, floorY + 2f * u, centerD,
                1f, 0f, 0f,
                0f, 1f, 0f,
                u, u);
        return new Shelter(hull, interior, List.of(high, low), 2, 6f * u);
    }

    /**
     * A fence arch's opening as a {@link Shelter.Kind#GATE} (cosmetic_fence_arch_*.json): build
     * cells of a grid cell's width (0.875 / 3), posts at x = ±1 and the opening the cell between,
     * on the sand, open front and back, so its two mouths face along depth. Hand-placed, like
     * {@link #clayPipe}; the posts themselves are obstacles in the game and not modelled here.
     */
    public static Shelter fenceArch(float centerL, float floorY, float centerD) {
        float u = 0.875f / 3f, h = u * 0.5f;
        Shelter.OrientedBox hull = Shelter.OrientedBox.ofBounds(
                centerL - 3f * h, floorY, centerD - h, centerL + 3f * h, floorY + 3f * u, centerD + h);
        Shelter.OrientedBox interior = Shelter.OrientedBox.ofBounds(
                centerL - h, floorY, centerD - h, centerL + h, floorY + u, centerD + h);
        Shelter.Mouth front = new Shelter.Mouth(
                centerL, floorY + h, centerD - h,
                0f, 0f, 1f,
                1f, 0f, 0f,
                h, h);
        Shelter.Mouth back = new Shelter.Mouth(
                centerL, floorY + h, centerD + h,
                0f, 0f, -1f,
                1f, 0f, 0f,
                h, h);
        return new Shelter(hull, interior, List.of(front, back), 1, u, Shelter.Kind.GATE);
    }

    /**
     * Obstacle boxes from a file: one per line, {@code minL minY minD maxL maxY maxD}, blank lines
     * and {@code #} comments skipped (the format {@code ObstacleExport} writes).
     */
    public static List<Shelter.OrientedBox> obstacles(java.nio.file.Path file) throws java.io.IOException {
        List<Shelter.OrientedBox> out = new ArrayList<>();
        for (String line : java.nio.file.Files.readAllLines(file)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\\s+");
            out.add(Shelter.OrientedBox.ofBounds(Float.parseFloat(f[0]), Float.parseFloat(f[1]), Float.parseFloat(f[2]),
                    Float.parseFloat(f[3]), Float.parseFloat(f[4]), Float.parseFloat(f[5])));
        }
        return out;
    }

    public static Shelter hollowLog(float centerL, float floorY, float centerD) {
        float u = 0.09f;
        float hullMinL = centerL - 3f * u;
        float hullMinD = centerD - 2f * u;
        Shelter.OrientedBox hull = Shelter.OrientedBox.ofBounds(
                hullMinL, floorY, hullMinD, hullMinL + 6f * u, floorY + 4f * u, hullMinD + 4f * u);
        Shelter.OrientedBox interior = Shelter.OrientedBox.ofBounds(
                hullMinL + u, floorY + u, hullMinD + u, hullMinL + 6f * u, floorY + 3f * u, hullMinD + 3f * u);
        Shelter.Mouth mouth = new Shelter.Mouth(
                hullMinL + 6f * u, floorY + 2f * u, centerD,
                -1f, 0f, 0f,
                0f, 1f, 0f,
                u, u);
        return new Shelter(hull, interior, List.of(mouth), 5, 5f * u);
    }
}
