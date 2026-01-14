package grill24;

import grill24.fishtastic.fishtank.FishTankFrameType;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;

import static grill24.fishtastic.util.Utility.ft;

public class FishtasticRegistries {
    public static final ResourceKey<Registry<FishTankFrameType>> FISH_TANK_FRAME_TYPE_REGISTRY_KEY = ResourceKey.createRegistryKey(ft("fish_tank_frame_type"));
}
