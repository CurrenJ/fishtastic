package grill24.fishtastic.menu;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticMenuTypes;
import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.blockentity.FishTankAssemblyBlockEntity;
import grill24.fishtastic.component.FishTankMaterials;
import io.github.currenj.gelatinui.gui.GelatinMenu;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

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
    private static final int INPUT_SLOT_COUNT = 3;

    // GUI-space slot positions, matching fish_tank_assembly_side.png's hand-edited layout.
    // Input slots inset 2px, result slot inset 6px, to sit centered in their drawn frames.
    private static final int FRAME_X = 56, FRAME_Y = 17;
    private static final int GLASS_X = 56, GLASS_Y = 35;
    private static final int SAND_X = 56, SAND_Y = 53;
    private static final int RESULT_X = 116, RESULT_Y = 35;
    private static final int INV_X = 8, INV_Y = 84;
    private static final int HOTBAR_Y = 142;

    private final Container inputContainer;
    private final Container resultContainer = new SimpleContainer(1);

    /** Client-side constructor, used by the registered {@code MenuType} factory. */
    public FishTankAssemblyMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(FishTankAssemblyBlockEntity.CONTAINER_SIZE));
    }

    /** Server-side constructor, used by {@link FishTankAssemblyBlockEntity#createMenu}. */
    public FishTankAssemblyMenu(int containerId, Inventory playerInventory, FishTankAssemblyBlockEntity blockEntity) {
        this(containerId, playerInventory, (Container) blockEntity);
    }

    private FishTankAssemblyMenu(int containerId, Inventory playerInventory, Container inputContainer) {
        super(FishtasticMenuTypes.FISH_TANK_ASSEMBLY.value(), containerId);
        this.inputContainer = inputContainer;

        addSlot(new MaterialSlot(inputContainer, FRAME_SLOT, FRAME_X, FRAME_Y, "frame"));
        addSlot(new MaterialSlot(inputContainer, GLASS_SLOT, GLASS_X, GLASS_Y, "glass"));
        addSlot(new MaterialSlot(inputContainer, SAND_SLOT, SAND_X, SAND_Y, "sand"));
        addSlot(new ResultSlot(resultContainer, 0, RESULT_X, RESULT_Y));

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
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ContainerInput containerInput, Player player) {
        super.clicked(slotId, button, containerInput, player);
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
                if (!moveItemStackTo(stackInSlot, INPUT_SLOT_COUNT + 1, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
                slot.onQuickCraft(stackInSlot, newStack);
            } else if (index < INPUT_SLOT_COUNT + 1) {
                if (!moveItemStackTo(stackInSlot, INPUT_SLOT_COUNT + 1, slots.size(), false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stackInSlot, 0, INPUT_SLOT_COUNT, false)) {
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

    private void updateResult() {
        ItemStack frame = inputContainer.getItem(FRAME_SLOT);
        ItemStack sand = inputContainer.getItem(SAND_SLOT);
        ItemStack glass = inputContainer.getItem(GLASS_SLOT);

        ItemStack result = ItemStack.EMPTY;
        if (!frame.isEmpty() && !sand.isEmpty() && !glass.isEmpty()
                && frame.getItem() instanceof BlockItem frameItem
                && sand.getItem() instanceof BlockItem sandItem
                && glass.getItem() instanceof BlockItem glassItem
                && isValidMaterial(frameItem.getBlock(), "frame")
                && isValidMaterial(sandItem.getBlock(), "sand")
                && isValidMaterial(glassItem.getBlock(), "glass")) {
            FishTankMaterials materials = new FishTankMaterials(frameItem.getBlock(), sandItem.getBlock(), glassItem.getBlock());
            result = new ItemStack(FishtasticBlocks.FISH_TANK.value());
            result.set(FishtasticDataComponents.FISH_TANK_MATERIALS.value(), materials);
        }
        resultContainer.setItem(0, result);
        broadcastChanges();
    }

    private static boolean isValidMaterial(Block block, String part) {
        return !RegistrationApiSided.getInstance().isBlockBlacklisted(block, part);
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
            inputContainer.removeItem(FRAME_SLOT, 1);
            inputContainer.removeItem(SAND_SLOT, 1);
            inputContainer.removeItem(GLASS_SLOT, 1);
            updateResult();
            super.onTake(player, stack);
        }
    }
}
