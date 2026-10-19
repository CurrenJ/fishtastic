package grill24.fishtastic.network;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Common transport seam. Fabric/NeoForge keep vanilla payload-packet sending; Forge installs a
 * channel-based sender so payloads are encoded with its {@code Channel} (see
 * {@code ForgePacketRegistrar}), because directly sending a {@code ClientboundCustomPayloadPacket}
 * on Forge bypasses the channel's encoding/direction checks.
 */
public final class FishtasticTransport {
    public interface Sender {
        void sendToServer(CustomPacketPayload payload);
        void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);
        void sendToAll(MinecraftServer server, CustomPacketPayload payload);
    }

    private static Sender sender = new VanillaSender();

    private FishtasticTransport() {}

    public static void install(Sender newSender) {
        sender = newSender;
        grill24.fishtastic.Fishtastic.LOGGER.info("[transport] installed {}", newSender.getClass().getName());
    }

    public static void sendToServer(CustomPacketPayload payload) {
        sender.sendToServer(payload);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        sender.sendToPlayer(player, payload);
    }

    public static void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
        sender.sendToAll(server, payload);
    }

    private static final class VanillaSender implements Sender {
        @Override
        public void sendToServer(CustomPacketPayload payload) {
            var connection = Minecraft.getInstance().getConnection();
            if (connection != null) {
                connection.send(new ServerboundCustomPayloadPacket(payload));
            }
        }

        @Override
        public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
            player.connection.send(new ClientboundCustomPayloadPacket(payload));
        }

        @Override
        public void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                sendToPlayer(player, payload);
            }
        }
    }
}
