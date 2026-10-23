package grill24.fishsim.ambient;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The Phase 2 discovery contract (docs/fish-in-world.md §discovery): budgets that hold every
 * tick, water geometry that survives fractional surfaces and waterlogged cells, patch keys that do
 * not move when the window does, disconnected pools that stay apart, merges when water joins them,
 * and eviction that retires what the window has left behind.
 */
class WaterDiscoveryTest {
    @Test
    void connectedWaterSeparatesContextBoundariesAndRevalidatesBiomeChanges() {
        SyntheticWater wet = pond(10, 14, 10, 14, 3);
        boolean[] boundary = {true};
        WaterSource source = new WaterSource() {
            public int flags(int x, int y, int z) { return wet.flags(x, y, z); }
            public float fluidTop(int x, int y, int z) { return wet.fluidTop(x, y, z); }
            public float collisionTop(int x, int y, int z) { return wet.collisionTop(x, y, z); }
            public Object contextKey(int x, int y, int z) { return boundary[0] && x >= 13 ? "ocean" : "river"; }
        };
        WaterDiscovery d = discovery(10, 48, 512);
        for (int t = 0; t < 100; t++) d.tick(source, 12, 65, 12, t);
        assertEquals(25, d.columnCount());
        assertTrue(d.patchKeyAt(11, 12) != d.patchKeyAt(14, 12));
        assertEquals(2, d.livePatches().size());
        long river = d.patchKeyAt(11, 12);
        boundary[0] = false;
        assertTrue(d.revalidate(source, 13, 12, 65, 101));
        assertEquals(river, d.patchKeyAt(13, 12));
        assertTrue(d.revalidate(source, 14, 12, 65, 102));
        assertEquals(river, d.patchKeyAt(14, 12));
    }

    private static final int SEA = 62;
    private static final int TOP = 64;

    private static WaterDiscovery discovery(int radius, int columnBudget, int cellBudget) {
        WaterDiscovery d = new WaterDiscovery();
        d.setRadius(radius);
        d.setEvictSlack(2);
        d.setVerticalWindow(6, 24);
        d.setBudgets(columnBudget, cellBudget);
        return d;
    }

