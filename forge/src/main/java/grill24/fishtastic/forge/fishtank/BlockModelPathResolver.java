package grill24.fishtastic.forge.fishtank;

import com.electronwill.nightconfig.core.Config;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.client.compositemodel.BlockstateRedirectRegistry;
import grill24.fishtastic.forge.FishtasticConfig;
import grill24.fishtastic.util.Ids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

/**
 * Resolves model locations for blocks, taking into account config overrides for non-standard paths.
 * Priority: blockstate redirect -> config overrides -> standard path.
 */
public class BlockModelPathResolver {
    public static List<ResourceLocation> getModelLocations(Block block) {
        List<ResourceLocation> locations = new ArrayList<>();
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        ResourceLocation standardPath = blockId.withPrefix("block/");

        ResourceLocation redirect = BlockstateRedirectRegistry.getRedirect(standardPath);
        if (redirect != null) locations.add(redirect);

        locations.addAll(getOverrideLocations(block, blockId));
        locations.add(standardPath);
        return locations;
    }

    private static List<ResourceLocation> getOverrideLocations(Block block, ResourceLocation blockId) {
        List<ResourceLocation> overrides = new ArrayList<>();
        for (Config entry : FishtasticConfig.STARTUP.blockModelPathOverrides.get()) {
            if (entry.isEmpty()) continue;
            String modelPath = entry.get("modelPath");
            if (modelPath == null || modelPath.isEmpty()) continue;
            if (doesOverrideApply(entry, block, blockId)) {
                String path = modelPath.replace("{name}", blockId.getPath());
                try {
                    overrides.add(Ids.parse(path));
                } catch (Exception e) {
                    Fishtastic.LOGGER.error("Invalid model path in config for block {}: {}", blockId, modelPath, e);
                }
            }
        }
        return overrides;
    }

    private static boolean doesOverrideApply(Config entry, Block block, ResourceLocation blockId) {
        String pattern = entry.get("pattern");
        if (pattern != null && !pattern.isEmpty() && matchesPattern(blockId, pattern)) return true;

        String blocks = entry.get("blocks");
        if (blocks != null && !blocks.isEmpty()) {
            if (blocks.startsWith("#")) {
                try {
                    ResourceLocation tagId = Ids.parse(blocks.substring(1));
                    TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagId);
                    return StreamSupport.stream(BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey).spliterator(), false)
                            .anyMatch(holder -> holder.value() == block);
                } catch (Exception e) {
                    Fishtastic.LOGGER.error("Invalid tag reference in config: {}", blocks, e);
                }
            } else {
                try {
                    return blockId.equals(Ids.parse(blocks));
                } catch (Exception e) {
                    Fishtastic.LOGGER.error("Invalid block ID in config: {}", blocks, e);
                }
            }
        }
        return false;
    }

    private static boolean matchesPattern(ResourceLocation blockId, String patternStr) {
        try {
            String regex = patternStr.replace(".", "\\.").replace("*", ".*");
            return Pattern.compile(regex).matcher(blockId.toString()).matches();
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Invalid pattern in config: {}", patternStr, e);
            return false;
        }
    }
}
