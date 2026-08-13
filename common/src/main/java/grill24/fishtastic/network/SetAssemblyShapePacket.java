package grill24.fishtastic.network;

import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.menu.FishTankAssemblyMenu;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sent CLIENT → SERVER to select the fish tank body shape the assembly block crafts.
 * The server applies it to the block entity (persisting it) and recomputes the result slot.
 */
public record SetAssemblyShapePacket(FishTankShape shape) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetAssemblyShapePacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.SET_ASSEMBLY_SHAPE_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, SetAssemblyShapePacket> STREAM_CODEC =
            StreamCodec.composite(
                    FishTankShape.STREAM_CODEC,
                    SetAssemblyShapePacket::shape,
                    SetAssemblyShapePacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(SetAssemblyShapePacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.getPlayer() instanceof ServerPlayer player)) return;
            // Only applies while the player actually has the assembly menu open; a stale packet
            // from a closed screen is ignored.
            if (!(player.containerMenu instanceof FishTankAssemblyMenu assembly)) return;

            // The screen's cycle button already skips locked shapes without sending this packet;
            // this is a defense-in-depth check against a modified client sending one directly.
            MinecraftServer server = ((ServerLevel) player.level()).getServer();
            if (server == null) return;
            PlayerQuestState state = FishCatchSavedData.getOrCreate(server).getOrCreateQuestState(player);
            if (!packet.shape().isUnlockedFor(quest -> state.getProgress(quest).claimed())) return;

            assembly.setShape(packet.shape());
        });
    }
}
