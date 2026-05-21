package grill24.fishtastic.client.compositemodel;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Map;

/**
 * Holds a pre-built map from a block's standard model path ({@code namespace:block/name}) to the
 * actual model path declared in its blockstate JSON, populated each resource reload by the
 * platform-specific adapter before model baking begins.
 *
 * <p>Fabric: populated by {@code BlockstateModelRedirectPlugin} via {@code PreparableModelLoadingPlugin}.
 * <p>NeoForge: populated by {@code BlockstateModelReloadListener} via {@code AddClientReloadListenersEvent}.
 */
public final class BlockstateRedirectRegistry {
    private static volatile Map<Identifier, Identifier> redirects = Map.of();

    private BlockstateRedirectRegistry() {}

    public static void update(Map<Identifier, Identifier> newRedirects) {
        redirects = Map.copyOf(newRedirects);
    }

    @Nullable
    public static Identifier getRedirect(Identifier standardModelPath) {
        return redirects.get(standardModelPath);
    }
}
