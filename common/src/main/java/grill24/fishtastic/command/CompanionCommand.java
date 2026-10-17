package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.companion.Companions;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.server.FishCatchBackups;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Dev command for fish companions (docs/fish-companions.md). Registered only in development.
 *
 * <pre>
 *   /fishtastic companion on                              - bond with the held fish (the real bond path)
 *   /fishtastic companion off [slot]                      - unequip; the fish comes back
 *   /fishtastic companion give &lt;player&gt; &lt;species&gt; [size [slot]] - equip a conjured fish directly
 *   /fishtastic companion catchcount &lt;player&gt; &lt;species&gt; &lt;count&gt; - set one species' catch count
 *   /fishtastic companion unlock &lt;player&gt; &lt;species&gt;       - force-unlock a bond
 *   /fishtastic companion resetbonds &lt;player&gt;             - forget every stored bond unlock
 * </pre>
 *
 * <p>Port note (F08-4): only the server-side subcommands exist on this branch. The source's
 * client-driven triggers (debug, switch, temperament, fake, mood, ground/climb/tumble, swell,
 * squeeze, catchdemo, phantom, minigame, hideothers/hideown, feature), the predator scene and the
 * ghost owner depend on the client companion layer (F09/F10), the scene manager (F11) and the
 * tutorial (F15); they arrive with those features. {@code unlock} omits the first-bond tutorial
 * call until F15 (TODO(F15)).
 */
public class CompanionCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("companion")
                .requires(FishtasticPermissions.gamemaster())
                .then(Commands.literal("on").executes(CompanionCommand::on))
                .then(Commands.literal("off").executes(ctx -> off(ctx, Optional.empty()))
                        .then(slotArgument().executes(ctx -> off(ctx, Optional.of(slotOf(ctx))))))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("species", ResourceLocationArgument.id())
                                        .suggests(CompanionCommand::fishSuggestions)
                                        .executes(ctx -> give(ctx, Optional.empty(), Optional.empty()))
                                        .then(Commands.argument("size", FloatArgumentType.floatArg(1f, 2000f))
                                                .executes(ctx -> give(ctx, Optional.of(FloatArgumentType.getFloat(ctx, "size")), Optional.empty()))
                                                .then(slotArgument().executes(ctx -> give(ctx,
                                                        Optional.of(FloatArgumentType.getFloat(ctx, "size")), Optional.of(slotOf(ctx)))))))))
                .then(Commands.literal("catchcount")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("species", ResourceLocationArgument.id())
                                        .suggests(CompanionCommand::fishSuggestions)
                                        .then(Commands.argument("count", IntegerArgumentType.integer(0))
                                                .executes(CompanionCommand::catchCount)))))
                .then(Commands.literal("unlock")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("species", ResourceLocationArgument.id())
                                        .suggests(CompanionCommand::fishSuggestions)
                                        .executes(CompanionCommand::unlock))))
                .then(Commands.literal("resetbonds")
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(CompanionCommand::resetBonds)));
    }

    /** Dev: sets one species' catch count, so a scene can park a species just below or at its gate. */
    private static int catchCount(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "species");
        int count = IntegerArgumentType.getInteger(ctx, "count");
        FishCatchBackups.beforeDestructiveCommand(ctx.getSource().getServer(), "companion_catchcount");
        var data = FishCatchSavedData.getOrCreate(ctx.getSource().getServer());
        data.setCatchCount(data.resolvePlayerKey(player), player.getName().getString(), id, count);
        FishEncyclopediaSyncPacket.sendToPlayer(player, data);
        ctx.getSource().sendSuccess(() -> Component.literal("Set " + id.getPath() + " catch count to " + count)
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** Dev: force-unlocks a bond the way the natural transition would - stored, banner queued. */
    private static int unlock(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "species");
        FishCatchBackups.beforeDestructiveCommand(ctx.getSource().getServer(), "companion_unlock");
        var data = FishCatchSavedData.getOrCreate(ctx.getSource().getServer());
        if (!data.getOrCreateQuestState(player).unlockCompanionBond(id)) {
            ctx.getSource().sendFailure(Component.literal(id.getPath() + " is already unlocked"));
            return 0;
        }
        data.setDirty();
        // TODO(F15): CompanionTutorialManager.onBondUnlocked(player)
        QuestSyncPacket.sendToPlayer(player, data);
        ctx.getSource().sendSuccess(() -> Component.literal("Unlocked the bond for " + id.getPath())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    /** Dev: forgets every stored bond, so a scene can start from a locked world. */
    private static int resetBonds(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        FishCatchBackups.beforeDestructiveCommand(ctx.getSource().getServer(), "companion_resetbonds");
        var data = FishCatchSavedData.getOrCreate(ctx.getSource().getServer());
        data.getOrCreateQuestState(player).clearCompanionBonds();
        data.setDirty();
        QuestSyncPacket.sendToPlayer(player, data);
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

    /** A party slot as players count it: 1 (the lead) to 3. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> slotArgument() {
        return Commands.argument("slot", IntegerArgumentType.integer(1, CompanionParty.SIZE));
    }

    /** The {@code slot} argument as a party index (0 = lead). */
    private static int slotOf(CommandContext<CommandSourceStack> ctx) {
        return IntegerArgumentType.getInteger(ctx, "slot") - 1;
    }

    /** Returns one slot's companion, or with no slot named, the whole party. */
    private static int off(CommandContext<CommandSourceStack> ctx, Optional<Integer> slot) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        int returned = 0;
        for (int i = 0; i < CompanionParty.SIZE; i++) {
            if (slot.isPresent() && slot.get() != i) continue;
            if (Companions.unequip(player, i)) returned++;
        }
        if (returned == 0) {
            ctx.getSource().sendFailure(Component.literal(slot.isPresent() ? "No companion in that slot" : "No companions in your party"));
            return 0;
        }
        int count = returned;
        ctx.getSource().sendSuccess(() -> Component.literal(count == 1 ? "Companion unequipped" : count + " companions unequipped")
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    /**
     * Conjures a companion into a party slot: the named one (its previous fish returns to the
     * inventory), or the first empty one. A full party with no slot named changes nothing.
     */
    private static int give(CommandContext<CommandSourceStack> ctx, Optional<Float> size, Optional<Integer> slot)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
        ResourceLocation id = ResourceLocationArgument.getId(ctx, "species");
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty() || !item.get().builtInRegistryHolder().is(FishtasticItemTags.FISH)) {
            ctx.getSource().sendFailure(Component.literal("Not a fish: " + id));
            return 0;
        }
        int target = slot.orElseGet(() -> CompanionStorage.party(player).firstEmpty());
        if (target < 0) {
            ctx.getSource().sendFailure(Component.literal("That party is full - name a slot (1-3) to replace"));
            return 0;
        }
        ItemStack fish = new ItemStack(item.get());
        size.ifPresent(cm -> ItemSizeHelper.setSize(fish, cm));
        Companions.equip(player, target, fish);
        ctx.getSource().sendSuccess(() -> Component.literal("Gave " + player.getName().getString() + " a "
                + CompanionStorage.get(player, target).getHoverName().getString() + " companion in slot " + (target + 1))
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> fishSuggestions(
            CommandContext<CommandSourceStack> ctx, com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(BuiltInRegistries.ITEM.stream()
                .filter(item -> item.builtInRegistryHolder().is(FishtasticItemTags.FISH))
                .map(item -> BuiltInRegistries.ITEM.getKey(item).toString()), builder);
    }
}
