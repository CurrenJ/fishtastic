package grill24.fishtastic.forge.fishtank;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import grill24.fishtastic.client.compositemodel.FishTankGeometry;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.geometry.IGeometryBakingContext;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.client.model.geometry.IUnbakedGeometry;

import java.util.function.Function;

/**
 * The fish tank's model geometry on Forge 47, referenced from {@code models/block/fish_tank.json}
 * as {@code "loader": "fishtastic:fish_tank"} and registered in {@code ModelEvent.RegisterGeometryLoaders}.
 * The tank item's model has that block model as its parent, so it bakes through here too.
 *
 * <p>All loading happens in {@link FishTankGeometry#bake}.
 */
public final class FishTankModel implements IUnbakedGeometry<FishTankModel> {
    private FishTankModel() {}

    @Override
    public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter,
                           ModelState modelState, ItemOverrides overrides, ResourceLocation modelLocation) {
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
