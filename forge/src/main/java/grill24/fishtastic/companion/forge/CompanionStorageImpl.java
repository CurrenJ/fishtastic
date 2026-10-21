package grill24.fishtastic.companion.forge;

import grill24.fishtastic.companion.CompanionParty;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;

/** F03 Forge backing; F05 adds the explicit owner/watcher sync and cache. */
public final class CompanionStorageImpl {
    private CompanionStorageImpl() {}

    public static CompanionParty party(Player player) {
        if (player.level().isClientSide()) {
            return grill24.fishtastic.companion.CompanionPartyClientCache.get(player.getUUID());
        }
        MinecraftServer server = player.level().getServer();
        return server == null ? CompanionParty.EMPTY : CompanionPartySavedData.getOrCreate(server).get(player.getUUID());
    }

    public static void setParty(Player player, CompanionParty party) {
        if (player.level().isClientSide()) return;
        MinecraftServer server = player.level().getServer();
        if (server == null) return;
        CompanionPartySavedData.getOrCreate(server).set(player.getUUID(), party);
        for (var online : server.getPlayerList().getPlayers()) {
            grill24.fishtastic.network.CompanionPartySyncPacket.sendToPlayer(
                    online, player.getUUID(), party);
        }
    }
}
