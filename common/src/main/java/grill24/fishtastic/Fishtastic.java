package grill24.fishtastic;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public final class Fishtastic {
    public static final String MOD_ID = "fishtastic";
    public static Logger LOGGER = LogUtils.getLogger();

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
