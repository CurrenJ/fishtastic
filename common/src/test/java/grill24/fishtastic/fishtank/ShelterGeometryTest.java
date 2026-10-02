package grill24.fishtastic.fishtank;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The derived half of a shelter (docs/fish-shelters.md §3.1). Everything an author can get wrong
 * by marking the wrong cells is decided here, so each rule has a case: a sealed hollow, a pocket
 * that touches the hollow but not the outside, a tube with two ends, and the real Hollow Log.
 */
class ShelterGeometryTest {

    private static ShelterGeometry.Cell c(int x, int y, int z) {
        return new ShelterGeometry.Cell(x, y, z);
    }

    /** Solid cells of the box {@code min..max}, minus {@code hollow} and anything in {@code open}. */
    private static Set<ShelterGeometry.Cell> shell(int x0, int y0, int z0, int x1, int y1, int z1,
                                                   Set<ShelterGeometry.Cell> hollow, Set<ShelterGeometry.Cell> open) {
        Set<ShelterGeometry.Cell> out = new HashSet<>();
        for (int x = x0; x <= x1; x++)
            for (int y = y0; y <= y1; y++)
                for (int z = z0; z <= z1; z++) {
                    ShelterGeometry.Cell cell = c(x, y, z);
                    if (!hollow.contains(cell) && !open.contains(cell)) out.add(cell);
                }
        return out;
    }

    /** A hollow with no way out is an authoring error, and the message says so. */
    @Test
    void aSealedHollowIsRejected() {
        Set<ShelterGeometry.Cell> hollow = Set.of(c(1, 1, 1));
        ShelterGeometry.Result result = ShelterGeometry.derive(hollow, shell(0, 0, 0, 2, 2, 2, hollow, Set.of()));
        assertFalse(result.ok());
        assertTrue(result.error().contains("sealed"), result.error());
    }

    /** One open end: one mouth, on that end, pointing out of it. */
    @Test
    void anOpenEndIsOneMouth() {
        // A 3-long tube along x, closed at x=0, open at x=4.
        Set<ShelterGeometry.Cell> hollow = Set.of(c(1, 1, 1), c(2, 1, 1), c(3, 1, 1));
        Set<ShelterGeometry.Cell> parts = shell(0, 0, 0, 4, 2, 2, hollow, Set.of(c(4, 1, 1)));
        ShelterGeometry.Result result = ShelterGeometry.derive(hollow, parts);
        assertTrue(result.ok(), result.error());

        List<ShelterGeometry.Mouth> mouths = result.shape().mouths();
        assertEquals(1, mouths.size());
        assertArrayEquals(new int[]{1, 0, 0}, mouths.get(0).outward());
        assertEquals(c(3, 1, 1), mouths.get(0).min());
        assertEquals(3, result.shape().interiorRun());
        assertEquals(c(0, 0, 0), result.shape().hullMin());
        assertEquals(c(4, 2, 2), result.shape().hullMax());
    }

    /** A pipe open at both ends has two mouths, facing opposite ways. */
    @Test
    void aPipeHasTwoMouths() {
        Set<ShelterGeometry.Cell> hollow = Set.of(c(1, 1, 1), c(1, 1, 2), c(1, 1, 3));
        Set<ShelterGeometry.Cell> parts = shell(0, 0, 0, 2, 2, 4, hollow, Set.of(c(1, 1, 0), c(1, 1, 4)));
        ShelterGeometry.Result result = ShelterGeometry.derive(hollow, parts);
        assertTrue(result.ok(), result.error());

        List<ShelterGeometry.Mouth> mouths = result.shape().mouths();
        assertEquals(2, mouths.size());
        Set<List<Integer>> outward = new HashSet<>();
        for (ShelterGeometry.Mouth m : mouths) outward.add(List.of(m.outward()[0], m.outward()[1], m.outward()[2]));
        assertEquals(Set.of(List.of(0, 0, -1), List.of(0, 0, 1)), outward);
    }

    /**
     * A face that opens into an enclosed pocket is not a mouth: the pocket is open water as far as
     * the hollow is concerned, but nothing can swim in from outside through it.
     */
    @Test
    void aPocketThatDoesNotReachOutsideIsNotAMouth() {
        // Hollow (1,1,1)-(2,1,1), open at x=3; a sealed pocket at (1,1,2) beside it, walled in.
        Set<ShelterGeometry.Cell> hollow = Set.of(c(1, 1, 1), c(2, 1, 1));
        Set<ShelterGeometry.Cell> parts = shell(0, 0, 0, 3, 2, 3, hollow, Set.of(c(3, 1, 1), c(1, 1, 2)));
        ShelterGeometry.Result result = ShelterGeometry.derive(hollow, parts);
        assertTrue(result.ok(), result.error());
        assertEquals(1, result.shape().mouths().size(), "the pocket's face was counted as a mouth");
        assertArrayEquals(new int[]{1, 0, 0}, result.shape().mouths().get(0).outward());

        // Seal the real end too and the pocket alone must not save it.
        Set<ShelterGeometry.Cell> sealed = shell(0, 0, 0, 3, 2, 3, hollow, Set.of(c(1, 1, 2)));
        assertFalse(ShelterGeometry.derive(hollow, sealed).ok());
    }

