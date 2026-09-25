package grill24.fishtastic.neoforge.fishtank;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.client.compositemodel.FishTankGeometry;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.fishtank.FishTankCompositeModelData;
import grill24.fishtastic.fishtank.FishTankShape;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.RenderTypeHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tank item's per-stack model on NeoForge 1.21.1: resolves the stack's
 * {@code FISH_TANK_SHAPE} and {@code FISH_TANK_MATERIALS} to the fully closed (permutation 0)
 * composite, as 26.1.2's {@code FishTankItemModel} does. Each material layer is its own render
 * pass so it can use its own item render type (glass translucent, frame and sand cutout).
 */
final class FishTankItemModel extends ItemOverrides {
    private final FishTankBakedModel blockModel;
    private final Map<FishTankGeometry.Composite, BakedModel> resolved = new ConcurrentHashMap<>();

    FishTankItemModel(FishTankBakedModel blockModel) {
        this.blockModel = blockModel;
    }

    @Override
    public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        FishTankShape shape = FishtasticItemData.getOrDefault(stack, FishtasticDataComponents.FISH_TANK_SHAPE, FishTankShape.STANDARD);
        FishTankMaterials materials = FishtasticItemData.getOrDefault(stack, FishtasticDataComponents.FISH_TANK_MATERIALS,
                FishTankMaterials.defaultMaterials());
        FishTankGeometry.Composite composite = blockModel.geometry().composite(
                new FishTankCompositeModelData(shape, materials.frame(), materials.sand(), materials.glass()));
        return resolved.computeIfAbsent(composite, c -> new Resolved(c, blockModel));
    }

    /** One stack's tank: display transforms plus one pass per material layer. */
    private static final class Resolved extends PassModel {
        private final List<BakedModel> passes = new ArrayList<>();

        Resolved(FishTankGeometry.Composite composite, FishTankBakedModel blockModel) {
            super(null, null, composite.particle(), blockModel.geometry().blockItemTransforms());
            for (FishTankGeometry.LayerQuads layer : composite.layers()) {
                passes.add(new PassModel(layer, blockModel.chunkLayer(layer.source()), composite.particle(), ItemTransforms.NO_TRANSFORMS));
            }
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return passes;
        }
    }

    private static class PassModel implements BakedModel {
        private final @Nullable FishTankGeometry.LayerQuads layer;
        private final @Nullable RenderType chunkLayer;
        private final TextureAtlasSprite particle;
        private final ItemTransforms transforms;

        PassModel(@Nullable FishTankGeometry.LayerQuads layer, @Nullable RenderType chunkLayer, TextureAtlasSprite particle,
                  ItemTransforms transforms) {
            this.layer = layer;
            this.chunkLayer = chunkLayer;
            this.particle = particle;
            this.transforms = transforms;
        }

        @Override
        public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
            return layer == null ? List.of() : layer.get(side);
        }

        @Override
        public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
            return List.of(RenderTypeHelper.getEntityRenderType(chunkLayer == null ? RenderType.solid() : chunkLayer, fabulous));
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
            return particle;
        }

        @Override
        public ItemTransforms getTransforms() {
            return transforms;
        }

        @Override
        public ItemOverrides getOverrides() {
            return ItemOverrides.EMPTY;
        }
    }
}
