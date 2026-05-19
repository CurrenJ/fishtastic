package grill24.fishtastic.fabric.fishtank;

import grill24.fishtastic.client.util.BlockstateModelScanner;
import net.fabricmc.fabric.api.client.model.loading.v1.PreparableModelLoadingPlugin;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Preparable model loading plugin that scans all blockstate JSONs at resource-reload time and
 * builds a redirect map from each block's standard model path (namespace:block/name) to the
 * actual model path declared in its blockstate JSON.
 *
 * <p>Runs off-thread during the prepare phase, before model baking, so the map is ready when
 * {@link BlockModelPathResolverFabric} is first consulted during baking.
 */
public final class BlockstateModelRedirectPlugin {
    private BlockstateModelRedirectPlugin() {}

    public static final PreparableModelLoadingPlugin.DataLoader<Map<Identifier, Identifier>> LOADER =
            (resourceReloaderStore, executor) -> CompletableFuture.supplyAsync(
                    () -> BlockstateModelScanner.buildRedirectMap(resourceReloaderStore.resourceManager()), executor);

    public static final PreparableModelLoadingPlugin<Map<Identifier, Identifier>> PLUGIN =
            (data, pluginContext) -> BlockstateRedirectRegistry.update(data);
}