    /** Touching coplanar faces merge into one rectangle: a 2×2 open end is one mouth, two faces wide each way. */
    @Test
    void aWideOpeningIsOneMouth() {
        Set<ShelterGeometry.Cell> hollow = new HashSet<>();
        for (int x = 1; x <= 3; x++) for (int y = 1; y <= 2; y++) for (int z = 1; z <= 2; z++) hollow.add(c(x, y, z));
        Set<ShelterGeometry.Cell> open = Set.of(c(4, 1, 1), c(4, 1, 2), c(4, 2, 1), c(4, 2, 2));
        ShelterGeometry.Result result = ShelterGeometry.derive(hollow, shell(0, 0, 0, 4, 3, 3, hollow, open));
        assertTrue(result.ok(), result.error());
        assertEquals(1, result.shape().mouths().size());
        ShelterGeometry.Mouth mouth = result.shape().mouths().get(0);
        assertEquals(2, mouth.span(1));
        assertEquals(2, mouth.span(2));
        assertEquals(1, mouth.span(0));
    }

    /** Two separate hollows each need their own way out. */
    @Test
    void everyHollowNeedsItsOwnMouth() {
        Set<ShelterGeometry.Cell> hollow = Set.of(c(1, 1, 1), c(3, 1, 1));
        Set<ShelterGeometry.Cell> parts = shell(0, 0, 0, 4, 2, 2, hollow, Set.of(c(1, 2, 1)));
        assertFalse(ShelterGeometry.derive(hollow, parts).ok(), "the second hollow is sealed");
    }

    /** Marking a cell that is also a part is rejected rather than silently dropped. */
    @Test
    void anInteriorCellCannotBeAPart() {
        Set<ShelterGeometry.Cell> hollow = Set.of(c(1, 1, 1));
        Set<ShelterGeometry.Cell> parts = shell(0, 0, 0, 2, 2, 2, Set.of(), Set.of(c(2, 1, 1)));
        assertFalse(ShelterGeometry.derive(hollow, parts).ok());
    }

    /**
     * The shipped Hollow Log, read from its datapack file with the interior it ships with: its
     * 2×2 bore opens at the high-x end, and a gap in the roof above the bore's closed end is a
     * second, upward mouth. Guards {@code partCells}' undoing of capture's horizontal
     * compression, which every other case here skips by building cells directly.
     */
    @Test
    void theHollowLogOpensAtItsEndAndThroughItsRoof() throws IOException {
        Path file = Path.of("src/main/resources/data/fishtastic/fishtastic/cosmetic_structure/hollow_log.json");
        JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        float scale = json.get("scale").getAsFloat();
        float xzRatio = scale / (float) CosmeticGridCell.CELL_WIDTH;
        List<ShelterGeometry.Cell> parts = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("parts")) {
            JsonObject p = e.getAsJsonObject();
            parts.add(c(Math.round(get(p, "offsetX") / xzRatio), Math.round(get(p, "offsetY")),
                    Math.round(get(p, "offsetZ") / xzRatio)));
        }
        List<ShelterGeometry.Cell> bore = new ArrayList<>();
        for (JsonElement e : json.getAsJsonObject("shelter").getAsJsonArray("interior")) {
            JsonObject cell = e.getAsJsonObject();
            bore.add(c(cell.get("x").getAsInt(), cell.get("y").getAsInt(), cell.get("z").getAsInt()));
        }
        assertEquals(20, bore.size(), "a 5-long, 2×2 bore");

        ShelterGeometry.Result result = ShelterGeometry.derive(bore, parts);
        assertTrue(result.ok(), result.error());
        List<ShelterGeometry.Mouth> mouths = result.shape().mouths();
        assertEquals(2, mouths.size(), "mouths: " + mouths);

        ShelterGeometry.Mouth end = null, roof = null;
        for (ShelterGeometry.Mouth m : mouths) {
            if (m.outward()[0] == 1) end = m;
            if (m.outward()[1] == 1) roof = m;
        }
        assertNotNull(end, "no mouth at the open end");
        assertEquals(2, end.span(1));
        assertEquals(2, end.span(2));
        assertNotNull(roof, "no mouth through the roof");
        assertEquals(-2, roof.min().x(), "the roof gap is over the closed end");
        assertEquals(5, result.shape().interiorRun());
    }

    private static float get(JsonObject o, String key) {
        return o.has(key) ? o.get(key).getAsFloat() : 0f;
    }
}
