package grill24.fishtastic.companion.forge;

import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionPartyClientCache;
import grill24.fishtastic.network.CompanionPartySyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Forge backing for {@link grill24.fishtastic.companion.CompanionStorage}. */
public final class CompanionStorageImpl {
    private CompanionStorageImpl() {}

    public static CompanionParty party(Player player) {
        if (player.level().isClientSide()) {
            return CompanionPartyClientCache.get(player.getUUID());
        }
        MinecraftServer server = player.getServer();
        if (server == null) return CompanionParty.EMPTY;
        return CompanionPartySavedData.getOrCreate(server).get(player.getUUID());
    }

    public static void setParty(Player player, CompanionParty party) {
        if (player.level().isClientSide()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        CompanionPartySavedData.getOrCreate(server).set(player.getUUID(), party);
        // Prototype visibility: every online player gets the owner's party (owner + watchers).
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            CompanionPartySyncPacket.sendToPlayer(online, player.getUUID(), party);
        }
    }
}
