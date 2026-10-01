package grill24.fishtastic.client;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticPlacement;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import com.mojang.blaze3d.vertex.PoseStack;
import grill24.fishtastic.client.renderer.FishtasticRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Highlights the grid cell(s) a held cosmetic would go into while the player points it at a tank:
 * green where a click would place it, red where it would be refused. The cell comes from the same
 * {@link CosmeticPlacement} plan the server applies on click, so the preview can't drift from the
 * placement — including pass-through targeting of neighbouring tanks in the group, a structure's
 * whole rotated footprint, and lid cells for hanging cosmetics.
 *
 * <p>PORT-ONLY shape: 26.1.2 collects these as per-tick {@code Gizmos}, which 1.21.1 doesn't have, so
 * — like {@link CosmeticCaptureClientState} — this is drawn from the loaders' world-render hooks
 * ({@link #render}), stroked through {@link FishtasticRenderTypes#GIZMO_LINES} (a fixed 2 px, where
 * 26.1.2 asks for 3 px cell strokes) and filled with {@code DebugRenderer.renderFilledBox}.
 */
public final class CosmeticPlacementPreview {

    private static final int VALID_STROKE = FastColor.ARGB32.color(255, 90, 255, 120);
    private static final int VALID_FILL = FastColor.ARGB32.color(100, 90, 255, 120);
    private static final int INVALID_STROKE = FastColor.ARGB32.color(255, 255, 80, 70);
    private static final int INVALID_FILL = FastColor.ARGB32.color(100, 255, 80, 70);
    /** Thickness of the highlight slab laid on the sand or under the lid, in blocks. */
    private static final double SLAB = 1.0 / 32.0;
    /** Inset from the cell edge, so neighbouring highlighted cells read as separate squares. */
    private static final double INSET = 0.01;

    private CosmeticPlacementPreview() {}

    /** Called from the loaders' world-render hooks with a world-space pose stack (see {@link CosmeticCaptureClientState#render}). */
    public static void render(PoseStack pose, MultiBufferSource buffers, Vec3 camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        // Sneaking with an item in hand skips Block#useItemOn entirely, so nothing would be placed.
        if (mc.player.isSecondaryUseActive()) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!CosmeticPlacement.isCosmetic(held)) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        if (!(mc.level.getBlockEntity(hit.getBlockPos()) instanceof FishTankBlockEntity clicked)) return;

        CosmeticPlacement.Plan plan = CosmeticPlacement.plan(mc.player, clicked, held);
        if (plan == null || (plan.cells().isEmpty() && plan.region() == null)) return;

        int stroke = plan.valid() ? VALID_STROKE : INVALID_STROKE;
        int fill = plan.valid() ? VALID_FILL : INVALID_FILL;
        if (plan.region() != null) {
            // A spanning structure: outline the whole box of tanks it would fill.
            stroke(pose, buffers, camera, plan.region().deflate(0.02), stroke);
        }
        double half = CosmeticGridCell.CELL_WIDTH / 2.0 - INSET;
        for (CosmeticPlacement.TankCell target : plan.cells()) {
            FishTankBlockEntity tank = target.tank();
            CosmeticGridCell cell = target.cell();
            BlockPos pos = tank.getBlockPos();
            double x = pos.getX() + cell.localX();
            double z = pos.getZ() + cell.localZ();
            // An occupied cell (a strand to grow, a pickle to add to, or a refusal) is outlined over
            // the cosmetic's whole height: a tall kelp strand's root can be storeys below the view.
            PlacedCosmetic existing = plan.ceiling()
                    ? tank.getCeilingCosmetics().get(cell)
                    : tank.getCosmetics().get(cell);
            double height = SLAB;
            if (existing != null) {
                height = Math.max(SLAB, CosmeticTransforms.get(existing.block()).scale() * Math.max(1, existing.height()));
            }
            double y0 = plan.ceiling()
                    ? pos.getY() + CosmeticGridCell.CEILING_Y - height
                    : pos.getY() + CosmeticGridCell.FLOOR_Y;
            AABB box = new AABB(x - half, y0, z - half, x + half, y0 + height, z + half);
            fill(pose, buffers, camera, box, fill);
            stroke(pose, buffers, camera, box, stroke);
        }
    }

    private static void stroke(PoseStack pose, MultiBufferSource buffers, Vec3 camera, AABB box, int color) {
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(FishtasticRenderTypes.GIZMO_LINES), box,
                FastColor.ARGB32.red(color) / 255f, FastColor.ARGB32.green(color) / 255f,
                FastColor.ARGB32.blue(color) / 255f, FastColor.ARGB32.alpha(color) / 255f);
        pose.popPose();
    }

    private static void fill(PoseStack pose, MultiBufferSource buffers, Vec3 camera, AABB box, int color) {
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        DebugRenderer.renderFilledBox(pose, buffers, box,
                FastColor.ARGB32.red(color) / 255f, FastColor.ARGB32.green(color) / 255f,
                FastColor.ARGB32.blue(color) / 255f, FastColor.ARGB32.alpha(color) / 255f);
        pose.popPose();
    }
}
