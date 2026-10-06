package grill24.fishtastic.client.util;

import grill24.fishsim.domain.Shelter;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticReaction;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.ShippedStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every shipped reaction (docs/fish-shelters.md §12.13) reaches the engine as a trigger, through
 * every step a tank's shelters take: placed in the block frame, then moved into a lone tank's
 * turned frame or a group's. A step that rebuilt a shelter and dropped its trigger crashed world
 * load on the first tank holding a nose anchor.
 */
class TankReactionsTest {

    @Test
    void everyReactionReachesTheEngineAsATrigger() {
        int checked = 0;
        for (var e : ShippedStructures.all().entrySet()) {
            CosmeticStructure structure = e.getValue();
            if (structure.reactions().isEmpty()) continue;
            CosmeticGridCell anchor = new CosmeticGridCell(1, 1);
            for (Rotation rotation : Rotation.values()) {
                List<Shelter> placed = TankShelters.inBlockFrame(structure, (float) anchor.localX(), (float) anchor.localZ(), rotation,
                        r -> new CosmeticReaction.Key(BlockPos.ZERO, anchor, r));
                for (float yaw : new float[]{0f, 90f, 37f}) {
                    List<Shelter> engine = placed.stream().map(s -> TankShelters.toEngine(s, 0.5f, 0.3f, 0.5f, yaw)).toList();
                    long triggers = engine.stream().filter(s -> s.trigger() != null).count();
                    assertEquals(structure.reactions().size(), triggers, e.getKey() + " " + rotation + " yaw " + yaw);
                    for (Shelter s : engine) {
                        if (s.kind() == Shelter.Kind.TRIGGER) assertNotNull(s.trigger(), e.getKey());
                    }
                }
            }
            checked++;
        }
        assertTrue(checked >= 4, "the four reactive cosmetics: " + checked);
    }
}
