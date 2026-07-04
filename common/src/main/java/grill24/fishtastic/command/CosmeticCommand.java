package grill24.fishtastic.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;
import grill24.fishtastic.block.FishTankBlock;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticTransforms;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.phys.AABB;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
                .executes(CosmeticCommand::dump))
            .then(Commands.literal("capture")
                .then(Commands.argument("from", BlockPosArgument.blockPos())
                    .then(Commands.argument("to", BlockPosArgument.blockPos())
                        .then(Commands.argument("anchor", BlockPosArgument.blockPos())
                            .then(Commands.argument("name", StringArgumentType.word())
                                .executes(ctx -> capture(ctx,
                                    BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                    BlockPosArgument.getLoadedBlockPos(ctx, "to"),
                                    BlockPosArgument.getLoadedBlockPos(ctx, "anchor"),
                                    StringArgumentType.getString(ctx, "name"),
                                    (float) CosmeticGridCell.CELL_WIDTH))
                                .then(Commands.argument("scale", FloatArgumentType.floatArg(0.001f))
                                    .executes(ctx -> capture(ctx,
                                        BlockPosArgument.getLoadedBlockPos(ctx, "from"),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "to"),
                                        BlockPosArgument.getLoadedBlockPos(ctx, "anchor"),
                                        StringArgumentType.getString(ctx, "name"),
                                        FloatArgumentType.getFloat(ctx, "scale")))))))));
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

    /**
     * Scans the block box between {@code from}/{@code to} (inclusive, air excluded) and encodes it as a
     * {@link CosmeticStructure}: {@code anchor} becomes local origin {@code (0,0,0)} (the structure's
     * placement point / footprint cell {@code (0,0)}). Build the structure facing south first (see
     * {@link CosmeticStructure}'s class doc) before capturing.
     * <p>
     * {@code scale} defaults to {@link CosmeticGridCell#CELL_WIDTH}, matching a structure built with
     * plain 1-block spacing rendering at that same spacing (touching parts stay touching). Passing a
     * smaller/larger {@code scale} compresses/expands {@code offsetX}/{@code offsetZ} by
     * {@code scale / CELL_WIDTH} to keep touching parts touching at the new size — see
     * {@link FishTankBlockEntityRenderer#renderStructureCosmetics} for why X/Z (fixed {@code CELL_WIDTH}
     * pitch) need this compensation but {@code offsetY} (already multiplied by {@code scale} at render
     * time) doesn't and is captured as a plain, unscaled world-block delta.
     */
    private static int capture(CommandContext<CommandSourceStack> ctx, BlockPos from, BlockPos to, BlockPos anchor, String name, float scale) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();

        BlockPos min = new BlockPos(Math.min(from.getX(), to.getX()), Math.min(from.getY(), to.getY()), Math.min(from.getZ(), to.getZ()));
        BlockPos max = new BlockPos(Math.max(from.getX(), to.getX()), Math.max(from.getY(), to.getY()), Math.max(from.getZ(), to.getZ()));

        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (volume > 100_000) {
            source.sendFailure(Component.literal("Region too large (" + volume + " blocks) - check your corners."));
            return 0;
        }

        float xzRatio = scale / (float) CosmeticGridCell.CELL_WIDTH;

        List<CosmeticStructure.StructurePart> parts = new ArrayList<>();
        Set<CosmeticStructure.GridOffset> footprint = new LinkedHashSet<>();
        footprint.add(new CosmeticStructure.GridOffset(0, 0));

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    BlockState state = level.getBlockState(new BlockPos(x, y, z));
                    if (state.isAir()) continue;

                    int dy = y - anchor.getY();
                    float offsetX = (x - anchor.getX()) * xzRatio;
                    float offsetZ = (z - anchor.getZ()) * xzRatio;
                    parts.add(new CosmeticStructure.StructurePart(state, offsetX, dy, offsetZ));
                    footprint.add(new CosmeticStructure.GridOffset(Math.round(offsetX), Math.round(offsetZ)));
                }
            }
        }

        if (parts.isEmpty()) {
            source.sendFailure(Component.literal("No non-air blocks found between " + min.toShortString() + " and " + max.toShortString() + "."));
            return 0;
        }

        // Every part sitting below the anchor is almost always a mis-placed anchor (e.g. anchored with
        // ~ ~ ~ while standing on top of the structure, whose feet position is one block above it) -
        // not a deliberately authored structure, since parts are meant to sit at/above the tank floor.
        if (parts.stream().allMatch(part -> part.offsetY() < 0)) {
            source.sendSystemMessage(Component.literal(
                    "Warning: every captured block has a negative Y offset, meaning the anchor sits above the whole "
                            + "structure. This usually means anchor was targeted one block too high (e.g. your feet via "
                            + "~ ~ ~ while standing on top of it) - anchor the base block itself, or use ~ ~-1 ~.")
                    .withStyle(ChatFormatting.YELLOW));
        }

        CosmeticStructure structure = new CosmeticStructure(List.copyOf(footprint), parts, scale);

        JsonElement json = CosmeticStructure.CODEC.encodeStart(JsonOps.INSTANCE, structure)
                .resultOrPartial(err -> source.sendFailure(Component.literal("Failed to encode structure: " + err)))
                .orElse(null);
        if (json == null) return 0;

        // Encoding never runs the codec's own validation (only decoding does) - round-trip it here so
        // footprint/grid-size problems surface immediately instead of only on the next resource reload.
        CosmeticStructure.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(err -> source.sendSystemMessage(Component.literal("Warning: " + err).withStyle(ChatFormatting.RED)));

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String jsonText = gson.toJson(json);

        try {
            Path dir = source.getServer().getServerDirectory().resolve("fishtastic_cosmetic_structures");
            Files.createDirectories(dir);
            Path file = dir.resolve(name + ".json");
            Files.writeString(file, jsonText, StandardCharsets.UTF_8);

            int partCount = parts.size();
            source.sendSuccess(() -> Component.literal("Captured " + partCount + " block(s) to ")
                    .append(Component.literal(file.toAbsolutePath().toString())
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.AQUA)
                                    .withUnderlined(true)
                                    .withClickEvent(new ClickEvent.CopyToClipboard(jsonText)))), true);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to write file: " + e.getMessage()));
            return 0;
        }

        return parts.size();
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
