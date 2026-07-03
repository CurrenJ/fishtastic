package grill24.fishtastic.client.renderer;

import com.mojang.serialization.MapCodec;
import grill24.FishtasticRegistries;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Renders a {@link FishTankStructureCosmeticItem}'s icon as a shrunk 3D replica of its whole
 * {@link CosmeticStructure} — every part's real authored {@link BlockState} quads, positioned with
 * the same grid-cell spacing math {@link FishTankBlockEntityRenderer#renderStructureCosmetics} uses
 * in-tank, plus one outer fit scale so the assembly lands inside a normal item slot's camera framing.
 * <p>
 * Reusing each part's own vanilla item model (the way {@link PileOfFishItemModel} composites nested
 * item stacks) was deliberately avoided: vanilla item models for connectable blocks like fences are
 * simplified, state-blind "post only" icons, which would lose the connector-arm geometry that's the
 * entire visual point of a structure like {@code cosmetic_fence_arch}.
 */
public class CosmeticStructureItemModel implements ItemModel {

    /** Fixed so repeated bakes of the same state produce identical quads (matches vanilla's model-seed convention). */
    private static final long MODEL_SEED = 42L;

    /** Target world-space span (in block units) the assembly's longest axis is scaled to fit. */
    private static final float TARGET_SPAN = 1.0f;

    /**
     * Blocks whose rendering goes through a {@code SpecialModelRenderer} rather than baked quads
     * (chest, banner, shulker box, bed, skull, ...). Raw quad extraction doesn't work for these —
     * they're rendered by delegating to that block's own item, mirroring how
     * {@link FishTankBlockEntityRenderer#renderStructureCosmetics} already special-cases
     * {@link Blocks#CHEST} with its own {@code ChestModel}. Extend this set as structures adopt more
     * such blocks.
     */
    private static final Set<Block> SPECIAL_MODEL_BLOCKS = Set.of(Blocks.CHEST);

    private final ItemTransforms sharedBlockTransforms;
    private final Map<ResourceKey<CosmeticStructure>, PreparedIcon> cache = new HashMap<>();

    private CosmeticStructureItemModel(ItemTransforms sharedBlockTransforms) {
        this.sharedBlockTransforms = sharedBlockTransforms;
    }

    @Override
    public void update(
            ItemStackRenderState output,
            ItemStack item,
            ItemModelResolver resolver,
            ItemDisplayContext displayContext,
            @Nullable ClientLevel level,
            @Nullable ItemOwner owner,
            int seed) {
        output.appendModelIdentityElement(this);
        if (!(item.getItem() instanceof FishTankStructureCosmeticItem cosmeticItem)) {
            return;
        }
        ResourceKey<CosmeticStructure> structureId = cosmeticItem.getStructureId();
        output.appendModelIdentityElement(structureId);

        ClientLevel effectiveLevel = level != null ? level : Minecraft.getInstance().level;
        if (effectiveLevel == null) return;

        PreparedIcon icon = cache.computeIfAbsent(structureId, id -> prepare(id, effectiveLevel));
        if (icon == null) return;

        ItemTransform sharedTransform = sharedBlockTransforms.getTransform(displayContext);
        FishtasticItemStackRenderState access = (FishtasticItemStackRenderState) output;

        for (PartIcon part : icon.parts()) {
            if (part.specialFallbackItem() != null) {
                int before = access.fishtastic$getActiveLayerCount();
                resolver.appendItemLayers(output, new ItemStack(part.specialFallbackItem()), displayContext, level, owner, seed);
                int after = access.fishtastic$getActiveLayerCount();
                for (int i = before; i < after; i++) {
                    ItemStackRenderState.LayerRenderState layer = access.fishtastic$getLayer(i);
                    layer.setItemTransform(sharedTransform);
                    layer.setLocalTransform(part.localTransform());
                }
            } else {
                ItemStackRenderState.LayerRenderState layer = output.newLayer();
                layer.setUsesBlockLight(true);
                layer.setItemTransform(sharedTransform);
                layer.setLocalTransform(part.localTransform());
                layer.prepareQuadList().addAll(part.quads());
            }
        }
    }

    // ── Structure → prepared icon ────────────────────────────────────────────

    private record PartPlacement(BlockState state, float x, float y, float z) {}

    private record PartIcon(@Nullable List<BakedQuad> quads, @Nullable Item specialFallbackItem, Matrix4f localTransform) {}

    private record PreparedIcon(List<PartIcon> parts) {}

