package grill24.fishtastic.network;

import grill24.fishtastic.companion.Companions;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Sent CLIENT → SERVER to equip a fish the player is carrying (wire id kept as {@code bond_companion}; docs/fish-companions.md
 * §5.3). {@code fish} describes the fish the player picked; the server finds an identical one itself
 * ({@link Companions#bond}) — never an inventory index, and never trusted to exist. {@code slot} is
 * the party slot to fill, or {@link Companions#FIRST_EMPTY}; the server validates it.
 */
public record EquipCompanionPacket(ItemStack fish, int slot) implements FishtasticPayload {

    /** Bonds into the first empty party slot. */
    public EquipCompanionPacket(ItemStack fish) {
        this(fish, Companions.FIRST_EMPTY);
    }

    public static final FishtasticPayload.PayloadType<EquipCompanionPacket> TYPE =
            new FishtasticPayload.PayloadType<>(FishtasticPackets.BOND_COMPANION_ID);

    public static final BufCodec<EquipCompanionPacket> STREAM_CODEC =
            BufCodec.composite(
                    BufCodecs.ITEM_STACK, EquipCompanionPacket::fish,
                    BufCodecs.VAR_INT, EquipCompanionPacket::slot,
                    EquipCompanionPacket::new
            );

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(EquipCompanionPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player) {
                Companions.BondResult result = Companions.bond(player, packet.fish(), packet.slot());
                CompanionFeedbackPacket.sendToPlayer(player, CompanionFeedbackPacket.of(result));
            }
        });
    }
}
