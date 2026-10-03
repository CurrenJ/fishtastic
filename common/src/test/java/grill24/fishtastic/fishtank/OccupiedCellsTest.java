package grill24.fishtastic.fishtank;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code occupied_cells} (docs/fish-shelters.md §12.10): the cells of a structure's footprint that
 * actually block other cosmetics and crawling fish.
 */
class OccupiedCellsTest {

    private static final int G = CosmeticGridCell.GRID_SIZE;

    /** A span's footprint as box-wide cells: tank offset × 3 + cell, as {@code occupied_cells} counts them. */
    private static Set<CosmeticStructure.GridOffset> boxCells(Map<BlockPos, List<CosmeticGridCell>> footprint) {
        Set<CosmeticStructure.GridOffset> out = new HashSet<>();
        footprint.forEach((tank, cells) -> {
            for (CosmeticGridCell cell : cells) {
                out.add(new CosmeticStructure.GridOffset(tank.getX() * G + cell.gridX(), tank.getZ() * G + cell.gridZ()));
            }
        });
        return out;
    }

    private static CosmeticStructure withOccupied(CosmeticStructure s, List<CosmeticStructure.GridOffset> occupied) {
        return new CosmeticStructure(s.footprintCells(), s.parts(), s.scale(), s.itemIcon(),
                s.bypassAnchorCellRequirement(), s.span(), s.shelters(), occupied);
    }

    /**
     * Authored cells turn with the box exactly as derived ones do: authoring a span's own derived
     * footprint unrotated gives back its derived footprint at every rotation. Covers boxes longer
     * than deep, where a quarter turn swaps the axes.
     */
    @Test
    void authoredSpanCellsTurnWithTheBox() {
        int checked = 0;
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            if (e.getValue().span().isEmpty()) continue;
            CosmeticStructure derived = withOccupied(e.getValue(), List.of());
            List<CosmeticStructure.GridOffset> authored =
                    new ArrayList<>(boxCells(SpanStructures.footprint(derived, Rotation.NONE)));
            CosmeticStructure fixed = withOccupied(derived, authored);
            for (Rotation rotation : Rotation.values()) {
                assertEquals(boxCells(SpanStructures.footprint(derived, rotation)),
                        boxCells(SpanStructures.footprint(fixed, rotation)), e.getKey() + " at " + rotation);
            }
            checked++;
        }
        assertTrue(checked >= 4, "expected the shipped spans, found " + checked);
    }

    /** A span's footprint is its occupied_cells when it has them, and those never add to what its parts stand on. */
    @Test
    void shippedSpansOnlyEverFreeCells() {
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            CosmeticStructure s = e.getValue();
            if (s.span().isEmpty() || s.occupiedCells().isEmpty()) continue;
            Set<CosmeticStructure.GridOffset> standsOn = boxCells(SpanStructures.footprint(withOccupied(s, List.of()), Rotation.NONE));
            Set<CosmeticStructure.GridOffset> occupied = boxCells(SpanStructures.footprint(s, Rotation.NONE));
            assertEquals(new HashSet<>(s.occupiedCells()), occupied, e.getKey());
            assertTrue(standsOn.containsAll(occupied), e.getKey() + " occupies a cell none of its parts stand on");
        }
    }

    @Test
    void floorOccupiedCellsDefaultToTheFootprint() {
        CosmeticStructure plain = ShippedStructures.all().get("cosmetic_fence_arch_oak");
        assertEquals(plain.footprintCells(), plain.occupied());
        CosmeticStructure torii = ShippedStructures.all().get("torii_gate");
        assertEquals(3, torii.occupied().size(), "the torii stands on its two posts and its middle");
        assertTrue(torii.footprintCells().containsAll(torii.occupied()));
    }

    private static String parseError(String json) {
        ShippedStructures.bootstrap();
        return CosmeticStructure.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error()
                .map(err -> err.message()).orElse(null);
    }

    private static final String PART = "\"parts\": [{\"state\": {\"Name\": \"minecraft:stone\"}}]";

    @Test
    void occupiedCellsMustLieInTheFootprintAndKeepTheAnchor() {
        String footprint = "\"footprint_cells\": [{\"dx\": 0, \"dz\": 0}, {\"dx\": 1, \"dz\": 0}], ";
        assertEquals(null, parseError("{" + footprint + "\"occupied_cells\": [{\"dx\": 0, \"dz\": 0}], " + PART + "}"));
        assertTrue(parseError("{" + footprint + "\"occupied_cells\": [{\"dx\": 0, \"dz\": 0}, {\"dx\": 0, \"dz\": 1}], " + PART + "}")
                .contains("not in footprint_cells"));
        assertTrue(parseError("{" + footprint + "\"occupied_cells\": [{\"dx\": 1, \"dz\": 0}], " + PART + "}")
                .contains("anchor"));
    }

    @Test
    void spanOccupiedCellsMustLieOnTheBoxFloor() {
        String span = "\"span\": {\"x\": 2, \"y\": 1, \"z\": 1}, \"scale\": 0.5, ";
        assertEquals(null, parseError("{" + span + "\"occupied_cells\": [{\"dx\": 5, \"dz\": 2}], " + PART + "}"));
        assertTrue(parseError("{" + span + "\"occupied_cells\": [{\"dx\": 6, \"dz\": 0}], " + PART + "}")
                .contains("outside"));
    }
}
