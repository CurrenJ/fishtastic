package grill24.fishtastic.network;

import grill24.fishtastic.companion.Companions;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent CLIENT → SERVER to set the name of the companion in one party slot (docs/fish-companions.md
 * §7). The name is the fish item's custom name, so this rewrites that component on the stored stack;
 * an empty string clears it back to the species name. The server validates the slot and length and
 * answers with a {@link CompanionFeedbackPacket}.
 */
public record RenameCompanionPacket(int slot, String name) implements FishtasticPayload {

    /** Long enough for a real name, short enough to render; server clamps to this too. */
    public static final int MAX_NAME_LENGTH = 32;

    public static final FishtasticPayload.PayloadType<RenameCompanionPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.RENAME_COMPANION_ID);

    public static final BufCodec<RenameCompanionPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.VAR_INT, RenameCompanionPacket::slot,
                    BufCodecs.STRING_UTF8, RenameCompanionPacket::name,
                    RenameCompanionPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(RenameCompanionPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player) {
                boolean renamed = grill24.fishtastic.companion.CompanionParty.validSlot(packet.slot())
                        && Companions.rename(player, packet.slot(), packet.name());
                CompanionFeedbackPacket.sendToPlayer(player, renamed
                        ? CompanionFeedbackPacket.Outcome.RENAMED
                        : CompanionFeedbackPacket.Outcome.NOTHING_TO_RENAME);
            }
        });
    }
}
