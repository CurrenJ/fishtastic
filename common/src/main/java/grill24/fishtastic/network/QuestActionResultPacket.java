package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Correlated, authoritative receipt for quest claims, purchases and shop rerolls. */
public record QuestActionResultPacket(long requestId, String action, Identifier target, String result) implements CustomPacketPayload {
    public static final Type<QuestActionResultPacket> TYPE = new Type<>(Fishtastic.id("quest_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, QuestActionResultPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, QuestActionResultPacket::requestId,
            ByteBufCodecs.stringUtf8(16), QuestActionResultPacket::action,
            Identifier.STREAM_CODEC, QuestActionResultPacket::target,
            ByteBufCodecs.stringUtf8(32), QuestActionResultPacket::result, QuestActionResultPacket::new);
    private static final Map<ServerPlayer, LinkedHashMap<Long, QuestActionResultPacket>> RECEIPTS = new WeakHashMap<>();
    public static Consumer<QuestActionResultPacket> clientHandler = packet -> {};

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    /** Runs on the server thread. Retransmitting a request can never charge or grant twice. */
    public static void respond(ServerPlayer player, long requestId, String action, Identifier target, Supplier<String> operation) {
        var receipts = RECEIPTS.computeIfAbsent(player, key -> new LinkedHashMap<>());
        QuestActionResultPacket previous = requestId > 0 ? receipts.get(requestId) : null;
        QuestActionResultPacket receipt;
        if (previous != null) {
            receipt = previous.action().equals(action) && previous.target().equals(target) ? previous
                    : new QuestActionResultPacket(requestId, action, target, "duplicate");
        } else {
            receipt = new QuestActionResultPacket(requestId, action, target, operation.get());
            if (requestId > 0) {
                receipts.put(requestId, receipt);
                if (receipts.size() > 64) receipts.remove(receipts.keySet().iterator().next());
            }
        }
        // Always refresh rejected/stale UI too. The receipt follows its authoritative snapshot.
        QuestSyncPacket.sendToPlayer(player, FishCatchSavedData.getOrCreate(player.level().getServer()));
        player.connection.send(new ClientboundCustomPayloadPacket(receipt));
    }

    public static void handleServerToClient(QuestActionResultPacket packet, FishtasticPackets.IPacketContext context) {
        context.enqueueWork(() -> clientHandler.accept(packet));
    }
}
