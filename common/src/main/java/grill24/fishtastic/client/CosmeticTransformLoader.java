package grill24.fishtastic.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Client-side resource reload listener that loads cosmetic transforms from
 * {@code assets/<namespace>/cosmetic_transforms/<name>.json}.
 *
 * <p>Pack creators and other mods can ship their own files in any namespace.
 * Each file specifies a {@code "block"} field (the block registry ID) plus
 * optional transform fields; any field absent in the file uses its default value.
 */
public final class CosmeticTransformLoader implements PreparableReloadListener {

    public static final CosmeticTransformLoader INSTANCE = new CosmeticTransformLoader();
    public static final Identifier ID = Fishtastic.id("cosmetic_transforms");

    private static final FileToIdConverter LISTER = FileToIdConverter.json("cosmetic_transforms");
    private static final Gson GSON = new Gson();

    private static final Codec<Block> BLOCK_CODEC = Identifier.CODEC.flatXmap(
        id -> BuiltInRegistries.BLOCK.getOptional(id)
            .map(DataResult::success)
            .orElseGet(() -> DataResult.error(() -> "Unknown block: " + id)),
        block -> DataResult.success(BuiltInRegistries.BLOCK.getKey(block))
    );

    private record Entry(Block block, CosmeticTransforms.Transform transform) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BLOCK_CODEC.fieldOf("block").forGetter(Entry::block),
            CosmeticTransforms.Transform.MAP_CODEC.forGetter(Entry::transform)
        ).apply(instance, Entry::new));
    }

    private CosmeticTransformLoader() {}

    @Override
    public CompletableFuture<Void> reload(
            SharedState state,
            Executor backgroundExecutor,
            PreparationBarrier barrier,
            Executor gameExecutor) {
        return CompletableFuture
            .supplyAsync(() -> load(state.resourceManager()), backgroundExecutor)
            .thenCompose(barrier::wait)
            .thenAcceptAsync(CosmeticTransforms::replaceAll, gameExecutor);
    }

    private static Map<Block, CosmeticTransforms.Transform> load(ResourceManager manager) {
        Map<Block, CosmeticTransforms.Transform> result = new HashMap<>();
        LISTER.listMatchingResources(manager).forEach((fileId, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                JsonElement json = GSON.fromJson(reader, JsonElement.class);
                Entry.CODEC.parse(JsonOps.INSTANCE, json)
                    .resultOrPartial(err -> Fishtastic.LOGGER.warn("cosmetic_transforms {}: {}", fileId, err))
                    .ifPresent(entry -> result.put(entry.block(), entry.transform()));
            } catch (IOException e) {
                Fishtastic.LOGGER.error("cosmetic_transforms: failed to read {}", fileId, e);
            }
        });
        Fishtastic.LOGGER.info("Loaded {} cosmetic transform(s)", result.size());
        return result;
    }
}
