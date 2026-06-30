package grill24.fishtastic;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Item tags for Fishtastic mod
 */
public class FishtasticItemTags {
    public static final TagKey<Item> FISHING_RODS = create("fishing_rods");
    public static final TagKey<Item> FISH = create("fish");
    public static final TagKey<Item> TRASH = create("trash");
    public static final TagKey<Item> FISHING_BAIT = create("fishing_bait");
    public static final TagKey<Item> FISHING_HOOKS = create("fishing_hooks");
    public static final TagKey<Item> FISHING_CHARMS = create("fishing_charms");
    public static final TagKey<Item> EXOTIC_FISH = create("exotic_fish");
    public static final TagKey<Item> FRESHWATER_FISH = create("freshwater_fish");
    public static final TagKey<Item> OCEAN_FISH = create("ocean_fish");
    public static final TagKey<Item> PREDATOR_FISH = create("predator_fish");
    public static final TagKey<Item> DEEP_SEA_FISH = create("deep_sea_fish");

    /** The bait-affinity groups a fish can belong to — used by bait tooltips and the encyclopedia's Types section. */
    public static final List<TagKey<Item>> FISH_GROUPS = List.of(
            FRESHWATER_FISH, OCEAN_FISH, PREDATOR_FISH, EXOTIC_FISH, DEEP_SEA_FISH);

    private static TagKey<Item> create(String name) {
        return TagKey.create(Registries.ITEM, Fishtastic.id(name));
    }
}
