package grill24.fishtastic.network;

import grill24.FishtasticRegistries;
import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.core.Registry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Set;
import java.util.List;

public record PurchaseShopEntryPacket(Identifier entryId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<PurchaseShopEntryPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.PURCHASE_SHOP_ENTRY_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, PurchaseShopEntryPacket> STREAM_CODEC =
            StreamCodec.composite(
                    Identifier.STREAM_CODEC,
                    PurchaseShopEntryPacket::entryId,
                    PurchaseShopEntryPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(PurchaseShopEntryPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            var player = ctx.getPlayer();
            if (!(player instanceof ServerPlayer serverPlayer)) return;

            MinecraftServer server = ((ServerLevel) serverPlayer.level()).getServer();
            if (server == null) return;

            ResourceKey<ShopEntry> entryKey = ResourceKey.create(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY, packet.entryId());

            Registry<ShopEntry> shopRegistry;
            try {
                shopRegistry = server.registryAccess().lookupOrThrow(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY);
            } catch (Exception e) {
                return;
            }

            ShopEntry entry = shopRegistry.getOptional(entryKey).orElse(null);
            if (entry == null) return;

            FishCatchSavedData data = FishCatchSavedData.getOrCreate(server);
            PlayerQuestState state = data.getOrCreateQuestState(serverPlayer);

            long currentDay = server.overworld().getGameTime() / 24000L;
            // Re-derive the draw with this player's unlocks so a gated capstone entry can't be
            // bought by anyone who hasn't claimed the quest that unlocks it, whatever the client sent.
            Set<ResourceKey<ShopEntry>> activeToday = ShopEntry.getActiveDailyShop(
                    shopRegistry, currentDay, state.getShopRefreshCount(),
                    questKey -> state.getProgress(questKey).claimed());
            if (!activeToday.contains(entryKey)) return;

            if (!state.purchase(entryKey, entry)) return;

            grantRewards(serverPlayer, entry);
            data.setDirty();

            QuestSyncPacket.sendToPlayer(serverPlayer, data);
        });
    }

    /**
     * Delivers a purchased entry's rewards to the player, dropping at their feet whatever
     * doesn't fit rather than silently discarding a paid-for reward. Public so gametests can
     * exercise the full-inventory path directly without going through the registry/daily-draw
     * plumbing above — mirrors {@link grill24.fishtastic.server.FishingMinigameManager#seedSessionForTest}.
     */
    public static void grantRewards(ServerPlayer serverPlayer, ShopEntry entry) {
        for (ShopEntry.ShopReward reward : entry.reward()) {
            ItemStack stack = reward.toItemStack();
            if (stack.isEmpty()) continue;
            serverPlayer.getInventory().add(stack);
            // Inventory.add() only consumes what fits, leaving the remainder in `stack` -
            // drop it at the player's feet instead of silently discarding a paid-for reward.
            if (!stack.isEmpty()) serverPlayer.drop(stack, false);
        }
    }
}
