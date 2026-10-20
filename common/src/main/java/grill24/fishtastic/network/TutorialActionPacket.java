package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.tutorial.CompanionTutorialManager;
import grill24.fishtastic.tutorial.EncyclopediaTutorialManager;
import grill24.fishtastic.tutorial.TutorialManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Self-service help actions only affect the sending player's lessons. */
public record TutorialActionPacket(Action action) implements CustomPacketPayload {
    public enum Action {
        DISMISS_ALL, DISMISS_FISHING, DISMISS_ENCYCLOPEDIA, DISMISS_COMPANIONS,
        REPLAY_FISHING, REPLAY_ENCYCLOPEDIA, REPLAY_COMPANIONS
    }

    public static final Type<TutorialActionPacket> TYPE = new Type<>(Fishtastic.id("tutorial_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TutorialActionPacket> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8.map(Action::valueOf, Action::name),
                    TutorialActionPacket::action, TutorialActionPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClientToServer(TutorialActionPacket packet, FishtasticPackets.IPacketContext ctx) {
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
