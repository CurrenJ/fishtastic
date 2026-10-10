package grill24.fishtastic.network;

import grill24.fishtastic.companion.Companions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Sent CLIENT → SERVER to give the equipped companion back (docs/fish-companions.md §8.1). */
public record UnequipCompanionPacket() implements CustomPacketPayload {

    public static final UnequipCompanionPacket INSTANCE = new UnequipCompanionPacket();

    public static final CustomPacketPayload.Type<UnequipCompanionPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.UNEQUIP_COMPANION_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, UnequipCompanionPacket> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(UnequipCompanionPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player) {
                Companions.unequip(player);
            }
        });
    }
}