    /** A square pond whose water cells are {@code TOP-depth+1 .. TOP}. */
    private static SyntheticWater pond(int x0, int x1, int z0, int z1, int depth) {
        SyntheticWater w = new SyntheticWater(-64, 96, -64, 96);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) w.water(x, z, TOP, depth);
        }
        return w;
    }

    private static int wetColumns(WaterDiscovery d) {
        int n = 0;
        for (WaterDiscovery.Column c : d.columns()) n++;
        return n;
    }

    /** A discovery with the runtime's own vertical band (8 above, 16 below the player's feet). */
    private static WaterDiscovery runtimeBanded(int radius) {
        WaterDiscovery d = new WaterDiscovery();
        d.setRadius(radius);
        d.setEvictSlack(2);
        d.setVerticalWindow(8, 16);
        d.setBudgets(48, 512);
        return d;
    }

    /** Every cached column's full water — spans and truncation flag — for an unchanged assert. */
    private static Map<Long, String> columnSnapshot(WaterDiscovery d) {
        Map<Long, String> out = new HashMap<>();
        for (WaterDiscovery.Column c : d.columns()) {
            out.put(SyntheticWater.columnKey(c.x(), c.z()), c.water().toString());
        }
        return out;
    }

    // ── Budgets ─────────────────────────────────────────────────────────────

    @Test
    void budgetsHoldOnEveryTickAndCoveringAPondTakesSeveralTicks() {
        SyntheticWater world = pond(10, 14, 10, 14, 3); // 25 columns
        WaterDiscovery d = discovery(20, 48, 200);      // a deliberately tight cell budget
        int ticks = 0;
        while (d.columnCount() < 25 && ticks < 200) {
            d.tick(world, 12, 65, 12, ticks++);
            WaterDiscovery.Counters c = d.counters();
            assertTrue(c.scansThisTick <= 48, "scans in one tick: " + c.scansThisTick);
            assertTrue(c.cellsThisTick <= 200,
                    "classified cells in one tick: " + c.cellsThisTick + " (budget 200)");
        }
        assertEquals(25, d.columnCount(), "pond columns discovered");
        // A pond column costs its water plus the gap that ends the scan (~14 cells here), so 25 of
        // them cannot fit in one 200-cell tick.
        assertTrue(ticks >= 2, "a 25-column pond should not be covered in one tick (took " + ticks + ")");
        assertTrue(d.counters().columnsScanned >= 25);
    }

    @Test
    void dryColumnsAreNotCachedButStillCostTheirBand() {
        SyntheticWater world = pond(10, 14, 10, 14, 3);
        WaterDiscovery d = discovery(20, 48, 512);
        for (int i = 0; i < 60; i++) d.tick(world, 12, 65, 12, i);
        assertEquals(25, d.columnCount(), "only the pond's columns are kept");
        long reads = world.totalReads();
        assertTrue(reads > 25 * 20, "the sweep must have classified the dry ring too: " + reads);
    }

    // ── Patch identity ──────────────────────────────────────────────────────

    @Test
    void pondIsOnePatchWithAStableKeyAndOrigin() {
        SyntheticWater world = pond(10, 14, 10, 14, 3);
        WaterDiscovery d = discovery(20, 48, 512);
        for (int i = 0; i < 40; i++) d.tick(world, 12, 65, 12, i);

        assertEquals(1, d.livePatches().size());
        WaterDiscovery.Patch p = d.livePatches().get(0);
        assertEquals(25, p.columns());
        // The seed is the first column of the pond the sweep reaches — the near pass starts at its
        // low corner — and it stays the seed for the patch's whole life.
        assertEquals(10, p.seedX());
        assertEquals(10, p.seedZ());
        assertEquals(SEA - 1, p.originY(), "origin sits one below the seed column's bed");
        long key = p.key();

        // Every discovered column carries that key.
        for (WaterDiscovery.Column c : d.columns()) {
            assertEquals(key, c.patchKey(), "column " + c.x() + "," + c.z());
        }

        // Frames never move: the key and origin are identical after many more ticks and a short
        // walk that leaves the pond in view.
        for (int i = 40; i < 120; i++) d.tick(world, 12 + (i % 5) - 2, 65, 12, i);
        WaterDiscovery.Patch again = d.livePatches().get(0);
        assertEquals(key, again.key(), "the patch key must not change as the window moves");
        assertEquals(p.originX(), again.originX(), "origin must not move");
        assertEquals(p.originY(), again.originY(), "origin must not move");
        assertEquals(p.originZ(), again.originZ(), "origin must not move");
    }

    @Test
    void aReturningKeyKeepsItsOriginSeedAndCreationTick() {
        SyntheticWater world = pond(10, 14, 10, 14, 3);
        WaterDiscovery d = discovery(6, 48, 512);
        for (int i = 0; i < 40; i++) d.tick(world, 12, 65, 12, i);
        WaterDiscovery.Patch first = d.livePatches().get(0);
        long key = first.key();

        // Walk 20 blocks west, a block a tick, then stand there long enough to evict the pond.
        for (int i = 0; i < 200; i++) d.tick(world, 12 - Math.min(20, i), 65, 12, 100 + i);
        assertEquals(0, d.livePatches().size(), "the pond is out of the window and must be retired");
        assertTrue(d.keyReserved(key), "the key stays reserved after its columns are gone");
        assertEquals(0, d.patch(key).columns(), "a reserved patch holds no columns");

        // Come back from the east — a different approach re-seeds the water from the other side,
        // which is what makes a fresh frame visible if one has been minted.
        for (int i = 0; i < 80; i++) d.tick(world, 20, 65, 12, 400 + i);
        assertTrue(d.columnCount(key) > 0, "the east edge finds the pond again");
        // And stand in the middle of it.
        for (int i = 0; i < 120 && d.columnCount() < 25; i++) d.tick(world, 12, 65, 12, 500 + i);
        assertEquals(25, d.columnCount(), "the whole pond is back");
        assertEquals(1, d.livePatches().size(), "and it is one patch, not a re-seeded half and a new one");

        WaterDiscovery.Patch again = d.livePatches().get(0);
        assertEquals(key, again.key(), "re-entry must not renumber the water");
        // The frame belongs to the water, not to the incarnation: the reserved key comes back with
        // the seed, origin and creation tick it was born with, so a population that stayed (its
        // members only ever fade) keeps its coordinate frame. Jumping the frame under a fading shoal
        // teleports it 16 blocks, which is what a fresh incarnation mints.
        assertEquals(first.seedX(), again.seedX(), "the seed cell must not move");
        assertEquals(first.seedZ(), again.seedZ(), "the seed cell must not move");
        assertEquals(first.originX(), again.originX(), "origin must not move");
        assertEquals(first.originY(), again.originY(), "origin must not move");
        assertEquals(first.originZ(), again.originZ(), "origin must not move");
        assertEquals(first.createdTick(), again.createdTick(), "the patch's birth tick must survive");
        assertNotNull(d.columnAt(again.seedX(), again.seedZ()), "the seed names a discovered column");
        assertEquals(key, d.patchKeyAt(again.seedX(), again.seedZ()), "and one of this patch");
    }

    // ── Revalidation bands (the player's own window) ────────────────────────

    @Test
    void aBandThatCannotSeeAColumnLeavesItAlone() {
        SyntheticWater world = pond(10, 14, 10, 14, 3);
        // Fly well above the pond: the band's floor is then above the water the cached columns hold.
        for (int py : new int[]{82, 40}) {
            WaterDiscovery d = runtimeBanded(20);
            // The implicit per-tick pass (covered by theDriverItselfNoticesAnEdit…) is off here:
            // this test drives revalidateSome itself and counts its passes exactly.
            d.setRevalidateColumnsPerTick(0);
            for (int i = 0; i < 60; i++) d.tick(world, 12, 65, 12, i);
            assertEquals(25, d.columnCount(), "precondition: the pond is cached");
            Map<Long, String> before = columnSnapshot(d);
            long key = d.patchKeyAt(12, 12);

            int changed = 0;
            for (int i = 0; i < 40; i++) {
                d.tick(world, 12, py, 12, 100 + i);
                changed += d.revalidateSome(world, py, 4096, 100 + i);
            }
            assertEquals(0, changed, "a band that cannot see the water must change nothing (py=" + py + ")");
            assertEquals(25 * 40, d.counters().revalidationsDeferred,
                    "every one of the 40 passes over the 25 columns was deferred, and counted");
            assertEquals(25, d.columnCount(), "no column may be deleted by a band that cannot see it");
            assertEquals(25, d.columnCount(key), "the patch keeps every column");
            assertEquals(1, d.livePatches().size(), "and stays live");
            assertEquals(before, columnSnapshot(d), "every span is exactly as it was found");
        }
    }

    @Test
    void aBandFloorInsideTheWaterMustNotInventATruncatedColumn() {
        SyntheticWater world = pond(10, 14, 10, 14, 3);
        WaterDiscovery d = runtimeBanded(20);
        for (int i = 0; i < 60; i++) d.tick(world, 12, 65, 12, i);
        WaterColumn found = d.columnAt(12, 12);
        assertNotNull(found, "precondition: the pond's columns are cached");
        assertFalse(found.truncated(), "precondition: the pond is shallow enough to see the bottom of");
        assertEquals(62f, found.lo(0), 1e-4);

        // High above the pond the scan band's floor lands inside the water: a revalidation there sees
        // a column that starts mid-water and is still wet at its floor. That is not the column's
        // water — it is the band's edge — and must not be stored: a truncated column's bed is unknown
        // and a domain built on it hands benthic fish a NaN floor.
        int changed = d.revalidateSome(world, 79, 4096, 100);
        assertEquals(0, changed, "the band's own edge is not a water edit");

        WaterColumn after = d.columnAt(12, 12);
        assertEquals(found.toString(), after.toString(), "the column is byte-identical after the pass");
        assertFalse(after.truncated(), "the band's floor must not truncate a column it cut in half");
        assertEquals(25, d.columnCount());
    }

    @Test
    void aDeferredColumnIsStillEditableOnceTheBandCoversIt() {
        SyntheticWater world = pond(10, 14, 10, 14, 3);
        WaterDiscovery d = runtimeBanded(20);
        // The implicit per-tick pass (covered by theDriverItselfNoticesAnEdit…) is off here: this
        // test pins when the explicitly driven pass applies the edit, tick by tick.
        d.setRevalidateColumnsPerTick(0);
        for (int i = 0; i < 60; i++) d.tick(world, 12, 65, 12, i);
        Map<Long, String> before = columnSnapshot(d);
        long key = d.patchKeyAt(12, 12);

        // Fly up: a covering band defers, it does not forget.
        for (int i = 0; i < 40; i++) {
            d.tick(world, 12, 82, 12, 100 + i);
            d.revalidateSome(world, 82, 4096, 100 + i);
        }
        assertEquals(before, columnSnapshot(d), "deferral changes nothing");
        assertEquals(25 * 40, d.counters().revalidationsDeferred, "and every deferral is counted");
        assertEquals(25, d.columnCount(key));

        // Drain the pond's middle column while the player is up there: the edit must not be applied
        // from a band that cannot see the column, but must be applied the moment one can.
        world.dry(12, 12);
        for (int i = 0; i < 20; i++) {
            d.tick(world, 12, 82, 12, 200 + i);
            d.revalidateSome(world, 82, 4096, 200 + i);
        }
        assertEquals(25, d.columnCount(), "an edit under a blind band is not applied");

        // Back down into the pond: the same edit is now visible and revalidation does its job.
        int changed = 0;
        for (int i = 0; i < 4 && changed == 0; i++) {
            d.tick(world, 12, 65, 12, 300 + i);
            changed += d.revalidateSome(world, 65, 4096, 300 + i);
        }
        assertEquals(1, changed, "the drained column is noticed as soon as the band covers it");
        assertEquals(24, d.columnCount());
        assertEquals(0L, d.patchKeyAt(12, 12), "a dry column has no patch key");

        // Refilled: the sweep re-discovers it as a member of the same patch.
        world.water(12, 12, TOP, 3);
        for (int i = 0; i < 400 && d.columnCount() < 25; i++) d.tick(world, 12, 65, 12, 400 + i);
        assertEquals(25, d.columnCount());
        assertEquals(key, d.patchKeyAt(12, 12));

        // A shrink is seen the same way — and it must not come back truncated.
        world.water(12, 12, TOP - 1, 2);
        assertEquals(1, d.revalidateSome(world, 65, 4096, 810), "the shrink is a change");
        WaterColumn shrunk = d.columnAt(12, 12);
        assertNotNull(shrunk);
        assertEquals(1, shrunk.spanCount());
        assertEquals(SEA, shrunk.lo(0), 1e-4);
        assertEquals(TOP - 1 + 8f / 9f, shrunk.hi(0), 1e-4);
        assertFalse(shrunk.truncated(), "the new surface is well inside the band");
    }

    @Test
    void aZeroRevalidationBudgetScansNothing() {
        SyntheticWater world = pond(10, 14, 10, 14, 3);
        WaterDiscovery d = discovery(20, 48, 512);
        for (int i = 0; i < 60; i++) d.tick(world, 12, 65, 12, i);
        d.counters().reset();
        long reads = world.totalReads();

        assertEquals(0, d.revalidateSome(world, 65, 0, 100), "no budget, no changes");
        assertEquals(0, d.counters().revalidatedThisTick, "no budget, no scans");
        assertEquals(0, d.counters().revalidationsDeferred, "no budget, no deferrals either");
        assertEquals(reads, world.totalReads(), "a zero budget must not touch the world");
        assertEquals(25, d.columnCount());
    }

    @Test
    void aTruncationFlagChangeIsAChange() {
        float[] spans = {62f, 64.888f};
        assertFalse(WaterColumn.of(spans, false).spansEqual(WaterColumn.of(spans, true)),
                "a column that now knows its bottom is not the same column");
        assertTrue(WaterColumn.of(spans, true).spansEqual(WaterColumn.of(spans.clone(), true)));

        // In the world: water a cell above the band's ceiling, then an edit that lowers the surface
        // out of that band. The revalidation runs from a band that can see the whole column.
        SyntheticWater world = new SyntheticWater(-64, 96, -64, 96);
        WaterDiscovery d = discovery(4, 48, 512);
        d.setVerticalWindow(8, 16);
        world.water(12, 12, TOP + 1, 4);
        for (int i = 0; i < 20; i++) d.tick(world, 12, 56, 12, i);
        WaterColumn stored = d.columnAt(12, 12);
        assertNotNull(stored, "precondition: the column is cached");
        assertTrue(stored.truncated(), "the band's ceiling cut the water, so the top is unknown");

        world.water(12, 12, TOP, 3);
        assertTrue(d.revalidate(world, 12, 12, 60, 100), "the edit is a change");
        WaterColumn after = d.columnAt(12, 12);
        assertFalse(after.truncated(), "with the top visible again the column is whole");
        assertEquals(TOP + 8f / 9f, after.hi(after.spanCount() - 1), 1e-4);
    }

    @Test
    void twoPoolsAreTwoPatchesAndNeitherCrosses() {
        SyntheticWater world = pond(0, 4, 0, 4, 3).water(0, 0, TOP, 3); // second pool
        for (int x = 8; x <= 12; x++) {
            for (int z = 0; z <= 4; z++) world.water(x, z, TOP, 3);
        }
        WaterDiscovery d = discovery(20, 48, 512);
        for (int i = 0; i < 120; i++) d.tick(world, 2, 65, 2, i);

        assertEquals(2, d.livePatches().size(), "dry land between the pools keeps them separate");
        Map<Long, Integer> counts = new HashMap<>();
        for (WaterDiscovery.Column c : d.columns()) {
            counts.merge(c.patchKey(), 1, Integer::sum);
        }
        assertEquals(2, counts.size());
        for (int n : counts.values()) assertEquals(25, n, "each pool has all its columns, whole");

        // Columns of one pool share a key; keys differ across the gap.
        long a = d.patchKeyAt(2, 2);
        long b = d.patchKeyAt(10, 2);
        assertEquals(d.patchKeyAt(4, 4), a);
        assertEquals(d.patchKeyAt(12, 4), b);
        assertTrue(a != b);
    }

    @Test
    void waterThatJoinsTwoPatchesMergesThemUnderOneKey() {
        SyntheticWater world = pond(0, 4, 0, 4, 3);
        for (int x = 8; x <= 12; x++) {
            for (int z = 0; z <= 4; z++) world.water(x, z, TOP, 3);
        }
        WaterDiscovery d = discovery(20, 48, 512);
        for (int i = 0; i < 120; i++) d.tick(world, 2, 65, 2, i);
        assertEquals(2, d.livePatches().size());
        long kept = d.patchKeyAt(2, 2);

        // The gap fills in: the next sweep round must connect them.
        for (int x = 5; x <= 7; x++) {
            for (int z = 0; z <= 4; z++) world.water(x, z, TOP, 3);
        }
        int expect = 2 * 25 + 3 * 5;
        for (int i = 120; i < 900 && (d.livePatches().size() > 1 || d.columnCount() < expect); i++) {
            d.tick(world, 2, 65, 2, i);
        }

        assertEquals(1, d.livePatches().size(), "connected water is one patch");
        assertEquals(kept, d.livePatches().get(0).key(), "the established key survives the merge");
        assertEquals(expect, d.livePatches().get(0).columns(), "both pools and the land between, joined");
        assertTrue(d.counters().patchMerges >= 1);
    }

    @Test
    void westwardExpansionKeepsTheEstablishedBodyKeyAcrossQuantumBoundaries() {
        // A newer negative key must not replace established water merely because its packed
        // coordinates sort first. This formerly retired a whole nearby population on a merge.
        SyntheticWater world = new SyntheticWater(-64, 96, -64, 96);
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 3; z++) world.water(x, z, TOP, 3); // body, seeded in quantum (0, 0)
        }
        WaterDiscovery d = discovery(20, 48, 512);
        for (int i = 0; i < 60; i++) d.tick(world, 1, 65, 1, i);
        long body = d.patchKeyAt(2, 2);
        assertTrue(body > 0, "a quantum (0, 0) key is positive: " + body);
        assertEquals(0, WaterDiscovery.keyQx(body), "the body's seed sits in quantum x = 0");

        // A pocket four dry columns west, across the x = 0 boundary: its own patch, seeded at
        // x = -4, so its key packs quantum x = -1 and is negative. (A dry-land sweep pass is long —
        // half the cell budget goes to the near square every tick — so give the rings two rounds.)
        for (int z = 1; z <= 3; z++) world.water(-4, z, TOP, 3);
        int t = 60;
        for (; t < 500 && d.livePatches().size() < 2; t++) d.tick(world, 1, 65, 1, t);
        assertEquals(2, d.livePatches().size(), "dry land in between keeps the pocket its own patch");
        long pocket = d.patchKeyAt(-4, 2);
        assertTrue(pocket < 0, "a quantum (-1, 0) key is negative: " + pocket);
        assertEquals(-1, WaterDiscovery.keyQx(pocket), "the pocket's seed sits in quantum x = -1");

        // The gap fills in: the older body's key and fixed coordinate frame survive.
        for (int x = -3; x <= 0; x++) {
            for (int z = 1; z <= 3; z++) world.water(x, z, TOP, 3);
        }
        for (; t < 1400 && d.livePatches().size() > 1; t++) d.tick(world, 1, 65, 1, t);
        assertEquals(1, d.livePatches().size(), "connected water is one patch");
        assertEquals(body, d.livePatches().get(0).key(), "the oldest body survives the merge");
        assertEquals(body, d.patchKeyAt(2, 2), "the body's columns keep their key");
        assertEquals(body, d.patchKeyAt(-4, 2), "the new pocket joins the established body");
        assertNull(d.patch(pocket), "the newer pocket's key was merged");
        assertTrue(d.counters().patchMerges >= 1);
    }

    // ── Eviction and window movement ────────────────────────────────────────

    @Test
    void aWalledRiverKeepsOneKeyWhileThePlayerWalksIt() {
        SyntheticWater world = new SyntheticWater(-64, 96, -64, 96);
        for (int x = 0; x <= 40; x++) world.water(x, 0, TOP, 3);
        WaterDiscovery d = discovery(6, 48, 512);
        d.tick(world, 4, 65, 0, 0);
        for (int i = 0; i < 30; i++) d.tick(world, 4, 65, 0, 1 + i);
        long key = d.livePatches().get(0).key();
        WaterDiscovery.Patch first = d.livePatches().get(0);

        for (int step = 1; step <= 34; step++) {
            for (int i = 0; i < 3; i++) d.tick(world, 4 + step, 65, 0, 100 + step * 3 + i);
            assertEquals(key, d.patchKeyAt(4 + step, 0),
                    "the water under the walker keeps its number at step " + step);
        }
        // A moving player can leave a column-sized hole in the ring cursor's wake — the sweep is a
        // distance, not a visited set, so a cell of the ring it has already passed at the old position
        // goes unscanned at the new one. The hole closes on the next sweep round, which is why the
        // contract is the key under the player, not "exactly one patch at every step".
        for (int i = 0; i < 60; i++) d.tick(world, 38, 65, 0, 300 + i);
        assertEquals(1, d.livePatches().size(), "the river the walker stands in is one patch once the rings wrap");

        assertTrue(d.counters().columnsEvicted > 0, "columns behind the walker are dropped");
        assertEquals(first.originY(), d.livePatches().get(0).originY());
        assertEquals(first.seedX(), d.livePatches().get(0).seedX(), "the seed, and the frame, stay put");
    }

    @Test
    void columnsBeyondTheWindowAreNeverDiscovered() {
        SyntheticWater world = new SyntheticWater(-64, 96, -64, 96);
        for (int x = 0; x <= 40; x++) world.water(x, 0, TOP, 3);
        WaterDiscovery d = discovery(6, 48, 512);
        for (int i = 0; i < 60; i++) d.tick(world, 20, 65, 0, i);
        for (WaterDiscovery.Column c : d.columns()) {
            assertTrue(Math.abs(c.x() - 20) <= 6, "discovered " + c.x() + ", outside the 6-block window");
        }
    }

    // ── Loaded-chunk discipline ─────────────────────────────────────────────

    @Test
    void unloadedColumnsAreNotWaterAndDoNotBlockTheSweep() {
        SyntheticWater world = pond(10, 14, 10, 14, 3);
        world.unload(12, 12); // the player's own column, mid-pond
        WaterDiscovery d = discovery(20, 48, 512);
        for (int i = 0; i < 60; i++) d.tick(world, 12, 65, 12, i);

        assertTrue(d.counters().unloadedScans > 0, "the unloaded column is reported as unloaded");
        assertEquals(24, d.columnCount(), "every other pond column is still discovered");
        assertEquals(1, d.livePatches().size());

        // Load it and the sweep picks it up on its next pass.
        world.load(12, 12);
        for (int i = 60; i < 400 && d.columnCount() < 25; i++) d.tick(world, 12, 65, 12, i);
        assertEquals(25, d.columnCount(), "a newly loaded chunk joins the patch");
        assertEquals(d.patchKeyAt(11, 12), d.patchKeyAt(12, 12));
    }

    // ── Scan assembly ───────────────────────────────────────────────────────

    @Test
    void fractionalSurfacesAndWaterloggedObstaclesBecomeSpans() {
        SyntheticWater world = new SyntheticWater(-8, 8, -8, 8);
        WaterDiscovery d = discovery(4, 48, 512);

        // A still source cell: surface 8/9 of a block.
        WaterColumn source = d.scanColumn(world.water(0, 0, TOP, 1), 0, 0, 70, 60);
        assertNotNull(source);
        assertEquals(1, source.spanCount());
        assertEquals(TOP, source.lo(0), 1e-4);
        assertEquals(TOP + 8f / 9f, source.hi(0), 1e-4);

        // A flowing cell: surface 4/9, and the water body continues below it.
        world.water(1, 0, TOP, 1).cellFluid(1, TOP, 0, 4f / 9f);
        WaterColumn flowing = d.scanColumn(world, 1, 0, 70, 60);
        assertEquals(1, flowing.spanCount());
        assertEquals(TOP + 4f / 9f, flowing.hi(0), 1e-4);

        // A waterlogged bottom slab: the water stands on the slab, 0.5 above the cell's floor.
        world.water(2, 0, TOP, 1).cellCollision(2, TOP, 0, 0.5f);
        WaterColumn slab = d.scanColumn(world, 2, 0, 70, 60);
        assertEquals(1, slab.spanCount());
        assertEquals(TOP + 0.5f, slab.lo(0), 1e-4);
        assertEquals(TOP + 8f / 9f, slab.hi(0), 1e-4);

        // A pond with a cave pool three cells under it: two spans, and the gap between them kept.
        SyntheticWater stacked = new SyntheticWater(-8, 8, -8, 8);
        stacked.water(0, 0, TOP, 1).body(0, 0, TOP - 3, 1);
        WaterColumn two = d.scanColumn(stacked, 0, 0, 70, 60);
        assertEquals(2, two.spanCount(), "a cave pool under a pond is a second span");
        assertEquals(TOP - 3, two.lo(0), 1e-4);
        assertEquals(TOP - 3 + 8f / 9f, two.hi(0), 1e-4);
        assertEquals(TOP, two.lo(1), 1e-4);
        assertEquals(TOP + 8f / 9f, two.hi(1), 1e-4);
        assertFalse(two.containsY(TOP - 1.5f), "the air between the two bodies is not water");
    }

    @Test
    void deepWaterTruncatesAtTheScanBandInsteadOfPretendingToKnowTheBottom() {
        SyntheticWater world = new SyntheticWater(-8, 8, -8, 8);
        WaterDiscovery d = discovery(4, 48, 512);
        d.setVerticalWindow(4, 8);
        world.water(0, 0, TOP, 40);
        WaterColumn col = d.scanColumn(world, 0, 0, 66, 58);
        assertNotNull(col);
        assertTrue(col.truncated(), "a column still in water at the band's floor is truncated");
        assertTrue(col.touchesBandBottom(58), "the cut is at the band's floor, and the query says so");
        assertEquals(58f, col.lo(0), 1e-4);

        // Shallow water that ends inside the band is not truncated.
        world.water(1, 0, TOP, 2);
        WaterColumn shallow = d.scanColumn(world, 1, 0, 66, 58);
        assertFalse(shallow.truncated());
        assertFalse(shallow.touchesBandBottom(58), "the bed is inside the band, not at its floor");
    }

    @Test
    void waterAboveTheBandTopIsTruncatedToo() {
        SyntheticWater world = new SyntheticWater(-8, 8, -8, 8);
        WaterDiscovery d = discovery(4, 48, 512);
        world.water(0, 0, 70, 6);
        WaterColumn col = d.scanColumn(world, 0, 0, 68, 60);
        assertTrue(col.truncated(), "water at the top of the band continues above it");
        assertTrue(col.touchesBandTop(68), "the water fills the band's top cell");
    }

    @Test
    void waterThatEndsExactlyAtTheBandTopIsNotTruncated() {
        SyntheticWater world = new SyntheticWater(-8, 8, -8, 8);
        WaterDiscovery d = discovery(4, 48, 512);
        // The pond's surface cell is the band's first cell — six blocks under a player standing in the
        // shallows — and the air above it ends the water body. A scan band whose ceiling is wet is not
        // the same thing as water that continues upward: the column is whole, bottom and top both.
        world.water(0, 0, TOP, 3);
        WaterColumn col = d.scanColumn(world, 0, 0, TOP, 44);
        assertNotNull(col);
        assertFalse(col.truncated(), "water that ends inside the band's top cell is not truncated");
        // The two halves of an open top face: the water does fill the band's top cell, but the flag
        // — which the probe above the band decides — says it does not continue. Only both together
        // mark a face open, so this column is a surface, not a cut.
        assertTrue(col.touchesBandTop(TOP), "the surface reaches the band's top cell");
        assertEquals(SEA, col.lo(0), 1e-4);
        assertEquals(TOP + 8f / 9f, col.hi(0), 1e-4);
    }

    @Test
    void aFlowingTopCellWithWaterAboveItIsStillTruncated() {
        SyntheticWater world = new SyntheticWater(-8, 8, -8, 8);
        WaterDiscovery d = discovery(4, 48, 512);
        // A flowing top cell 2/3 empty, with the water body continuing above the band: the surface is
        // only 0.35 above the band's ceiling, so a "surface above the ceiling by half a block" test
        // would miss it. The water is genuinely cut, and the flag says so.
        world.water(0, 0, 70, 6).cellFluid(0, 68, 0, 0.35f);
        WaterColumn col = d.scanColumn(world, 0, 0, 68, 60);
        assertTrue(col.truncated(), "a flowing cell at the band's ceiling is cut off as much as a full one");
        assertTrue(col.touchesBandTop(68), "and lands in the band's top cell");
        assertEquals(68.35f, col.surfaceY(), 1e-4);
    }

    // ── Trim (the hard column cap) ──────────────────────────────────────────

    @Test
    void aTrimEvictionStampsTheRealTickAndKeepsTheKeyReserved() {
        // Sixteen columns at the player and sixteen more twenty blocks off, under a cap that fits them
        // all: nothing is trimmed while they are discovered. Lowering the cap then forces a trim that
        // has to drop the far patch whole. A trim that evicts with tick 0 looks like "the epoch" to
        // the key expiry, so the key is un-reserved on the next evict — water that comes back
        // afterwards is renumbered, and a shoal that stayed has no key to come home to.
        //
        // The cell budget is raised off the runtime's own 512: at that budget the near pass still
        // spends most of the tick on its own square and the sweep only reaches the pool at ring 15
        // after this test's window has closed (measured: still un-found at tick 1059). Sweep speed
        // has its own test; this one needs both pools cached.
        SyntheticWater world = new SyntheticWater(-64, 96, -64, 96);
        for (int x = 0; x <= 3; x++) {
            for (int z = 0; z <= 3; z++) world.water(x, z, TOP, 3);
        }
        for (int x = 14; x <= 17; x++) {
            for (int z = 14; z <= 17; z++) world.water(x, z, TOP, 3);
        }
        WaterDiscovery d = runtimeBanded(24);
        d.setBudgets(48, 4096);
        d.setMaxColumns(32);
        for (int i = 0; i < 60; i++) d.tick(world, 1, 65, 1, 1000 + i);
        assertEquals(32, d.columnCount(), "both pools are discovered under the wide cap");
        assertEquals(2, d.livePatches().size());
        long farKey = d.patchKeyAt(15, 15);
        assertTrue(farKey != 0, "the far patch has a key");
        assertEquals(16, d.columnCount(farKey));

        // Make one near column unknown again and give the near pass time to store it back — its
        // square is rescanned every tick, so the refill is found again, and the trim has to run
        // AFTER that scan pass, not on the scan path it used to live on.
        world.dry(0, 0);
        assertEquals(1, d.revalidateSome(world, 65, 4096, 1059), "the drained column leaves the cache");
        world.water(0, 0, TOP, 3);
        for (int i = 0; i < 8; i++) d.tick(world, 1, 65, 1, 1060 + i);
        assertEquals(32, d.columnCount(), "the refilled column is stored again");

        long trimTick = 1068;
        d.setMaxColumns(16);
        d.tick(world, 1, 65, 1, trimTick);
        assertEquals(0, d.patch(farKey).columns(), "the far patch is trimmed away whole");
        assertEquals(trimTick, d.patch(farKey).lastSeenTick(),
                "a trim eviction must stamp the tick it happened on, not zero");
        assertTrue(d.columnCount() <= d.maxColumns(), "the cap holds at the end of a tick");

        // And the key survives the evicts that follow like any reserved key: the trim took its
        // columns, not its identity.
        for (int i = 1; i <= 12; i++) d.tick(world, 1, 65, 1, trimTick + i);
        assertTrue(d.keyReserved(farKey), "the trimmed key stays reserved");
    }

    @Test
    void revalidationNoticesEditsAndRemovals() {
        SyntheticWater world = pond(10, 14, 10, 14, 3);
        WaterDiscovery d = discovery(20, 48, 512);
        for (int i = 0; i < 60; i++) d.tick(world, 12, 65, 12, i);
        long key = d.patchKeyAt(12, 12);

        // Drained next to the player: the column loses its water.
        world.dry(12, 12);
        int changed = d.revalidateSome(world, 65, 4096, 100);
        assertEquals(1, changed, "the drained column is the only change");
        assertEquals(24, d.columnCount(), "one fewer column");
        assertEquals(24, d.columnCount(key), "the patch lost the column");
        assertEquals(24, d.livePatches().get(0).columns());
        assertEquals(0L, d.patchKeyAt(12, 12), "a dry column has no patch key");

        // Refilled: it comes back under the same patch.
        world.water(12, 12, TOP, 3);
        for (int i = 0; i < 400 && d.columnCount() < 25; i++) d.tick(world, 12, 65, 12, 110 + i);
        assertEquals(25, d.columnCount());
        assertEquals(key, d.patchKeyAt(12, 12));
    }

    @Test
    void theDriverItselfNoticesAnEditWhileDryLandKeepsTheSweepBusy() {
        // The ambient runtime's regime in miniature: a pond in flat dry land with the player on the
        // shore, so both the near pass and the sweep always have dry cells to classify and the tick's
        // cell budget is spent down to the last full column. The runtime used to revalidate known
        // columns from the leftover of that budget (spare / band), and in exactly this regime the
        // leftover is always below one column's band — the allowance was zero on every tick, known
        // columns were never re-read, and a block broken in cached water was invisible forever.
        // The driver is the only party that sees every tick, so tick() itself has to carry the pass.
        SyntheticWater world = pond(10, 14, 10, 14, 3);
        WaterDiscovery d = runtimeBanded(24);
        for (int i = 0; i < 120; i++) d.tick(world, 7, 65, 7, i);
        assertEquals(25, d.columnCount(), "precondition: the pond is cached");

        // The edit: drain the column in the middle of the pond.
        world.dry(12, 12);
        int noticed = -1;
        int zeroAllowanceTicks = 0; // diagnostic — the arithmetic the runtime used to drive the pass
        long scansAtEdit = d.counters().columnsScanned;
        for (int i = 120; i < 320; i++) {
            d.tick(world, 7, 65, 7, i);
            int spare = d.cellBudget() - d.counters().cellsThisTick;
            if (spare / d.scanBandCells() == 0) zeroAllowanceTicks++;
            assertTrue(d.counters().cellsThisTick <= d.cellBudget(),
                    "the tick's classifications must stay within the budget: " + d.counters().cellsThisTick);
            if (d.patchKeyAt(12, 12) == 0L) {
                noticed = i - 120;
                break;
            }
        }
        assertTrue(noticed >= 0 && noticed <= 200,
                "the driver never re-read the drained column in 200 ticks; the old spare/band"
                        + " allowance was zero on " + zeroAllowanceTicks + " of them");
        assertEquals(24, d.columnCount(), "the drained column left the cache");
        assertTrue(d.counters().columnsScanned > scansAtEdit,
                "the sweep must keep moving: revalidation may not eat its whole budget");
    }

    @Test
    void waterOutsideTheNearSquareIsFoundByTheSweep() {
        // Dry columns are not remembered, so the near pass rescans the same cells every tick: if it
        // could spend the whole cell budget it would spend every tick on the 5x5 around the player and
        // the sweep — the only thing that expands discovery — would never advance, leaving water a few
        // blocks away permanently unknown. The pond here sits just outside the square, and its columns
        // are only reachable by the sweep's rings, so a sweep that starves or that drops the cell its
        // budget ran out on (the cursor used to advance before the budget check) both fail this.
        SyntheticWater world = pond(5, 9, 0, 4, 3);
        WaterDiscovery d = discovery(24, 48, 512); // the runtime's own budgets
        for (int i = 0; i < 120; i++) d.tick(world, 0, 65, 0, i);
        assertEquals(1, d.livePatches().size(), "the pond off to one side is a patch");
        assertEquals(25, d.columnCount(), "and every one of its columns was found by the sweep");
    }

    @Test
    void aSaturatedWindowOfStillWaterKeepsOneSignatureTickAfterTick() {
        // The dedicated-server scene in miniature: water everywhere, the player standing still in
        // it, so the only moving parts are the driver's own (revalidation rotation, sweep rings,
        // eviction). The ambient runtime decides "the water moved, re-request context" by folding
        // the patch's sorted columns and spans into one long (AmbientFishRuntime.geometrySignature),
        // and in the fix-round dedicated run that signature moved about every 40-50 ticks while the
        // server's fingerprint of the same water never changed once — a request/drop loop on a
        // motionless world. This probe runs the driver at the runtime's own budgets over a
        // saturated all-water window and requires the same fold to be a constant of the world.
        SyntheticWater world = new SyntheticWater(-64, 96, -64, 96);
        for (int x = -48; x <= 48; x++) {
            for (int z = -48; z <= 48; z++) world.water(x, z, TOP, 3);
        }
        WaterDiscovery d = runtimeBanded(24);
        for (int i = 0; i < 300; i++) d.tick(world, 0, 65, 0, i);
        assertEquals(1, d.livePatches().size(), "precondition: one body of water, one patch");
        long key = d.patchKeyAt(0, 0);
        assertTrue(key != 0L, "precondition: a patch key");
        long baseline = patchSignature(d, key);
        Map<Long, String> before = columnSnapshot(d);
        for (int i = 300; i < 1500; i++) {
            d.tick(world, 0, 65, 0, i);
            long now = patchSignature(d, key);
            if (now != baseline) {
                Map<Long, String> after = columnSnapshot(d);
                StringBuilder moved = new StringBuilder();
                for (Map.Entry<Long, String> e : after.entrySet()) {
                    String old = before.get(e.getKey());
                    if (!e.getValue().equals(old)) {
                        moved.append("\n  column ").append(e.getKey()).append(": ").append(old)
                                .append(" -> ").append(e.getValue());
                    }
                }
                for (Map.Entry<Long, String> e : before.entrySet()) {
                    if (!after.containsKey(e.getKey())) {
                        moved.append("\n  column ").append(e.getKey()).append(": ").append(e.getValue())
                                .append(" -> gone");
                    }
                }
                fail("the signature moved at tick " + i + " while the water stood still"
                        + " (revalidatedThisTick=" + d.counters().revalidatedThisTick
                        + ", columnsChanged=" + d.counters().columnsChanged
                        + ", columnsEvicted=" + d.counters().columnsEvicted + "):" + moved);
            }
        }
    }

    /** The runtime's geometrySignature fold over one patch's columns (AmbientFishRuntime). */
    private static long patchSignature(WaterDiscovery d, long patchKey) {
        List<WaterDiscovery.Column> columns = new ArrayList<>(d.columnsOf(patchKey));
        columns.sort(Comparator.comparingInt(WaterDiscovery.Column::x)
                .thenComparingInt(WaterDiscovery.Column::z));
        long hash = 0xcbf29ce484222325L;
        for (WaterDiscovery.Column c : columns) {
            hash = (hash ^ c.x()) * 0x100000001b3L;
            hash = (hash ^ c.z()) * 0x100000001b3L;
            WaterColumn water = c.water();
            hash = (hash ^ (water.truncated() ? 1 : 0)) * 0x100000001b3L;
            for (int i = 0; i < water.spanCount(); i++) {
                hash = (hash ^ Float.floatToIntBits(water.lo(i))) * 0x100000001b3L;
                hash = (hash ^ Float.floatToIntBits(water.hi(i))) * 0x100000001b3L;
            }
        }
        return hash;
    }

    // ── Per-patch index (pre-backport cleanup step 4) ───────────────────────

    @Test
    void nearestWaterDistancesFollowThePlayerDrainingAndSessionReset() {
        SyntheticWater world = pond(10, 14, 10, 14, 3);
        WaterDiscovery d = discovery(10, 48, 512);
        for (int t = 0; t < 200; t++) d.tick(world, 12, 65, 12, t);
        long key = d.patchKeyAt(10, 10);
        assertEquals(Map.of(key, 0), d.nearestSquaredDistances(10, 10));
        assertEquals(Map.of(key, 9), d.nearestSquaredDistances(7, 10),
                "moving the observer changes the nearest distance without changing the water");
        assertEquals(Map.of(key, 0), d.nearestSquaredDistances(10, 10));

        world.water(10, 10, TOP, 0);
        assertTrue(d.revalidate(world, 10, 10, 65, 201));
        assertEquals(Map.of(key, 1), d.nearestSquaredDistances(10, 10),
                "a cached nearest column cannot survive removal");

        d.reset();
        assertEquals(Map.of(), d.nearestSquaredDistances(10, 10),
                "a new session must not reuse the old distance map");
    }

    @Test
    void thePatchIndexAgreesWithAFullScanThroughMergesDrainsContextFlipsAndEviction() {
        // Two ponds that a channel later joins (a merge), a column that drains and refills, a
        // context boundary that flips, a player walk that evicts one pond, and a cap low enough to
        // trim: after every tick the indexed queries must say exactly what a scan of every column says.
        SyntheticWater wet = pond(0, 4, 0, 4, 3);
        for (int x = 8; x <= 12; x++) for (int z = 0; z <= 4; z++) wet.water(x, z, TOP, 3);
        boolean[] boundary = {false};
        WaterSource source = new WaterSource() {
            public int flags(int x, int y, int z) { return wet.flags(x, y, z); }
            public float fluidTop(int x, int y, int z) { return wet.fluidTop(x, y, z); }
            public float collisionTop(int x, int y, int z) { return wet.collisionTop(x, y, z); }
            public Object contextKey(int x, int y, int z) { return boundary[0] && z >= 3 ? "river" : "ocean"; }
        };
        WaterDiscovery d = discovery(12, 48, 512);
        d.setMaxColumns(60);
        int px = 6;
        for (int t = 0; t < 600; t++) {
            if (t == 120) for (int x = 5; x <= 7; x++) wet.water(x, 2, TOP, 3); // the channel: a merge
            if (t == 200) wet.dry(2, 2);
            if (t == 260) wet.water(2, 2, TOP, 3);
            if (t == 300) boundary[0] = true;
            if (t == 380) boundary[0] = false;
            if (t >= 420 && t % 10 == 0 && px < 40) px += 2; // walk away: eviction
            d.tick(source, px, 65, 2, t);
            assertIndexMatchesScan(d, px, 2, "tick " + t);
        }
        WaterDiscovery.Counters c = d.counters();
        assertTrue(c.patchMerges > 0, "the scenario must merge patches");
        assertTrue(c.columnsChanged > 0, "the scenario must re-read changed columns");
        assertTrue(c.columnsEvicted > 0, "the scenario must evict or trim columns");
    }

    @Test
    void aPatchVersionHoldsWhileItsWaterStandsStillAndMovesWhenAColumnChanges() {
        SyntheticWater world = pond(10, 14, 10, 14, 3);
        WaterDiscovery d = discovery(10, 48, 512);
        for (int t = 0; t < 200; t++) d.tick(world, 12, 65, 12, t);
        long key = d.patchKeyAt(12, 12);
        long version = d.patchVersion(key);
        assertTrue(version != 0L, "a patch with columns has a version");
        for (int t = 200; t < 1200; t++) {
            d.tick(world, 12, 65, 12, t);
            assertEquals(version, d.patchVersion(key), "still water must keep its version (tick " + t + ")");
        }
        world.cellFluid(12, TOP, 12, 0.5f); // a lowered surface in one column
        boolean moved = false;
        for (int t = 1200; t < 1400 && !moved; t++) {
            d.tick(world, 12, 65, 12, t);
            moved = d.patchVersion(key) != version;
        }
        assertTrue(moved, "a re-read column with different water moves the version");
        assertEquals(0L, d.patchVersion(12345L), "an unknown patch has version zero");
    }

    private static void assertIndexMatchesScan(WaterDiscovery d, int x, int z, String when) {
        Map<Long, List<String>> scan = new HashMap<>();
        Map<Long, Integer> nearest = new HashMap<>();
        for (WaterDiscovery.Column c : d.columns()) {
            scan.computeIfAbsent(c.patchKey(), k -> new ArrayList<>()).add(describe(c));
            int dx = c.x() - x, dz = c.z() - z;
            nearest.merge(c.patchKey(), dx * dx + dz * dz, Math::min);
        }
        assertEquals(nearest, d.nearestSquaredDistances(x, z), when + ": nearest distances");
        for (Map.Entry<Long, List<String>> e : scan.entrySet()) {
            List<String> indexed = new ArrayList<>();
            for (WaterDiscovery.Column c : d.patchColumns(e.getKey())) indexed.add(describe(c));
            List<String> scanned = new ArrayList<>(e.getValue());
            indexed.sort(null);
            scanned.sort(null);
            assertEquals(scanned, indexed, when + ": columns of patch " + e.getKey());
            assertTrue(d.patchVersion(e.getKey()) != 0L, when + ": a patch with columns has a version");
        }
        for (WaterDiscovery.Patch p : d.livePatches()) {
            assertEquals(p.columns(), d.patchColumns(p.key()).size(), when + ": count of patch " + p.key());
        }
    }

    private static String describe(WaterDiscovery.Column c) {
        return c.x() + "," + c.z() + " " + c.patchKey() + " " + c.contextSignature() + " " + c.water();
    }

    @Test
    void keysRoundTripThroughTheirPacking() {
        long k = WaterDiscovery.packKey(-37, 91, 5);
        assertEquals(-37, WaterDiscovery.keyQx(k));
        assertEquals(91, WaterDiscovery.keyQz(k));
        assertEquals(5, WaterDiscovery.keyInstance(k));
        long l = WaterDiscovery.packKey(1024, -2048, 0);
        assertEquals(1024, WaterDiscovery.keyQx(l));
        assertEquals(-2048, WaterDiscovery.keyQz(l));
        assertEquals(0, WaterDiscovery.keyInstance(l));
        assertTrue(k != l);
    }
}
