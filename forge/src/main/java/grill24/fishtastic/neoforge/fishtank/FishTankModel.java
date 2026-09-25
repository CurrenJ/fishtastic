package grill24.fishtastic.neoforge.fishtank;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import grill24.fishtastic.client.compositemodel.FishTankGeometry;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;

import java.util.function.Function;

/**
 * The fish tank's model geometry on NeoForge 1.21.1, referenced from {@code models/block/fish_tank.json}
 * as {@code "loader": "fishtastic:fish_tank"} and registered in {@code ModelEvent.RegisterGeometryLoaders}.
 * The tank item's model has that block model as its parent, so it bakes through here too.
 *
 * <p>26.1.2 has a blockstate-level custom model ({@code FishTankBlockStateModel}) instead; 1.21.1
 * only has model-level custom geometry. All loading happens in {@link FishTankGeometry#bake}.
 */
public final class FishTankModel implements IUnbakedGeometry<FishTankModel> {
    private FishTankModel() {}

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides) {
        FishTankGeometry geometry = FishTankGeometry.bake(baker, spriteGetter, BlockModelPathResolver::getModelLocations);
        return new FishTankBakedModel(geometry);
    }

    public enum Loader implements IGeometryLoader<FishTankModel> {
        INSTANCE;

        @Override
        public FishTankModel read(JsonObject json, JsonDeserializationContext context) {
            return new FishTankModel();
        }
    }
}
