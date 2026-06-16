package grill24.fishtastic.neoforge.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import java.util.UUID;

/**
 * NeoForge equivalent of the deprecated GameTestHelper.makeMockServerPlayerInLevel().
 *
 * Vanilla's mock connection never goes through the configuration-phase handshake that
 * registers NeoForge's modded payload channels, so NetworkRegistry.checkPacket() rejects
 * any custom packet sent to it (e.g. Fishtastic's OnDatapackSyncEvent listener, which sends
 * QuestSyncPacket/TutorialSyncPacket on every player join, including this synthetic one).
 * NetworkRegistry.configureMockConnection() exists specifically to pre-empt this for tests —
 * it just has to run before placeNewPlayer(), which vanilla's helper doesn't expose a hook for.
 */
public final class NeoForgeTestPlayers {

    private NeoForgeTestPlayers() {}

    public static ServerPlayer makeMockServerPlayerInLevel(GameTestHelper helper) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(
            helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation()) {
            @Override
            public GameType gameMode() {
                return GameType.CREATIVE;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
