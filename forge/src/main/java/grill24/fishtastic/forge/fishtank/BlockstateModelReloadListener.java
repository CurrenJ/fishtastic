package grill24.fishtastic.forge.fishtank;

import grill24.fishtastic.client.compositemodel.BlockstateModelScanner;
import grill24.fishtastic.client.compositemodel.BlockstateRedirectRegistry;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Client-side reload listener that scans all blockstate JSONs and builds a redirect map from each
 * block's standard model path (namespace:block/name) to the model path its blockstate declares,
 * as NeoForge's and Fabric's counterparts do.
 *
 * <p>Without it Forge only had the {@code blockModelPathOverrides} config patterns, which cover the
 * stained borderless and clear glass but not plain {@code borderless_glass} or {@code clear_glass}
 * (both under {@code block/glass/}), so a tank built with either fell back to the default model.
 *
 * <p>Forge's {@code RegisterClientReloadListenersEvent} has no ordering hook, so this runs after
 * model baking. That is enough: the tank models resolve material textures lazily when a tank is
 * meshed, and never cache a failed resolution.
 */
public final class BlockstateModelReloadListener implements PreparableReloadListener {
    public static final BlockstateModelReloadListener INSTANCE = new BlockstateModelReloadListener();

    private BlockstateModelReloadListener() {}

    @Override
    public CompletableFuture<Void> reload(PreparableReloadListener.SharedState sharedState, Executor backgroundExecutor,
                                          PreparableReloadListener.PreparationBarrier barrier, Executor gameExecutor) {
        return CompletableFuture
                .supplyAsync(() -> BlockstateModelScanner.buildRedirectMap(sharedState.resourceManager()), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(BlockstateRedirectRegistry::update, gameExecutor);
    }
}
