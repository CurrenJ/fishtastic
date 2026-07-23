package grill24.fishtastic.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import grill24.FishtasticRegistries;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.FishtasticFishItem;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Debug command for verifying zone gating without casting a line.
 * Usage:
 *   /fishtastic fishzone         — print the executing player's current Zone
 *   /fishtastic fishzone list    — print every fish eligible in the player's current Zone
 */
public class FishZoneCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("fishzone")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(FishZoneCommand::executeWhereAmI)
                .then(Commands.literal("list")
                        .executes(FishZoneCommand::executeList));
    }

    private static int executeWhereAmI(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = resolvePlayer(source);
        if (player == null) return 0;

        Set<FishProfile.Zone> zones = resolveZone(player);
        source.sendSuccess(() -> Component.literal("Current zone: " + zoneNames(zones))
                .withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    private static int executeList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = resolvePlayer(source);
        if (player == null) return 0;

        Set<FishProfile.Zone> zones = resolveZone(player);
        Registry<FishProfile> fishProfileRegistry = source.getServer().registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        Registry<Item> itemRegistry = source.getServer().registryAccess().lookupOrThrow(Registries.ITEM);

        List<String> eligible = new ArrayList<>();
        for (Holder<Item> holder : itemRegistry.getTagOrEmpty(ItemTags.FISHES)) {
            if (FishtasticFishItem.isZoneEligible(holder, fishProfileRegistry, zones)) {
                eligible.add(holder.unwrapKey().map(k -> k.identifier().toString()).orElse("?"));
            }
        }
        eligible.sort(String::compareTo);

        source.sendSuccess(() -> Component.literal("Zone " + zoneNames(zones) + " — "
                + eligible.size() + " eligible fish:").withStyle(ChatFormatting.GOLD), false);
        for (String id : eligible) {
            source.sendSuccess(() -> Component.literal("  " + id).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }

    private static Set<FishProfile.Zone> resolveZone(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        Holder<Biome> biome = player.level().getBiome(pos);
        return FishProfile.Zone.resolve(biome, pos.getY(), player.level().getSeaLevel());
    }

    private static String zoneNames(Set<FishProfile.Zone> zones) {
        return zones.stream().map(FishProfile.Zone::getSerializedName).collect(Collectors.joining(", "));
    }

    private static ServerPlayer resolvePlayer(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer sp) return sp;
        source.sendFailure(Component.literal("This command must be run as a player."));
        return null;
    }
}
