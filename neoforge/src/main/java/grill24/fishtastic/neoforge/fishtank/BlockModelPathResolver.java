package grill24.fishtastic.neoforge.fishtank;

import com.electronwill.nightconfig.core.Config;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.neoforge.FishtasticConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

/**
 * Resolves model locations for blocks, taking into account config overrides for non-standard paths.
 */
public class BlockModelPathResolver {

    /**
     * Get all possible model locations for a block.
     * Returns a list with the highest-priority location first: blockstate redirect, then any
     * config overrides, then the standard path as a final fallback.
     */
    public static List<Identifier> getModelLocations(Block block) {
        List<Identifier> locations = new ArrayList<>();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        Identifier standardPath = blockId.withPrefix("block/");

        // Check blockstate redirect first (automatic, built from blockstate JSON scanning).
        Identifier redirect = BlockstateRedirectRegistry.getRedirect(standardPath);
        if (redirect != null) {
            locations.add(redirect);
        }

        // Then check config overrides (manual escape hatch for edge cases).
        locations.addAll(getOverrideLocations(block, blockId));

        // Always add the standard location as a final fallback.
        locations.add(standardPath);

        return locations;
    }

    /**
     * Get override locations from config for a specific block.
     */
    private static List<Identifier> getOverrideLocations(Block block, Identifier blockId) {
        List<Identifier> overrides = new ArrayList<>();

        for (Config entry : FishtasticConfig.STARTUP.blockModelPathOverrides.get()) {
            if (entry.isEmpty()) {
                continue;
            }

            String modelPath = entry.get("modelPath");
            if (modelPath == null || modelPath.isEmpty()) {
                continue;
            }

            // Check if this override applies to the block
            if (doesOverrideApply(entry, block, blockId)) {
                // Replace placeholder with block name
                String path = modelPath.replace("{name}", blockId.getPath());
                try {
                    Identifier location = Identifier.parse(path);
                    overrides.add(location);
                    Fishtastic.LOGGER.debug("Found model path override for {}: {}", blockId, location);
                } catch (Exception e) {
                    Fishtastic.LOGGER.error("Invalid model path in config for block {}: {}", blockId, modelPath, e);
                }
            }
        }

        return overrides;
    }

    /**
     * Check if a config override entry applies to the given block.
     */
    private static boolean doesOverrideApply(Config entry, Block block, Identifier blockId) {
        // Check pattern matching
        String pattern = entry.get("pattern");
        if (pattern != null && !pattern.isEmpty()) {
            if (matchesPattern(blockId, pattern)) {
                return true;
            }
        }

        // Check blocks (can be a tag reference or a single block ID)
        String blocks = entry.get("blocks");
        if (blocks != null && !blocks.isEmpty()) {
            if (blocks.startsWith("#")) {
                // Tag reference
                try {
                    Identifier tagId = Identifier.parse(blocks.substring(1));
                    TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagId);
                    return StreamSupport.stream(BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey).spliterator(), false)
                            .anyMatch(holder -> holder.value() == block);
                } catch (Exception e) {
                    Fishtastic.LOGGER.error("Invalid tag reference in config: {}", blocks, e);
                }
            } else {
                // Single block ID
                try {
                    Identifier targetId = Identifier.parse(blocks);
                    return blockId.equals(targetId);
                } catch (Exception e) {
                    Fishtastic.LOGGER.error("Invalid block ID in config: {}", blocks, e);
                }
            }
        }

        return false;
    }

    /**
     * Check if a block ID matches a wildcard pattern.
     * Supports * as a wildcard character.
     */
    private static boolean matchesPattern(Identifier blockId, String patternStr) {
        try {
            // Convert the pattern to a regex
            // Escape regex special characters except *
            String regex = patternStr
                    .replace(".", "\\.")
                    .replace("*", ".*");

            Pattern pattern = Pattern.compile(regex);
            return pattern.matcher(blockId.toString()).matches();
        } catch (Exception e) {
            Fishtastic.LOGGER.error("Invalid pattern in config: {}", patternStr, e);
            return false;
        }
    }
}
