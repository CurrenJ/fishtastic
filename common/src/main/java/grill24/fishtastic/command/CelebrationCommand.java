package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.util.CatchCelebration;
import grill24.fishtastic.util.FishQualityHelper;
import grill24.fishtastic.util.FishingMinigameAnimation;
import grill24.fishtastic.util.IGameRendererExtension;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Dev command that plays a catch celebration on demand, so its timings can be tuned without
 * fishing up a legendary for every iteration. Single-player only — it drives the client-side
 * animation directly, the same way {@link TestQuestNotifyCommand} drives the notification manager.
 *
 * <pre>
 *   /fishtastic celebration hero        — the full legendary sequence
 *   /fishtastic celebration discovery   — the shorter first-catch variant
 * </pre>
 *
 * The held item is used as the hero item when there is one, so any fish can be staged; for the
 * hero tier it's stamped as legendary so the quality outline shader renders the way it would on a
 * real catch.
 */
public class CelebrationCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("celebration")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("hero")
                        .executes(ctx -> execute(ctx, CatchCelebration.Tier.HERO)))
                .then(Commands.literal("discovery")
                        .executes(ctx -> execute(ctx, CatchCelebration.Tier.DISCOVERY)));
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, CatchCelebration.Tier tier) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.gameRenderer == null) {
            ctx.getSource().sendFailure(Component.literal("Client-side only — run this in single-player."));
            return 0;
        }

        ItemStack held = minecraft.player.getMainHandItem();
        ItemStack hero = (held.isEmpty() ? new ItemStack(Items.COD) : held).copy();
        hero.setCount(1);
        if (tier == CatchCelebration.Tier.HERO) {
            FishQualityHelper.setQuality(hero, FishQuality.Quality.LEGENDARY);
        }

        FishingMinigameAnimation preview = FishingMinigameAnimation.createCelebrationPreview(tier, hero);
        ((IGameRendererExtension) minecraft.gameRenderer).fishtastic$displayItemActivation(preview);

        ctx.getSource().sendSuccess(() -> Component.literal(
                        "Playing " + tier.name().toLowerCase() + " celebration with " + hero.getHoverName().getString())
                .withStyle(ChatFormatting.GREEN), false);
        return 1;
    }
}
