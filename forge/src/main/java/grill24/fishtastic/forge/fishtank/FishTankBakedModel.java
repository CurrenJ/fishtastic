package grill24.fishtastic.forge.fishtank;

import grill24.fishtastic.client.compositemodel.FishTankGeometry;
import grill24.fishtastic.client.compositemodel.TankCosmeticMesh;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.model.IDynamicBakedModel;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runtime model for the Fish Tank on Forge 47: composites the frame, sand and glass
 * layers for the block entity's {@link FishTankCompositeModelData} (read from {@link ModelData}),
 * through the shared {@link FishTankGeometry}.
 *
 * <p>Each layer goes to its material block's own chunk layer (glass is usually translucent,
 * frame and sand solid).
 */
public class FishTankBakedModel implements IDynamicBakedModel {
    private final FishTankGeometry geometry;
    private final ItemOverrides itemOverrides;
    private final Map<Block, RenderType> chunkLayers = new ConcurrentHashMap<>();

    FishTankBakedModel(FishTankGeometry geometry) {
        this.geometry = geometry;
        // A fresh bake means fresh models and sprites: drop cosmetic quads baked from the old ones.
        TankCosmeticMesh.clearCache();
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
        if (side == null) {
            addCosmetics(extraData, renderType, quads);
        }
        return quads;
    }

    /**
     * The tank's static cosmetics, baked into the same chunk mesh as its body (see
     * {@link TankCosmeticMesh}): unculled, like 26.1.2's extra model parts, each in its own block's
     * chunk layer. The tinted ones come pre-coloured for this position from {@link #getModelData}
     * (their colour from the cosmetic's own tint sources, not the tank's).
     */
    private static void addCosmetics(ModelData extraData, @Nullable RenderType renderType, List<BakedQuad> out) {
        TankCosmeticMesh.Baked baked = TankCosmeticMesh.bake(data(extraData).cosmetics());
        for (TankCosmeticMesh.Quad quad : baked.untinted()) {
            if (renderType == null || quad.layer() == renderType) out.add(quad.quad());
        }
        if (baked.tinted().isEmpty()) return;
        List<BakedQuad> coloured = extraData.get(FishTankModelData.COSMETIC_TINTED_PROPERTY);
        for (int i = 0; i < baked.tinted().size(); i++) {
            TankCosmeticMesh.Quad quad = baked.tinted().get(i);
            if (renderType != null && quad.layer() != renderType) continue;
            out.add(coloured != null && i < coloured.size() ? coloured.get(i) : TankCosmeticMesh.withColor(quad.quad(), -1));
        }
    }

    /** Colours the tank's tinted cosmetic quads for this mesh (see {@link FishTankModelData#COSMETIC_TINTED_PROPERTY}). */
    @Override
    public @NotNull ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state,
                                           @NotNull ModelData modelData) {
        TankCosmeticMesh.Baked baked = TankCosmeticMesh.bake(data(modelData).cosmetics());
        if (baked.tinted().isEmpty()) return modelData;
        List<BakedQuad> coloured = new ArrayList<>(baked.tinted().size());
        for (TankCosmeticMesh.Quad quad : baked.tinted()) {
            coloured.add(TankCosmeticMesh.withColor(quad.quad(), TankCosmeticMesh.tintColor(quad, level, pos)));
        }
        return modelData.derive().with(FishTankModelData.COSMETIC_TINTED_PROPERTY, coloured).build();
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        List<RenderType> types = new ArrayList<>();
        for (FishTankGeometry.LayerQuads layer : geometry.composite(data(data)).layers()) {
            types.add(chunkLayer(layer.source()));
        }
        types.addAll(TankCosmeticMesh.bake(data(data).cosmetics()).layers());
        return ChunkRenderTypeSet.of(types);
    }

    @Override
    public boolean useAmbientOcclusion() {
        // The tank shell is assembled from many noOcclusion() blocks; vanilla AO compounds at the
        // internal seams and darkens large tanks. IForgeBakedModel's 1-/2-arg overloads both
        // delegate to this by default, so overriding just this covers every call site.
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
