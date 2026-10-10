package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.client.companion.CompanionSpike;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Dev command for the fish companion Phase 0 spike (docs/fish-companions.md §12). Single-player
 * only — it drives client state directly, the same way {@link CelebrationCommand} does.
 *
 * <pre>
 *   /fishtastic companion on        — companion from the held fish (a bluegill if none)
 *   /fishtastic companion off
 *   /fishtastic companion minigame  — opens a demo minigame bar so the fish docks without fishing
 *   /fishtastic companion stopgame  — closes it
 * </pre>
 */
public class CompanionSpikeCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("companion")
                .requires(FishtasticPermissions.gamemaster())
                .then(Commands.literal("on").executes(CompanionSpikeCommand::on))
                .then(Commands.literal("off").executes(ctx -> run(ctx, () -> {
                    CompanionSpike.disable();
                    return "Companion off";
                })))
                .then(Commands.literal("minigame").executes(ctx -> run(ctx, () -> {
                    Minecraft mc = Minecraft.getInstance();
                    ((IGameRendererExtension) mc.gameRenderer).fishtastic$displayItemActivation(new FishingMinigameAnimation());
                    return "Demo minigame open";
                })))
                .then(Commands.literal("stopgame").executes(ctx -> run(ctx, () -> {
                    ((IGameRendererExtension) Minecraft.getInstance().gameRenderer).fishtastic$cancelCurrentAnimation();
                    return "Demo minigame closed";
                })));
    }

    private static int on(CommandContext<CommandSourceStack> ctx) {
        return run(ctx, () -> {
            Minecraft mc = Minecraft.getInstance();
            ItemStack held = mc.player.getMainHandItem();
            ItemStack fish = held.is(FishtasticItemTags.FISH) ? held : new ItemStack(FishtasticItems.BLUEGILL.value());
            CompanionSpike.enable(fish);
            return "Companion on: " + fish.getHoverName().getString();
        });
    }

    private static int run(CommandContext<CommandSourceStack> ctx, java.util.function.Supplier<String> action) {
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
