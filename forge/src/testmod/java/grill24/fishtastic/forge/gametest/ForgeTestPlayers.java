package grill24.fishtastic.forge.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.UUID;

/**
 * Forge's gametest player factory (B6.1), the counterpart of 1.21.1's {@code NeoForgeTestPlayers}.
 *
 * <p>Vanilla's {@link GameTestHelper#makeMockServerPlayerInLevel()} is unusable on Forge 47: it
 * joins the player on a bare {@code new Connection(SERVERBOUND)} with no Netty channel, and
 * Forge's patched {@code PlayerList.placeNewPlayer} calls {@code NetworkHooks.sendMCRegistryPackets},
 * whose {@code NetworkFilters.injectIfNecessary} dereferences {@code connection.channel().pipeline()}
 * — an NPE for every test that needs a joined player. This is the same mock player (random
 * profile, creative, not spectating) with an {@link EmbeddedChannel} attached, which gives the
 * connection a real pipeline; packets sent to it just queue in the embedded channel's outbound
 * buffer.
 *
 * <p>The player is also switched to creative explicitly. Fabric's gametest server builds its world
 * from vanilla's creative test settings, but Forge 47's boots through the dedicated-server
 * {@code Main}, so the world takes {@code server.properties}' {@code gamemode=survival} and a joining
 * player would lack the {@code abilities.instabuild} the vanilla mock player is expected to have.
 */
final class ForgeTestPlayers {

    private ForgeTestPlayers() {}

    static ServerPlayer create(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };

        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        // Registering the connection as a handler fires channelActive, which binds its channel.
        new EmbeddedChannel(connection);
        connection.setProtocol(ConnectionProtocol.PLAY);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(GameType.CREATIVE);
        return player;
    }
}
