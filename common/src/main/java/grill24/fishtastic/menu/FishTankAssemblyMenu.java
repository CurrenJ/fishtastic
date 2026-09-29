package grill24.fishtastic.menu;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.blockentity.FishTankAssemblyBlockEntity;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.fishtank.FishTankShape;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Menu for the Fish Tank Assembly block: 3 real material slots (frame/sand/glass)
 * plus a virtual result slot, modeled on vanilla's crafting-table result-slot pattern
 * (compute-on-change, consume-on-take) rather than a custom "assemble" packet — this
 * reuses vanilla's server-authoritative slot handling instead of reinventing it.
 */
public class FishTankAssemblyMenu extends GelatinMenu {
    public static final int FRAME_SLOT = 0;
    public static final int GLASS_SLOT = 1;
    public static final int SAND_SLOT = 2;
    public static final int RESULT_SLOT = 3;
    /** Menu index of the refit slot (its container index is {@link FishTankAssemblyBlockEntity#TANK_SLOT}). */
    public static final int TANK_SLOT = 4;
    private static final int INPUT_SLOT_COUNT = 3;
    /** First player-inventory menu slot: frame/glass/sand, result, tank. */
    private static final int INV_START = 5;

    // GUI-space slot positions, matching fish_tank_assembly_side.png's hand-edited layout.
    // Input slots inset 2px, result slot inset 6px, to sit centered in their drawn frames.
    private static final int FRAME_X = 56, FRAME_Y = 17;
    private static final int GLASS_X = 56, GLASS_Y = 35;
    private static final int SAND_X = 56, SAND_Y = 53;
    private static final int RESULT_X = 116, RESULT_Y = 35;
    private static final int TANK_X = 31, TANK_Y = 35;
    private static final int INV_X = 8, INV_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final Container inputContainer;
    private final Container resultContainer = new SimpleContainer(1);
    /** The tank last seen in the refit slot; a change re-seeds the selected shape from the new tank. */
    private ItemStack lastTank = ItemStack.EMPTY;

    /** Server→client sync of the selected shape's ordinal (index into {@link FishTankShape#values()}). */
    private DataSlot shapeSlot;
    /**
     * {@link #shapeSlot}'s id in {@code dataSlots}. It's the first (and currently only) data slot
     * added, so this stays 0; update it if another data slot is ever added before it.
     */
    private static final int SHAPE_DATA_SLOT_ID = 0;

