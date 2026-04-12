package grill24.fishtastic.neoforge.fishtank;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.model.UnbakedModelLoader;
import org.jspecify.annotations.Nullable;

import static grill24.fishtastic.util.Utility.ft;

/**
 * Custom {@link UnbakedModel} for the Fish Tank block item form.
 * <p>
 * Loaded via the {@code UnbakedModelLoader} from the JSON
 * {@code assets/fishtastic/models/block/fish_tank.json}  ({@code "loader": "fishtastic:fish_tank"}).
 * <p>
 * The item model JSON ({@code models/item/fish_tank.json}) parents this model,
 * which causes all 192 base sub-model dependencies to be loaded.
 * <p>
 * During baking, it produces a default composite of frame_0 + sand_0 + glass_0 using
 * their default textures (oak planks / sand / blue glass).
 */
public final class FishTankModel implements UnbakedModel {

    private static final String FRAME_PREFIX = "block/fishtankbase/fish_tank_frame_";
    private static final String SAND_PREFIX  = "block/fishtankbase/fish_tank_sand_";
    private static final String GLASS_PREFIX = "block/fishtankbase/fish_tank_glass_";
    private static final int PERMUTATION_COUNT = 64;

    private FishTankModel() {}

    // ── UnbakedModel ──────────────────────────────────────────────────────

    @Override
    public @Nullable Boolean ambientOcclusion() {
        return true;
    }

    @Override
    public UnbakedModel.@Nullable GuiLight guiLight() {
        return null;
    }

    @Override
    public @Nullable ItemTransforms transforms() {
        return null;
    }

    @Override
    public TextureSlots.Data textureSlots() {
        // Provide a default "all" texture so the model has a particle sprite.
        TextureSlots.Data.Builder builder = new TextureSlots.Data.Builder();
        builder.addTexture("all", new Material(Identifier.withDefaultNamespace("block/oak_planks")));
        builder.addTexture("particle", new Material(Identifier.withDefaultNamespace("block/oak_planks")));
        return builder.build();
    }

    @Override
    public @Nullable UnbakedGeometry geometry() {
        // Return the composite geometry that bakes all three default sub-models.
        return new FishTankItemGeometry();
    }

    @Override
    public @Nullable Identifier parent() {
        // Parent "block/block" gives standard block display transforms.
        return Identifier.withDefaultNamespace("block/block");
    }

    @Override
    public void resolveDependencies(Resolver resolver) {
        // Mark all 192 base models as dependencies so the model bakery loads them.
        // This is essential: the FishTankBlockStateModel also needs these models,
        // and the item model parents this model, so dependencies propagate.
        for (int i = 0; i < PERMUTATION_COUNT; i++) {
            resolver.markDependency(ft(FRAME_PREFIX + i));
            resolver.markDependency(ft(SAND_PREFIX + i));
            resolver.markDependency(ft(GLASS_PREFIX + i));
        }
    }

    // ── Item geometry (default composite) ─────────────────────────────────

    /**
     * {@link UnbakedGeometry} that bakes a default composite of frame_0 + sand_0 + glass_0.
     */
    private static class FishTankItemGeometry implements UnbakedGeometry {
        @Override
        public QuadCollection bake(TextureSlots textureSlots, ModelBaker baker,
                                   ModelState state, ModelDebugName debugName) {
            QuadCollection.Builder compositeBuilder = new QuadCollection.Builder();

            // Bake each default sub-model and merge.
            bakeAndAdd(compositeBuilder, baker, ft(FRAME_PREFIX + "0"), state);
            bakeAndAdd(compositeBuilder, baker, ft(SAND_PREFIX + "0"), state);
            bakeAndAdd(compositeBuilder, baker, ft(GLASS_PREFIX + "0"), state);

            return compositeBuilder.build();
        }

        private void bakeAndAdd(QuadCollection.Builder builder, ModelBaker baker,
                                Identifier modelId, ModelState state) {
            ResolvedModel model = baker.getModel(modelId);
            QuadCollection quads = model.bakeTopGeometry(
                    model.getTopTextureSlots(), baker, state);
            builder.addAll(quads);
        }
    }

    // ── Loader ────────────────────────────────────────────────────────────

    public enum Loader implements UnbakedModelLoader<FishTankModel> {
        INSTANCE;

        @Override
        public FishTankModel read(JsonObject jsonObject, JsonDeserializationContext deserializationContext) throws JsonParseException {
            return new FishTankModel();
        }
    }
}
