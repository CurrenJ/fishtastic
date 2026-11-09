package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;

/** The optional fish only supplies a species highlight; opening a menu never equips it. */
public record OpenCompanionMenuPacket(ItemStack species) implements CustomPacketPayload {
    public static final Type<OpenCompanionMenuPacket> TYPE = new Type<>(Fishtastic.id("open_companion_menu"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenCompanionMenuPacket> STREAM_CODEC =
            StreamCodec.composite(ItemStack.OPTIONAL_UNTRUSTED_STREAM_CODEC,
                    OpenCompanionMenuPacket::species, OpenCompanionMenuPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClientToServer(OpenCompanionMenuPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player) {
                // The species focus is client-side (CompanionsScreen.focusOnNextOpen); the server only opens the menu.
                grill24.fishtastic.compat.GelatinOpenMenuCompat.openCompanionsMenu(player);
                grill24.fishtastic.tutorial.CompanionTutorialManager.onCompanionsTabOpened(player);
            }
        });
    }
}
