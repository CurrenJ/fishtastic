package grill24;

import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.data.Temperament;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.FishTankFrameType;
import grill24.fishtastic.itemeffect.ItemEffect;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import static grill24.fishtastic.util.Utility.ft;

public class FishtasticRegistries {
    public static final ResourceKey<Registry<FishTankFrameType>> FISH_TANK_FRAME_TYPE_REGISTRY_KEY = ResourceKey.createRegistryKey(ft("fish_tank_frame_type"));
    public static final ResourceKey<Registry<ItemEffect>> ITEM_EFFECT_REGISTRY_KEY = ResourceKey.createRegistryKey(ft("item_effect"));
    public static final ResourceKey<Registry<FishProfile>> FISH_PROFILE_REGISTRY_KEY = ResourceKey.createRegistryKey(ft("fish_profile"));
    public static final ResourceKey<Registry<FishEncyclopediaEntry>> FISH_ENCYCLOPEDIA_ENTRY_REGISTRY_KEY = ResourceKey.createRegistryKey(ft("fish_encyclopedia_entry"));
    public static final ResourceKey<Registry<Temperament>> TEMPERAMENT_REGISTRY_KEY = ResourceKey.createRegistryKey(ft("temperament"));
    public static final ResourceKey<Registry<Quest>> QUEST_REGISTRY_KEY = ResourceKey.createRegistryKey(ft("quest"));
    public static final ResourceKey<Registry<ShopEntry>> SHOP_ENTRY_REGISTRY_KEY = ResourceKey.createRegistryKey(ft("shop_entry"));
    public static final ResourceKey<Registry<CosmeticStructure>> COSMETIC_STRUCTURE_REGISTRY_KEY = ResourceKey.createRegistryKey(ft("cosmetic_structure"));
}
