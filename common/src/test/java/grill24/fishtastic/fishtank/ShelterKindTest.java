package grill24.fishtastic.fishtank;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shelter's kind (docs/fish-shelters.md §12.1, §12.4): what each shipped shelter is, and the one
 * rule a kind adds to validation — a gate must have a way through.
 */
class ShelterKindTest {

    @Test
    void theShippedShelterKinds() {
        Set<String> gates = new TreeSet<>(), open = new TreeSet<>(), hollows = new TreeSet<>();
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            for (CosmeticStructure.ShelterSpec spec : e.getValue().shelters()) {
                (switch (spec.kind()) {
                    case GATE -> gates;
                    case OPEN -> open;
                    case HOLLOW -> hollows;
                }).add(e.getKey());
            }
        }
        for (String name : ShippedStructures.all().keySet()) {
            if (name.startsWith("cosmetic_fence_arch_")) assertTrue(gates.contains(name), name + " is a gate");
        }
        assertTrue(gates.contains("torii_gate"), "the Torii Gate is a gate: " + gates);
        for (String gate : List.of("moon_gate", "sea_arch", "drowned_cathedral", "coral_warren")) {
            assertTrue(gates.contains(gate), gate + " is a gate: " + gates);
        }
        assertEquals(Set.of("spruce_gazebo", "mangrove_knees", "leviathans_seat", "sunken_ziggurat", "whale_fall", "sea_arch",
                "drowned_cathedral", "reef_crowned_skull"), open);
        assertEquals(Set.of("clay_pipe", "hollow_log", "whale_fall", "amphora", "drowned_bell", "basalt_grotto",
                "sunken_ziggurat", "capsized_galleon", "drowned_cathedral", "sea_arch", "reef_crowned_skull"), hollows, "a shelter without a kind is a hollow");
    }

    /** Every shipped gate opens on both sides, horizontally — the way through it. */
    @Test
    void everyShippedGateOpensBothWays() {
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            CosmeticStructure s = e.getValue();
            for (CosmeticStructure.DerivedShelter d : s.shelterShapes()) {
                if (d.spec().kind() != CosmeticStructure.ShelterKind.GATE) continue;
                ShelterGeometry.Shape shape = d.shape();
                int sideways = 0;
                for (ShelterGeometry.Mouth m : shape.mouths()) if (m.outward()[1] == 0) sideways++;
                assertEquals(2, sideways, e.getKey() + ": one mouth front, one back");
                assertTrue(CosmeticStructure.opensBothWays(shape), e.getKey());
            }
        }
    }

    /**
     * A gate's opening may take in a part's cell only if fish can swim through that part — the
     * arches' lantern. The loader can't check this (softness is a tag), so it is held here.
     */
    @Test
    void whatHangsInAGatewayIsSoft() {
        int checked = 0;
        for (Map.Entry<String, CosmeticStructure> e : ShippedStructures.all().entrySet()) {
            CosmeticStructure s = e.getValue();
            for (CosmeticStructure.ShelterSpec spec : s.shelters()) {
                if (spec.kind() != CosmeticStructure.ShelterKind.GATE) continue;
                Set<ShelterGeometry.Cell> opening = Set.copyOf(spec.interior());
                List<ShelterGeometry.Cell> cells = s.partCells();
                for (int k = 0; k < cells.size(); k++) {
                    if (!opening.contains(cells.get(k))) continue;
                    // A locked gate's door stands in its gateway at rest, and is out of it when a fish goes through.
                    if (s.isGateDoor(k)) continue;
                    assertTrue(ShippedStructures.softTag().test(s.parts().get(k).state()),
                            e.getKey() + ": solid " + s.parts().get(k).state() + " in the gateway at " + cells.get(k));
                    checked++;
                }
            }
        }
        assertTrue(checked >= 10, "every fence arch's lantern hangs in its gateway: " + checked);
    }

    /** The gazebo's floor is open on all four sides, between its corner posts. */
    @Test
    void theGazeboOpensOnEverySide() {
        ShelterGeometry.Shape shape = ShippedStructures.all().get("spruce_gazebo").shelterShapes().get(0).shape();
        Set<String> sides = new TreeSet<>();
        for (ShelterGeometry.Mouth m : shape.mouths()) {
            if (m.outward()[1] == 0) sides.add(m.outward()[0] + "," + m.outward()[2]);
        }
        assertEquals(Set.of("-1,0", "0,-1", "0,1", "1,0"), sides);
    }

    /** A one-mouthed hollow marked as a gate is an authoring mistake: there is no way through it. */
    @Test
    void aGateWithNoWayThroughFailsToLoad() throws IOException {
        ShippedStructures.bootstrap();
        JsonObject json = JsonParser.parseString(Files.readString(ShippedStructures.DIR.resolve("hollow_log.json"))).getAsJsonObject();
        json.getAsJsonObject("shelter").addProperty("kind", "gate");
        DataResult<CosmeticStructure> result = CosmeticStructure.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent(), "a log loaded as a gate");
        assertTrue(result.error().get().message().contains("gate"), result.error().get().message());
    }
}
