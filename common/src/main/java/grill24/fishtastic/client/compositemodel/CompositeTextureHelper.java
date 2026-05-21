package grill24.fishtastic.client.compositemodel;

import grill24.fishtastic.Fishtastic;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Shared texture-resolution and slot-override utilities used by both platform baked model classes.
 */
public final class CompositeTextureHelper {

    private static final String MISSING_MODEL_DEBUG_NAME = "minecraft:builtin/missing";
    private static final String[] TEXTURE_SLOT_PRIORITY = {"all", "top", "side", "front", "end", "particle"};

    private CompositeTextureHelper() {}

    /**
     * Walks {@code modelLocations} in priority order and returns the first usable {@link Material}
     * found in the model's texture slots.
     *
     * <p>Tries {@code "all"} first (cube_all style), then common multi-texture slot names.
     * Skips models that resolve to the missing-model placeholder.
     */
    @Nullable
    public static Material resolveBlockTexture(Block block, ModelBaker baker, List<Identifier> modelLocations) {
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);

        for (Identifier location : modelLocations) {
            try {
                ResolvedModel blockModel = baker.getModel(location);

                if (MISSING_MODEL_DEBUG_NAME.equals(blockModel.debugName())) {
                    continue;
                }

                TextureSlots slots = blockModel.getTopTextureSlots();

                for (String slotName : TEXTURE_SLOT_PRIORITY) {
                    Material mat = slots.getMaterial(slotName);
                    if (mat != null) return mat;
                }

                Fishtastic.LOGGER.warn("[CompositeTextureHelper] block={} location={} has no usable texture slot!", blockId, location);
            } catch (Exception e) {
                Fishtastic.LOGGER.debug("[CompositeTextureHelper] could not resolve model {} for block {}: {}", location, blockId, e.getMessage());
            }
        }

        Fishtastic.LOGGER.warn("[CompositeTextureHelper] block={} — no texture found across {} location(s)", blockId, modelLocations.size());
        return null;
    }

    /**
     * Creates a {@link TextureSlots} that overrides {@code "all"} and {@code "particle"} with
     * {@code texture}, while chaining through {@code baseModel}'s full texture hierarchy as a
     * fallback for any other slots the geometry may reference.
     *
     * <p>Used by composite block models to retexture a geometry template with a runtime block's texture.
     */
    public static TextureSlots overrideAllTexture(Material texture, ResolvedModel baseModel) {
        TextureSlots.Data.Builder builder = new TextureSlots.Data.Builder();
        builder.addTexture("all", texture);
        builder.addTexture("particle", texture);

        TextureSlots.Resolver resolver = new TextureSlots.Resolver().addFirst(builder.build());

        for (ResolvedModel m = baseModel; m != null; m = m.parent()) {
            resolver.addLast(m.wrapped().textureSlots());
        }

        return resolver.resolve(() -> "composite_override");
    }
}
