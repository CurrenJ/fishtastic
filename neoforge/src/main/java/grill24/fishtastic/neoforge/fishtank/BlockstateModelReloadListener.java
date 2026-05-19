package grill24.fishtastic.neoforge.fishtank;

import grill24.fishtastic.client.util.BlockstateModelScanner;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Client-side reload listener that scans all blockstate JSONs and builds a redirect map from
 * each block's standard model path (namespace:block/name) to the actual model path declared in
 * its blockstate JSON.
 *
 * <p>Registered via {@link net.neoforged.neoforge.client.event.AddClientReloadListenersEvent}
 * with a dependency on {@link net.neoforged.neoforge.client.resources.VanillaClientListeners#MODELS},
 * ensuring the map is populated before model baking begins.
 */
public final class BlockstateModelReloadListener implements PreparableReloadListener {
    public static final BlockstateModelReloadListener INSTANCE = new BlockstateModelReloadListener();

    private BlockstateModelReloadListener() {}

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                          Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture
                .supplyAsync(() -> BlockstateModelScanner.buildRedirectMap(resourceManager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(BlockstateRedirectRegistry::update, gameExecutor);
    }
}
