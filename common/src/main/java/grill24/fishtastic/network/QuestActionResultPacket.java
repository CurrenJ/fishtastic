package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Correlated, authoritative receipt for quest claims, purchases and shop rerolls. */
public record QuestActionResultPacket(long requestId, String action, ResourceLocation target, String result) implements FishtasticPayload {
    public static final FishtasticPayload.PayloadType<QuestActionResultPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("quest_action_result"));
    public static final BufCodec<QuestActionResultPacket> STREAM_CODEC = BufCodec.composite(
            BufCodecs.VAR_LONG, QuestActionResultPacket::requestId,
            BufCodecs.stringUtf8(16), QuestActionResultPacket::action,
            BufCodecs.RESOURCE_LOCATION, QuestActionResultPacket::target,
            BufCodecs.stringUtf8(32), QuestActionResultPacket::result, QuestActionResultPacket::new);
    private static final Map<ServerPlayer, LinkedHashMap<Long, QuestActionResultPacket>> RECEIPTS = new WeakHashMap<>();
    public static Consumer<QuestActionResultPacket> clientHandler = packet -> {};

    @Override public FishtasticPayload.PayloadType<?> type() { return TYPE; }

    /** Runs on the server thread. Retransmitting a request can never charge or grant twice. */
    public static void respond(ServerPlayer player, long requestId, String action, ResourceLocation target, Supplier<String> operation) {
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
        NetworkApiSided.sendToPlayer(player, receipt, STREAM_CODEC);
    }

    public static void handleServerToClient(QuestActionResultPacket packet, FishtasticPacketHandling.IPacketContext context) {
        context.enqueueWork(() -> clientHandler.accept(packet));
    }
}
