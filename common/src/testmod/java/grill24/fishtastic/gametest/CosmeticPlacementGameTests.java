package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticPlacement;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.SpanStructures;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import grill24.fishtastic.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Where {@link CosmeticPlacement} puts a structure cosmetic as the player's aim walks cell by cell
 * along a row of joined tanks. A structure follows the aimed grid cell — not just the aimed tank —
 * wherever it fits, crossing the open seams between tanks when it has to.
 */
public final class CosmeticPlacementGameTests {

    private CosmeticPlacementGameTests() {}

    private static final int G = CosmeticGridCell.GRID_SIZE;

    /** A 3×3-footprint floor structure: before cross-tank placement it fit only on a tank's centre cell. */
    private static final ResourceKey<CosmeticStructure> GEODE = key("amethyst_geode");
    /** A 4×2×1 span with room to spare along x. */
    private static final ResourceKey<CosmeticStructure> ZIGGURAT = key("sunken_ziggurat");

    private static ResourceKey<CosmeticStructure> key(String path) {
        return ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, Ids.of("fishtastic", path));
    }

    /** A row of {@code length} joined tanks along x from {@code (0, 1, 2)}, {@code storeys} high, on stone. */
    private static List<FishTankBlockEntity> row(GameTestHelper helper, int length, int storeys) {
        List<FishTankBlockEntity> bottom = new ArrayList<>();
        for (int x = 0; x < length; x++) {
            helper.setBlock(new BlockPos(x, 0, 2), Blocks.STONE);
            for (int y = 1; y <= storeys; y++) {
                helper.setBlock(new BlockPos(x, y, 2), FishtasticBlocks.FISH_TANK.value());
            }
        }
        for (int x = 0; x < length; x++) {
            bottom.add((FishTankBlockEntity) helper.getBlockEntity(new BlockPos(x, 1, 2)));
        }
        return bottom;
    }

    /**
     * Stands the player's eye inside the row's bottom storey, straight over the centre of global
     * grid cell {@code (gx, 1)} (counted from the row's west end), looking down — facing north, so
     * structures keep their authored rotation.
     */
    private static void aimAt(GameTestHelper helper, Player player, List<FishTankBlockEntity> row, int gx) {
        BlockPos tank = row.get(gx / G).getBlockPos();
        CosmeticGridCell cell = new CosmeticGridCell(gx % G, 1);
        double x = tank.getX() + cell.localX();
        double z = tank.getZ() + cell.localZ();
        double eyeY = tank.getY() + 0.9;
        player.moveTo(x, eyeY - player.getEyeHeight(), z, 180f, 90f);
    }

    /** The global x of every footprint cell a plan shows, counted from the row's west end. */
    private static TreeSet<Integer> footprintX(List<FishTankBlockEntity> row, CosmeticPlacement.Plan plan) {
        TreeSet<Integer> xs = new TreeSet<>();
        int west = row.get(0).getBlockPos().getX();
        for (CosmeticPlacement.TankCell c : plan.cells()) {
            xs.add((c.tank().getBlockPos().getX() - west) * G + c.cell().gridX());
        }
        return xs;
    }

    private static ItemStack stack(ResourceKey<CosmeticStructure> structure) {
        return new ItemStack(FishTankStructureCosmeticItem.forStructure(structure));
    }

    /**
     * A 3×3 floor structure in a row of three tanks centres on whichever cell is aimed at, as long
     * as its footprint stays on the row's floor: every cell but the two end ones.
     */
    public static void floorStructureFollowsAimedCell(GameTestHelper helper) {
        List<FishTankBlockEntity> row = row(helper, 3, 1);
        Player player = helper.makeMockPlayer();
        StringBuilder observed = new StringBuilder();
        boolean ok = true;
        for (int gx = 0; gx < 3 * G; gx++) {
            aimAt(helper, player, row, gx);
            CosmeticPlacement.Plan plan = CosmeticPlacement.plan(player, row.get(gx / G), stack(GEODE));
            TreeSet<Integer> xs = footprintX(row, plan);
            boolean fits = gx >= 1 && gx <= 3 * G - 2;
            boolean expected = fits
                    ? plan.valid() && xs.equals(new TreeSet<>(List.of(gx - 1, gx, gx + 1)))
                    : !plan.valid();
            ok &= expected;
            observed.append(String.format("%n  aim %d: %s footprint x %s", gx, plan.valid() ? "VALID" : "refused", xs));
        }
        helper.assertTrue(ok, "3x3 structure must centre on every aimed cell it fits on:" + observed);
        helper.succeed();
    }

    /**
     * A span in a row of six tanks moves with every aimed cell, not every aimed tank: the three
     * cells of one tank give three different placements, and aiming further east never moves it west.
     */
    public static void spanStructureFollowsAimedCell(GameTestHelper helper) {
        List<FishTankBlockEntity> row = row(helper, 6, 2);
        Player player = helper.makeMockPlayer();
        StringBuilder observed = new StringBuilder();
        List<Integer> west = new ArrayList<>();
        boolean allValid = true;
        for (int gx = 0; gx < 6 * G; gx++) {
            aimAt(helper, player, row, gx);
            CosmeticPlacement.Plan plan = CosmeticPlacement.plan(player, row.get(gx / G), stack(ZIGGURAT));
            TreeSet<Integer> xs = footprintX(row, plan);
            allValid &= plan.valid();
            west.add(xs.isEmpty() ? -1 : xs.first());
            observed.append(String.format("%n  aim %2d: %s region %s footprint x %s", gx,
                    plan.valid() ? "VALID" : "refused", plan.region(), xs));
        }
        boolean monotonic = true;
        for (int i = 1; i < west.size(); i++) monotonic &= west.get(i) >= west.get(i - 1);
        int a = west.get(2 * G), b = west.get(2 * G + 1), c = west.get(2 * G + 2);
        boolean perCell = a != b && b != c && a != c;
        helper.assertTrue(allValid && monotonic && perCell,
                "span must follow the aimed cell (valid=" + allValid + " monotonic=" + monotonic
                        + " perCell=" + perCell + "):" + observed);
        helper.succeed();
    }

    /** Saves a tank and loads it back over itself, as a world reload (or a client sync) would. */
    private static void reload(GameTestHelper helper, FishTankBlockEntity tank) {
        CompoundTag saved = tank.saveWithoutMetadata();
        tank.load(saved);
    }

    /**
     * A 3×3 structure anchored on a tank's east edge cell reaches across the seam: the anchor holds
     * it, the east neighbour links the cell it reaches there, both survive a reload, an overlapping placement
     * is refused, and taking the structure out frees the neighbour's cells too.
     */
    public static void crossSeamStructureLinksNeighbour(GameTestHelper helper) {
        List<FishTankBlockEntity> row = row(helper, 3, 1);
        FishTankBlockEntity west = row.get(0), east = row.get(1);
        Player player = helper.makeMockPlayer();
        aimAt(helper, player, row, 2);
        CosmeticPlacement.Plan plan = CosmeticPlacement.plan(player, west, stack(GEODE));
        helper.assertTrue(plan != null && plan.valid(), "geode must fit on the west tank's edge cell");
        plan.apply().run();

        CosmeticGridCell anchor = new CosmeticGridCell(2, 1);
        reload(helper, west);
        reload(helper, east);
        helper.assertTrue(west.getStructureCosmetics().containsKey(anchor), "anchor tank must hold the geode at its aimed cell");
        // The geode occupies a plus of five cells: its east arm lands on the neighbour's (0, 1).
        Set<CosmeticGridCell> expected = Set.of(new CosmeticGridCell(0, 1));
        helper.assertTrue(east.getStructureLinks().keySet().equals(expected),
                "east tank must link the geode's east arm, got " + east.getStructureLinks().keySet());
        helper.assertTrue(east.getStructureCellIndex().keySet().containsAll(expected), "linked cells must block the floor");
        helper.assertTrue(west.getStructureCellIndex().size() == 4, "anchor must keep its own 4 cells, got " + west.getStructureCellIndex());

        aimAt(helper, player, row, 4);
        CosmeticPlacement.Plan overlap = CosmeticPlacement.plan(player, east, stack(GEODE));
        helper.assertTrue(overlap != null && !overlap.valid(), "a geode overlapping the linked column must be refused");

        west.removeStructureCosmeticEntry(anchor);
        helper.assertTrue(east.getStructureLinks().isEmpty() && east.getStructureCellIndex().isEmpty(),
                "removing the geode must free the neighbour's cells, got " + east.getStructureCellIndex());
        helper.assertTrue(west.getStructureCellIndex().isEmpty(), "removing the geode must free the anchor's cells");
        helper.succeed();
    }

    /** A span placed off its authored box keeps its shift through a reload, and every tank of its box resolves to it. */
    public static void shiftedSpanRoundTrips(GameTestHelper helper) {
        List<FishTankBlockEntity> row = row(helper, 6, 2);
        Player player = helper.makeMockPlayer();
        aimAt(helper, player, row, 2 * G + 1);
        CosmeticPlacement.Plan plan = CosmeticPlacement.plan(player, row.get(2), stack(ZIGGURAT));
        helper.assertTrue(plan != null && plan.valid(), "ziggurat must fit");
        plan.apply().run();

        FishTankBlockEntity anchor = null;
        for (FishTankBlockEntity tank : row) {
            if (SpanStructures.anchoredIn(helper.getLevel(), tank) != null) anchor = tank;
        }
        helper.assertTrue(anchor != null, "one tank must anchor the span");
        FishTankBlockEntity.PlacedStructureCosmetic before = anchor.getStructureCosmetics().get(new CosmeticGridCell(0, 0));
        helper.assertTrue(before.shiftX() != 0f, "aiming off the box-centred spot must shift the span, got " + before);
        reload(helper, anchor);
        FishTankBlockEntity.PlacedStructureCosmetic after = anchor.getStructureCosmetics().get(new CosmeticGridCell(0, 0));
        helper.assertTrue(before.equals(after), "shift must survive a reload: " + before + " -> " + after);

        SpanStructures.Ref ref = SpanStructures.anchoredIn(helper.getLevel(), anchor);
        CosmeticStructure.Span box = ref.box();
        for (int x = 0; x < box.x(); x++) {
            for (int y = 0; y < box.y(); y++) {
                BlockPos pos = anchor.getBlockPos().offset(x, y, 0);
                FishTankBlockEntity tank = (FishTankBlockEntity) helper.getLevel().getBlockEntity(pos);
                SpanStructures.Ref seen = SpanStructures.resolve(helper.getLevel(), tank);
                helper.assertTrue(seen != null && seen.anchor() == anchor, "box tank " + pos + " must resolve to the span");
            }
        }
        helper.succeed();
    }
}
