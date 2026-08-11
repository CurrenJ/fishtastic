package grill24.fishtastic.item;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.client.FishEncyclopediaClientCache;
import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import grill24.fishtastic.data.EncyclopediaRewardSection;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.tutorial.EncyclopediaTutorialManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Opens the Fish Encyclopedia on use. Wears the alert texture ({@link FishtasticDataComponents#HAS_ALERT})
 * whenever any caught fish has an unlocked-but-unclaimed reward section — the same condition that
 * drives the encyclopedia screen's own green pip, see
 * {@link grill24.fishtastic.client.FishEncyclopediaClientHelper#fishHasUnclaimedReward}.
 */
public class FishopediaItem extends Item {

    public FishopediaItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MinecraftServer server = ((ServerLevel) level).getServer();
            FishEncyclopediaSyncPacket.sendToPlayer(serverPlayer, FishCatchSavedData.getOrCreate(server));
            GelatinOpenMenuCompat.openFishEncyclopediaMenu(serverPlayer);
            EncyclopediaTutorialManager.onEncyclopediaOpened(serverPlayer);
        }
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    /** Keeps {@link FishtasticDataComponents#HAS_ALERT} in sync with live server-side reward state. */
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        super.inventoryTick(stack, level, entity, slot);
        if (!(entity instanceof ServerPlayer player)) return;

        boolean alert = hasUnclaimedReward(level.getServer(), player);
        if (stack.has(FishtasticDataComponents.HAS_ALERT.value()) != alert) {
            if (alert) {
                stack.set(FishtasticDataComponents.HAS_ALERT.value(), Unit.INSTANCE);
            } else {
                stack.remove(FishtasticDataComponents.HAS_ALERT.value());
            }
        }
    }

    private static boolean hasUnclaimedReward(MinecraftServer server, ServerPlayer player) {
        Registry<FishProfile> fishRegistry;
        try {
            fishRegistry = server.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        } catch (Exception e) {
            return false;
        }

        FishCatchSavedData catchData = FishCatchSavedData.getOrCreate(server);
        UUID key = catchData.resolvePlayerKey(player);
        PlayerQuestState questState = catchData.getOrCreateQuestState(player);

        for (ResourceKey<FishProfile> fishKey : fishRegistry.registryKeySet()) {
            Identifier fishId = fishKey.identifier();
            int catchCount = catchData.getCatchCount(key, fishId);
            if (catchCount <= 0) continue;

            FishEncyclopediaEntry.UnlockThresholds thresholds = getThresholds(server, fishKey);
            for (EncyclopediaRewardSection section : EncyclopediaRewardSection.values()) {
                if (catchCount >= section.threshold(thresholds) && !questState.isEncyclopediaRewardClaimed(fishId, section)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static FishEncyclopediaEntry.UnlockThresholds getThresholds(MinecraftServer server, ResourceKey<FishProfile> fishKey) {
        try {
            Registry<FishEncyclopediaEntry> registry =
                    server.registryAccess().lookupOrThrow(FishtasticRegistries.FISH_ENCYCLOPEDIA_ENTRY_REGISTRY_KEY);
            ResourceKey<FishEncyclopediaEntry> entryKey =
                    ResourceKey.create(FishtasticRegistries.FISH_ENCYCLOPEDIA_ENTRY_REGISTRY_KEY, fishKey.identifier());
            return registry.getOptional(entryKey).map(FishEncyclopediaEntry::thresholds).orElse(FishEncyclopediaEntry.UnlockThresholds.DEFAULT);
        } catch (Exception e) {
            return FishEncyclopediaEntry.UnlockThresholds.DEFAULT;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                 Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);

        HolderLookup.Provider registries = context.registries();
        if (registries == null) return;
        HolderLookup.RegistryLookup<FishProfile> fishRegistry;
        try {
            fishRegistry = registries.lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        } catch (Exception e) {
            return;
        }

        int total = 0;
        int discovered = 0;
        for (var it = fishRegistry.listElementIds().iterator(); it.hasNext(); ) {
            ResourceKey<FishProfile> fishKey = it.next();
            total++;
            if (FishEncyclopediaClientCache.getCatchCount(fishKey.identifier()) > 0) discovered++;
        }

        builder.accept(Component.translatable("tooltip.fishtastic.fishopedia.progress", discovered, total)
                .withStyle(ChatFormatting.AQUA));
    }
}
