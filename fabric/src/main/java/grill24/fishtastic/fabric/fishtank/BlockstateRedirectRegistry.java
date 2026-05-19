package grill24.fishtastic.fabric.fishtank;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Holds a pre-built map from a block's standard model path (namespace:block/name) to the
 * actual model path declared in its blockstate JSON, populated each resource reload by
 * {@link BlockstateModelRedirectPlugin} before model baking begins.
 */
public final class BlockstateRedirectRegistry {
    private static volatile Map<Identifier, Identifier> redirects = Map.of();

    private BlockstateRedirectRegistry() {}

    static void update(Map<Identifier, Identifier> newRedirects) {
        redirects = Map.copyOf(newRedirects);
    }

    @Nullable
    public static Identifier getRedirect(Identifier standardModelPath) {
        return redirects.get(standardModelPath);
    }
}
