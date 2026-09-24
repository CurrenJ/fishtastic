package grill24.fishtastic.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import grill24.FishtasticRegistries;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link FishTankStructureCosmeticItem}'s icon as a shrunk 3D replica of its whole
 * {@link CosmeticStructure} — every part's real authored {@link BlockState}, positioned with the
 * same grid-cell spacing math {@link FishTankBlockEntityRenderer#renderStructureCosmetics} uses
 * in-tank, plus one outer fit scale so the assembly lands inside a normal item slot's camera framing.
 * <p>
 * Reusing each part's own vanilla item model was deliberately avoided: vanilla item models for
 * connectable blocks like fences are simplified, state-blind "post only" icons, which would lose
 * the connector-arm geometry that's the entire visual point of a structure like {@code cosmetic_fence_arch}.
 * <p>
 * 26.1.2 builds this as an {@code ItemModel} that copies each part's baked quads and tint colours
 * into render-state layers. On 1.21.1 the item's {@code builtin/entity} renderer
 * ({@link FishtasticItemRenderers}) draws each part with {@code BlockRenderDispatcher.renderSingleBlock},
 * which applies the block colours itself and draws block-entity blocks (the chest) through their
 * own item renderer, so neither 26.1.2's tint extraction nor its special-model fallback is needed.
 * The display transform is {@code block/block}'s, from the item model (datagen).
 */
public final class CosmeticStructureItemModel {

    /** Target world-space span (in block units) the assembly's longest axis is scaled to fit. */
    private static final float TARGET_SPAN = 1.0f;

    private record PartIcon(BlockState state, Matrix4f localTransform) {}

    private static final Map<ResourceKey<CosmeticStructure>, List<PartIcon>> CACHE = new HashMap<>();
    /** The registries {@link #CACHE} was built from; a different world (or a /reload) clears it. */
    private static @Nullable RegistryAccess cachedFor;

    /** Called with the pose in the item's model space, like any {@code builtin/entity} renderer. */
    static void render(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack,
                       MultiBufferSource buffers, int light, int overlay) {
        if (!(stack.getItem() instanceof FishTankStructureCosmeticItem cosmeticItem)) return;
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        if (cachedFor != level.registryAccess()) {
            CACHE.clear();
            cachedFor = level.registryAccess();
        }
        List<PartIcon> parts = CACHE.computeIfAbsent(cosmeticItem.getStructureId(), id -> prepare(id, level));
        if (parts == null) return;

        for (PartIcon part : parts) {
            poseStack.pushPose();
            poseStack.mulPose(part.localTransform());
            mc.getBlockRenderer().renderSingleBlock(part.state(), poseStack, buffers, light, overlay);
            poseStack.popPose();
        }
    }

    // ── Structure → prepared icon ────────────────────────────────────────────

    private record PartPlacement(BlockState state, float x, float y, float z) {}

    @Nullable
    private static List<PartIcon> prepare(ResourceKey<CosmeticStructure> structureId, ClientLevel level) {
        CosmeticStructure structure = level.registryAccess()
                .registryOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY)
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
            parts.add(new PartIcon(placement.state(), local));
        }
        return parts;
    }

    private CosmeticStructureItemModel() {}
}