    /** Client-side constructor, used by the registered {@code MenuType} factory. */
    public FishTankAssemblyMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(FishTankAssemblyBlockEntity.CONTAINER_SIZE), FishTankShape.STANDARD);
    }

    /** Server-side constructor, used by {@link FishTankAssemblyBlockEntity#createMenu}. */
    public FishTankAssemblyMenu(int containerId, Inventory playerInventory, FishTankAssemblyBlockEntity blockEntity) {
        this(containerId, playerInventory, (Container) blockEntity, blockEntity.getShape());
    }

    private FishTankAssemblyMenu(int containerId, Inventory playerInventory, Container inputContainer, FishTankShape initialShape) {
        super(FishtasticMenuTypes.FISH_TANK_ASSEMBLY.value(), containerId);
        this.inputContainer = inputContainer;

        // The client's mirror starts at STANDARD and is corrected by the server's first
        // broadcastChanges (the data slot value is server→client only).
        this.shapeSlot = addDataSlot(DataSlot.standalone());
        this.shapeSlot.set(initialShape.ordinal());

        addSlot(new MaterialSlot(inputContainer, FRAME_SLOT, FRAME_X, FRAME_Y, "frame"));
        addSlot(new MaterialSlot(inputContainer, GLASS_SLOT, GLASS_X, GLASS_Y, "glass"));
        addSlot(new MaterialSlot(inputContainer, SAND_SLOT, SAND_X, SAND_Y, "sand"));
        addSlot(new ResultSlot(resultContainer, 0, RESULT_X, RESULT_Y));
        addSlot(new TankSlot(inputContainer, FishTankAssemblyBlockEntity.TANK_SLOT, TANK_X, TANK_Y));
        // Seed from whatever a reopened block already holds so its persisted shape selection isn't
        // overwritten by the tank on the first click.
        lastTank = inputContainer.getItem(FishTankAssemblyBlockEntity.TANK_SLOT).copy();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, INV_X + col * 18, HOTBAR_Y));
        }

        updateResult();
    }

    @Override
    public boolean stillValid(Player player) {
        return inputContainer.stillValid(player);
    }

    /** The currently selected fish tank shape (client and server both read their mirrored data slot). */
    public FishTankShape getShape() {
        FishTankShape[] shapes = FishTankShape.values();
        int ord = shapeSlot.get();
        return ord >= 0 && ord < shapes.length ? shapes[ord] : FishTankShape.STANDARD;
    }

    /**
     * Server-side shape change (from {@link grill24.fishtastic.network.SetAssemblyShapePacket}):
     * persist it on the block entity, update the sync slot, and recompute the crafted result.
     */
    public void setShape(FishTankShape shape) {
        if (inputContainer instanceof FishTankAssemblyBlockEntity be) {
            be.setShape(shape);
        }
        shapeSlot.set(shape.ordinal());
        updateResult();
        broadcastChanges();
    }

    /**
     * Client-side optimistic update so the result preview reacts instantly on click, before the
     * server confirms via {@link #setData}. The server remains authoritative.
     */
    public void setShapeLocal(FishTankShape shape) {
        shapeSlot.set(shape.ordinal());
        updateResult();
    }

    @Override
    public void setData(int id, int value) {
        super.setData(id, value);
        // When the server syncs the shape (on open, or after a shape change), recompute the
        // result preview so it reflects the authoritative selection.
        if (id == SHAPE_DATA_SLOT_ID) {
            updateResult();
        }
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        if (container == inputContainer) {
            updateResult();
        }
    }

    /**
     * {@code Container.setChanged()} on {@link #inputContainer} (a block entity, or a plain
     * {@code SimpleContainer} client-side) never calls back into {@link #slotsChanged}, unlike
     * vanilla's crafting-grid containers which override {@code setChanged()} specifically to
     * notify their menu. Recomputing after every click instead is simpler and catches every
     * interaction (placing/removing/shift-clicking a material) that could change the result.
     */
    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        super.clicked(slotId, button, clickType, player);
        syncShapeToTank(player);
        updateResult();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stackInSlot = slot.getItem();
            newStack = stackInSlot.copy();
            if (index == RESULT_SLOT) {
                if (!moveItemStackTo(stackInSlot, INV_START, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, newStack);
            } else if (index < INV_START) {
                if (!moveItemStackTo(stackInSlot, INV_START, slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stackInSlot, TANK_SLOT, TANK_SLOT + 1, false)
                    && !moveItemStackTo(stackInSlot, 0, INPUT_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }

            if (stackInSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stackInSlot.getCount() == newStack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stackInSlot);
        }
        return newStack;
    }

    private static FishTankShape shapeOf(ItemStack tank) {
        return FishtasticItemData.getOrDefault(tank, FishtasticDataComponents.FISH_TANK_SHAPE, FishTankShape.STANDARD);
    }

    private static FishTankMaterials materialsOf(ItemStack tank) {
        return FishtasticItemData.getOrDefault(tank, FishtasticDataComponents.FISH_TANK_MATERIALS, FishTankMaterials.defaultMaterials());
    }

    /** When a different tank lands in the refit slot, select its shape so the gallery starts from it. */
    private void syncShapeToTank(Player player) {
        ItemStack tank = inputContainer.getItem(FishTankAssemblyBlockEntity.TANK_SLOT);
        if (ItemStack.matches(tank, lastTank)) return;
        lastTank = tank.copy();
        if (!tank.isEmpty() && !player.level().isClientSide()) {
            setShape(shapeOf(tank));
        }
    }

    /**
     * What taking the result would do to a tank in the refit slot: the replacement block per part
     * (null = keep the tank's current one) and the stack produced.
     */
    private record Refit(ItemStack result, ItemStack tank, Block frame, Block sand, Block glass) {}

    /** The pending refit, or null when there is no tank or nothing would change (output stays blocked). */
    private Refit planRefit() {
        ItemStack tank = inputContainer.getItem(FishTankAssemblyBlockEntity.TANK_SLOT);
        if (tank.isEmpty()) return null;

        FishTankMaterials current = materialsOf(tank);
        FishTankShape currentShape = shapeOf(tank);
        FishTankShape shape = getShape();

        Block frame = replacement(FRAME_SLOT, "frame", current.frame());
        Block glass = replacement(GLASS_SLOT, "glass", current.glass());
        // A shape that never renders sand ignores the slot, as crafting does.
        Block sand = shape.requiresSandMaterial() ? replacement(SAND_SLOT, "sand", current.sand()) : null;

        if (shape == currentShape && frame == null && glass == null && sand == null) return null;

        ItemStack result = tank.copyWithCount(1);
        FishtasticItemData.set(result, FishtasticDataComponents.FISH_TANK_MATERIALS, new FishTankMaterials(
                frame != null ? frame : current.frame(),
                sand != null ? sand : current.sand(),
                glass != null ? glass : current.glass()));
        FishtasticItemData.set(result, FishtasticDataComponents.FISH_TANK_SHAPE, shape);
        return new Refit(result, tank, frame, sand, glass);
    }

    /** The staged block for a part if it's valid and differs from {@code current}, else null. */
    private Block replacement(int slot, String part, Block current) {
        ItemStack stack = inputContainer.getItem(slot);
        if (stack.getItem() instanceof BlockItem item && isValidMaterial(item.getBlock(), part) && item.getBlock() != current) {
            return item.getBlock();
        }
        return null;
    }

    private void updateResult() {
        if (!inputContainer.getItem(FishTankAssemblyBlockEntity.TANK_SLOT).isEmpty()) {
            Refit refit = planRefit();
            resultContainer.setItem(0, refit == null ? ItemStack.EMPTY : refit.result());
            broadcastChanges();
            return;
        }

        ItemStack frame = inputContainer.getItem(FRAME_SLOT);
        ItemStack sand = inputContainer.getItem(SAND_SLOT);
        ItemStack glass = inputContainer.getItem(GLASS_SLOT);
        boolean needsSand = getShape().requiresSandMaterial();

        // A shape that never renders sand (VITRINE) doesn't need the slot filled to craft. Its
        // FishTankMaterials record still needs *some* sand Block (the field isn't nullable), so an
        // empty slot falls back to a placeholder that's never actually sampled by that shape's
        // geometry. A filled slot is still honored (and still validated) so the choice round-trips
        // if the player later swaps to a shape that does use sand.
        Block sandBlock = Blocks.SAND;
        boolean sandOk;
        if (!sand.isEmpty()) {
            sandOk = sand.getItem() instanceof BlockItem sandItem && isValidMaterial(sandItem.getBlock(), "sand");
            if (sandOk) sandBlock = ((BlockItem) sand.getItem()).getBlock();
        } else {
            sandOk = !needsSand;
        }

        ItemStack result = ItemStack.EMPTY;
        if (!frame.isEmpty() && sandOk && !glass.isEmpty()
                && frame.getItem() instanceof BlockItem frameItem
                && glass.getItem() instanceof BlockItem glassItem
                && isValidMaterial(frameItem.getBlock(), "frame")
                && isValidMaterial(glassItem.getBlock(), "glass")) {
            FishTankMaterials materials = new FishTankMaterials(frameItem.getBlock(), sandBlock, glassItem.getBlock());
            result = new ItemStack(FishtasticBlocks.FISH_TANK.value());
            FishtasticItemData.set(result, FishtasticDataComponents.FISH_TANK_MATERIALS, materials);
            FishtasticItemData.set(result, FishtasticDataComponents.FISH_TANK_SHAPE, getShape());
        }
        resultContainer.setItem(0, result);
        broadcastChanges();
    }

    private static boolean isValidMaterial(Block block, String part) {
        return !RegistrationApiSided.getInstance().isBlockBlacklisted(block, part);
    }

    /** Consumes the tank and each applied material, and hands back the blocks they replaced. */
    private void takeRefit(Player player, Refit refit) {
        // Read before the tank is removed: a stack shrunk to count 0 reports no components.
        FishTankMaterials old = materialsOf(refit.tank());
        boolean hadRealSand = shapeOf(refit.tank()).requiresSandMaterial();
        boolean serverSide = !player.level().isClientSide();

        inputContainer.removeItem(FishTankAssemblyBlockEntity.TANK_SLOT, 1);
        if (refit.frame() != null) {
            inputContainer.removeItem(FRAME_SLOT, 1);
            if (serverSide) refund(player, old.frame());
        }
        if (refit.glass() != null) {
            inputContainer.removeItem(GLASS_SLOT, 1);
            if (serverSide) refund(player, old.glass());
        }
        if (refit.sand() != null) {
            inputContainer.removeItem(SAND_SLOT, 1);
            // A sand-less tank's sand is only a placeholder that was never a real material.
            if (serverSide && hadRealSand) refund(player, old.sand());
        }
    }

    private static void refund(Player player, Block block) {
        ItemStack stack = new ItemStack(block);
        if (!stack.isEmpty()) {
            player.getInventory().placeItemBackInInventory(stack);
        }
    }

    /** Refit slot: holds exactly one fish tank item. */
    private static class TankSlot extends Slot {
        TankSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(FishtasticBlocks.FISH_TANK.value().asItem());
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Input slot restricted to non-blacklisted block items. */
    private static class MaterialSlot extends Slot {
        private final String part;

        MaterialSlot(Container container, int slot, int x, int y, String part) {
            super(container, slot, x, y);
            this.part = part;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof BlockItem blockItem && isValidMaterial(blockItem.getBlock(), part);
        }
    }

    /** Virtual output slot: never manually fillable, consumes one of each input on take. */
    private class ResultSlot extends Slot {
        ResultSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            Refit refit = planRefit();
            if (refit != null) {
                takeRefit(player, refit);
                updateResult();
                super.onTake(player, stack);
                return;
            }
            inputContainer.removeItem(FRAME_SLOT, 1);
            if (getShape().requiresSandMaterial()) {
                inputContainer.removeItem(SAND_SLOT, 1);
            }
            inputContainer.removeItem(GLASS_SLOT, 1);
            updateResult();
            super.onTake(player, stack);
        }
    }
}
