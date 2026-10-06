package grill24.fishtastic.fishtank;

import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link CosmeticStructure#liveParts()} finds, once, the parts the tank renderer used to find by
 * rotating and checking every part every frame (docs/cosmetic-render-cost.md). For every shipped
 * structure, at every rotation, it must pick out exactly the parts that scan did.
 */
class LivePartsTest {

    /** The per-frame scan the renderer did before: rotate each part, then test it. */
    private static int[] oldScan(CosmeticStructure structure, Rotation rotation, boolean chests) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < structure.parts().size(); i++) {
            BlockState s = structure.parts().get(i).state().rotate(rotation);
            boolean hit = chests ? s.getBlock() == Blocks.CHEST
                    : s.getBlock() instanceof AbstractFurnaceBlock && s.getValue(AbstractFurnaceBlock.LIT);
            if (hit) out.add(i);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    @Test
    void livePartsMatchThePerFrameScanAtEveryRotation() {
        int structures = 0, withLiveParts = 0;
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            CosmeticStructure structure = e.getValue();
            CosmeticStructure.LiveParts live = structure.liveParts();
            for (Rotation rotation : Rotation.values()) {
                assertTrue(Arrays.equals(oldScan(structure, rotation, true), live.chests()),
                        e.getKey() + " chests at " + rotation);
                assertTrue(Arrays.equals(oldScan(structure, rotation, false), live.litFurnaces()),
                        e.getKey() + " lit furnaces at " + rotation);
            }
            structures++;
            if (live.chests().length + live.litFurnaces().length > 0) withLiveParts++;
        }
        assertTrue(structures > 0, "no shipped structures loaded");
        assertTrue(withLiveParts > 0, "no shipped structure has a chest or lit furnace, so nothing was compared");
    }

    @Test
    void livePartsAreFoundOncePerInstance() {
        CosmeticStructure structure = ShippedStructures.all().values().iterator().next();
        assertSame(structure.liveParts(), structure.liveParts());
    }

    @Test
    void aStructureWithoutChestsOrFurnacesHasNoLiveParts() {
        CosmeticStructure seat = ShippedStructures.all().get("leviathans_seat");
        assertEquals(0, seat.liveParts().chests().length);
        assertEquals(0, seat.liveParts().litFurnaces().length);
    }
}
