package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.companion.Companions;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Dev command for fish companions (docs/fish-companions.md). Registered only in development.
 *
 * <pre>
 *   /fishtastic companion on                              — bond with the held fish (the real bond path)
 *   /fishtastic companion off                             — unequip; the fish comes back
 *   /fishtastic companion give &lt;player&gt; &lt;species&gt; [size] — equip a conjured fish directly
 *   /fishtastic companion minigame                        — opens a demo minigame bar so the fish docks
 *   /fishtastic companion stopgame                        — closes it
 *   /fishtastic companion catchcount &lt;player&gt; &lt;species&gt; &lt;count&gt; — set one species' catch count
 *   /fishtastic companion unlock &lt;player&gt; &lt;species&gt;       — force-unlock a bond (fires the banner)
 *   /fishtastic companion resetbonds &lt;player&gt;             — forget every stored bond unlock
 * </pre>
 *
 * {@code minigame}/{@code stopgame} drive client state directly, the same way
 * {@link CelebrationCommand} does, so they only work in single-player.
 */
public class CompanionCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("companion")
                .requires(FishtasticPermissions.gamemaster())
                .then(Commands.literal("on").executes(CompanionCommand::on))
                .then(Commands.literal("off").executes(CompanionCommand::off))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("species", IdentifierArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                BuiltInRegistries.ITEM.stream()
                                                        .filter(item -> item.builtInRegistryHolder().is(FishtasticItemTags.FISH))
                                                        .map(item -> BuiltInRegistries.ITEM.getKey(item).toString()),
                                                builder))
                                        .executes(ctx -> give(ctx, Optional.empty()))
                                        .then(Commands.argument("size", FloatArgumentType.floatArg(1f, 2000f))
                                                .executes(ctx -> give(ctx, Optional.of(FloatArgumentType.getFloat(ctx, "size"))))))))
                .then(Commands.literal("minigame").executes(ctx -> client(ctx, () -> {
                    ((IGameRendererExtension) Minecraft.getInstance().gameRenderer)
                            .fishtastic$displayItemActivation(new FishingMinigameAnimation());
                    return "Demo minigame open";
                })))
                .then(Commands.literal("stopgame").executes(ctx -> client(ctx, () -> {
                    ((IGameRendererExtension) Minecraft.getInstance().gameRenderer).fishtastic$cancelCurrentAnimation();
                    return "Demo minigame closed";
                })))
                .then(Commands.literal("catchcount")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("species", IdentifierArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                BuiltInRegistries.ITEM.stream()
                                                        .filter(item -> item.builtInRegistryHolder().is(FishtasticItemTags.FISH))
                                                        .map(item -> BuiltInRegistries.ITEM.getKey(item).toString()),
                                                builder))
                                        .then(Commands.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
                                                .executes(CompanionCommand::catchCount)))))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("species", IdentifierArgument.id())
                                        .executes(CompanionCommand::unlock))))
                .then(Commands.literal("resetbonds")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(CompanionCommand::resetBonds)));
    }

    /** Dev: sets one species' catch count, so a scene can park a species just below or at its gate. */
    private static int catchCount(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Identifier id = IdentifierArgument.getId(ctx, "species");
        int count = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count");
        grill24.fishtastic.server.FishCatchBackups.beforeDestructiveCommand(ctx.getSource().getServer(),
                "companion_catchcount");
        var data = grill24.fishtastic.server.FishCatchSavedData.getOrCreate(ctx.getSource().getServer());
        data.setCatchCount(data.resolvePlayerKey(player), player.getName().getString(), id, count);
        grill24.fishtastic.network.FishEncyclopediaSyncPacket.sendToPlayer(player, data);
        int finalCount = count;
        ctx.getSource().sendSuccess(() -> Component.literal("Set " + id.getPath() + " catch count to " + finalCount)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /**
     * Dev: force-unlocks a bond the way the natural transition would — stored, banner queued, FTUE
     * started — so a scene can exercise the unlock path without meeting the real gate.
     */
    private static int unlock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Identifier id = IdentifierArgument.getId(ctx, "species");
        grill24.fishtastic.server.FishCatchBackups.beforeDestructiveCommand(ctx.getSource().getServer(),
                "companion_unlock");
        var data = grill24.fishtastic.server.FishCatchSavedData.getOrCreate(ctx.getSource().getServer());
        if (!data.getOrCreateQuestState(player).unlockCompanionBond(id)) {
            ctx.getSource().sendFailure(Component.literal(id.getPath() + " is already unlocked"));
            return 0;
        }
        data.setDirty();
        grill24.fishtastic.tutorial.CompanionTutorialManager.onBondUnlocked(player);
        grill24.fishtastic.network.QuestSyncPacket.sendToPlayer(player, data);
        ctx.getSource().sendSuccess(() -> Component.literal("Unlocked the bond for " + id.getPath())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** Dev: forgets every stored bond, so a self-test scene can start from a locked world. */
    private static int resetBonds(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        grill24.fishtastic.server.FishCatchBackups.beforeDestructiveCommand(ctx.getSource().getServer(),
                "companion_resetbonds");
        var data = grill24.fishtastic.server.FishCatchSavedData.getOrCreate(ctx.getSource().getServer());
        data.getOrCreateQuestState(player).clearCompanionBonds();
        data.setDirty();
        grill24.fishtastic.network.QuestSyncPacket.sendToPlayer(player, data);
        ctx.getSource().sendSuccess(() -> Component.literal("Cleared every stored companion bond")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int on(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem().copyWithCount(1);
        Companions.BondResult result = Companions.bond(player, held);
        if (result != Companions.BondResult.BONDED) {
            ctx.getSource().sendFailure(Component.literal("Couldn't bond: " + result));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Bonded with " + held.getHoverName().getString())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int off(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!Companions.unequip(player)) {
            ctx.getSource().sendFailure(Component.literal("No companion equipped"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Companion unequipped").withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, Optional<Float> size) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        Identifier id = IdentifierArgument.getId(ctx, "species");
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty() || !item.get().builtInRegistryHolder().is(FishtasticItemTags.FISH)) {
            ctx.getSource().sendFailure(Component.literal("Not a fish: " + id));
            return 0;
        }
        ItemStack fish = new ItemStack(item.get());
        size.ifPresent(cm -> ItemSizeHelper.setSize(fish, cm));
        Companions.equip(player, fish);
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + player.getName().getString() + " a "
                + CompanionStorage.get(player).getHoverName().getString() + " companion").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int client(CommandContext<CommandSourceStack> ctx, java.util.function.Supplier<String> action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.gameRenderer == null) {
            ctx.getSource().sendFailure(Component.literal("Client-side only — run this in single-player."));
            return 0;
        }
        String message = action.get();
        ctx.getSource().sendSuccess(() -> Component.literal(message).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
