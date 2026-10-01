package grill24.fishtastic.neoforge.fishtank;

import grill24.fishtastic.client.compositemodel.FishTankGeometry;
import grill24.fishtastic.client.compositemodel.TankCosmeticMesh;
import grill24.fishtastic.client.renderer.TankInteriorLight;
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
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.model.IDynamicBakedModel;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.common.util.TriState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
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
    /** Interior quads re-made at {@link #litLevel}'s light, by the identity of the quad they were made from. */
    private final Map<BakedQuad, BakedQuad> litQuads = Collections.synchronizedMap(new IdentityHashMap<>());
    private volatile int litLevel = -1;

    FishTankBakedModel(FishTankGeometry geometry) {
        this.geometry = geometry;
        this.itemOverrides = new FishTankItemModel(this);
        // A fresh bake means fresh models and sprites: drop cosmetic quads baked from the old ones.
        TankCosmeticMesh.clearCache();
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
                // The frame keeps world light; the sand and glass are the interior (TankInteriorLight).
                if (layer.layer() == FishTankGeometry.Layer.FRAME) {
                    quads.addAll(layer.get(side));
                } else {
                    for (BakedQuad quad : layer.get(side)) quads.add(withLightEmission(quad));
                }
            }
        }
        if (side == null) {
            addCosmetics(extraData, renderType, quads);
        }
        return quads;
    }

    /**
     * The tank's static cosmetics, baked into the same chunk mesh as its body (see
     * {@link TankCosmeticMesh}), unculled and without AO like the body: the untinted quads as
     * baked, and the tinted ones re-made with their colour (from the cosmetic's own tint sources)
     * baked into the vertex colours, since the tank's tint sources aren't theirs. PORT-ONLY: 1.21.1's
     * {@code getQuads} has no level or position, so the tinted quads are coloured earlier, in
     * {@link #getModelData(BlockAndTintGetter, BlockPos, BlockState, ModelData)}, and each quad goes to
     * its cosmetic block's own chunk layer.
     */
    private void addCosmetics(ModelData extraData, @Nullable RenderType renderType, List<BakedQuad> quads) {
        TankCosmeticMesh.Baked baked = TankCosmeticMesh.bake(data(extraData).cosmetics());
        for (TankCosmeticMesh.Quad quad : baked.untinted()) {
            if (renderType == null || chunkLayer(quad.tintState().getBlock()) == renderType) quads.add(withLightEmission(quad.quad()));
        }
        List<TankCosmeticMesh.Quad> tinted = extraData.get(FishTankModelData.TINTED_COSMETICS_PROPERTY);
        if (tinted == null) return;
        for (TankCosmeticMesh.Quad quad : tinted) {
            if (renderType == null || chunkLayer(quad.tintState().getBlock()) == renderType) quads.add(quad.quad());
        }
    }

    /** Runs on the meshing thread with the section's level view: colours the tinted cosmetic quads for this position. */
    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        TankCosmeticMesh.Baked baked = TankCosmeticMesh.bake(data(modelData).cosmetics());
        if (baked.tinted().isEmpty()) return modelData;
        List<TankCosmeticMesh.Quad> tinted = new ArrayList<>(baked.tinted().size());
        for (TankCosmeticMesh.Quad quad : baked.tinted()) {
            int color = TankCosmeticMesh.tintColor(quad, level, pos);
            BakedQuad colored = TankCosmeticMesh.withBakedColor(quad.quad(), color);
            int interiorLight = TankInteriorLight.level();
            tinted.add(new TankCosmeticMesh.Quad(interiorLight > 0 ? withLightEmission(colored, interiorLight) : colored, quad.tintState()));
        }
        return modelData.derive().with(FishTankModelData.TINTED_COSMETICS_PROPERTY, List.copyOf(tinted)).build();
    }

    /**
     * {@code quad} drawn at no less than {@link TankInteriorLight}'s level, cached per source quad
     * (the composite and cosmetic caches hand out the same quads mesh after mesh).
     */
    private BakedQuad withLightEmission(BakedQuad quad) {
        int level = TankInteriorLight.level();
        if (level <= 0) return quad;
        if (litLevel != level || litQuads.size() > 65536) {
            litQuads.clear();
            litLevel = level;
        }
        return litQuads.computeIfAbsent(quad, q -> withLightEmission(q, level));
    }

    /**
     * {@code q} re-made with a baked block-light floor of {@code level}. PORT-ONLY mechanism: 26.1.2
     * uses the quad's light emission, which raises sky light too; NeoForge 1.21.1's patched
     * {@code putBulkData} takes the per-vertex maximum of world light and the lightmap baked into the
     * quad ({@code IVertexConsumerExtension#applyBakedLighting}), so this raises block light alone,
     * as Fabric does on both versions. Like emission, it never spreads into the world.
     */
    private static BakedQuad withLightEmission(BakedQuad q, int level) {
        int[] vertices = q.getVertices().clone();
        int minimum = TankInteriorLight.minimumLightmap();
        for (int v = 0; v < 4; v++) {
            int uv2 = v * 8 + 6;
            vertices[uv2] = TankInteriorLight.max(vertices[uv2], minimum);
        }
        return new BakedQuad(vertices, q.getTintIndex(), q.getDirection(), q.getSprite(), q.isShade(), q.hasAmbientOcclusion());
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource rand, ModelData data) {
        List<RenderType> types = new ArrayList<>();
        for (FishTankGeometry.LayerQuads layer : geometry.composite(data(data)).layers()) {
            types.add(chunkLayer(layer.source()));
        }
        TankCosmeticMesh.Baked cosmetics = TankCosmeticMesh.bake(data(data).cosmetics());
        for (TankCosmeticMesh.Quad quad : cosmetics.untinted()) types.add(chunkLayer(quad.tintState().getBlock()));
        for (TankCosmeticMesh.Quad quad : cosmetics.tinted()) types.add(chunkLayer(quad.tintState().getBlock()));
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
