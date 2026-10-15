package grill24.fishtastic.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.companion.CompanionParty;
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
 *   /fishtastic companion debug &lt;bool&gt;                  — overlay: goal, drives, pose hint, temperament, sim cost
 *   /fishtastic companion switch [reset|&lt;name&gt; &lt;value&gt;] — list, set or reset the party switches (rebuilds when needed)
 *   /fishtastic companion temperament &lt;id&gt;|species      — force a temperament on your companion (or its own)
 *   /fishtastic companion fake hostile|cat|item|companion — a scripted thing for it to react to (clear removes them)
 *   /fishtastic companion predatorscene                   — your party's predator laps you now, its prey hides
 *   /fishtastic companion ghost add &lt;species&gt;...         — a second player with that party walks up (party spec §28, D31)
 *   /fishtastic companion ghost walk approach|stand|leave [blocks] — walk up (to [blocks]), stand, or walk off and leave
 *   /fishtastic companion ghost scene g1|g2 [late ticks]  — a scene with the ghost now (a late packet joins mid-path)
 *   /fishtastic companion ghost remove                    — the ghost logs out
 *   /fishtastic companion hurt                            — it sees you hurt (an aggressive fish bites)
 *   /fishtastic companion drowsy                          — as if you'd idled long enough for it to settle
 *   /fishtastic companion ground                          — a ground benthic (and its group) comes back down off the perch
 *   /fishtastic companion climb                           — a ground benthic climbs onto its perch (group: one after another)
 *   /fishtastic companion tumble                          — a perched ground benthic is knocked off (waits until perched)
 *   /fishtastic companion swell [gain]                    — show or set the upright benthics' push-off swell (default 2.5; 1 = tank's)
 *   /fishtastic companion catchdemo                       — open a demo bar with a fish of your companion's species
 *   /fishtastic companion catch                           — land the demo bar's first active target (celebration + greet)
 *   /fishtastic companion phantom &lt;species&gt; [size]      — another companion, swimming round an imaginary owner ahead
 *   /fishtastic companion phantom off                     — remove every dev phantom
 *   /fishtastic companion minigame                        — opens a demo minigame bar
 *   /fishtastic companion stopgame                        — closes it
 *   /fishtastic companion catchcount &lt;player&gt; &lt;species&gt; &lt;count&gt; — set one species' catch count
 *   /fishtastic companion unlock &lt;player&gt; &lt;species&gt;       — force-unlock a bond (fires the banner)
 *   /fishtastic companion resetbonds &lt;player&gt;             — forget every stored bond unlock
 *   /fishtastic companion hideothers &lt;bool&gt;              — hide other players' companions (persists)
 *   /fishtastic companion hideown &lt;bool&gt;                 — hide your own in first person (persists)
 *   /fishtastic companion waitatobber &lt;bool&gt;             — skip docking during the minigame (persists; docking is parked)
 * </pre>
 *
 * <p>The visual triggers ({@code debug … waitatbobber}) drive client state directly, the same way
 * {@link CelebrationCommand} does, so they only work in single-player — they exist so every
 * companion animation and interaction can be seen solo, without a second player or a headless run.
 *
 * <p>This class must never <i>use</i> {@code mc.player} beyond the null-compare in
 * {@link #client} — no method calls on it, no passing it as an argument. Doing so forces
 * {@code LocalPlayer} to resolve when this class is verified, and Fabric refuses to load vanilla
 * client classes in a server environment, which crashes {@code :fabric:runGametest} at command
 * registration. That is why every dev action lives in
 * {@code grill24.fishtastic.client.companion.ClientCompanions} (client-only, loaded only when a
 * command actually runs) and this class only ever calls into it, plus the pure
 * {@code FishtasticClientConfig} — found the hard way, after an afternoon of bisecting.
 */
public class CompanionCommand {

    private static int feature(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String kind = StringArgumentType.getString(ctx, "kind");
        if (kind.equals("jukebox") || kind.equals("notes"))
            return client(ctx, () -> grill24.fishtastic.client.companion.CompanionFeatureDev.music(kind));
        var player = ctx.getSource().getPlayerOrException();
        var pos = player.blockPosition().relative(player.getDirection(), kind.equals("light") ? 5 : kind.equals("warm") ? 4 : 2);
        var state = switch (kind) {
            case "water" -> net.minecraft.world.level.block.Blocks.WATER.defaultBlockState();
            case "light" -> net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState();
            case "warm" -> net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState();
            default -> null;
        };
        if (state == null) { ctx.getSource().sendFailure(Component.literal("Unknown feature: " + kind)); return 0; }
        player.level().setBlockAndUpdate(pos, state);
        ctx.getSource().sendSuccess(() -> Component.literal("Placed companion feature: " + kind), false);
        return 1;
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("companion")
                .requires(FishtasticPermissions.gamemaster())
                .then(Commands.literal("on").executes(CompanionCommand::on))
                .then(Commands.literal("off").executes(ctx -> off(ctx, Optional.empty()))
                        .then(slotArgument().executes(ctx -> off(ctx, Optional.of(slotOf(ctx))))))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("species", IdentifierArgument.id())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                BuiltInRegistries.ITEM.stream()
                                                        .filter(item -> item.builtInRegistryHolder().is(FishtasticItemTags.FISH))
                                                        .map(item -> BuiltInRegistries.ITEM.getKey(item).toString()),
                                                builder))
                                        .executes(ctx -> give(ctx, Optional.empty(), Optional.empty()))
                                        .then(Commands.argument("size", FloatArgumentType.floatArg(1f, 2000f))
                                                .executes(ctx -> give(ctx, Optional.of(FloatArgumentType.getFloat(ctx, "size")), Optional.empty()))
                                                .then(slotArgument().executes(ctx -> give(ctx,
                                                        Optional.of(FloatArgumentType.getFloat(ctx, "size")), Optional.of(slotOf(ctx)))))))))
                .then(Commands.literal("debug")
                        // Which party slot the overlay and the dev triggers act on (the lead by default).
                        .then(Commands.literal("slot").then(slotArgument().executes(ctx -> client(ctx,
                                () -> grill24.fishtastic.client.companion.ClientCompanions.devSlot(slotOf(ctx))))))
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> client(ctx,
                                () -> grill24.fishtastic.client.companion.ClientCompanions.devOverlay(
                                        BoolArgumentType.getBool(ctx, "value"))))))
                // Party switches, live (party spec Phase 3c): list, set one, or reset them all.
                .then(Commands.literal("switch")
                        .executes(ctx -> client(ctx, grill24.fishtastic.client.companion.ClientCompanions::devSwitchList))
                        .then(Commands.literal("reset").executes(ctx -> client(ctx,
                                grill24.fishtastic.client.companion.ClientCompanions::devSwitchReset)))
                        .then(Commands.argument("name", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        grill24.fishsim.companion.CompanionTunables.DEV_SWITCHES, builder))
                                .then(Commands.argument("value", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                "anchoredPredatorDuckSeconds".equals(StringArgumentType.getString(ctx, "name"))
                                                        ? java.util.List.of("0", "2") : java.util.List.of("on", "off"), builder))
                                        .executes(ctx -> client(ctx,
                                                () -> grill24.fishtastic.client.companion.ClientCompanions.devSwitch(
                                                        StringArgumentType.getString(ctx, "name"),
                                                        StringArgumentType.getString(ctx, "value")))))))
                .then(Commands.literal("temperament")
                        .then(Commands.literal("species").executes(ctx -> client(ctx,
                                () -> grill24.fishtastic.client.companion.ClientCompanions.devTemperament(null))))
                        .then(Commands.argument("id", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.stream.Stream.concat(java.util.stream.Stream.of("baseline"),
                                                grill24.fishsim.companion.Temperament.ALL.stream()
                                                        .map(grill24.fishsim.companion.Temperament::name)),
                                        builder))
                                .executes(ctx -> client(ctx,
                                        () -> grill24.fishtastic.client.companion.ClientCompanions.devTemperament(
                                                StringArgumentType.getString(ctx, "id"))))))
                .then(Commands.literal("fake")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        java.util.List.of("hostile", "cat", "item", "companion", "clear"), builder))
                                .executes(ctx -> {
                                    String kind = StringArgumentType.getString(ctx, "kind");
                                    if (!java.util.List.of("hostile", "cat", "item", "companion", "clear").contains(kind)) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown fake: " + kind));
                                        return 0;
                                    }
                                    return client(ctx, () -> grill24.fishtastic.client.companion.ClientCompanions.devFake(kind));
                                })))
                // The predator scene (party D21, G4) now: your predator laps you, its prey hides.
                .then(Commands.literal("predatorscene").executes(ctx -> client(ctx,
                        grill24.fishtastic.client.companion.ClientCompanions::devPredatorScene)))
                // The ghost owner (party spec §28.3, D31): cross-player scenes, alone.
                .then(Commands.literal("ghost")
                        .then(Commands.literal("add")
                                .then(Commands.argument("species", IdentifierArgument.id()).suggests(CompanionCommand::fishSuggestions)
                                        .executes(ctx -> ghostAdd(ctx, 1))
                                        .then(Commands.argument("species2", IdentifierArgument.id()).suggests(CompanionCommand::fishSuggestions)
                                                .executes(ctx -> ghostAdd(ctx, 2))
                                                .then(Commands.argument("species3", IdentifierArgument.id()).suggests(CompanionCommand::fishSuggestions)
                                                        .executes(ctx -> ghostAdd(ctx, 3))))))
                        .then(Commands.literal("walk")
                                .then(Commands.literal("approach").executes(ctx -> ghostWalk(ctx, grill24.fishtastic.server.CompanionGhost.Mode.APPROACH, grill24.fishtastic.server.CompanionGhost.STAND_OFF))
                                        .then(Commands.argument("blocks", FloatArgumentType.floatArg(1f, 6f)).executes(ctx -> ghostWalk(ctx,
                                                grill24.fishtastic.server.CompanionGhost.Mode.APPROACH, FloatArgumentType.getFloat(ctx, "blocks")))))
                                .then(Commands.literal("stand").executes(ctx -> ghostWalk(ctx, grill24.fishtastic.server.CompanionGhost.Mode.STAND, grill24.fishtastic.server.CompanionGhost.STAND_OFF)))
                                .then(Commands.literal("leave").executes(ctx -> ghostWalk(ctx, grill24.fishtastic.server.CompanionGhost.Mode.LEAVE, grill24.fishtastic.server.CompanionGhost.STAND_OFF))))
                        .then(Commands.literal("scene")
                                .then(Commands.literal("g1").executes(ctx -> ghostScene(ctx, grill24.fishtastic.companion.CompanionSceneType.G1_SAME_SPECIES, 0))
                                        .then(Commands.argument("late", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 600))
                                                .executes(ctx -> ghostScene(ctx, grill24.fishtastic.companion.CompanionSceneType.G1_SAME_SPECIES, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "late")))))
                                .then(Commands.literal("g2").executes(ctx -> ghostScene(ctx, grill24.fishtastic.companion.CompanionSceneType.G2_FREE_SWIM_EIGHT, 0))
                                        .then(Commands.argument("late", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 600))
                                                .executes(ctx -> ghostScene(ctx, grill24.fishtastic.companion.CompanionSceneType.G2_FREE_SWIM_EIGHT, com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "late"))))))
                        .then(Commands.literal("remove").executes(ctx -> {
                            grill24.fishtastic.server.CompanionGhost.remove(ctx.getSource().getServer());
                            ctx.getSource().sendSuccess(() -> Component.literal("Ghost removed").withStyle(ChatFormatting.GREEN), false);
                            return 1;
                        }))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal(grill24.fishtastic.server.CompanionGhost.describe()), false);
                            return 1;
                        }))
                .then(Commands.literal("hurt").executes(ctx -> client(ctx,
                        grill24.fishtastic.client.companion.ClientCompanions::devHurt)))
                .then(Commands.literal("drowsy").executes(ctx -> client(ctx,
                        grill24.fishtastic.client.companion.ClientCompanions::devDrowsy)))
                .then(Commands.literal("feature")
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                        java.util.List.of("jukebox", "notes", "water", "light", "warm"), b))
                                .executes(CompanionCommand::feature)))
                .then(Commands.literal("mood")
                        .then(Commands.argument("mood", StringArgumentType.word())
                                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        java.util.List.of("cold", "warm", "glum", "glee", "sleepy", "moon", "off"), b))
                                .executes(ctx -> {
                                    String mood = StringArgumentType.getString(ctx, "mood");
                                    return client(ctx, () -> grill24.fishtastic.client.companion.ClientCompanions.devMood(mood));
                                })))
                .then(Commands.literal("ground").executes(ctx -> client(ctx,
                        grill24.fishtastic.client.companion.ClientCompanions::devGround)))
                .then(Commands.literal("climb").executes(ctx -> client(ctx,
                        grill24.fishtastic.client.companion.ClientCompanions::devClimb)))
                .then(Commands.literal("tumble").executes(ctx -> client(ctx,
                        grill24.fishtastic.client.companion.ClientCompanions::devTumble)))
                .then(Commands.literal("swell")
                        .executes(ctx -> client(ctx, () -> "Upright benthic swell gain: "
                                + grill24.fishtastic.client.renderer.CompanionRender.uprightSwell
                                + " (default " + grill24.fishtastic.client.renderer.CompanionRender.DEFAULT_UPRIGHT_SWELL + ")"))
                        .then(Commands.argument("gain", FloatArgumentType.floatArg(0f, 8f)).executes(ctx -> {
                            float gain = FloatArgumentType.getFloat(ctx, "gain");
                            return client(ctx, () -> {
                                grill24.fishtastic.client.renderer.CompanionRender.uprightSwell = gain;
                                return "Upright benthic swell gain set to " + gain;
                            });
                        })))
                .then(Commands.literal("squeeze")
                        .executes(ctx -> client(ctx, () -> "Drifter squeeze gain: "
                                + grill24.fishtastic.client.renderer.CompanionRender.driftSqueeze
                                + " (default " + grill24.fishtastic.client.renderer.CompanionRender.DEFAULT_DRIFT_SQUEEZE + ")"))
                        .then(Commands.argument("gain", FloatArgumentType.floatArg(0f, 8f)).executes(ctx -> {
                            float gain = FloatArgumentType.getFloat(ctx, "gain");
                            return client(ctx, () -> {
                                grill24.fishtastic.client.renderer.CompanionRender.driftSqueeze = gain;
                                return "Drifter squeeze gain set to " + gain;
                            });
                        })))
                .then(Commands.literal("catchdemo").executes(ctx -> client(ctx,
                        grill24.fishtastic.client.companion.ClientCompanions::devOpenCatchDemo)))
                .then(Commands.literal("catch").executes(ctx -> client(ctx,
                        grill24.fishtastic.client.companion.ClientCompanions::devForceCatch)))
                .then(Commands.literal("phantom")
                        .then(Commands.literal("off").executes(ctx -> client(ctx, () -> {
                            grill24.fishtastic.client.companion.ClientCompanions.clearPhantoms();
                            return "Dev phantoms removed";
                        })))
                        .then(Commands.argument("species", IdentifierArgument.id())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        BuiltInRegistries.ITEM.stream()
                                                .filter(item -> item.builtInRegistryHolder().is(FishtasticItemTags.FISH))
                                                .map(item -> BuiltInRegistries.ITEM.getKey(item).toString()),
                                        builder))
                                .executes(ctx -> phantom(ctx, Optional.empty()))
                                .then(Commands.argument("size", FloatArgumentType.floatArg(1f, 2000f))
                                        .executes(ctx -> phantom(ctx, Optional.of(FloatArgumentType.getFloat(ctx, "size")))))))
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
                                .executes(CompanionCommand::resetBonds)))
                .then(Commands.literal("hideothers")
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                            boolean value = BoolArgumentType.getBool(ctx, "value");
                            grill24.fishtastic.client.FishtasticClientConfig.setHideOtherCompanions(value);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Other players' companions hidden: " + value + " (persisted)"), false);
                            return 1;
                        })))
                .then(Commands.literal("hideown")
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                            boolean value = BoolArgumentType.getBool(ctx, "value");
                            grill24.fishtastic.client.FishtasticClientConfig.setHideOwnCompanionFirstPerson(value);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Own companion hidden in first person: " + value + " (persisted)"), false);
                            return 1;
                        })))
                .then(Commands.literal("waitatbobber")
                        .then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> {
                            boolean value = BoolArgumentType.getBool(ctx, "value");
                            grill24.fishtastic.client.FishtasticClientConfig.setCompanionWaitsAtBobber(value);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                    "Companion waits at the bobber: " + value + " (persisted)"), false);
                            return 1;
                        })));
    }

    /**
     * Parks a dev phantom companion (an ownerless second fish) beside the player. Validation is
     * server-safe; the client action lives in {@code ClientCompanions} for the same reason as
     * {@code catchdemo} (see the class note).
     */
    private static int phantom(CommandContext<CommandSourceStack> ctx, Optional<Float> size) throws CommandSyntaxException {
        Identifier id = IdentifierArgument.getId(ctx, "species");
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty() || !item.get().builtInRegistryHolder().is(FishtasticItemTags.FISH)) {
            ctx.getSource().sendFailure(Component.literal("Not a fish: " + id));
            return 0;
        }
        ItemStack fish = new ItemStack(item.get());
        size.ifPresent(cm -> ItemSizeHelper.setSize(fish, cm));
        return client(ctx, () -> grill24.fishtastic.client.companion.ClientCompanions.devAddPhantom(fish));
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

    /** A party slot as players count it: 1 (the lead) to 3. */
    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, Integer> slotArgument() {
        return Commands.argument("slot", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, CompanionParty.SIZE));
    }

    /** The {@code slot} argument as a party index (0 = lead). */
    private static int slotOf(CommandContext<CommandSourceStack> ctx) {
        return com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "slot") - 1;
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
        Identifier id = IdentifierArgument.getId(ctx, "species");
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        if (item.isEmpty() || !item.get().builtInRegistryHolder().is(FishtasticItemTags.FISH)) {
            ctx.getSource().sendFailure(Component.literal("Not a fish: " + id));
            return 0;
        }
        int target = slot.orElseGet(() -> CompanionStorage.party(player).firstEmpty());
        if (target < 0) {
            ctx.getSource().sendFailure(Component.literal("That party is full — name a slot (1-3) to replace"));
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

    /** The ghost owner is a dev tool for one player's own world: a dev run or a single-player world. */
    private static boolean ghostAllowed(CommandContext<CommandSourceStack> ctx) {
        if (grill24.fishtastic.env.DevEnvironmentCheck.isDevelopmentEnvironment() || ctx.getSource().getServer().isSingleplayer()) return true;
        ctx.getSource().sendFailure(Component.literal("The ghost owner is for dev runs and single-player worlds only"));
        return false;
    }

    /** The ghost can't be its own owner (an "execute as @p" can land on it, being the nearest player). */
    private static boolean isGhost(CommandContext<CommandSourceStack> ctx, ServerPlayer player) {
        if (!player.getUUID().equals(grill24.fishtastic.server.CompanionGhost.ID)) return false;
        ctx.getSource().sendFailure(Component.literal("Run this as yourself, not as the ghost"));
        return true;
    }

    private static int ghostAdd(CommandContext<CommandSourceStack> ctx, int count) throws CommandSyntaxException {
        if (!ghostAllowed(ctx)) return 0;
        ServerPlayer owner = ctx.getSource().getPlayerOrException();
        if (isGhost(ctx, owner)) return 0;
        java.util.List<ItemStack> fish = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            Identifier id = IdentifierArgument.getId(ctx, i == 0 ? "species" : "species" + (i + 1));
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            if (item.isEmpty() || !item.get().builtInRegistryHolder().is(FishtasticItemTags.FISH)) {
                ctx.getSource().sendFailure(Component.literal("Not a fish: " + id));
                return 0;
            }
            fish.add(new ItemStack(item.get()));
        }
        grill24.fishtastic.server.CompanionGhost.add(owner, fish);
        ctx.getSource().sendSuccess(() -> Component.literal("Ghost added, walking up: " + grill24.fishtastic.server.CompanionGhost.describe())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int ghostWalk(CommandContext<CommandSourceStack> ctx, grill24.fishtastic.server.CompanionGhost.Mode mode, double standOff) {
        if (!ghostAllowed(ctx)) return 0;
        if (grill24.fishtastic.server.CompanionGhost.ghost() == null) {
            ctx.getSource().sendFailure(Component.literal("No ghost: /fishtastic companion ghost add <species> first"));
            return 0;
        }
        grill24.fishtastic.server.CompanionGhost.walk(mode, standOff);
        ctx.getSource().sendSuccess(() -> Component.literal("Ghost: " + mode.name().toLowerCase()).withStyle(ChatFormatting.GREEN), false);
        return 1;
    }

    private static int ghostScene(CommandContext<CommandSourceStack> ctx, grill24.fishtastic.companion.CompanionSceneType type, int late) throws CommandSyntaxException {
        if (!ghostAllowed(ctx)) return 0;
        ServerPlayer owner = ctx.getSource().getPlayerOrException();
        if (isGhost(ctx, owner)) return 0;
        ServerPlayer ghost = grill24.fishtastic.server.CompanionGhost.ghost();
        if (ghost == null) {
            ctx.getSource().sendFailure(Component.literal("No ghost: /fishtastic companion ghost add <species> first"));
            return 0;
        }
        String result = grill24.fishtastic.server.CompanionSceneManager.devForceScene(owner.level(), owner, ghost, type, late);
        ctx.getSource().sendSuccess(() -> Component.literal(result).withStyle(ChatFormatting.GREEN), false);
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
