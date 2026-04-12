package grill24.fishtastic.neoforge.fishtank;

import com.electronwill.nightconfig.core.Config;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.neoforge.FishtasticConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.*;
import java.util.stream.StreamSupport;

/**
 * Utility class to check if blocks are blacklisted for specific fish tank parts.
 */
public class FishTankPartBlacklistChecker {

    public enum FishTankPart {
        FRAME("frame"),
        GLASS("glass"),
        SAND("sand");

        private final String configName;

        FishTankPart(String configName) {
            this.configName = configName;
        }

        public String getConfigName() {
            return configName;
        }
    }

    /**
     * Check if a block is blacklisted for a specific fish tank part.
     */
    public static boolean isBlacklisted(Block block, FishTankPart part) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);

        for (Config entry : FishtasticConfig.STARTUP.fishTankPartBlacklists.get()) {
            if (entry.isEmpty()) {
                continue;
            }

            // Check if this entry applies to the specified part
            if (!entryAppliesToPart(entry, part)) {
                continue;
            }

            // Get the blocks list
            Object blocksObj = entry.get("blocks");
            if (blocksObj == null) {
                continue;
            }

            // Handle both single string and list of strings
            List<String> blocksList = new ArrayList<>();
            if (blocksObj instanceof String) {
                blocksList.add((String) blocksObj);
            } else if (blocksObj instanceof List<?>) {
                for (Object item : (List<?>) blocksObj) {
                    if (item instanceof String) {
                        blocksList.add((String) item);
                    }
                }
            }

            // Check each pattern/block in the list
            for (String pattern : blocksList) {
                if (pattern == null || pattern.isEmpty()) {
                    continue;
                }

                if (pattern.startsWith("#")) {
                    // Tag reference
                    try {
                        Identifier tagId = Identifier.parse(pattern.substring(1));
                        TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagId);
                        boolean inTag = StreamSupport.stream(BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey).spliterator(), false)
                                .anyMatch(holder -> holder.value() == block);
                        if (inTag) {
                            Fishtastic.LOGGER.debug("Block {} is blacklisted for {} (matches tag {})",
                                    blockId, part.getConfigName(), tagId);
                            return true;
                        }
                    } catch (Exception e) {
                        Fishtastic.LOGGER.error("Invalid tag reference in fishTankPartBlacklists: {}", pattern, e);
                    }
                } else {
                    // Direct block ID
                    try {
                        Identifier targetId = Identifier.parse(pattern);
                        if (blockId.equals(targetId)) {
                            Fishtastic.LOGGER.debug("Block {} is blacklisted for {}", blockId, part.getConfigName());
                            return true;
                        }
                    } catch (Exception e) {
                        Fishtastic.LOGGER.error("Invalid block ID in fishTankPartBlacklists: {}", pattern, e);
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if a config entry applies to the specified part.
     * Supports both "part" (single) and "parts" (list) fields for backward compatibility.
     */
    private static boolean entryAppliesToPart(Config entry, FishTankPart part) {
        String partName = part.getConfigName();

        // Check for "parts" field (new format - list of parts)
        Object partsObj = entry.get("parts");
        if (partsObj != null) {
            if (partsObj instanceof List<?>) {
                for (Object item : (List<?>) partsObj) {
                    if (item instanceof String && ((String) item).equalsIgnoreCase(partName)) {
                        return true;
                    }
                }
            }
            return false; // If "parts" is specified but doesn't contain our part, don't apply
        }

        // Check for "part" field (old format - single part)
        Object partObj = entry.get("part");
        if (partObj instanceof String) {
            return ((String) partObj).equalsIgnoreCase(partName);
        }

        return false; // No valid part specification found
    }

    /**
     * Get a user-friendly message for why a block is blacklisted.
     */
    public static String getBlacklistMessage(Block block, FishTankPart part) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        return String.format("Block %s cannot be used for fish tank %s (blacklisted in config)",
                blockId, part.getConfigName());
    }
}
