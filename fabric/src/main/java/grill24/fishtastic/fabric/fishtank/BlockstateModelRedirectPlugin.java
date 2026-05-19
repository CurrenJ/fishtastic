package grill24.fishtastic.fabric.fishtank;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import grill24.fishtastic.Fishtastic;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jspecify.annotations.Nullable;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Preparable model loading plugin that scans all blockstate JSONs at resource-reload time and
 * builds a redirect map from each block's standard model path (namespace:block/name) to the
 * actual model path declared in its blockstate JSON.
 *
 * <p>Runs off-thread during the prepare phase, before model baking, so the map is ready when
 * {@link BlockModelPathResolverFabric} is first consulted during baking.
 */
public final class BlockstateModelRedirectPlugin {
    private static final Gson GSON = new Gson();
    private static final FileToIdConverter BLOCKSTATE_LISTER = FileToIdConverter.json("blockstates");

    private BlockstateModelRedirectPlugin() {}

    public static final PreparableModelLoadingPlugin.DataLoader<Map<Identifier, Identifier>> LOADER =
            (resourceReloaderStore, executor) -> CompletableFuture.supplyAsync(
                    () -> buildRedirectMap(resourceReloaderStore.resourceManager()), executor);

    public static final PreparableModelLoadingPlugin<Map<Identifier, Identifier>> PLUGIN =
            (data, pluginContext) -> BlockstateRedirectRegistry.update(data);

    private static Map<Identifier, Identifier> buildRedirectMap(ResourceManager manager) {
        Map<Identifier, Identifier> redirects = new HashMap<>();
        Map<Identifier, Resource> blockstates = BLOCKSTATE_LISTER.listMatchingResources(manager);

        for (Map.Entry<Identifier, Resource> entry : blockstates.entrySet()) {
            Identifier blockId = BLOCKSTATE_LISTER.fileToId(entry.getKey());
            Identifier standardPath = blockId.withPrefix("block/");

            try (Reader reader = entry.getValue().openAsReader()) {
                JsonObject json = GSON.fromJson(reader, JsonObject.class);
                if (json == null) continue;

                Identifier modelId = extractFirstModelId(json);
                if (modelId != null && !modelId.equals(standardPath)) {
                    redirects.put(standardPath, modelId);
                }
            } catch (Exception e) {
                Fishtastic.LOGGER.debug("[BlockstateModelRedirectPlugin] Skipping {}: {}", entry.getKey(), e.getMessage());
            }
        }

        Fishtastic.LOGGER.info("[BlockstateModelRedirectPlugin] Built redirect map with {} entries.", redirects.size());
        return redirects;
    }

    @Nullable
    private static Identifier extractFirstModelId(JsonObject json) {
        String path = extractFirstModelPath(json);
        if (path == null) return null;
        try {
            return Identifier.parse(path);
        } catch (Exception e) {
            return null;
        }
    }

    @Nullable
    private static String extractFirstModelPath(JsonObject json) {
        // variants format: { "variants": { "<state>": { "model": "..." } | [ { "model": "..." }, ... ] } }
        if (json.has("variants")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("variants").entrySet()) {
                String path = modelFromElement(entry.getValue());
                if (path != null) return path;
            }
        }
        // multipart format: { "multipart": [ { "apply": { "model": "..." } | [ ... ] }, ... ] }
        if (json.has("multipart")) {
            for (JsonElement part : json.getAsJsonArray("multipart")) {
                if (!part.isJsonObject()) continue;
                JsonElement apply = part.getAsJsonObject().get("apply");
                if (apply == null) continue;
                String path = modelFromElement(apply);
                if (path != null) return path;
            }
        }
        return null;
    }

    @Nullable
    private static String modelFromElement(JsonElement element) {
        if (element.isJsonObject()) {
            JsonElement model = element.getAsJsonObject().get("model");
            return (model != null && model.isJsonPrimitive()) ? model.getAsString() : null;
        }
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            return arr.isEmpty() ? null : modelFromElement(arr.get(0));
        }
        return null;
    }
}
