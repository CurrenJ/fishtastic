package grill24.fishtastic.network;

import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.menu.CompanionMenu;
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
            StreamCodec.composite(ItemStack.OPTIONAL_STREAM_CODEC,
                    OpenCompanionMenuPacket::species, OpenCompanionMenuPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handleClientToServer(OpenCompanionMenuPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.getPlayer() instanceof ServerPlayer player) {
                player.openMenu(new SimpleMenuProvider((id, inventory, owner) ->
                        new CompanionMenu(id, inventory, packet.species.isEmpty() ? null : packet.species.getItem()),
                        Component.translatable("screen.fishtastic.companion.title")));
                // TODO(F15): CompanionTutorialManager.onCompanionsTabOpened(player)
            }
        });
    }
}
