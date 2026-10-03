package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.renderer.TankSimDebug;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Dev command that prints what the client-side simulation is doing with the fish in the tank the
 * player is looking at — see {@link TankSimDebug}. Single-player / dev-only, like
 * {@link PoseDebugCommand}: the state it reads lives on the client, which in single-player is the
 * same JVM the command runs in.
 *
 * <pre>
 *   /fishtastic tanksim
 * </pre>
 */
public class TankSimDebugCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(TankSimDebugCommand.class);

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("tanksim")
                .requires(FishtasticPermissions.gamemaster())
                .executes(TankSimDebugCommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        Player player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command can only be used by players."));
            return 0;
        }
        // The tank being looked at, resolved the way Item#getPlayerPOVHitResult would.
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.calculateViewVector(player.getXRot(), player.getYRot()).scale(player.blockInteractionRange()));
        BlockHitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        BlockPos pos = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
        if (pos == null || !(player.level().getBlockEntity(pos) instanceof FishTankBlockEntity tank)) {
            source.sendFailure(Component.literal("Look at a fish tank first."));
            return 0;
        }

        List<String> lines = TankSimDebug.describe(tank, player.level());
        for (String line : lines) {
            LOGGER.info("[tanksim] {}", line);
            source.sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.AQUA), false);
        }
        return 1;
    }
}
