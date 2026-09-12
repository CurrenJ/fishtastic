package grill24.fishtastic.menu;

import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;

/**
 * Menu behind the fish tank browser GUI: lists every fish and cosmetic across a connected tank
 * group and lets the player remove any of them (see docs/fish-tank-interaction-redesign.md).
 * Carries no real {@code Slot}s — the screen reads the group's contents straight off the already
 * chunk-synced {@link FishTankBlockEntity}s (see {@link grill24.fishtastic.fishtank.TankGroups}),
 * and removal goes through {@link grill24.fishtastic.network.RemoveTankEntryPacket} instead of
 * vanilla slot clicks. The only thing this menu carries is the clicked tank's position, synced to
 * the client via data slots exactly like {@code FishTankAssemblyMenu} syncs its selected shape.
 */
public class FishTankBrowserMenu extends GelatinMenu {

    private final DataSlot anchorXSlot;
    private final DataSlot anchorYSlot;
    private final DataSlot anchorZSlot;

    /** Server-side only; null on the client, where there's no local block entity to check. */
    private final FishTankBlockEntity fishTank;

    /** Client-side constructor, used by the registered {@code MenuType} factory. */
    public FishTankBrowserMenu(int containerId, Inventory playerInventory) {
        this(containerId, null, BlockPos.ZERO);
    }

    /** Server-side constructor, used by {@link FishTankBlockEntity#createMenu}. */
    public FishTankBrowserMenu(int containerId, Inventory playerInventory, FishTankBlockEntity fishTank) {
        this(containerId, fishTank, fishTank.getBlockPos());
    }

    private FishTankBrowserMenu(int containerId, FishTankBlockEntity fishTank, BlockPos anchorPos) {
        super(FishtasticMenuTypes.FISH_TANK_BROWSER.value(), containerId);
        this.fishTank = fishTank;
        this.anchorXSlot = addDataSlot(DataSlot.standalone());
        this.anchorYSlot = addDataSlot(DataSlot.standalone());
        this.anchorZSlot = addDataSlot(DataSlot.standalone());
        anchorXSlot.set(anchorPos.getX());
        anchorYSlot.set(anchorPos.getY());
        anchorZSlot.set(anchorPos.getZ());
    }

    /** Position of the tank segment the player clicked to open this menu. */
    public BlockPos getAnchorPos() {
        return new BlockPos(anchorXSlot.get(), anchorYSlot.get(), anchorZSlot.get());
    }

    /** Client-side only: the browser screen refreshes its rows once the anchor pos is fixed. */
    private Runnable onAnchorSynced;

    /**
     * {@link #anchorZSlot}'s id in {@code dataSlots} — it's the third (and last) data slot added,
     * so this stays 2; update it if another data slot is ever added before it. The X/Y/Z slots
     * sync as three separate packets sent in addition order, so gating the refresh on the last
     * one avoids rebuilding the grid (and re-resolving a possibly-wrong block entity from a still
     * partially-synced position) three times per menu open instead of once.
     */
    private static final int ANCHOR_Z_DATA_SLOT_ID = 2;

    public void setOnAnchorSynced(Runnable listener) {
        this.onAnchorSynced = listener;
    }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        if (id == ANCHOR_Z_DATA_SLOT_ID && onAnchorSynced != null) {
            onAnchorSynced.run();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return fishTank == null || fishTank.stillValid(player);
    }
}
