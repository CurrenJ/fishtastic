package grill24.fishtastic.network;

import grill24.fishtastic.network.FishtasticTransport;

import grill24.fishtastic.companion.Companions;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent CLIENT → SERVER to set the name of the companion in one party slot (docs/fish-companions.md
 * §7). The name is the fish item's custom name, so this rewrites that component on the stored stack;
 * an empty string clears it back to the species name. The server validates the slot and length and
 * answers with a {@link CompanionFeedbackPacket}.
 */
public record RenameCompanionPacket(int slot, String name) implements CustomPacketPayload {

    /** Long enough for a real name, short enough to render; server clamps to this too. */
    public static final int MAX_NAME_LENGTH = 32;

    public static final CustomPacketPayload.Type<RenameCompanionPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.RENAME_COMPANION_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameCompanionPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, RenameCompanionPacket::slot,
                    ByteBufCodecs.STRING_UTF8, RenameCompanionPacket::name,
                    RenameCompanionPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(RenameCompanionPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player) {
                boolean renamed = grill24.fishtastic.companion.CompanionParty.validSlot(packet.slot())
                        && Companions.rename(player, packet.slot(), packet.name());
                FishtasticTransport.sendToPlayer(player, new CompanionFeedbackPacket(renamed ? CompanionFeedbackPacket.Outcome.RENAMED : CompanionFeedbackPacket.Outcome.NOTHING_TO_RENAME));
            }
        });
    }
}
