package grill24.fishtastic.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import grill24.fishtastic.school.VisitShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A profile's {@code world_encounter} block (docs/in-world-fish-encounters-design-spec.md §8) and
 * the shipped species' authored shapes (§3.2).
 */
class WorldEncounterTest {

    private static final Path PROFILES = Path.of("src/main/resources/data/fishtastic/fishtastic/fish_profile");
    /** Items with a profile that never appear in the world: bait, and the denied shrimp (§5.2). */
    private static final Set<String> NOT_ENCOUNTERS = Set.of("blazed_grub", "shrimp", "fried_shrimp");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    private static JsonElement json(Object value) {
        return new Gson().toJsonTree(value);
    }

    private static FishProfile.WorldEncounter parse(Object value) {
        return FishProfile.WorldEncounter.CODEC.parse(JsonOps.INSTANCE, json(value))
                .getOrThrow(false, message -> { throw new IllegalStateException(message); });
    }

    private static FishProfile profile(int baseWeight, Optional<FishProfile.WorldEncounter> encounter) {
        return new FishProfile(baseWeight, FishProfile.SizeParams.DEFAULT, List.of(), List.of(), List.of(), List.of(),
                List.of(FishProfile.Zone.RIVER), Optional.empty(), Optional.empty(), SwarmConfig.DEFAULT,
                FishProfile.DEFAULT_RENDER_CALIBRATION, Optional.empty(), FishProfile.CompanionGait.CRAWL,
                FishProfile.CompanionDrift.BELL, encounter);
    }

    @Test
    void parsesEveryField() {
        FishProfile.WorldEncounter encounter = parse(Map.of("shape", "pair",
                "members", Map.of("min", 2, "max", 2), "lifetime_scale", 0.5f));
        assertEquals(Optional.of(VisitShape.PAIR), encounter.shape());
        assertEquals(Optional.of(new FishProfile.WorldEncounter.MemberRange(2, 2)), encounter.members());
        assertEquals(0.5f, encounter.lifetimeScale());
    }

    @Test
    void everyFieldIsOptional() {
        FishProfile.WorldEncounter encounter = parse(Map.of());
        assertEquals(Optional.empty(), encounter.shape());
        assertEquals(Optional.empty(), encounter.members());
        assertEquals(1.0f, encounter.lifetimeScale());
    }

    @Test
    void rejectsBadValues() {
        var codec = FishProfile.WorldEncounter.CODEC;
        assertTrue(VisitShape.CODEC.parse(JsonOps.INSTANCE, json("herd")).error().isPresent(), "shape codec must reject unknown names");
        assertTrue(codec.parse(JsonOps.INSTANCE, json(Map.of("shape", "herd"))).error().isPresent());
        assertTrue(codec.parse(JsonOps.INSTANCE, json(Map.of("members", Map.of("min", 3, "max", 2)))).error().isPresent());
        assertTrue(codec.parse(JsonOps.INSTANCE, json(Map.of("lifetime_scale", 5.0f))).error().isPresent());
    }

    @Test
    void anAuthoredShapeWinsOverTheRarityFallback() {
        assertEquals(VisitShape.SCHOOL, profile(75, Optional.empty()).visitShape());
        assertEquals(VisitShape.SOLITARY, profile(4, Optional.empty()).visitShape());
        assertEquals(VisitShape.SCHOOL, profile(4, Optional.of(parse(Map.of("shape", "school")))).visitShape());
        assertEquals(VisitShape.SOLITARY, profile(4, Optional.of(parse(Map.of()))).visitShape(),
                "a block with no shape still falls back");
    }

    @Test
    void shippedProfilesAuthorTheApprovedShapes() throws IOException {
        Map<VisitShape, Integer> counts = new EnumMap<>(VisitShape.class);
        Map<String, String> missing = new TreeMap<>();
        try (Stream<Path> files = Files.list(PROFILES)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                JsonElement block = json.get("world_encounter");
                boolean nether = json.getAsJsonArray("zones").toString().contains("nether");
                JsonObject animation = json.has("animation") ? json.getAsJsonObject("animation") : new JsonObject();
                String mode = animation.has("mode") ? animation.get("mode").getAsString() : "horizontal_swim";
                if (block == null) {
                    // Only species outside this phase may go without: nether (lava), gliders
                    // (parked) and the items that never appear.
                    if (!nether && !"belly_down".equals(mode) && !NOT_ENCOUNTERS.contains(name)) {
                        missing.put(name, mode);
                    }
                    continue;
                }
                FishProfile.WorldEncounter encounter = FishProfile.WorldEncounter.CODEC.parse(JsonOps.INSTANCE, block)
                        .getOrThrow(false, message -> { throw new IllegalStateException(name + ": " + message); });
                VisitShape shape = encounter.shape().orElseThrow(() -> new IllegalStateException(name + " has no shape"));
                assertTrue(shape != VisitShape.SCHOOL || "horizontal_swim".equals(mode),
                        name + " is not a free swimmer, so it cannot school");
                counts.merge(shape, 1, Integer::sum);
            }
        }
        assertTrue(missing.isEmpty(), "species in this phase without an authored shape: " + missing);
        // The approved table (spec §3.2).
        assertEquals(8, counts.getOrDefault(VisitShape.SCHOOL, 0));
        assertEquals(14, counts.getOrDefault(VisitShape.LOOSE_GROUP, 0));
        assertEquals(9, counts.getOrDefault(VisitShape.PAIR, 0));
        assertEquals(22, counts.getOrDefault(VisitShape.SOLITARY, 0));
    }
}
