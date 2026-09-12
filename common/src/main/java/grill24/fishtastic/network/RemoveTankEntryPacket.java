package grill24.fishtastic.network;

import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.TankEntryKind;
import grill24.fishtastic.fishtank.TankGroups;
import grill24.fishtastic.item.PileOfFishItem;
import grill24.fishtastic.menu.FishTankBrowserMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Sent CLIENT → SERVER to remove one entry (a fish, a single-cell cosmetic, or a structure
 * cosmetic) from a fish tank browser GUI row. {@code segmentPos} is whichever tank in the
 * connected group actually holds the entry — not necessarily the tank the player clicked to open
 * the menu — and {@code key} is a slot index for {@link TankEntryKind#FISH}, or a packed
 * {@link CosmeticGridCell} for the cosmetic kinds. See docs/fish-tank-interaction-redesign.md.
 */
public record RemoveTankEntryPacket(BlockPos segmentPos, TankEntryKind kind, int key) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RemoveTankEntryPacket> TYPE =
            new CustomPacketPayload.Type<>(FishtasticPackets.REMOVE_TANK_ENTRY_ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoveTankEntryPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RemoveTankEntryPacket::segmentPos,
                    TankEntryKind.STREAM_CODEC, RemoveTankEntryPacket::kind,
                    ByteBufCodecs.VAR_INT, RemoveTankEntryPacket::key,
                    RemoveTankEntryPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClientToServer(RemoveTankEntryPacket packet, FishtasticPackets.IPacketContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.getPlayer() instanceof ServerPlayer player)) return;
            // Only applies while the player actually has the browser menu open; a stale packet
            // from a closed screen is ignored.
            if (!(player.containerMenu instanceof FishTankBrowserMenu menu)) return;

            BlockEntity anchorBe = player.level().getBlockEntity(menu.getAnchorPos());
            if (!(anchorBe instanceof FishTankBlockEntity anchorTank)) return;

            // The targeted segment must actually be part of the group the open menu is browsing —
            // defense against a modified client naming an arbitrary tank elsewhere in the world.
            TankGroups.Group group = TankGroups.of(anchorTank, player.level(), TankGroups.GAMEPLAY_MAX_GROUP_SIZE);
            if (!group.members().contains(packet.segmentPos())) return;

            if (!(player.level().getBlockEntity(packet.segmentPos()) instanceof FishTankBlockEntity fishTank)) return;

            ItemStack toGive = switch (packet.kind()) {
                case FISH -> {
                    if (packet.key() < 0 || packet.key() >= FishTankBlockEntity.CONTAINER_SIZE) yield ItemStack.EMPTY;
                    ItemStack existing = fishTank.getItem(packet.key());
                    yield fishTank.removeItem(packet.key(), existing.getCount());
                }
                case COSMETIC -> isValidCell(packet.key())
                        ? fishTank.removeCosmeticEntry(CosmeticGridCell.unpack(packet.key()))
                        : ItemStack.EMPTY;
                case STRUCTURE_COSMETIC -> isValidCell(packet.key())
                        ? fishTank.removeStructureCosmeticEntry(CosmeticGridCell.unpack(packet.key()))
                        : ItemStack.EMPTY;
            };

            giveOrDrop(player, toGive);
        });
    }

    /**
     * Delivers a removed entry to the player, piling fish rather than filling separate slots and
     * dropping at their feet whatever doesn't fit rather than silently discarding it. Public so
     * gametests can exercise the full-inventory path directly without an open
     * {@link FishTankBrowserMenu} — mirrors {@link grill24.fishtastic.network.PurchaseShopEntryPacket#grantRewards}.
     */
    public static void giveOrDrop(ServerPlayer player, ItemStack toGive) {
        if (toGive.isEmpty()) return;

        // Inventory.add() special-cases a creative player: once nothing fits, instead of leaving
        // the leftover for the caller to drop, it just zeroes the stack's count outright (see
        // Inventory#add's "player.hasInfiniteMaterials()" branch) — reasonable for something like
        // a creative-menu pick, which isn't a real item to begin with, but wrong here: this is an
        // existing fish/cosmetic coming OUT of the tank, not a freshly conjured one, so a creative
        // player with a full inventory needs it dropped exactly like a survival player would.
        // Suppressing instabuild for the duration of the give forces Inventory.add() down the
        // normal "leave the leftover in the stack" path regardless of game mode.
        boolean instabuild = player.getAbilities().instabuild;
        player.getAbilities().instabuild = false;
        try {
            // Fish get piled rather than each landing in its own slot — every fish carries its own
            // random ItemSize/FishQuality, so vanilla's stack-matching never merges two of them and
            // clearing a tank one click at a time would otherwise flood the inventory with
            // single-fish stacks (see PileOfFishItem#fillOrCreatePiles).
            if (PileOfFishItem.canInsertInPile(toGive)) {
                PileOfFishItem.fillOrCreatePiles(player, toGive);
            } else {
                player.getInventory().add(toGive);
            }
        } finally {
            player.getAbilities().instabuild = instabuild;
        }
        // Inventory.add() / fillOrCreatePiles() only consume what fits, leaving the remainder in
        // `toGive` — drop it at the player's feet instead of silently discarding a removed item
        // (see PurchaseShopEntryPacket#grantRewards, fixed for the same reason).
        if (!toGive.isEmpty()) {
            player.drop(toGive, false);
        }
    }

    private static boolean isValidCell(int packed) {
        return packed >= 0 && packed < CosmeticGridCell.GRID_SIZE * CosmeticGridCell.GRID_SIZE;
    }
}
