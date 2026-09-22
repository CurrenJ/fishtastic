package grill24.fishtastic.client.renderer;

import com.mojang.serialization.MapCodec;
import grill24.fishtastic.blockentity.FishPileBlockEntity;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Renders a bundle of fish the way the placed Fish Pile <em>block</em> looks — each fish laid flat
 * and stacked like pancakes inside a one-block cube, viewed through the vanilla block display
 * transform so it reads as a real block rather than a flat icon.
 *
 * <p>Geometry is the same math {@link FishPileBlockEntityRenderer} draws in-world with (shared via
 * {@link FishPileBlockEntity}'s render constants), expressed as per-layer local transforms instead
 * of {@code PoseStack} pushes: a layer's final pose is its {@link ItemTransform} (here the block
 * model's, which ends on the standard {@code -0.5} cube-centering shift) followed by its local
 * transform, so a local transform maps fish-model space straight into block space {@code [0,1]³}.
 *
 * <p>Unlike {@link PileOfFishItemModel} — the flat, overlapping icon for the Pile of Fish
 * <em>item</em> — this model isn't bound to an item: it's selected per stack through the
 * {@code minecraft:item_model} component (see
 * {@link grill24.fishtastic.client.util.FishPileIcons}), so any fish-bearing bundle can be drawn
 * as a pile block. Contents render bottom-up in list order, matching the block's oldest-first
 * insertion order.
 */
public class FishPileBlockItemModel implements ItemModel {
    private static final float SCALE = FishPileBlockEntity.RENDER_SCALE;
    private static final float BASE_Y = FishPileBlockEntity.RENDER_BASE_Y;
    private static final float LAYER_HEIGHT = FishPileBlockEntity.RENDER_LAYER_HEIGHT;
    private static final float JITTER_XZ = FishPileBlockEntityRenderer.JITTER_XZ;
    /** Fixed (rather than the in-world block-position hash) so one pile always looks the same. */
    private static final long JITTER_SEED = 1913L;

    private final ItemTransforms blockTransforms;

    private FishPileBlockItemModel(ItemTransforms blockTransforms) {
        this.blockTransforms = blockTransforms;
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack item,
                       ItemModelResolver resolver, ItemDisplayContext displayContext,
                       @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        output.appendModelIdentityElement(this);
        List<ItemStackTemplate> templates =
                item.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY).items();
        int limit = Math.min(templates.size(), FishPileBlockEntity.MAX_FISH);
        if (limit == 0) return;

        ItemTransform blockTransform = blockTransforms.getTransform(displayContext);
        RandomSource random = RandomSource.create(JITTER_SEED);
        FishtasticItemStackRenderState access = (FishtasticItemStackRenderState) output;

        for (int i = 0; i < limit; i++) {
            float jitterX = (random.nextFloat() - 0.5f) * 2f * JITTER_XZ;
            float jitterZ = (random.nextFloat() - 0.5f) * 2f * JITTER_XZ;
            float rotation = random.nextFloat() * 360f;

            ItemStack layerStack = templates.get(i).create();
            if (layerStack.isEmpty()) continue;

            // Applied to a fish item model's own [0,1]³ vertices right-to-left: centre the fish on
            // the origin, shrink it, lay it flat, spin it, then lift it onto its layer of the stack.
            Matrix4f local = new Matrix4f()
                    .translate(0.5f + jitterX, BASE_Y + i * LAYER_HEIGHT, 0.5f + jitterZ)
                    .rotateY((float) Math.toRadians(rotation))
                    .rotateX((float) Math.toRadians(90f))
                    .scale(SCALE)
                    .translate(-0.5f, -0.5f, -0.5f);

            int before = access.fishtastic$getActiveLayerCount();
            resolver.appendItemLayers(output, layerStack, displayContext, level, owner, seed + i);
            int after = access.fishtastic$getActiveLayerCount();
            for (int j = before; j < after; j++) {
                ItemStackRenderState.LayerRenderState layer = access.fishtastic$getLayer(j);
                // Block lighting, like the block this mimics: the fish are oriented in 3D here, so
                // the flat front-on item lighting their own models ask for would flatten the stack.
                layer.setUsesBlockLight(true);
                layer.setItemTransform(blockTransform);
                layer.setLocalTransform(local);
            }
        }
    }

    public record Unbaked() implements ItemModel.Unbaked {
        private static final Identifier BLOCK_BLOCK_MODEL = Identifier.withDefaultNamespace("block/block");
        public static final MapCodec<Unbaked> MAP_CODEC = MapCodec.unit(new Unbaked());

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(BLOCK_BLOCK_MODEL);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            ModelBaker baker = context.blockModelBaker();
            ResolvedModel blockBase = baker.getModel(BLOCK_BLOCK_MODEL);
            return new FishPileBlockItemModel(blockBase.getTopTransforms());
        }
    }
}
