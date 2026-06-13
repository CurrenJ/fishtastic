package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.block.FishTankBlock;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.Optional;

public class CosmeticCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("cosmetic")
            .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.literal("nudge")
                .then(Commands.argument("x", FloatArgumentType.floatArg())
                    .then(Commands.argument("y", FloatArgumentType.floatArg())
                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                            .executes(ctx -> nudge(ctx,
                                FloatArgumentType.getFloat(ctx, "x"),
                                FloatArgumentType.getFloat(ctx, "y"),
                                FloatArgumentType.getFloat(ctx, "z")))))))
            .then(Commands.literal("rotate")
                .then(Commands.argument("x", FloatArgumentType.floatArg())
                    .then(Commands.argument("y", FloatArgumentType.floatArg())
                        .then(Commands.argument("z", FloatArgumentType.floatArg())
                            .executes(ctx -> rotate(ctx,
                                FloatArgumentType.getFloat(ctx, "x"),
                                FloatArgumentType.getFloat(ctx, "y"),
                                FloatArgumentType.getFloat(ctx, "z")))))))
            .then(Commands.literal("scale")
                .then(Commands.argument("value", FloatArgumentType.floatArg(0.01f))
                    .executes(ctx -> scale(ctx, FloatArgumentType.getFloat(ctx, "value")))))
            .then(Commands.literal("dump")
                .executes(CosmeticCommand::dump));
    }

    private static int nudge(CommandContext<CommandSourceStack> ctx, float dx, float dy, float dz) {
        return withTargetedCosmetic(ctx, (block, transform) -> {
            CosmeticTransforms.set(block, transform.withOffset(dx, dy, dz));
            ctx.getSource().sendSuccess(() -> Component.literal("Nudged cosmetic by (" + dx + ", " + dy + ", " + dz + ")"), false);
        });
    }

    private static int rotate(CommandContext<CommandSourceStack> ctx, float rx, float ry, float rz) {
        return withTargetedCosmetic(ctx, (block, transform) -> {
            CosmeticTransforms.set(block, transform.withRotation(rx, ry, rz));
            ctx.getSource().sendSuccess(() -> Component.literal("Rotated cosmetic by (" + rx + ", " + ry + ", " + rz + ")°"), false);
        });
    }

    private static int scale(CommandContext<CommandSourceStack> ctx, float value) {
        return withTargetedCosmetic(ctx, (block, transform) -> {
            CosmeticTransforms.set(block, transform.withScale(value));
            ctx.getSource().sendSuccess(() -> Component.literal("Set cosmetic scale to " + value), false);
        });
    }

    private static int dump(CommandContext<CommandSourceStack> ctx) {
        return withTargetedCosmetic(ctx, (block, transform) -> {
            ctx.getSource().sendSuccess(() -> Component.literal(transform.toFileJson(block)), false);
        });
    }

    @FunctionalInterface
    interface TransformAction {
        void apply(net.minecraft.world.level.block.Block block, CosmeticTransforms.Transform transform);
    }

    private static int withTargetedCosmetic(CommandContext<CommandSourceStack> ctx, TransformAction action) {
        CommandSourceStack src = ctx.getSource();
        if (!(src.getEntity() instanceof ServerPlayer player)) {
            src.sendFailure(Component.literal("Must be used by a player."));
            return 0;
        }

        TargetedCosmetic target = findTargetedCosmetic(player);
        if (target == null) {
            src.sendFailure(Component.literal("Not targeting a cosmetic in a fish tank."));
            return 0;
        }

        CosmeticTransforms.Transform current = CosmeticTransforms.get(target.cosmetic().block());
        action.apply(target.cosmetic().block(), current);
        return 1;
    }

    record TargetedCosmetic(BlockPos tankPos, CosmeticGridCell cell, PlacedCosmetic cosmetic) {}

    @Nullable
    private static TargetedCosmetic findTargetedCosmetic(ServerPlayer player) {
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;

        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = player.level().getBlockEntity(pos);
        if (!(be instanceof FishTankBlockEntity tankBE)) return null;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        double reach = player.blockInteractionRange();
        Vec3 end = eye.add(look.scale(reach));

        CosmeticGridCell closest = null;
        double closestDist = Double.MAX_VALUE;
        for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : tankBE.getCosmetics().entrySet()) {
            CosmeticGridCell cell = entry.getKey();
            CosmeticTransforms.Transform t = CosmeticTransforms.get(entry.getValue().block());
            double wx = pos.getX() + cell.localX() + t.offsetX();
            double wy = pos.getY() + CosmeticGridCell.FLOOR_Y + t.offsetY();
            double wz = pos.getZ() + cell.localZ() + t.offsetZ();
            float half = t.scale() / 2f;
            AABB box = new AABB(wx - half, wy, wz - half, wx + half, wy + t.scale(), wz + half);
            Optional<Vec3> hitVec = box.clip(eye, end);
            if (hitVec.isPresent()) {
                double dist = hitVec.get().distanceToSqr(eye);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = cell;
                }
            }
        }

        if (closest == null) return null;
        return new TargetedCosmetic(pos, closest, tankBE.getCosmetics().get(closest));
    }
}
