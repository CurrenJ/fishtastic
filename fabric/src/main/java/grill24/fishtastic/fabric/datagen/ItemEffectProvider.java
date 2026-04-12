package grill24.fishtastic.fabric.datagen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.component.FishQuality;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Data generator for item effects.
 * Generates JSON files for quality-based glow effects for fish items.
 */
public class ItemEffectProvider implements DataProvider {
    private final PackOutput.PathProvider pathProvider;

    public ItemEffectProvider(FabricPackOutput output) {
        this.pathProvider = output.createPathProvider(PackOutput.Target.DATA_PACK, "item_effect");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> futures = new ArrayList<>();

        // Generate item effect for each quality level (except COMMON which has no effect)
        for (FishQuality.Quality quality : FishQuality.Quality.values()) {
            if (quality.ordinal() >= FishQuality.Quality.UNCOMMON.ordinal()) {
                futures.add(generateQualityEffect(cache, quality));
            }
        }

        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    /**
     * Generate an item effect JSON file for a specific quality level.
     */
    private CompletableFuture<?> generateQualityEffect(CachedOutput cache, FishQuality.Quality quality) {
        JsonObject effect = new JsonObject();

        // Set the texture path for this quality
        String texturePath = "fishtastic:textures/misc/" + quality.getSerializedName() + "_quality_glow.png";
        effect.addProperty("texture", texturePath);

        // Create conditions array
        JsonArray conditions = new JsonArray();

        // Add component value condition for this quality level
        JsonObject condition = new JsonObject();
        condition.addProperty("type", "component_value");
        condition.addProperty("component", "fishtastic:fish_quality");
        condition.addProperty("field", "quality");
        condition.addProperty("value", quality.getSerializedName());
        conditions.add(condition);

        effect.add("conditions", conditions);

        // Set priority based on quality level
        // Higher quality = higher priority so it takes precedence
        int priority = (quality.ordinal() + 1) * 10;
        effect.addProperty("priority", priority);

        // Generate the file path
        String fileName = quality.getSerializedName() + "_quality_glow";
        Path path = pathProvider.json(Fishtastic.id(fileName));

        // Save the file
        return DataProvider.saveStable(cache, effect, path);
    }

    @Override
    public String getName() {
        return "Fishtastic Item Effects";
    }
}

