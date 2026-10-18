package grill24.fishtastic.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 1.20.1 writes every synced datapack registry in the login packet with plain {@link NbtOps}
 * (no RegistryOps), so a synced codec that needs registry access fails with "Can't access
 * registry worldgen/biome" and nobody can join a dedicated server. This pins that every shipped
 * fish profile survives a registry-free JSON decode, NBT encode and NBT decode.
 */
final class FishProfileNetworkCodecTest {
    private static final Path PROFILES = Path.of("src/main/resources/data/fishtastic/fishtastic/fish_profile");

    @BeforeAll
    static void bootstrap() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void everyShippedProfileRoundTripsWithoutRegistryAccess() throws IOException {
        List<String> problems = new ArrayList<>();
        int count = 0;
        try (Stream<Path> files = Files.list(PROFILES)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                count++;
                JsonElement json = JsonParser.parseString(Files.readString(file));
                var decoded = FishProfile.CODEC.parse(JsonOps.INSTANCE, json);
                if (decoded.error().isPresent()) {
                    problems.add(file.getFileName() + " json decode: " + decoded.error().get().message());
                    continue;
                }
                // optionalFieldOf swallows a field it cannot decode, so count what survived.
                int expected = json.getAsJsonObject().has("biome_weights")
                        ? json.getAsJsonObject().getAsJsonArray("biome_weights").size() : 0;
                if (decoded.result().get().biomeWeights().size() != expected) {
                    problems.add(file.getFileName() + " biome_weights kept " + decoded.result().get().biomeWeights().size() + " of " + expected);
                    continue;
                }
                var encoded = FishProfile.CODEC.encodeStart(NbtOps.INSTANCE, decoded.result().get());
                if (encoded.error().isPresent()) {
                    problems.add(file.getFileName() + " nbt encode: " + encoded.error().get().message());
                    continue;
                }
                Tag tag = encoded.result().get();
                var back = FishProfile.CODEC.parse(NbtOps.INSTANCE, tag);
                if (back.error().isPresent()) {
                    problems.add(file.getFileName() + " nbt decode: " + back.error().get().message());
                    continue;
                }
                assertEquals(tag, FishProfile.CODEC.encodeStart(NbtOps.INSTANCE, back.result().get()).result().get(),
                        file.getFileName() + " re-encode differs");
            }
        }
        assertFalse(count == 0, "no fish profiles found");
        assertEquals(List.of(), problems.size() > 5 ? problems.subList(0, 5) : problems,
                problems.size() + " of " + count + " profiles fail");
    }

    @Test
    void biomeSelectorFormsRoundTrip() {
        for (String form : List.of("\"#minecraft:is_jungle\"", "\"minecraft:swamp\"", "[\"minecraft:swamp\",\"minecraft:mangrove_swamp\"]")) {
            JsonElement json = JsonParser.parseString("{\"biome\":" + form + ",\"multiplier\":1.5}");
            var weight = FishProfile.BiomeWeight.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(false, s -> {});
            Tag tag = FishProfile.BiomeWeight.CODEC.encodeStart(NbtOps.INSTANCE, weight).getOrThrow(false, s -> {});
            assertEquals(weight, FishProfile.BiomeWeight.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow(false, s -> {}), form);
        }
    }
}
