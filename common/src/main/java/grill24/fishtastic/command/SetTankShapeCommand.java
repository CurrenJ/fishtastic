package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.block.FishTankBlock;
import grill24.fishtastic.fishtank.FishTankShape;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * Command to set the body shape of a fish tank item in the player's hand.
 * Usage: /settankshape <shape>
 * Shape values: standard, skylight, trimmed, reinforced, honed, sturdy, faceted, bastion,
 * rampart, ornate, shaggy, bramble, tooth, film
 */
public class SetTankShapeCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("settankshape")
                        .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)) // Requires operator permission
                        .then(Commands.argument("shape", StringArgumentType.word())
                                .suggests((context, builder) -> {
                                    for (FishTankShape shape : FishTankShape.values()) {
                                        builder.suggest(shape.getSerializedName());
                                    }
                                    return builder.buildFuture();
                                })
                                .executes(SetTankShapeCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String shapeString = StringArgumentType.getString(context, "shape").toLowerCase(Locale.ROOT);

        FishTankShape shape = null;
        for (FishTankShape candidate : FishTankShape.values()) {
            if (candidate.getSerializedName().equals(shapeString)) {
                shape = candidate;
                break;
            }
        }
        if (shape == null) {
            source.sendFailure(Component.literal("Invalid shape! Valid values: "
                    + java.util.Arrays.stream(FishTankShape.values())
                            .map(FishTankShape::getSerializedName)
                            .reduce((a, b) -> a + ", " + b)
                            .orElse("")));
            return 0;
        }

        if (source.getEntity() instanceof Player player) {
            ItemStack heldItem = player.getMainHandItem();

            if (heldItem.isEmpty()) {
                source.sendFailure(Component.literal("You must be holding an item!"));
                return 0;
            }

            if (!(heldItem.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof FishTankBlock)) {
                source.sendFailure(Component.literal("You must be holding a fish tank item!"));
                return 0;
            }

            heldItem.set(FishtasticDataComponents.FISH_TANK_SHAPE.value(), shape);

            FishTankShape finalShape = shape;
            source.sendSuccess(() -> Component.literal("Set fish tank shape to " + finalShape.getSerializedName()), true);
            return 1;
        }

        source.sendFailure(Component.literal("This command must be run by a player!"));
        return 0;
    }
}
