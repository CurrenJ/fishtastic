package grill24.fishtastic.neoforge.fishtank;

import grill24.fishtastic.client.compositemodel.FishTankGeometry;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime model for the Fish Tank on NeoForge 1.21.1: composites the frame, sand and glass
 * layers for the block entity's {@link FishTankCompositeModelData} (read from {@link ModelData}),
 * through the shared {@link FishTankGeometry}.
 *
 * <p>Each layer goes to its material block's own chunk layer (glass is usually translucent,
 * frame and sand solid). 26.1.2 derives the same from its quads' material flags.
 */
public class FishTankBakedModel implements IDynamicBakedModel {
    private final FishTankGeometry geometry;
    private final ItemOverrides itemOverrides;
    private final Map<Block, RenderType> chunkLayers = new ConcurrentHashMap<>();

    FishTankBakedModel(FishTankGeometry geometry) {
        this.geometry = geometry;
        this.itemOverrides = new FishTankItemModel(this);
    }

    FishTankGeometry geometry() {
        return geometry;
    }

    private static FishTankCompositeModelData data(ModelData modelData) {
        FishTankCompositeModelData data = modelData.get(FishTankModelData.DATA_PROPERTY);
        return data != null ? data : FishTankCompositeModelData.DEFAULT;
    }

    /** The chunk layer {@code block} itself renders in, from its own model's render types. */
    RenderType chunkLayer(Block block) {
        return chunkLayers.computeIfAbsent(block, b -> {
            BlockState state = b.defaultBlockState();
            ChunkRenderTypeSet types = Minecraft.getInstance().getBlockRenderer().getBlockModel(state)
                    .getRenderTypes(state, RandomSource.create(42L), ModelData.EMPTY);
            if (types.contains(RenderType.translucent())) return RenderType.translucent();
            if (types.contains(RenderType.cutoutMipped())) return RenderType.cutoutMipped();
            if (types.contains(RenderType.cutout())) return RenderType.cutout();
            return RenderType.solid();
        });
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand,
                                    ModelData extraData, @Nullable RenderType renderType) {
        FishTankGeometry.Composite composite = geometry.composite(data(extraData));
        List<BakedQuad> quads = new ArrayList<>();
        for (FishTankGeometry.LayerQuads layer : composite.layers()) {
            if (renderType == null || chunkLayer(layer.source()) == renderType) {
                quads.addAll(layer.get(side));
            }
        }
        return quads;
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        List<RenderType> types = new ArrayList<>();
        for (FishTankGeometry.LayerQuads layer : geometry.composite(data(data)).layers()) {
            types.add(chunkLayer(layer.source()));
        }
        return ChunkRenderTypeSet.of(types);
    }

    @Override
    public TriState useAmbientOcclusion(BlockState state, ModelData data, RenderType renderType) {
        // The tank shell is assembled from many noOcclusion() blocks; vanilla AO compounds at the
        // internal seams and darkens large tanks (26.1.2 disables it on the model part too).
        return TriState.FALSE;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean isGui3d() {
        return true;
    }

    @Override
    public boolean usesBlockLight() {
        return true;
    }

    @Override
    public boolean isCustomRenderer() {
        return false;
    }

    @Override
    public TextureAtlasSprite getParticleIcon() {
        return geometry.composite(null).particle();
    }

    @Override
    public TextureAtlasSprite getParticleIcon(ModelData data) {
        return geometry.composite(data(data)).particle();
    }

    @Override
    public ItemTransforms getTransforms() {
        return geometry.blockItemTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return itemOverrides;
    }
}
