package grill24.fishtastic.client.compositemodel;

import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Holds a pre-built map from a block's standard model path ({@code namespace:block/name}) to the
 * actual model path declared in its blockstate JSON, populated each resource reload by the
 * platform-specific adapter before model baking begins.
 *
 * <p>On 1.21.1 both loaders populate it from {@link FishTankGeometry#bake}, at the start of the
 * tank model's bake (26.1.2 uses a Fabric model-loading plugin and a NeoForge reload listener).
 */
public final class BlockstateRedirectRegistry {
    private static volatile Map<ResourceLocation, ResourceLocation> redirects = Map.of();

    private BlockstateRedirectRegistry() {}

    public static void update(Map<ResourceLocation, ResourceLocation> newRedirects) {
        redirects = Map.copyOf(newRedirects);
    }

    @Nullable
    public static ResourceLocation getRedirect(ResourceLocation standardModelPath) {
        return redirects.get(standardModelPath);
    }
}
