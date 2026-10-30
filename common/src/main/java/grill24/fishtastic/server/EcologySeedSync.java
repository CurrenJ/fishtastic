package grill24.fishtastic.server;

import grill24.fishtastic.ecology.EcologySeed;
import grill24.fishtastic.network.EcologySeedSyncPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Keeps every client holding the ecology seed of the dimension it is in. Driven from the server tick
 * instead of per-loader join and dimension-change events: a player who is new to the table, or whose
 * dimension differs from the last one sent, gets a {@link EcologySeedSyncPacket}. That covers login,
 * respawn and every dimension change identically on all loaders. Only the derived seed is sent.
 */
public final class EcologySeedSync {

    /**
     * What each player was last sent, with the player object it went to: a player who reconnects
     * between two ticks is a new object on a new connection and gets the seed again. Weak, like
     * {@link #tracked}, since the table outlives the server it was filled on.
     */
    private record Sent(WeakReference<ServerPlayer> player, String dimension) {
    }

    private static final Map<UUID, Sent> SENT = new HashMap<>();
    /**
     * The server the table belongs to; a different one (new world in the same JVM) starts the table
     * over. Weak, so a stopped integrated server is not kept alive from the title screen.
     */
    private static WeakReference<MinecraftServer> tracked = new WeakReference<>(null);

    private EcologySeedSync() {
    }

    /** The seed for a level: derived from the world seed and the dimension, never sent as such. */
    public static long seedFor(ServerLevel level) {
        return EcologySeed.derive(level.getSeed(), level.dimension().identifier().toString());
    }

    public static void tick(MinecraftServer server) {
        if (server != tracked.get()) {
            SENT.clear();
            tracked = new WeakReference<>(server);
        }
        var players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            SENT.clear();
            return;
        }
        for (ServerPlayer player : players) {
            ServerLevel level = (ServerLevel) player.level();
            String dimension = level.dimension().identifier().toString();
            Sent last = SENT.get(player.getUUID());
            if (last == null || last.player().get() != player || !last.dimension().equals(dimension)) {
                SENT.put(player.getUUID(), new Sent(new WeakReference<>(player), dimension));
                EcologySeedSyncPacket.sendToPlayer(player, dimension, seedFor(level));
            }
        }
        if (SENT.size() != players.size()) {
            SENT.keySet().removeIf(id -> players.stream().noneMatch(p -> p.getUUID().equals(id)));
        }
    }

}
