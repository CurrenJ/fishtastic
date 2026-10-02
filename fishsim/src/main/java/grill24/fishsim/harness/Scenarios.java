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

    /** A named cast: {@code mixed} ({@link #specs}), {@code visitors}, {@code loaches} or {@code skittish}. */
    public static FishSpec[] cast(String name, int n, long seed) {
        return switch (name) {
            case "mixed" -> specs(n, seed);
            case "visitors" -> visitors(n, seed);
            case "loaches" -> loaches(n, seed);
            case "skittish" -> skittish(n, seed);
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
     * centre (the centre itself may be outside an L).
     */
    public static VoxelDomain domain(String name) {
        String[] parts = name.split("\\+");
        boolean[][][] occupancy = occupancy(parts[0]);
        VoxelDomain domain = new VoxelDomain(occupancy);
        List<Shelter> shelters = new ArrayList<>();
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].equals("log")) throw new IllegalArgumentException("Unknown shelter: " + parts[i]);
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
            shelters.add(hollowLog(bestL, -sy / 2f + SAND_SURFACE, bestD));
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
