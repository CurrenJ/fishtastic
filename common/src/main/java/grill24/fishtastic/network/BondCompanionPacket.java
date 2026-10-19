package grill24.fishtastic.network;

import grill24.fishtastic.companion.Companions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Sent CLIENT → SERVER to bond with a fish the player is carrying (docs/fish-companions-party.md
 * §5.3). {@code fish} describes the fish the player picked; the server finds an identical one itself
 * ({@link Companions#bond}) — never an inventory index, and never trusted to exist. {@code slot} is
 * the party slot to fill, or {@link Companions#FIRST_EMPTY}; the server validates it.
 */
public record BondCompanionPacket(ItemStack fish, int slot) implements CustomPacketPayload {

    /** Bonds into the first empty party slot. */
    public BondCompanionPacket(ItemStack fish) {
        this(fish, Companions.FIRST_EMPTY);
    }

    public static final CustomPacketPayload.Type<BondCompanionPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.BOND_COMPANION_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, BondCompanionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC, BondCompanionPacket::fish,
                    net.minecraft.network.codec.ByteBufCodecs.VAR_INT, BondCompanionPacket::slot,
                    BondCompanionPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(BondCompanionPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player) {
                Companions.BondResult result = Companions.bond(player, packet.fish(), packet.slot());
                player.connection.send(new net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket(
                        new CompanionFeedbackPacket(CompanionFeedbackPacket.of(result))));
            }
        });
    }
}