    @Nullable
    private static PreparedIcon prepare(ResourceKey<CosmeticStructure> structureId, ClientLevel level) {
        CosmeticStructure structure = level.registryAccess()
                .lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY)
                .getOptional(structureId)
                .orElse(null);
        if (structure == null || structure.parts().isEmpty()) return null;

        float scale = structure.scale();

        // Mirrors FishTankBlockEntityRenderer.renderStructureCosmetics()'s partX/partY/partZ math,
        // minus the tank-floor constant and per-tank anchor cell (meaningless for a floating icon) —
        // rendered in the structure's authored orientation since an icon has no facing.
        List<PartPlacement> placements = new ArrayList<>(structure.parts().size());
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        for (CosmeticStructure.StructurePart part : structure.parts()) {
            float partX = part.offsetX() * (float) CosmeticGridCell.CELL_WIDTH;
            float partZ = part.offsetZ() * (float) CosmeticGridCell.CELL_WIDTH;
            float partY = part.offsetY() * scale;
            placements.add(new PartPlacement(part.state(), partX, partY, partZ));

            minX = Math.min(minX, partX - 0.5f * scale);
            maxX = Math.max(maxX, partX + 0.5f * scale);
            minY = Math.min(minY, partY);
            maxY = Math.max(maxY, partY + scale);
            minZ = Math.min(minZ, partZ - 0.5f * scale);
            maxZ = Math.max(maxZ, partZ + 0.5f * scale);
        }

        float maxSpan = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        float outerScale = maxSpan > 1.0e-4f ? TARGET_SPAN / maxSpan : 1f;

        // Author-authored nudge (CosmeticStructure.itemIcon()) layered on top of the auto-fit, pivoted
        // around the fitted assembly's own center so rotation/scale don't fling it off-slot; offset is
        // a plain translation applied last, in the same ~[0,1]-per-axis units the auto-fit targets.
        CosmeticTransforms.Transform iconT = structure.itemIcon();
        Matrix4f iconAdjustment = new Matrix4f()
                .translate(iconT.offsetX(), iconT.offsetY(), iconT.offsetZ())
                .translate(0.5f, 0.5f, 0.5f)
                .rotate(new Quaternionf().rotationXYZ(
                        (float) Math.toRadians(iconT.rotX()),
                        (float) Math.toRadians(iconT.rotY()),
                        (float) Math.toRadians(iconT.rotZ())))
                .scale(iconT.scale())
                .translate(-0.5f, -0.5f, -0.5f);

        List<PartIcon> parts = new ArrayList<>(placements.size());
        for (PartPlacement placement : placements) {
            // Applied to a raw block-model vertex (spanning roughly [0,1]^3) right-to-left:
            // recenter this part's own model, shrink it by the structure's per-part scale, move it
            // to its placement within the assembly, shift the whole assembly's bounding-box min
            // corner to the origin, shrink the whole assembly to fit a normal item slot, then apply
            // the structure's authored item-icon nudge.
            Matrix4f local = new Matrix4f(iconAdjustment)
                    .scale(outerScale)
                    .translate(-minX, -minY, -minZ)
                    .translate(placement.x(), placement.y(), placement.z())
                    .scale(scale)
                    .translate(-0.5f, 0f, -0.5f);

            Block block = placement.state().getBlock();
            if (SPECIAL_MODEL_BLOCKS.contains(block)) {
                parts.add(new PartIcon(null, block.asItem(), local));
            } else {
                parts.add(new PartIcon(extractQuads(placement.state()), null, local));
            }
        }
        return new PreparedIcon(parts);
    }

    /**
     * Pulls every {@link BakedQuad} for the given state's real block model, including all 6
     * directional buckets — unlike in-world chunk rendering, a floating item icon has no neighbor
     * blocks to cull faces against, so nothing should be omitted.
     */
    private static List<BakedQuad> extractQuads(BlockState state) {
        BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
        List<BlockStateModelPart> modelParts = new ArrayList<>();
        model.collectParts(RandomSource.create(MODEL_SEED), modelParts);

        List<BakedQuad> quads = new ArrayList<>();
        for (BlockStateModelPart part : modelParts) {
            quads.addAll(part.getQuads(null));
            for (Direction direction : Direction.values()) {
                quads.addAll(part.getQuads(direction));
            }
        }
        return quads;
    }

    // ── Registration ──────────────────────────────────────────────────────────

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
            return new CosmeticStructureItemModel(blockBase.getTopTransforms());
        }
    }
}
