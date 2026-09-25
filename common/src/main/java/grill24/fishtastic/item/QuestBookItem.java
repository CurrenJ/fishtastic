package grill24.fishtastic.item;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.compat.GelatinOpenMenuCompat;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Opens the Quest Log on use. Wears the alert texture ({@link FishtasticDataComponents#HAS_ALERT})
 * whenever any quest is completed but not yet claimed — the same condition that drives the quest
 * log's own tab pips, see {@link grill24.fishtastic.client.QuestLogScreen#refreshTabAlerts}.
 */
public class QuestBookItem extends Item {

    public QuestBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MinecraftServer server = ((ServerLevel) level).getServer();
            QuestSyncPacket.sendToPlayer(serverPlayer, FishCatchSavedData.getOrCreate(server));
            GelatinOpenMenuCompat.openQuestLogMenu(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
    }

    /** Keeps {@link FishtasticDataComponents#HAS_ALERT} in sync with live server-side quest state. */
    @Override
    public void inventoryTick(ItemStack stack, Level tickLevel, Entity entity, int slotId, boolean selected) {
        super.inventoryTick(stack, tickLevel, entity, slotId, selected);
        // 26.1.2 only ticks inventories server-side; 1.21.1 ticks both sides.
        if (!(tickLevel instanceof ServerLevel level)) return;
        if (!(entity instanceof ServerPlayer player)) return;

        boolean alert = hasClaimableQuest(level.getServer(), player);
        if (FishtasticItemData.has(stack, FishtasticDataComponents.HAS_ALERT) != alert) {
            if (alert) {
                FishtasticItemData.set(stack, FishtasticDataComponents.HAS_ALERT, Unit.INSTANCE);
            } else {
                FishtasticItemData.remove(stack, FishtasticDataComponents.HAS_ALERT);
            }
        }
    }

    private static boolean hasClaimableQuest(MinecraftServer server, ServerPlayer player) {
        Registry<Quest> questRegistry;
        try {
            questRegistry = server.registryAccess().registryOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);
        } catch (Exception e) {
            return false;
        }

        PlayerQuestState state = FishCatchSavedData.getOrCreate(server).getOrCreateQuestState(player);
        for (Map.Entry<ResourceKey<Quest>, Quest> entry : questRegistry.entrySet()) {
            int target = entry.getValue().objective().effectiveTargetCount(server.registryAccess());
            if (state.canClaim(entry.getKey(), target)) return true;
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        Consumer<Component> builder = tooltip::add;

        if (FishtasticKeyBinds.openQuestLog != null && !FishtasticKeyBinds.openQuestLog.isUnbound()) {
            builder.accept(Component.translatable("tooltip.fishtastic.keybind_hint",
                            FishtasticKeyBinds.openQuestLog.getTranslatedKeyMessage())
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
