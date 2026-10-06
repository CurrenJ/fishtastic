package grill24.fishtastic.fishtank;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Several shelters in one structure, and a shelter kept for big fish (docs/fish-shelters.md
 * §12.9): how they are written, and the rule that keeps them apart.
 */
class MultiShelterTest {

    private static ShelterGeometry.Cell c(int x, int y, int z) {
        return new ShelterGeometry.Cell(x, y, z);
    }

    /** A three-cell tube along z at {@code (x, 1)}: its interior, walled on four sides, open at both ends. */
    private static List<ShelterGeometry.Cell> tube(int x) {
        return List.of(c(x, 1, 0), c(x, 1, 1), c(x, 1, 2));
    }

    private static Set<ShelterGeometry.Cell> walls(int x) {
        Set<ShelterGeometry.Cell> out = new HashSet<>();
        for (int z = 0; z <= 2; z++) {
            out.add(c(x - 1, 1, z));
            out.add(c(x + 1, 1, z));
            out.add(c(x, 0, z));
            out.add(c(x, 2, z));
        }
        return out;
    }

    private static String overlap(CosmeticStructure.ShelterKind kindA, int xA, CosmeticStructure.ShelterKind kindB, int xB) {
        Set<ShelterGeometry.Cell> parts = new HashSet<>(walls(xA));
        parts.addAll(walls(xB));
        parts.removeAll(tube(xA));
        parts.removeAll(tube(xB));
        List<CosmeticStructure.ShelterSpec> specs = List.of(
                new CosmeticStructure.ShelterSpec(tube(xA), Optional.empty(), kindA),
                new CosmeticStructure.ShelterSpec(tube(xB), Optional.empty(), kindB));
        List<ShelterGeometry.Shape> shapes = new ArrayList<>();
        for (CosmeticStructure.ShelterSpec spec : specs) {
            ShelterGeometry.Result result = ShelterGeometry.derive(spec.interior(), parts);
            assertTrue(result.ok(), result.error());
            shapes.add(result.shape());
        }
        return CosmeticStructure.overlap(specs, shapes);
    }

    @Test
    void sheltersApartStandTogether() {
        assertNull(overlap(CosmeticStructure.ShelterKind.HOLLOW, 0, CosmeticStructure.ShelterKind.HOLLOW, 4));
    }

    /** Two tubes side by side, open to each other: each one's interior lies in the other's hull. */
    @Test
    void anInteriorInAnotherHollowsHullIsRejected() {
        String why = overlap(CosmeticStructure.ShelterKind.HOLLOW, 0, CosmeticStructure.ShelterKind.HOLLOW, 1);
        assertTrue(why != null && why.contains("hull"), why);
    }

    /** A gate has no hull, so a hollow beside one may reach into its bounds. */
    @Test
    void aGateHasNoHullToReachInto() {
        String why = overlap(CosmeticStructure.ShelterKind.HOLLOW, 0, CosmeticStructure.ShelterKind.GATE, 1);
        // The gate still may not reach into the hollow's hull.
        assertTrue(why != null && why.startsWith("shelter 1"), why);
    }

    private static JsonObject log() throws IOException {
        ShippedStructures.bootstrap();
        return JsonParser.parseString(Files.readString(ShippedStructures.DIR.resolve("hollow_log.json"))).getAsJsonObject();
    }

    /** One shelter is written as "shelter", as before there could be several — every shipped file is unchanged. */
    @Test
    void oneShelterRoundTripsAsShelter() throws IOException {
        CosmeticStructure structure = CosmeticStructure.CODEC.parse(JsonOps.INSTANCE, log()).getOrThrow(false, error -> {});
        assertEquals(1, structure.shelters().size());
        JsonObject out = CosmeticStructure.CODEC.encodeStart(JsonOps.INSTANCE, structure).getOrThrow(false, error -> {}).getAsJsonObject();
        assertTrue(out.has("shelter"));
        assertFalse(out.has("shelters"));
    }

    @Test
    void aListOfOneLoadsTheSame() throws IOException {
        JsonObject json = log();
        JsonArray list = new JsonArray();
        list.add(json.remove("shelter"));
        json.add("shelters", list);
        CosmeticStructure structure = CosmeticStructure.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, error -> {});
        assertEquals(1, structure.shelterShapes().size());
    }

    @Test
    void theSameHollowTwiceIsRejected() throws IOException {
        JsonObject json = log();
        JsonElement shelter = json.remove("shelter");
        JsonArray list = new JsonArray();
        list.add(shelter);
        list.add(shelter.deepCopy());
        json.add("shelters", list);
        DataResult<CosmeticStructure> result = CosmeticStructure.CODEC.parse(JsonOps.INSTANCE, json);
        assertTrue(result.error().isPresent());
        assertTrue(result.error().get().message().contains("share"), result.error().get().message());
    }

    @Test
    void minLengthRoundTrips() throws IOException {
        JsonObject json = log();
        json.getAsJsonObject("shelter").addProperty("min_length", 0.4f);
        CosmeticStructure structure = CosmeticStructure.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, error -> {});
        assertEquals(Optional.of(0.4f), structure.shelters().get(0).minLength());
        JsonObject out = CosmeticStructure.CODEC.encodeStart(JsonOps.INSTANCE, structure).getOrThrow(false, error -> {}).getAsJsonObject();
        assertEquals(0.4f, out.getAsJsonObject("shelter").get("min_length").getAsFloat());
    }
}
