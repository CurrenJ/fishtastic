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
    public static final TagKey<Item> UNLISTED_FISH = create("unlisted_fish");
    public static final TagKey<Item> SMALL_FISH = create("small_fish");
    public static final TagKey<Item> BIG_FISH = create("big_fish");
    public static final TagKey<Item> CALM_FISH = create("calm_fish");
    public static final TagKey<Item> STEADY_FISH = create("steady_fish");
    public static final TagKey<Item> FRENZY_FISH = create("frenzy_fish");
    public static final TagKey<Item> COLOR_YELLOW = create("color_yellow");
    public static final TagKey<Item> ZONE_OCEAN = create("zone_ocean");
    public static final TagKey<Item> ZONE_DEEP_OCEAN = create("zone_deep_ocean");
    public static final TagKey<Item> ZONE_RIVER = create("zone_river");
    public static final TagKey<Item> ZONE_NETHER = create("zone_nether");
    public static final TagKey<Item> ZONE_CAVE = create("zone_cave");
    public static final TagKey<Item> ZONE_HIGH_ALTITUDE = create("zone_high_altitude");

    /** The bait-affinity groups a fish can belong to — used by bait tooltips and the encyclopedia's Types section. */
    public static final List<TagKey<Item>> FISH_GROUPS = List.of(
            SMALL_FISH, BIG_FISH, CALM_FISH, STEADY_FISH, FRENZY_FISH, EXOTIC_FISH);

    private static TagKey<Item> create(String name) {
        return TagKey.create(Registries.ITEM, Fishtastic.id(name));
    }
}
