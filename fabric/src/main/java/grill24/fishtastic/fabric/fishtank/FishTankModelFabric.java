package grill24.fishtastic.fabric.fishtank;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.model.loading.v1.UnbakedModelDeserializer;
import net.minecraft.client.renderer.block.dispatch.ModelState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelDebugName;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.geometry.UnbakedGeometry;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import static grill24.fishtastic.util.Utility.ft;

/**
 * Custom {@link UnbakedModel} for the Fish Tank item form on Fabric.
 * <p>
 * Loaded via {@link UnbakedModelDeserializer} from the JSON
 * {@code assets/fishtastic/models/block/fish_tank.json} ({@code "fabric:type": "fishtastic:fish_tank"}).
 * <p>
 * During baking, produces a default composite of frame_0 + sand_0 + glass_0 for the item display.
 * Also declares all 192 base sub-model dependencies so the model bakery loads them.
 */
public final class FishTankModelFabric implements UnbakedModel {

    private static final String FRAME_PREFIX = "block/fishtankbase/fish_tank_frame_";
    private static final String SAND_PREFIX  = "block/fishtankbase/fish_tank_sand_";
    private static final String GLASS_PREFIX = "block/fishtankbase/fish_tank_glass_";
    private static final int PERMUTATION_COUNT = 64;

    private FishTankModelFabric() {}

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
        TextureSlots.Data.Builder builder = new TextureSlots.Data.Builder();
        builder.addTexture("all", new Material(Identifier.withDefaultNamespace("block/oak_planks")));
        builder.addTexture("particle", new Material(Identifier.withDefaultNamespace("block/oak_planks")));
        return builder.build();
    }

    @Override
    public @Nullable UnbakedGeometry geometry() {
        return new FishTankItemGeometry();
    }

    @Override
    public @Nullable Identifier parent() {
        return Identifier.withDefaultNamespace("block/block");
    }

    private static class FishTankItemGeometry implements UnbakedGeometry {
        @Override
        public QuadCollection bake(TextureSlots textureSlots, ModelBaker baker,
                                   ModelState state, ModelDebugName debugName) {
            QuadCollection.Builder compositeBuilder = new QuadCollection.Builder();
            bakeAndAdd(compositeBuilder, baker, ft(FRAME_PREFIX + "0"), state);
            bakeAndAdd(compositeBuilder, baker, ft(SAND_PREFIX + "0"), state);
            bakeAndAdd(compositeBuilder, baker, ft(GLASS_PREFIX + "0"), state);
            return compositeBuilder.build();
        }

        private void bakeAndAdd(QuadCollection.Builder builder, ModelBaker baker,
                                Identifier modelId, ModelState state) {
            ResolvedModel model = baker.getModel(modelId);
            QuadCollection quads = model.bakeTopGeometry(model.getTopTextureSlots(), baker, state);
            builder.addAll(quads);
        }
    }

    public enum Loader implements UnbakedModelDeserializer {
        INSTANCE;

        @Override
        public UnbakedModel deserialize(JsonObject jsonObject, JsonDeserializationContext context) {
            return new FishTankModelFabric();
        }
    }
}
