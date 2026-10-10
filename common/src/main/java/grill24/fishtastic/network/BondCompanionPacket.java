package grill24.fishtastic.network;

import grill24.fishtastic.companion.Companions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Sent CLIENT → SERVER to bond with a fish the player is carrying (docs/fish-companions.md §8.2).
 * {@code fish} describes the fish the player picked; the server finds an identical one itself
 * ({@link Companions#bond}) — it is never a slot index, and never trusted to exist.
 */
public record BondCompanionPacket(ItemStack fish) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<BondCompanionPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.BOND_COMPANION_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, BondCompanionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, BondCompanionPacket::fish,
                    BondCompanionPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(BondCompanionPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player) {
                Companions.bond(player, packet.fish());
            }
        });
    }
}
