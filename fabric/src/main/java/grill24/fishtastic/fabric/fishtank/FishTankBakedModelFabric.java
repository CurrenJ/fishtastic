package grill24.fishtastic.fabric.fishtank;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.client.compositemodel.FishTankGeometry;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import grill24.fishtastic.fishtank.FishTankShape;
import net.fabricmc.fabric.api.blockview.v2.FabricBlockView;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Runtime model for the Fish Tank on Fabric 1.21.1 (new code). Emits the composite for the
 * block entity's {@link FishTankCompositeModelData} (Fabric render data) through the Fabric
 * Renderer API, each layer with its material block's own blend mode (glass usually translucent)
 * and AO off. The item emits the stack's closed tank the same way, as 26.1.2's
 * {@code FishTankItemModelFabric} does. Geometry comes from the shared {@link FishTankGeometry}.
 */
public class FishTankBakedModelFabric implements BakedModel {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final FishTankGeometry geometry;
    private final Map<BlendMode, RenderMaterial> materials = new EnumMap<>(BlendMode.class);

    FishTankBakedModelFabric(FishTankGeometry geometry) {
        this.geometry = geometry;
    }

    // ── Fabric Renderer API ───────────────────────────────────────────────

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter level, BlockState state, BlockPos pos, Supplier<RandomSource> randomSupplier,
                               RenderContext context) {
        Object renderData = ((FabricBlockView) level).getBlockEntityRenderData(pos);
        FishTankCompositeModelData data = renderData instanceof FishTankCompositeModelData d ? d : FishTankCompositeModelData.DEFAULT;
        emit(geometry.composite(data), context.getEmitter());
    }

    @Override
    public void emitItemQuads(ItemStack stack, Supplier<RandomSource> randomSupplier, RenderContext context) {
        FishTankShape shape = FishtasticItemData.getOrDefault(stack, FishtasticDataComponents.FISH_TANK_SHAPE, FishTankShape.STANDARD);
        FishTankMaterials tankMaterials = FishtasticItemData.getOrDefault(stack, FishtasticDataComponents.FISH_TANK_MATERIALS,
                FishTankMaterials.defaultMaterials());
        emit(geometry.composite(new FishTankCompositeModelData(shape, tankMaterials.frame(), tankMaterials.sand(), tankMaterials.glass())),
                context.getEmitter());
    }

    private void emit(FishTankGeometry.Composite composite, QuadEmitter emitter) {
        for (FishTankGeometry.LayerQuads layer : composite.layers()) {
            RenderMaterial material = material(BlendMode.fromRenderLayer(
                    ItemBlockRenderTypes.getChunkRenderType(layer.source().defaultBlockState())));
            for (int side = 0; side < 7; side++) {
                Direction cullFace = side == 6 ? null : DIRECTIONS[side];
                for (BakedQuad quad : layer.get(cullFace)) {
                    emitter.fromVanilla(quad, material, cullFace);
                    emitter.emit();
                }
            }
        }
    }

    private RenderMaterial material(BlendMode blendMode) {
        synchronized (materials) {
            // AO off: the tank shell is many noOcclusion() blocks, and vanilla AO compounds at the
            // internal seams (26.1.2 disables it on the model part).
            return materials.computeIfAbsent(blendMode, mode -> RendererAccess.INSTANCE.getRenderer().materialFinder()
                    .blendMode(mode).ambientOcclusion(TriState.FALSE).find());
        }
    }

    // ── Vanilla fallback (no Renderer API) ────────────────────────────────

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        List<BakedQuad> quads = new ArrayList<>();
        for (FishTankGeometry.LayerQuads layer : geometry.composite(null).layers()) {
            quads.addAll(layer.get(side));
        }
        return quads;
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
    public ItemTransforms getTransforms() {
        return geometry.blockItemTransforms();
    }

    @Override
    public ItemOverrides getOverrides() {
        return ItemOverrides.EMPTY;
    }
}
