package grill24.fishtastic.fishtank;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Every cosmetic structure the mod ships, decoded through the real codec, for tests that must
 * hold over the shipped set rather than synthetic shapes (docs/fish-shelters.md §12.3: "the
 * shipped set is where the thin posts, overhangs and spans are").
 *
 * <p>A plain unit test can bootstrap vanilla but not the mod, so two stand-ins: the mod's own
 * stained glass, a vanilla {@code StainedGlassBlock} under another name (see
 * {@code FishtasticBlocks}), is read as vanilla stained glass of the same colour — the same full
 * cube; and the {@code fishtastic:soft_cosmetic} tag is resolved from its JSON, with vanilla tags
 * it names read from the game's own data on the classpath, since no datapack is loaded.
 */
public final class ShippedStructures {

    private ShippedStructures() {}

    static final Path DIR = Path.of("src/main/resources/data/fishtastic/fishtastic/cosmetic_structure");

    private static Map<String, CosmeticStructure> all;
    private static Set<String> soft;

    public static synchronized void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Every shipped structure by file name (without {@code .json}), in name order. */
    public static synchronized Map<String, CosmeticStructure> all() {
        if (all != null) return all;
        bootstrap();
        Map<String, CosmeticStructure> out = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(DIR)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String json = Files.readString(f)
                        .replaceAll("fishtastic:([a-z_]+)_(?:clear|borderless)_stained_glass", "minecraft:$1_stained_glass");
                CosmeticStructure structure = CosmeticStructure.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                        .getOrThrow(error -> new IllegalStateException(f.getFileName() + ": " + error));
                out.put(f.getFileName().toString().replace(".json", ""), structure);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        all = out;
        return all;
    }

    /** {@code fishtastic:soft_cosmetic}, as the game would resolve it. */
    public static synchronized Predicate<BlockState> softTag() {
        if (soft == null) {
            bootstrap();
            soft = new HashSet<>();
            resolve("fishtastic:soft_cosmetic", soft);
        }
        return state -> soft.contains(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
    }

    private static void resolve(String tag, Set<String> into) {
        String[] id = tag.split(":");
        String resource = "data/" + id[0] + "/tags/block/" + id[1] + ".json";
        JsonElement json;
        try {
            Path local = Path.of("src/main/resources", resource);
            if (Files.exists(local)) {
                json = JsonParser.parseString(Files.readString(local));
            } else {
                try (InputStream in = ShippedStructures.class.getClassLoader().getResourceAsStream(resource)) {
                    if (in == null) throw new IllegalStateException("no tag " + tag);
                    json = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (JsonElement value : json.getAsJsonObject().getAsJsonArray("values")) {
            String entry = value.isJsonObject() ? value.getAsJsonObject().get("id").getAsString() : value.getAsString();
            if (entry.startsWith("#")) {
                resolve(entry.substring(1), into);
            } else {
                into.add(entry);
            }
        }
    }

    /** The shipped floor structures (those without a span). */
    public static List<String> floorNames() {
        return all().entrySet().stream().filter(e -> e.getValue().span().isEmpty()).map(Map.Entry::getKey).toList();
    }
}
