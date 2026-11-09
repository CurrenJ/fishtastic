package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

/** The optional fish only supplies a species highlight; opening a menu never equips it. */
public record OpenCompanionMenuPacket(ItemStack species) implements FishtasticPayload {
    public static final FishtasticPayload.PayloadType<OpenCompanionMenuPacket> TYPE =
            new FishtasticPayload.PayloadType<>(Fishtastic.id("open_companion_menu"));
    public static final BufCodec<OpenCompanionMenuPacket> STREAM_CODEC =
            BufCodec.composite(BufCodecs.ITEM_STACK, OpenCompanionMenuPacket::species, OpenCompanionMenuPacket::new);

    @Override
    public FishtasticPayload.PayloadType<?> type() {
        return TYPE;
    }

    public static void handleClientToServer(OpenCompanionMenuPacket packet, FishtasticPacketHandling.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player) {
                // The species focus is client-side (CompanionsScreen.focusOnNextOpen); the server only opens the menu.
                grill24.fishtastic.compat.GelatinOpenMenuCompat.openCompanionsMenu(player);
                grill24.fishtastic.tutorial.CompanionTutorialManager.onCompanionsTabOpened(player);
            }
        });
    }
}
