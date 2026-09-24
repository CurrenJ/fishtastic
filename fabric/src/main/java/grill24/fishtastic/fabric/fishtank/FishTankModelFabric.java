package grill24.fishtastic.fabric.fishtank;

import grill24.fishtastic.client.compositemodel.BlockModelPathResolver;
import grill24.fishtastic.client.compositemodel.FishTankGeometry;
import grill24.fishtastic.util.Ids;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.client.resources.model.UnbakedModel;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/**
 * The fish tank's unbaked model on Fabric 1.21.1 (new code: 26.1.2's Fabric tank is a
 * {@code CustomUnbakedBlockStateModel}, which this Fabric API doesn't have). {@link #PLUGIN}
 * resolves both {@code fishtastic:block/fish_tank} (the blockstate's model) and
 * {@code fishtastic:item/fish_tank} to it: the item can't simply name the block model as its
 * parent, because a vanilla {@code BlockModel}'s parent must be a {@code BlockModel}.
 * All loading happens in {@link FishTankGeometry#bake}.
 */
public final class FishTankModelFabric implements UnbakedModel {
    private static final ResourceLocation BLOCK_MODEL = Ids.of("fishtastic", "block/fish_tank");
    private static final ResourceLocation ITEM_MODEL = Ids.of("fishtastic", "item/fish_tank");

    public static final ModelLoadingPlugin PLUGIN = context -> {
        FishTankModelFabric model = new FishTankModelFabric();
        context.resolveModel().register(resolver ->
                resolver.id().equals(BLOCK_MODEL) || resolver.id().equals(ITEM_MODEL) ? model : null);
    };

    @Override
    public Collection<ResourceLocation> getDependencies() {
        // Fragments are fetched from the baker in bake(); nothing to preload.
        return List.of();
    }

    @Override
    public void resolveParents(Function<ResourceLocation, UnbakedModel> modelGetter) {
    }

    @Override
    public BakedModel bake(ModelBaker baker, Function<Material, TextureAtlasSprite> spriteGetter, ModelState modelState) {
        return new FishTankBakedModelFabric(FishTankGeometry.bake(baker, spriteGetter, BlockModelPathResolver::getModelLocations));
    }
}
