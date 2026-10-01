package grill24.fishtastic.client;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticPlacement;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Highlights the grid cell(s) a held cosmetic would go into while the player points it at a tank:
 * green where a click would place it, red where it would be refused. The cell comes from the same
 * {@link CosmeticPlacement} plan the server applies on click, so the preview can't drift from the
 * placement — including pass-through targeting of neighbouring tanks in the group, a structure's
 * whole rotated footprint, and lid cells for hanging cosmetics.
 */
public final class CosmeticPlacementPreview {

    private static final int VALID_STROKE = ARGB.color(255, 90, 255, 120);
    private static final int VALID_FILL = ARGB.color(100, 90, 255, 120);
    private static final int INVALID_STROKE = ARGB.color(255, 255, 80, 70);
    private static final int INVALID_FILL = ARGB.color(100, 255, 80, 70);
    /** Thickness of the highlight slab laid on the sand or under the lid, in blocks. */
    private static final double SLAB = 1.0 / 32.0;
    /** Inset from the cell edge, so neighbouring highlighted cells read as separate squares. */
    private static final double INSET = 0.01;

    private CosmeticPlacementPreview() {}

    /** Must be called from within Minecraft's per-tick gizmo collection scope (client tick hook). */
    public static void tickGizmos() {
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

        GizmoStyle style = plan.valid()
                ? GizmoStyle.strokeAndFill(VALID_STROKE, 3f, VALID_FILL)
                : GizmoStyle.strokeAndFill(INVALID_STROKE, 3f, INVALID_FILL);
        if (plan.region() != null) {
            // A spanning structure: outline the whole box of tanks it would fill.
            Gizmos.cuboid(plan.region().deflate(0.02), GizmoStyle.stroke(plan.valid() ? VALID_STROKE : INVALID_STROKE, 2f));
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
            Gizmos.cuboid(new AABB(x - half, y0, z - half, x + half, y0 + height, z + half), style);
        }
    }
}
