package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticPlacement;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.fishtank.FloorCosmetics;
import grill24.fishtastic.fishtank.HangingCosmetics;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import grill24.fishtastic.fishtank.SpanStructures;
import grill24.fishtastic.fishtank.TankColumns;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import grill24.fishtastic.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
            bottom.add(helper.getBlockEntity(new BlockPos(x, 1, 2), FishTankBlockEntity.class));
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
        player.snapTo(x, eyeY - player.getEyeHeight(), z, 180f, 90f);
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
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
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
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
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
        CompoundTag saved = tank.saveWithoutMetadata(helper.getLevel().registryAccess());
        tank.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
    }

    /**
     * A 3×3 structure anchored on a tank's east edge cell reaches across the seam: the anchor holds
     * it, the east neighbour links the cell it reaches there, both survive a reload, an overlapping placement
     * is refused, and taking the structure out frees the neighbour's cells too.
     */
    public static void crossSeamStructureLinksNeighbour(GameTestHelper helper) {
        List<FishTankBlockEntity> row = row(helper, 3, 1);
        FishTankBlockEntity west = row.get(0), east = row.get(1);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
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
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
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

    /** How many of {@code item} lie around {@code pos} after a break. */
    private static int droppedAround(GameTestHelper helper, BlockPos pos, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2.0)).stream()
                .filter(entity -> entity.getItem().is(item))
                .mapToInt(entity -> entity.getItem().getCount())
                .sum();
    }

    /** Breaking a tank hands back every unit of what it stores: strand segments and stacked pieces alike. */
    public static void brokenTankDropsItsCosmetics(GameTestHelper helper) {
        FishTankBlockEntity tank = row(helper, 1, 1).get(0);
        tank.setCosmetic(new CosmeticGridCell(1, 1), new PlacedCosmetic(Blocks.KELP.defaultBlockState(), 3));
        tank.setCosmetic(new CosmeticGridCell(0, 0), new PlacedCosmetic(Blocks.SEA_PICKLE.defaultBlockState().setValue(BlockStateProperties.PICKLES, 3)));
        tank.setCeilingCosmetic(new CosmeticGridCell(1, 1), new PlacedCosmetic(Blocks.POINTED_DRIPSTONE.defaultBlockState(), 2));
        BlockPos pos = tank.getBlockPos();

        helper.getLevel().destroyBlock(pos, true);

        int kelp = droppedAround(helper, pos, Items.KELP);
        int pickles = droppedAround(helper, pos, Items.SEA_PICKLE);
        int dripstone = droppedAround(helper, pos, Items.POINTED_DRIPSTONE);
        helper.assertTrue(kelp == 3, "All 3 kelp segments must drop, got " + kelp);
        helper.assertTrue(pickles == 3, "All 3 sea pickles must drop, got " + pickles);
        helper.assertTrue(dripstone == 2, "Both dripstone segments must drop, got " + dripstone);
        helper.succeed();
    }

    /**
     * Breaking a storey a strand passes through shortens the strand to what still fits and drops the
     * rest; the strands that already fit are left alone.
     */
    public static void brokenStoreyTrimsStrandsThroughIt(GameTestHelper helper) {
        FishTankBlockEntity bottom = row(helper, 1, 3).get(0);
        BlockPos middle = bottom.getBlockPos().above();
        FishTankBlockEntity top = (FishTankBlockEntity) helper.getLevel().getBlockEntity(middle.above());
        CosmeticGridCell tall = new CosmeticGridCell(1, 1), shortCell = new CosmeticGridCell(0, 0);
        bottom.setCosmetic(tall, new PlacedCosmetic(Blocks.KELP.defaultBlockState(), 9));
        bottom.setCosmetic(shortCell, new PlacedCosmetic(Blocks.KELP.defaultBlockState(), 2));
        top.setCeilingCosmetic(tall, new PlacedCosmetic(Blocks.POINTED_DRIPSTONE.defaultBlockState(), 8));

        helper.getLevel().destroyBlock(middle, true);

        int room = TankColumns.maxSegments(1);
        helper.assertTrue(bottom.getCosmetics().get(tall).height() == room,
                "Kelp must shrink to " + room + ", got " + bottom.getCosmetics().get(tall).height());
        helper.assertTrue(bottom.getCosmetics().get(shortCell).height() == 2, "A strand that still fits must be untouched");
        helper.assertTrue(top.getCeilingCosmetics().get(tall).height() == room,
                "Dripstone must shrink to " + room + ", got " + top.getCeilingCosmetics().get(tall).height());
        int kelp = droppedAround(helper, middle, Items.KELP);
        int dripstone = droppedAround(helper, middle, Items.POINTED_DRIPSTONE);
        helper.assertTrue(kelp == 9 - room, "The kelp's excess must drop, got " + kelp);
        helper.assertTrue(dripstone == 8 - room, "The dripstone's excess must drop, got " + dripstone);
        helper.succeed();
    }

    private static FishTankBlockEntity placeGeodeAcrossSeam(GameTestHelper helper, List<FishTankBlockEntity> row) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        aimAt(helper, player, row, 2);
        CosmeticPlacement.Plan plan = CosmeticPlacement.plan(player, row.get(0), stack(GEODE));
        helper.assertTrue(plan != null && plan.valid(), "geode must fit on the west tank's edge cell");
        plan.apply().run();
        return row.get(0);
    }

    /** Breaking the tank anchoring a structure drops its one item and frees the neighbour it reached into. */
    public static void brokenAnchorDropsSeamStructure(GameTestHelper helper) {
        List<FishTankBlockEntity> row = row(helper, 3, 1);
        FishTankBlockEntity anchor = placeGeodeAcrossSeam(helper, row), east = row.get(1);
        BlockPos pos = anchor.getBlockPos();

        helper.getLevel().destroyBlock(pos, true);

        int dropped = droppedAround(helper, pos, stack(GEODE).getItem());
        helper.assertTrue(dropped == 1, "The geode must drop exactly once, got " + dropped);
        helper.assertTrue(east.getStructureLinks().isEmpty() && east.getStructureCellIndex().isEmpty(),
                "The neighbour's cells must be freed, got " + east.getStructureCellIndex());
        helper.succeed();
    }

    /** Breaking a neighbour a structure reaches into drops nothing: the structure stays with its anchor. */
    public static void brokenNeighbourKeepsSeamStructure(GameTestHelper helper) {
        List<FishTankBlockEntity> row = row(helper, 3, 1);
        FishTankBlockEntity anchor = placeGeodeAcrossSeam(helper, row);
        BlockPos east = row.get(1).getBlockPos();

        helper.getLevel().destroyBlock(east, true);

        int dropped = droppedAround(helper, east, stack(GEODE).getItem());
        helper.assertTrue(dropped == 0, "Only the anchor drops the geode, got " + dropped);
        helper.assertTrue(anchor.getStructureCosmetics().containsKey(new CosmeticGridCell(2, 1)), "The anchor must still hold the geode");
        helper.succeed();
    }

    private static FishTankBlockEntity placeSpan(GameTestHelper helper, List<FishTankBlockEntity> row) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        aimAt(helper, player, row, 2 * G + 1);
        CosmeticPlacement.Plan plan = CosmeticPlacement.plan(player, row.get(2), stack(ZIGGURAT));
        helper.assertTrue(plan != null && plan.valid(), "ziggurat must fit");
        plan.apply().run();
        for (FishTankBlockEntity tank : row) {
            if (SpanStructures.anchoredIn(helper.getLevel(), tank) != null) return tank;
        }
        throw new AssertionError("one tank must anchor the span");
    }

    /** Breaking a span's anchor drops its one item and clears the links in the rest of its box. */
    public static void brokenSpanAnchorDropsTheSpan(GameTestHelper helper) {
        List<FishTankBlockEntity> row = row(helper, 6, 2);
        FishTankBlockEntity anchor = placeSpan(helper, row);
        BlockPos pos = anchor.getBlockPos();
        FishTankBlockEntity member = (FishTankBlockEntity) helper.getLevel().getBlockEntity(pos.east());
        helper.assertTrue(member.getSpanLink() != null, "Setup: the next tank must link to the span");

        helper.getLevel().destroyBlock(pos, true);

        int dropped = droppedAround(helper, pos, stack(ZIGGURAT).getItem());
        helper.assertTrue(dropped == 1, "The span must drop exactly once, got " + dropped);
        helper.assertTrue(member.getSpanLink() == null, "The span's links must be cleared");
        helper.succeed();
    }

    /** Breaking another tank of a span's box drops nothing: the span stays with its anchor. */
    public static void brokenSpanMemberKeepsTheSpan(GameTestHelper helper) {
        List<FishTankBlockEntity> row = row(helper, 6, 2);
        FishTankBlockEntity anchor = placeSpan(helper, row);
        BlockPos member = anchor.getBlockPos().east();

        helper.getLevel().destroyBlock(member, true);

        int dropped = droppedAround(helper, member, stack(ZIGGURAT).getItem());
        helper.assertTrue(dropped == 0, "Only the anchor drops the span, got " + dropped);
        helper.assertTrue(SpanStructures.anchoredIn(helper.getLevel(), anchor) != null, "The anchor must still hold the span");
        helper.succeed();
    }

    /** Puts the player's eye at {@code eye} looking straight at {@code target}. */
    private static void look(Player player, Vec3 eye, Vec3 target) {
        Vec3 d = target.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        player.snapTo(eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
    }

    /** The sand point at the centre of {@code cell} in {@code tank}. */
    private static Vec3 sand(FishTankBlockEntity tank, CosmeticGridCell cell) {
        BlockPos pos = tank.getBlockPos();
        return new Vec3(pos.getX() + cell.localX(), pos.getY() + CosmeticGridCell.FLOOR_Y, pos.getZ() + cell.localZ());
    }

    /**
     * A full-height kelp strand in the front cell doesn't hide the sand behind it: aiming past it
     * targets the back cell instead of growing the strand. Aiming at the strand's own sand still
     * targets it, and a level aim that reaches no sand falls back to the strand's hit box.
     */
    public static void kelpDoesNotHideCellBehindIt(GameTestHelper helper) {
        FishTankBlockEntity tank = row(helper, 1, 1).get(0);
        CosmeticGridCell front = new CosmeticGridCell(1, G - 1);
        CosmeticGridCell back = new CosmeticGridCell(1, 0);
        int full = TankColumns.maxSegments(1);
        tank.setCosmetic(front, new PlacedCosmetic(Blocks.KELP.defaultBlockState(), full));
        ItemStack kelp = new ItemStack(Blocks.KELP);
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        BlockPos pos = tank.getBlockPos();
        // Standing south of the tank, eye level with its lid, looking north and down through the glass.
        Vec3 eye = new Vec3(pos.getX() + front.localX(), pos.getY() + 1.0, pos.getZ() + 1.6);

        // Precondition: the ray to the back cell really does pass through the strand's hit box.
        CosmeticTransforms.Transform t = CosmeticTransforms.get(Blocks.KELP);
        Vec3 strand = sand(tank, front).add(t.offsetX(), t.offsetY(), t.offsetZ());
        double half = t.scale() / 2.0;
        AABB box = new AABB(strand.x - half, strand.y, strand.z - half, strand.x + half, strand.y + t.scale() * full, strand.z + half);
        helper.assertTrue(box.clip(eye, sand(tank, back)).isPresent(), "test setup: the strand must stand in the line of sight");

        look(player, eye, sand(tank, back));
        CosmeticPlacement.Plan behind = CosmeticPlacement.plan(player, tank, kelp);
        helper.assertTrue(behind.valid() && behind.cells().equals(List.of(new CosmeticPlacement.TankCell(tank, back))),
                "aiming past the strand must target the empty back cell, got " + behind.cells());

        look(player, eye, sand(tank, front));
        CosmeticPlacement.Plan base = CosmeticPlacement.plan(player, tank, kelp);
        helper.assertTrue(base.cells().equals(List.of(new CosmeticPlacement.TankCell(tank, front))) && !base.valid(),
                "aiming at the full strand's base must target it and refuse to grow it, got " + base.cells());

        Vec3 low = new Vec3(eye.x, strand.y + t.scale() * full / 2.0, eye.z);
        look(player, low, low.add(0, 0, -1));
        CosmeticPlacement.Plan level = CosmeticPlacement.plan(player, tank, kelp);
        helper.assertTrue(level.cells().equals(List.of(new CosmeticPlacement.TankCell(tank, front))),
                "a level aim that reaches no sand must fall back to the strand's box, got " + level.cells());
        helper.succeed();
    }

    /**
     * Vanilla plants place as floor cosmetics: every flower, sapling and coral the vanilla tags
     * name is accepted; pink petals stack one per click to four, survive a reload with their count,
     * and come back out one petal at a time; a sweet berry bush is drawn grown with berries even
     * after a reload; a sunflower is stored as its lower half (the mesh draws the top).
     */
    public static void plantsPlaceAsFloorCosmetics(GameTestHelper helper) {
        FishTankBlockEntity tank = row(helper, 1, 1).get(0);
        for (Item item : new Item[]{Items.POPPY, Items.OAK_SAPLING, Items.TUBE_CORAL, Items.SUNFLOWER,
                Items.SHORT_GRASS, Items.SWEET_BERRIES, Items.PINK_PETALS, Items.BAMBOO, Items.RED_MUSHROOM}) {
            helper.assertTrue(CosmeticPlacement.cosmeticBlockOf(new ItemStack(item)) != null, item + " must be a floor cosmetic");
        }
        helper.assertTrue(HangingCosmetics.blockOf(new ItemStack(Items.PALE_HANGING_MOSS)) != null,
                "pale hanging moss must hang from the lid");

        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        CosmeticGridCell cell = new CosmeticGridCell(1, 1);
        BlockPos pos = tank.getBlockPos();
        Vec3 eye = new Vec3(pos.getX() + cell.localX(), pos.getY() + 0.9, pos.getZ() + cell.localZ());
        look(player, eye, sand(tank, cell));
        ItemStack petals = new ItemStack(Items.PINK_PETALS);
        for (int i = 0; i < 4; i++) {
            CosmeticPlacement.Plan plan = CosmeticPlacement.plan(player, tank, petals);
            helper.assertTrue(plan.valid(), "petal " + (i + 1) + " must place");
            plan.apply().run();
        }
        helper.assertTrue(!CosmeticPlacement.plan(player, tank, petals).valid(), "a fifth petal must be refused");
        reload(helper, tank);
        helper.assertTrue(tank.getCosmetics().get(cell).blockState().getValue(BlockStateProperties.FLOWER_AMOUNT) == 4,
                "four petals must survive a reload");
        ItemStack back = tank.removeCosmeticEntry(cell);
        helper.assertTrue(back.is(Items.PINK_PETALS) && tank.getCosmetics().get(cell).blockState().getValue(BlockStateProperties.FLOWER_AMOUNT) == 3,
                "removal must refund one petal and leave three");

        CosmeticGridCell bushCell = new CosmeticGridCell(0, 0);
        look(player, new Vec3(pos.getX() + bushCell.localX(), pos.getY() + 0.9, pos.getZ() + bushCell.localZ()), sand(tank, bushCell));
        CosmeticPlacement.plan(player, tank, new ItemStack(Items.SWEET_BERRIES)).apply().run();
        reload(helper, tank);
        helper.assertTrue(tank.getCosmetics().get(bushCell).blockState().getValue(BlockStateProperties.AGE_3) == 3,
                "a sweet berry bush must show berries, before and after a reload");
        helper.assertTrue(tank.removeCosmeticEntry(bushCell).is(Items.SWEET_BERRIES), "the bush must refund sweet berries");

        BlockState sunflower = Blocks.SUNFLOWER.defaultBlockState();
        helper.assertTrue(sunflower.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                        && FloorCosmetics.upperHalf(sunflower).getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER
                        && FloorCosmetics.upperHalf(Blocks.POPPY.defaultBlockState()) == null,
                "two-tall plants must have an upper half drawn above them, single plants none");
        helper.succeed();
    }
}
