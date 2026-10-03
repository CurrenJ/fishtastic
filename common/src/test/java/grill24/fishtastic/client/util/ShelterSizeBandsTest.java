package grill24.fishtastic.client.util;

import grill24.fishsim.domain.Shelter;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.ShippedStructures;
import net.minecraft.world.level.block.Rotation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shelters built for medium and large fish (docs/fish-shelters.md §12.9) take the fish they
 * were built for, by the engine's own size rules (§5.2): a mouth admits a fish whose height,
 * 0.4 of its length, fits its narrower side; a hollow or open shelter holds a fish no longer than
 * 1.1 times its run (a gate is passed straight through, so asks nothing of length); and a seat
 * kept for big fish turns away the fish under its {@code min_length}.
 *
 * <p>Lengths are rendered lengths, blocks: a goby 0.10, a tetra 0.19, a loach 0.26, a bichir
 * 0.36, a pike 0.48, an eel 0.55, a manta 0.66, a sawfish 0.76. Only the fit is held here; whether
 * fish get there in a tank is {@link ObstacleShelterAccessTest}'s question.
 */
class ShelterSizeBandsTest {

    private static final float MOUTH_HEIGHT_RATIO = 0.4f, INTERIOR_LENGTH_SLACK = 1.1f;

    /** Whether a fish of rendered length {@code len} could use the shelter, by size alone. */
    private static boolean fits(Shelter s, float len) {
        if (len < s.minLength()) return false;
        if (s.kind() != Shelter.Kind.GATE && len > INTERIOR_LENGTH_SLACK * s.interiorRun()) return false;
        for (Shelter.Mouth m : s.mouths()) {
            // Only a horizontal mouth is one a fish lines up on.
            if (m.normalY() == 0f && MOUTH_HEIGHT_RATIO * len <= 2f * m.halfSize()) return true;
        }
        return false;
    }

    private static List<Shelter> shelters(String name) {
        CosmeticStructure structure = ShippedStructures.all().get(name);
        assertTrue(structure != null, name + " is shipped");
        return TankShelters.inBlockFrame(structure, 0.5f, 0.5f, Rotation.NONE);
    }

    private static void admits(String name, int index, float... lengths) {
        Shelter s = shelters(name).get(index);
        for (float len : lengths) assertTrue(fits(s, len), name + " #" + index + " should take a fish " + len + " long");
    }

    private static void refuses(String name, int index, float... lengths) {
        Shelter s = shelters(name).get(index);
        for (float len : lengths) assertTrue(!fits(s, len), name + " #" + index + " should turn away a fish " + len + " long");
    }

    @Test
    void mediumHides() {
        admits("amphora", 0, 0.10f, 0.26f, 0.36f);
        refuses("amphora", 0, 0.50f);
        admits("drowned_bell", 0, 0.10f, 0.26f, 0.36f);
        refuses("drowned_bell", 0, 0.48f);
        admits("mangrove_knees", 0, 0.10f, 0.36f, 0.45f);
    }

    @Test
    void largeDensAndLairs() {
        admits("basalt_grotto", 0, 0.26f, 0.36f, 0.48f, 0.55f);
        admits("sunken_ziggurat", 0, 0.26f, 0.48f, 0.55f);
        admits("capsized_galleon", 0, 0.36f, 0.48f, 0.55f, 0.76f);
        admits("whale_fall", 1, 0.26f, 0.55f, 0.66f, 0.76f);
    }

    @Test
    void gatesTakeTheBiggest() {
        admits("moon_gate", 0, 0.10f, 0.55f, 0.76f);
        admits("sea_arch", 0, 0.10f, 0.60f, 0.76f);
        admits("drowned_cathedral", 0, 0.10f, 0.66f, 0.76f);
    }

    @Test
    void theSeatIsKeptForBigFish() {
        refuses("leviathans_seat", 0, 0.10f, 0.26f, 0.36f, 0.55f);
        admits("leviathans_seat", 0, 0.41f, 0.45f, 0.48f);
        assertEquals(1, shelters("leviathans_seat").get(0).capacity());
    }

    /** One structure, three sizes: big fish sail the nave, medium fish rest in the belfry, small fish hide in the chapels. */
    @Test
    void theCathedralHasARoomForEverySize() {
        List<Shelter> s = shelters("drowned_cathedral");
        assertEquals(5, s.size());
        assertEquals(Shelter.Kind.GATE, s.get(0).kind());
        assertEquals(Shelter.Kind.OPEN, s.get(1).kind());
        admits("drowned_cathedral", 1, 0.26f, 0.36f);
        refuses("drowned_cathedral", 1, 0.55f);
        for (int chapel = 2; chapel <= 4; chapel++) {
            assertEquals(Shelter.Kind.HOLLOW, s.get(chapel).kind());
            admits("drowned_cathedral", chapel, 0.10f, 0.19f);
            refuses("drowned_cathedral", chapel, 0.36f);
        }
    }

    /** Three passages through the warren, all gates, so a shoal can stream through every one at once. */
    @Test
    void theWarrenRunsThreeWays() {
        List<Shelter> s = shelters("coral_warren");
        assertEquals(3, s.size());
        List<Shelter.Kind> kinds = new ArrayList<>();
        for (Shelter shelter : s) kinds.add(shelter.kind());
        assertEquals(List.of(Shelter.Kind.GATE, Shelter.Kind.GATE, Shelter.Kind.GATE), kinds);
        admits("coral_warren", 0, 0.10f, 0.26f);
        admits("coral_warren", 1, 0.26f, 0.36f, 0.55f);
        admits("coral_warren", 2, 0.10f, 0.26f);
        admits("sunken_ziggurat", 1, 0.10f, 0.19f);
        refuses("sunken_ziggurat", 1, 0.36f);
    }
}
