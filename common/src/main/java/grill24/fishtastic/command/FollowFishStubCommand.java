package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

/**
 * Server-side stub mirroring the shape of the client-only "Cool Cam" follow-fish camera command
 * ({@code grill24.fishtastic.fabric.compat.coolcam.FishtasticCoolCamCommands}), purely so
 * {@code followfish} appears in {@code /fishtastic } tab-completion.
 *
 * <p>Camera control only makes sense client-side, so the real implementation lives in a
 * separate client-only command tree. But Fabric's client-command merge has an ordering bug:
 * {@code ClientCommandInternals#copyChildren} inserts a freshly-built (still childless) copy of
 * an existing root literal into the vanilla suggestion tree, then only afterwards recurses to
 * attach its children — and when the root already exists (as {@code fishtastic} does here),
 * Brigadier's {@code CommandNode#addChild} takes the "merge into existing node" branch, which
 * reads the copy's children at that exact (still-empty) moment and never stores the copy itself.
 * Every child added a line later lands on an object the live tree no longer references, so it can
 * never show up in suggestions — a limitation of the merge helper, not something fixable from
 * either command tree alone.
 *
 * <p>Declaring the same shape here, as part of what the server actually sends down, sidesteps
 * that bug: this literal is real from the start, before any client-side merge runs. The executor
 * only ever fires when the client-side command didn't intercept it first (Cool Cam not installed,
 * or not on Fabric), so it just explains why nothing happened.
 */
public final class FollowFishStubCommand {
    private FollowFishStubCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("followfish")
            .executes(FollowFishStubCommand::unavailable)
            .then(Commands.argument("index", integer(0)).executes(FollowFishStubCommand::unavailable))
            .then(Commands.literal("closest")
                .executes(FollowFishStubCommand::unavailable)
                .then(Commands.argument("retargetCooldownSeconds", FloatArgumentType.floatArg(0.05f))
                    .executes(FollowFishStubCommand::unavailable)))
            .then(Commands.literal("species")
                .then(Commands.argument("species", ResourceLocationArgument.id())
                    .executes(FollowFishStubCommand::unavailable)
                    .then(Commands.argument("retargetCooldownSeconds", FloatArgumentType.floatArg(0.05f))
                        .executes(FollowFishStubCommand::unavailable))))
            .then(Commands.literal("stop").executes(FollowFishStubCommand::unavailable));
    }

    private static int unavailable(CommandContext<CommandSourceStack> context) {
        context.getSource().sendFailure(Component.literal(
            "Camera follow needs the Cool Cam mod installed and running on your (Fabric) client."));
        return 0;
    }
}
