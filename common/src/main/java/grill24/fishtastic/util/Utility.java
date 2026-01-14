package grill24.fishtastic.util;

import grill24.fishtastic.Fishtastic;
import net.minecraft.resources.ResourceLocation;

public class Utility {
    public static ResourceLocation ft(String id) {
        return ResourceLocation.fromNamespaceAndPath(Fishtastic.MOD_ID, id);
    }
}
