package grill24.fishtastic;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Item tags for Fishtastic mod
 */
public class FishtasticItemTags {
    public static final TagKey<Item> FISHING_RODS = create("fishing_rods");
    public static final TagKey<Item> FISH = create("fish");
    public static final TagKey<Item> FISHING_BAIT = create("fishing_bait");

    private static TagKey<Item> create(String name) {
        return TagKey.create(Registries.ITEM, Fishtastic.id(name));
    }
}
