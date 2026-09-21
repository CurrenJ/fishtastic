package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.client.util.FishermanPoseDebug;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dev command for live-tuning the fisherman hang pose ({@code HumanoidModelMixin} / {@code
 * FishTankBlockEntityRenderer#getHeldItemHangingRollDegrees}) without a recompile — single-player
 * only, drives {@link FishermanPoseDebug}'s fields directly the same way {@link
 * CelebrationCommand} drives client-side state.
 *
 * <pre>
 *   /fishtastic pose armx &lt;radians&gt;
 *   /fishtastic pose army &lt;radians&gt;
 *   /fishtastic pose armz &lt;radians&gt;
 *   /fishtastic pose rolldiagonal &lt;degrees&gt;
 *   /fishtastic pose rollstraight &lt;degrees&gt;
 *   /fishtastic pose offsetx &lt;value&gt;
 *   /fishtastic pose offsety &lt;value&gt;
 *   /fishtastic pose offsetz &lt;value&gt;
 *   /fishtastic pose world &lt;true|false&gt;   — preview on real players (see FishermanPoseDebug#enabledInWorld)
 *   /fishtastic pose print
 * </pre>
 *
 * Every set logs the full current value set to chat and to the console log (not just the field
 * that changed), so once a pose looks right in-game the final line can be copied straight from
 * the log to hand off as the new baked-in defaults.
 */
public class PoseDebugCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PoseDebugCommand.class);

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("pose")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("armx").then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> set(ctx, v -> FishermanPoseDebug.armXRot = v))))
                .then(Commands.literal("army").then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> set(ctx, v -> FishermanPoseDebug.armYRot = v))))
                .then(Commands.literal("armz").then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> set(ctx, v -> FishermanPoseDebug.armZRot = v))))
                .then(Commands.literal("rolldiagonal").then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> set(ctx, v -> FishermanPoseDebug.rollDiagonalDegrees = v))))
                .then(Commands.literal("rollstraight").then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> set(ctx, v -> FishermanPoseDebug.rollStraightDegrees = v))))
                .then(Commands.literal("offsetx").then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> set(ctx, v -> FishermanPoseDebug.offsetX = v))))
                .then(Commands.literal("offsety").then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> set(ctx, v -> FishermanPoseDebug.offsetY = v))))
                .then(Commands.literal("offsetz").then(Commands.argument("value", FloatArgumentType.floatArg())
                        .executes(ctx -> set(ctx, v -> FishermanPoseDebug.offsetZ = v))))
                .then(Commands.literal("world").then(Commands.argument("enabled", BoolArgumentType.bool())
                        .executes(PoseDebugCommand::setWorld)))
                .then(Commands.literal("print").executes(PoseDebugCommand::print));
    }

    private static int set(CommandContext<CommandSourceStack> ctx, FloatSetter setter) {
        float value = FloatArgumentType.getFloat(ctx, "value");
        setter.set(value);
        announce(ctx);
        return 1;
    }

    private static int setWorld(CommandContext<CommandSourceStack> ctx) {
        FishermanPoseDebug.enabledInWorld = BoolArgumentType.getBool(ctx, "enabled");
        announce(ctx);
        return 1;
    }

    private static int print(CommandContext<CommandSourceStack> ctx) {
        announce(ctx);
        return 1;
    }

    private static void announce(CommandContext<CommandSourceStack> ctx) {
        String description = FishermanPoseDebug.describe();
        LOGGER.info("Fisherman pose: {}", description);
        ctx.getSource().sendSuccess(() -> Component.literal(description).withStyle(ChatFormatting.GREEN), false);
    }

    @FunctionalInterface
    private interface FloatSetter {
        void set(float value);
    }
}
