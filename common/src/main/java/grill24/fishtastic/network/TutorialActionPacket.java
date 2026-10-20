package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import grill24.fishtastic.tutorial.CompanionTutorialManager;
import grill24.fishtastic.tutorial.EncyclopediaTutorialManager;
import grill24.fishtastic.tutorial.TutorialManager;
import net.minecraft.server.level.ServerPlayer;

/** Self-service help actions only affect the sending player's lessons. */
public record TutorialActionPacket(Action action) implements FishtasticPayload {
    public enum Action {
        DISMISS_ALL, DISMISS_FISHING, DISMISS_ENCYCLOPEDIA, DISMISS_COMPANIONS,
        REPLAY_FISHING, REPLAY_ENCYCLOPEDIA, REPLAY_COMPANIONS
    }

    public static final FishtasticPayload.PayloadType<TutorialActionPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("tutorial_action"));
    public static final BufCodec<TutorialActionPacket> STREAM_CODEC =
            BufCodec.composite(BufCodecs.STRING_UTF8.map(Action::valueOf, Action::name),
                    TutorialActionPacket::action, TutorialActionPacket::new);

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(TutorialActionPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player) handle(player, packet.action());
        });
    }

    public static void handle(ServerPlayer player, Action action) {
        switch (action) {
            case DISMISS_ALL -> {
                TutorialManager.dismiss(player);
                EncyclopediaTutorialManager.dismiss(player);
                CompanionTutorialManager.dismiss(player);
            }
            case DISMISS_FISHING -> TutorialManager.dismiss(player);
            case DISMISS_ENCYCLOPEDIA -> EncyclopediaTutorialManager.dismiss(player);
            case DISMISS_COMPANIONS -> CompanionTutorialManager.dismiss(player);
            case REPLAY_FISHING -> TutorialManager.replay(player);
            case REPLAY_ENCYCLOPEDIA -> {
                TutorialManager.dismiss(player);
                EncyclopediaTutorialManager.replay(player);
            }
            case REPLAY_COMPANIONS -> {
                TutorialManager.dismiss(player);
                CompanionTutorialManager.reset(player);
            }
        }
    }
}
