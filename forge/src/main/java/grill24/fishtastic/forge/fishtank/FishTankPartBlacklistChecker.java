package grill24.fishtastic.forge.fishtank;

import com.electronwill.nightconfig.core.Config;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.forge.FishtasticConfig;
import grill24.fishtastic.util.Ids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

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

    public static boolean isBlacklisted(Block block, FishTankPart part) {
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
        for (Config entry : FishtasticConfig.STARTUP.fishTankPartBlacklists.get()) {
            if (entry.isEmpty() || !entryAppliesToPart(entry, part)) continue;
            Object blocksObj = entry.get("blocks");
            if (blocksObj == null) continue;
            List<String> blocksList = new ArrayList<>();
            if (blocksObj instanceof String s) {
                blocksList.add(s);
            } else if (blocksObj instanceof List<?> list) {
                for (Object item : list) if (item instanceof String s) blocksList.add(s);
            }
            for (String pattern : blocksList) {
                if (pattern == null || pattern.isEmpty()) continue;
                if (pattern.startsWith("#")) {
                    try {
                        ResourceLocation tagId = Ids.parse(pattern.substring(1));
                        TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagId);
                        boolean inTag = StreamSupport.stream(BuiltInRegistries.BLOCK.getTagOrEmpty(tagKey).spliterator(), false)
                                .anyMatch(holder -> holder.value() == block);
                        if (inTag) return true;
                    } catch (Exception e) {
                        Fishtastic.LOGGER.error("Invalid tag reference in fishTankPartBlacklists: {}", pattern, e);
                    }
                } else {
                    try {
                        if (blockId.equals(Ids.parse(pattern))) return true;
                    } catch (Exception e) {
                        Fishtastic.LOGGER.error("Invalid block ID in fishTankPartBlacklists: {}", pattern, e);
                    }
                }
            }
        }
        return false;
    }

    private static boolean entryAppliesToPart(Config entry, FishTankPart part) {
        String partName = part.getConfigName();
        Object partsObj = entry.get("parts");
        if (partsObj != null) {
            if (partsObj instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s && s.equalsIgnoreCase(partName)) return true;
                }
            }
            return false;
        }
        Object partObj = entry.get("part");
        return partObj instanceof String s && s.equalsIgnoreCase(partName);
    }

    public static String getBlacklistMessage(Block block, FishTankPart part) {
        return String.format("Block %s cannot be used for fish tank %s (blacklisted in config)",
                BuiltInRegistries.BLOCK.getKey(block), part.getConfigName());
    }
}
