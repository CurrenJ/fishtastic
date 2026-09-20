package grill24.fishtastic.fabric.compat.coolcam;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.client.util.ClientTankFlocks;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * Demo of {@link CoolCamFollowBridge}: points Cool Cam's camera at one simulated fish in whatever
 * tank the player is looking at, without moving the camera itself. Registered only when Cool Cam
 * is installed — the whole command tree is otherwise absent, matching how fishtastic's JEI plugin
 * is only ever loaded by JEI itself when present.
 */
public final class FishtasticCoolCamCommands {
    private FishtasticCoolCamCommands() {}

    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (!CoolCamFollowBridge.isAvailable()) return;

        // Shares the "fishtastic" root with the server-side FishtasticCommand tree. This command
        // is registered on the separate client-only dispatcher (has to be — camera control has no
        // meaning on a dedicated server), so vanilla's chat-suggestion UI won't show these children
        // in the autocomplete dropdown under "/fishtastic " (it only renders the tree synced from
        // the server at login) — typing the full command and submitting it still works, since
        // Fabric's client command execution path is checked before falling back to the server.
        //
        // Sharing that root is only safe because of the fallback branch below. Fabric's
        // ClientCommandInternals tries this client dispatcher first for every "/fishtastic ..."
        // input; if "fishtastic" matches but nothing under it does, Brigadier throws
        // dispatcherUnknownArgument — a type Fabric does NOT treat as "not a client command", so
        // it reports the error directly to chat instead of forwarding to the server. That broke
        // every other /fishtastic subcommand (backup, quests, fishprofile, ...) the moment this
        // command was registered. The greedy fallback below intercepts anything that isn't
        // "followfish ..." and rethrows it as dispatcherUnknownCommand, which Fabric DOES treat as
        // "not ours" and correctly passes through to the server.
        dispatcher.register(literal("fishtastic")
            .then(literal("followfish")
                .executes(ctx -> followLookedAtTank(ctx.getSource(), 0))
                .then(argument("index", integer(0))
                    .executes(ctx -> followLookedAtTank(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "index"))))
                .then(literal("closest").executes(ctx -> followClosestInLookedAtTank(ctx.getSource())))
                .then(literal("stop").executes(ctx -> {
                    CoolCamFollowBridge.stopFollowing();
                    ctx.getSource().sendFeedback(Component.literal("Stopped following."));
                    return 1;
                })))
            .then(argument("fishtasticFallthrough", StringArgumentType.greedyString())
                .executes(ctx -> {
                    throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownCommand().create();
                })));
    }

    /** Resolves the fish tank block entity the player is currently looking at, or null with feedback sent. */
    private static @Nullable BlockPos lookedAtTank(FabricClientCommandSource source, Level level) {
        HitResult hit = Minecraft.getInstance().hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) {
            source.sendError(Component.literal("Not looking at a block."));
            return null;
        }
        BlockPos pos = blockHit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof FishTankBlockEntity)) {
            source.sendError(Component.literal("Not looking at a fish tank."));
            return null;
        }
        return pos;
    }

    private static int followLookedAtTank(FabricClientCommandSource source, int fishIndex) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return 0;

        BlockPos pos = lookedAtTank(source, level);
        if (pos == null) return 0;
        if (!ClientTankFlocks.hasFish(level, pos, fishIndex)) {
            source.sendError(Component.literal("That tank has no fish at index " + fishIndex + "."));
            return 0;
        }

        CoolCamFollowBridge.lookAt(new FollowablePosition() {
            @Override
            public Vec3 position(float partialTick) {
                Vec3 p = ClientTankFlocks.worldPositionOf(level, pos, fishIndex);
                return p != null ? p : Vec3.atCenterOf(pos);
            }

            @Override
            public boolean isValid() {
                return mc.level == level
                    && level.getBlockEntity(pos) instanceof FishTankBlockEntity
                    && ClientTankFlocks.hasFish(level, pos, fishIndex);
            }
        });
        source.sendFeedback(Component.literal("Following fish #" + fishIndex + " in the tank at " + pos.toShortString() + "."));
        return 1;
    }

    private static int followClosestInLookedAtTank(FabricClientCommandSource source) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return 0;

        BlockPos pos = lookedAtTank(source, level);
        if (pos == null) return 0;
        if (ClientTankFlocks.fishCount(level, pos) == 0) {
            source.sendError(Component.literal("That tank has no fish."));
            return 0;
        }

        CoolCamFollowBridge.lookAt(new ClosestFishFollowTarget(pos, level));
        source.sendFeedback(Component.literal("Following closest fish in the tank at " + pos.toShortString() + "."));
        return 1;
    }
}
