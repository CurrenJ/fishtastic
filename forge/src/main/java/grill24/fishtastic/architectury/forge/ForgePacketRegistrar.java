package grill24.fishtastic.architectury.forge;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.FishtasticPackets;
import grill24.fishtastic.network.FishtasticTransport;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.function.Supplier;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.payload.PayloadFlow;

/**
 * Forge 52 payload-channel registration. {@code PayloadFlow.add} invokes on the network thread;
 * the wrapper hands common {@code enqueueWork} to the Forge context so handlers keep their usual
 * main-thread scheduling.
 */
public class ForgePacketRegistrar implements FishtasticPackets.IPacketRegistrar {
    public static Channel<CustomPacketPayload> CHANNEL;

    private final PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow;

    /**
     * Client-only player lookup, installed by {@code FishtasticForgeClient}. Kept as a supplier so
     * this class never names a client-only type: on a dedicated server the previous direct
     * {@code Minecraft.getInstance()} fallback loaded {@code LocalPlayer} and crashed the join.
     */
    private static Supplier<Player> clientPlayer = () -> null;

    private ForgePacketRegistrar(PayloadFlow<RegistryFriendlyByteBuf, CustomPacketPayload> flow) {
        this.flow = flow;
    }

    public static void register() {
        var play = ChannelBuilder.named(Fishtastic.id("main"))
                .networkProtocolVersion(1)
                .payloadChannel()
                .play();

        var serverbound = play.serverbound();
        FishtasticPackets.registerClientToServerPackets(new ForgePacketRegistrar(serverbound));
        var clientbound = play.clientbound();
        FishtasticPackets.registerServerToClientPackets(new ForgePacketRegistrar(clientbound));

        CHANNEL = clientbound.build();
        FishtasticTransport.install(new ForgeSender());
        Fishtastic.LOGGER.info("Registered Forge network packets");
    }

    public static void installClientPlayer(Supplier<Player> supplier) {
        clientPlayer = supplier;
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientToServer(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            FishtasticPackets.IPacketHandler<T> handler) {
        add(type, codec, handler);
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerToClient(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            FishtasticPackets.IPacketHandler<T> handler) {
        add(type, codec, handler);
    }

    @SuppressWarnings("unchecked")
    private <T extends CustomPacketPayload> void add(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec,
            FishtasticPackets.IPacketHandler<T> handler) {
        StreamCodec<RegistryFriendlyByteBuf, T> boundedCodec = new StreamCodec<>() {
            @SuppressWarnings("unchecked")
            private final StreamCodec<RegistryFriendlyByteBuf, T> delegate =
                    (StreamCodec<RegistryFriendlyByteBuf, T>) codec;

            @Override
            public T decode(RegistryFriendlyByteBuf buf) {
                // Forge 52 hands the payload buffer to the codec with its reader at the end
                // (the vanilla packet already consumed the payload bytes). Rewind this isolated
                // payload buffer before decoding; each payload has its own buffer.
                buf.readerIndex(0);
                return delegate.decode(buf);
            }

            @Override
            public void encode(RegistryFriendlyByteBuf buf, T value) {
                delegate.encode(buf, value);
            }
        };
        flow.add(type, boundedCodec, (payload, context) -> {
            try {
                handler.handle(payload, new ForgePacketContext(context));
            } finally {
                // Forge's CustomPayloadEvent.Context does not mark a payload handled on our behalf.
                // If the flag stays false, NetworkInstance#dispatch reports "not handled" and Forge
                // falls through to vanilla dispatch, which logs "Unknown custom packet payload" and
                // runs the generic payload path for a payload it just decoded for us.
                context.setPacketHandled(true);
            }
        });
    }

    private static final class ForgePacketContext implements FishtasticPackets.IPacketContext {
        private final CustomPayloadEvent.Context context;

        ForgePacketContext(CustomPayloadEvent.Context context) {
            this.context = context;
        }

        @Override
        public Player getPlayer() {
            if (context.isClientSide()) {
                return clientPlayer.get();
            }
            return context.getSender();
        }

        @Override
        public void enqueueWork(Runnable runnable) {
            context.enqueueWork(runnable);
        }
    }

    private static final class ForgeSender implements FishtasticTransport.Sender {
        @Override
        public void sendToServer(CustomPacketPayload payload) {
            CHANNEL.send(payload, PacketDistributor.SERVER.noArg());
        }

        @Override
        public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
            CHANNEL.send(payload, PacketDistributor.PLAYER.with(player));
        }

        @Override
        public void sendToAll(MinecraftServer server, CustomPacketPayload payload) {
            CHANNEL.send(payload, PacketDistributor.ALL.noArg());
        }
    }
}
