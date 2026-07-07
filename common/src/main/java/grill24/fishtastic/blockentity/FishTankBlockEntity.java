package grill24.fishtastic.blockentity;

import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticStructures;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class FishTankBlockEntity extends BlockEntity implements Container {
    /** A placed multi-block structure cosmetic, anchored at one grid cell. */
    public record PlacedStructureCosmetic(ResourceKey<CosmeticStructure> structureId, Rotation rotation) {}

    /** Identifies one part within a placed structure cosmetic, for per-part particle throttling. */
    public record FurnacePartKey(CosmeticGridCell anchor, int partIndex) {}

    public static final int CONTAINER_SIZE = 27; // 3x9 slots like a chest

    // Maximum item size (in cm) that can be inserted without tank size requirements
    public static final float MAX_ITEM_SIZE_WITHOUT_REQUIREMENTS = 100.0f;

    // Minimum tank dimensions required for large items (3x3)
    public static final int MIN_TANK_WIDTH_FOR_LARGE_ITEMS = 3;
    public static final int MIN_TANK_HEIGHT_FOR_LARGE_ITEMS = 3;

    public FishTankBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(FishtasticBlockEntityTypes.FISH_TANK.value(), blockPos, blockState);
    }

    // Store the frame block directly - can be any block
    private Block frameBlock = Blocks.OAK_PLANKS; // Default frame block

    // Store the sand block directly - can be any block
    private Block sandBlock = Blocks.SAND; // Default sand block

    // Store the glass block for edges
    private Block glassBlock = FishtasticBlocks.CLEAR_STAINED_GLASS.get(DyeColor.BLUE).value(); // Default glass block

    // Store which faces are connected to other fish tanks (open faces)
    private Set<Direction> openFaces = EnumSet.noneOf(Direction.class);

    // Item storage
    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    // Store the rotation (in degrees) for the first item based on player direction when placed
    private float firstItemRotation = 0f;

    // Cosmetic decorations placed in the tank's 3×3 floor grid
    private Map<CosmeticGridCell, PlacedCosmetic> cosmetics = new HashMap<>();

    // Multi-block structure cosmetics, keyed by their anchor cell
    private Map<CosmeticGridCell, PlacedStructureCosmetic> structureCosmetics = new HashMap<>();
    // Derived from structureCosmetics on load/mutation: any occupied footprint cell -> its anchor cell.
    // Not persisted directly.
    private Map<CosmeticGridCell, CosmeticGridCell> structureCellIndex = new HashMap<>();

    // Client-side only: game time a lit furnace-family structure part last spawned smoke/flame
    // particles, for frame-rate-independent throttling. Not persisted or synced — a fresh
    // BlockEntityRenderState is allocated every rendered frame, so this can't live there; it needs
    // to live on the block entity itself, which persists for as long as the tank stays loaded.
    private final transient Map<FurnacePartKey, Long> furnaceLastParticleTick = new HashMap<>();

    public Map<FurnacePartKey, Long> getFurnaceLastParticleTick() {
        return furnaceLastParticleTick;
    }

    /**
     * Get the frame block for this fish tank.
     */
    public Block getFrameBlock() {
        return frameBlock;
    }

    /**
     * Get the sand block for this fish tank.
     */
    public Block getSandBlock() {
        return sandBlock;
    }

    /**
     * Get the glass block for this fish tank edges.
     */
    public Block getGlassBlock() {
        return glassBlock;
    }

    /**
     * Get the frame/sand/glass materials as a single component-shaped record.
     */
    public FishTankMaterials getMaterials() {
        return new FishTankMaterials(frameBlock, sandBlock, glassBlock);
    }

    /**
     * Set the frame/sand/glass materials at once (used when seeding a newly placed tank
     * from the placing item stack's {@link FishtasticDataComponents#FISH_TANK_MATERIALS}).
     */
    public void setMaterials(FishTankMaterials materials) {
        this.frameBlock = materials.frame();
        this.sandBlock = materials.sand();
        this.glassBlock = materials.glass();
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(FishtasticDataComponents.FISH_TANK_MATERIALS.value(), getMaterials());
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        FishTankMaterials materials = components.getOrDefault(FishtasticDataComponents.FISH_TANK_MATERIALS.value(), FishTankMaterials.defaultMaterials());
        this.frameBlock = materials.frame();
        this.sandBlock = materials.sand();
        this.glassBlock = materials.glass();
    }

    /**
     * Get the set of open faces (connected to other tanks)
     */
    public Set<Direction> getOpenFaces() {
        return EnumSet.copyOf(openFaces);
    }

    /**
     * Set a face as open (connected to another tank)
     */
    public void setFaceOpen(Direction face, boolean open) {
        if (open) {
            openFaces.add(face);
        } else {
            openFaces.remove(face);
        }
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    /**
     * Set all open faces at once
     */
    public void setOpenFaces(Set<Direction> faces) {
        this.openFaces = EnumSet.copyOf(faces);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    /**
     * Update connections by detecting adjacent fish tanks.
     * Called when the block is placed or when neighboring blocks change.
     */
    public void updateConnections(Level level, BlockPos pos) {
        Set<Direction> newOpenFaces = EnumSet.noneOf(Direction.class);

        // Check all 6 directions for adjacent fish tanks
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.relative(direction);
            BlockEntity adjacentBE = level.getBlockEntity(adjacentPos);

            // If there's a fish tank adjacent, open this face
            if (adjacentBE instanceof FishTankBlockEntity) {
                newOpenFaces.add(direction);
            }
        }

        // Only update if the connections have changed
        if (!newOpenFaces.equals(this.openFaces)) {
            this.openFaces = newOpenFaces;
            setChanged();
            if (!level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            RegistrationApiSided.getInstance().requestModelDataUpdate(this);
        }
    }

    /**
     * Set the frame block for this fish tank.
     */
    public void setFrameBlock(Block block) {
        this.frameBlock = block;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    /**
     * Set the sand block for this fish tank.
     */
    public void setSandBlock(Block block) {
        this.sandBlock = block;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    /**
     * Set the glass block for this fish tank edges.
     */
    public void setGlassBlock(Block block) {
        this.glassBlock = block;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        String frameId = BuiltInRegistries.BLOCK.getKey(frameBlock).toString();
        String sandId = BuiltInRegistries.BLOCK.getKey(sandBlock).toString();
        String glassId = BuiltInRegistries.BLOCK.getKey(glassBlock).toString();
        output.putString("FrameBlock", frameId);
        output.putString("SandBlock", sandId);
        output.putString("GlassBlock", glassId);

        // Save open faces as a bit field
        int openFacesBits = 0;
        for (Direction dir : openFaces) {
            openFacesBits |= (1 << dir.ordinal());
        }
        output.putInt("OpenFaces", openFacesBits);

        // Save items as a list of {Slot, Stack} entries
        ValueOutput.ValueOutputList itemsList = output.childrenList("Items");
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                ValueOutput child = itemsList.addChild();
                child.putInt("Slot", i);
                child.store("Stack", ItemStack.CODEC, stack);
            }
        }

        // Save first item rotation
        output.putFloat("FirstItemRotation", firstItemRotation);

        // Save cosmetics
        ValueOutput.ValueOutputList cosmeticsList = output.childrenList("Cosmetics");
        for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : cosmetics.entrySet()) {
            CosmeticGridCell cell = entry.getKey();
            PlacedCosmetic cosmetic = entry.getValue();
            String blockId = BuiltInRegistries.BLOCK.getKey(cosmetic.block()).toString();
            ValueOutput child = cosmeticsList.addChild();
            child.putInt("GridX", cell.gridX());
            child.putInt("GridZ", cell.gridZ());
            child.putString("Block", blockId);
            if (cosmetic.block() instanceof SeaPickleBlock) {
                child.putInt("Pickles", cosmetic.blockState().getValue(BlockStateProperties.PICKLES));
            }
            if (cosmetic.blockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                child.putString("Facing", cosmetic.blockState().getValue(BlockStateProperties.HORIZONTAL_FACING).getSerializedName());
            }
            if (cosmetic.height() != 1) {
                child.putInt("Height", cosmetic.height());
            }
        }

        // Save structure cosmetics (anchor cells only; footprint is re-derived from the structure
        // definition on load, not stored here).
        ValueOutput.ValueOutputList structureCosmeticsList = output.childrenList("StructureCosmetics");
        for (Map.Entry<CosmeticGridCell, PlacedStructureCosmetic> entry : structureCosmetics.entrySet()) {
            CosmeticGridCell cell = entry.getKey();
            PlacedStructureCosmetic placed = entry.getValue();
            ValueOutput child = structureCosmeticsList.addChild();
            child.putInt("GridX", cell.gridX());
            child.putInt("GridZ", cell.gridZ());
            child.putString("StructureId", placed.structureId().identifier().toString());
            child.store("Rotation", Rotation.CODEC, placed.rotation());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        // Load frame block
        String frameBlockStr = input.getStringOr("FrameBlock", "");
        if (!frameBlockStr.isEmpty()) {
            Identifier blockId = Identifier.tryParse(frameBlockStr);
            if (blockId != null) {
                Block b = BuiltInRegistries.BLOCK.getValue(blockId);
                if (b != null) {
                    frameBlock = b;
                } else {
                    Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, frameBlock registry lookup returned null for id={}", worldPosition, blockId);
                }
            } else {
                Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, failed to parse FrameBlock id='{}'", worldPosition, frameBlockStr);
            }
        }

        // Load sand block
        String sandBlockStr = input.getStringOr("SandBlock", "");
        if (!sandBlockStr.isEmpty()) {
            Identifier blockId = Identifier.tryParse(sandBlockStr);
            if (blockId != null) {
                Block b = BuiltInRegistries.BLOCK.getValue(blockId);
                if (b != null) {
                    sandBlock = b;
                } else {
                    Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, sandBlock registry lookup returned null for id={}", worldPosition, blockId);
                }
            } else {
                Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, failed to parse SandBlock id='{}'", worldPosition, sandBlockStr);
            }
        }

        // Load glass block
        String glassBlockStr = input.getStringOr("GlassBlock", "");
        if (!glassBlockStr.isEmpty()) {
            Identifier blockId = Identifier.tryParse(glassBlockStr);
            if (blockId != null) {
                Block b = BuiltInRegistries.BLOCK.getValue(blockId);
                if (b != null) {
                    glassBlock = b;
                } else {
                    Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, glassBlock registry lookup returned null for id={}", worldPosition, blockId);
                }
            } else {
                Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, failed to parse GlassBlock id='{}'", worldPosition, glassBlockStr);
            }
        }

        // Load open faces
        int openFacesBits = input.getIntOr("OpenFaces", 0);
        openFaces.clear();
        for (Direction dir : Direction.values()) {
            if ((openFacesBits & (1 << dir.ordinal())) != 0) {
                openFaces.add(dir);
            }
        }

        // Load items
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            items.set(i, ItemStack.EMPTY);
        }
        input.childrenListOrEmpty("Items").forEach(child -> {
            int slot = child.getIntOr("Slot", -1);
            if (slot >= 0 && slot < CONTAINER_SIZE) {
                child.read("Stack", ItemStack.CODEC).ifPresent(stack -> items.set(slot, stack));
            }
        });

        // Load first item rotation
        firstItemRotation = input.getFloatOr("FirstItemRotation", 0f);

        // Load cosmetics
        cosmetics.clear();
        input.childrenListOrEmpty("Cosmetics").forEach(child -> {
            int gridX = child.getIntOr("GridX", -1);
            int gridZ = child.getIntOr("GridZ", -1);
            String blockStr = child.getStringOr("Block", "");
            if (CosmeticGridCell.isValid(gridX, gridZ) && !blockStr.isEmpty()) {
                Identifier blockId = Identifier.tryParse(blockStr);
                if (blockId != null) {
                    Block b = BuiltInRegistries.BLOCK.getValue(blockId);
                    if (b != null) {
                        BlockState state = b.defaultBlockState();
                        if (b instanceof SeaPickleBlock) {
                            int pickles = child.getIntOr("Pickles", 1);
                            state = state.setValue(BlockStateProperties.PICKLES, Math.min(pickles, SeaPickleBlock.MAX_PICKLES));
                        }
                        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                            String facingStr = child.getStringOr("Facing", "");
                            Direction facing = Direction.byName(facingStr);
                            if (facing != null) {
                                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
                            }
                        }
                        int height = child.getIntOr("Height", 1);
                        cosmetics.put(new CosmeticGridCell(gridX, gridZ), new PlacedCosmetic(state, height));
                    } else {
                        Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, cosmetic block lookup returned null for id={}", worldPosition, blockId);
                    }
                }
            }
        });

        // Load structure cosmetics and rebuild the derived cell index from each structure's footprint.
        structureCosmetics.clear();
        structureCellIndex.clear();
        input.childrenListOrEmpty("StructureCosmetics").forEach(child -> {
            int gridX = child.getIntOr("GridX", -1);
            int gridZ = child.getIntOr("GridZ", -1);
            String structureIdStr = child.getStringOr("StructureId", "");
            if (!CosmeticGridCell.isValid(gridX, gridZ) || structureIdStr.isEmpty()) {
                return;
            }
            Identifier structureId = Identifier.tryParse(structureIdStr);
            if (structureId == null) {
                Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, failed to parse StructureId '{}'", worldPosition, structureIdStr);
                return;
            }
            Rotation rotation = child.read("Rotation", Rotation.CODEC).orElse(Rotation.NONE);
            ResourceKey<CosmeticStructure> key = ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, structureId);

            Optional<CosmeticStructure> structure = input.lookup().get(key)
                    .map(net.minecraft.core.Holder.Reference::value);
            if (structure.isEmpty()) {
                Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, cosmetic structure lookup returned nothing for id={}; skipping", worldPosition, structureId);
                return;
            }

            CosmeticGridCell anchor = new CosmeticGridCell(gridX, gridZ);
            List<CosmeticGridCell> footprintCells = rotatedFootprintCells(structure.get(), rotation, anchor);
            structureCosmetics.put(anchor, new PlacedStructureCosmetic(key, rotation));
            for (CosmeticGridCell footprintCell : footprintCells) {
                structureCellIndex.put(footprintCell, anchor);
            }
        });

        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    /** Rotates a structure's footprint cells and translates them to the given anchor. */
    private static List<CosmeticGridCell> rotatedFootprintCells(CosmeticStructure structure, Rotation rotation, CosmeticGridCell anchor) {
        List<CosmeticGridCell> cells = new java.util.ArrayList<>(structure.footprintCells().size());
        for (CosmeticStructure.GridOffset offset : structure.footprintCells()) {
            CosmeticStructure.GridOffset rotated = CosmeticStructures.rotateFootprintCell(rotation, offset);
            cells.add(new CosmeticGridCell(anchor.gridX() + rotated.dx(), anchor.gridZ() + rotated.dz()));
        }
        return cells;
    }

    @Override
    public net.minecraft.nbt.CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // Container interface methods
    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot >= 0 && slot < items.size()) {
            return items.get(slot);
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = getItem(slot);
        if (!stack.isEmpty()) {
            ItemStack result = stack.split(amount);
            if (stack.isEmpty()) {
                items.set(slot, ItemStack.EMPTY);
            }
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
            return result;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot >= 0 && slot < items.size()) {
            ItemStack stack = items.get(slot);
            items.set(slot, ItemStack.EMPTY);
            return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < items.size()) {
            items.set(slot, stack);
            if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
                stack.setCount(getMaxStackSize());
            }
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            items.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    /**
     * Try to add an item to the tank. Returns true if successful.
     */
    public boolean addItem(ItemStack stack) {
        return addItem(stack, 0f, null);
    }

    /**
     * Try to add an item to the tank with a specific rotation. Returns true if successful.
     */
    public boolean addItem(ItemStack stack, float rotation) {
        return addItem(stack, rotation, null);
    }

    /**
     * Try to add an item to the tank with a specific rotation and player reference. Returns true if successful.
     */
    public boolean addItem(ItemStack stack, float rotation, @Nullable Player player) {
        if (stack.isEmpty()) {
            return false;
        }

        // Check if item size is allowed based on tank dimensions
        if (!canInsertItem(stack, rotation, player)) {
            return false;
        }

        // Try to merge with existing stacks first
        for (int i = 0; i < items.size(); i++) {
            ItemStack existing = items.get(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int maxStackSize = Math.min(getMaxStackSize(), stack.getMaxStackSize());
                int canAdd = maxStackSize - existing.getCount();
                if (canAdd > 0) {
                    int toAdd = Math.min(canAdd, stack.getCount());
                    existing.grow(toAdd);
                    stack.shrink(toAdd);
                    setChanged();
                    if (level != null && !level.isClientSide()) {
                        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                    }
                    if (stack.isEmpty()) {
                        return true;
                    }
                }
            }
        }

        // Try to find an empty slot
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, stack.copy());
                // Store rotation only for the first slot (slot 0)
                if (i == 0) {
                    firstItemRotation = rotation;
                }
                stack.setCount(0);
                setChanged();
                if (level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
                return true;
            }
        }

        return false;
    }

    /**
     * Remove one item from the tank. Returns the removed item or ItemStack.EMPTY if empty.
     */
    public ItemStack extractItem() {
        // Find the last non-empty slot (LIFO)
        for (int i = items.size() - 1; i >= 0; i--) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                ItemStack result = stack.copy();
                items.set(i, ItemStack.EMPTY);
                setChanged();
                if (level != null && !level.isClientSide()) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                }
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Check if the tank has any items
     */
    public boolean hasItems() {
        return !isEmpty();
    }

    /**
     * Get the first non-empty item for rendering
     */
    public ItemStack getFirstItem() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Get the rotation angle for the first item
     */
    public float getFirstItemRotation() {
        return firstItemRotation;
    }

    public Map<CosmeticGridCell, PlacedCosmetic> getCosmetics() {
        return Collections.unmodifiableMap(cosmetics);
    }

    public void setCosmetic(CosmeticGridCell cell, PlacedCosmetic cosmetic) {
        cosmetics.put(cell, cosmetic);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void removeCosmetic(CosmeticGridCell cell) {
        if (cosmetics.remove(cell) != null) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    public Map<CosmeticGridCell, PlacedStructureCosmetic> getStructureCosmetics() {
        return Collections.unmodifiableMap(structureCosmetics);
    }

    /** Every cell occupied by a placed structure (including anchors), mapped to that structure's anchor. */
    public Map<CosmeticGridCell, CosmeticGridCell> getStructureCellIndex() {
        return Collections.unmodifiableMap(structureCellIndex);
    }

    /** Anchor cell (occupied by a placed structure) that {@code cell} belongs to, or null if none. */
    @Nullable
    public CosmeticGridCell getStructureAnchor(CosmeticGridCell cell) {
        return structureCellIndex.get(cell);
    }

    /**
     * Places a structure cosmetic. {@code footprintCells} must be the already-rotated, anchor-translated
     * footprint (see {@link grill24.fishtastic.fishtank.CosmeticStructures}) — every cell in it is recorded
     * in {@link #structureCellIndex} pointing back at {@code anchor}, including the anchor itself.
     */
    public void setStructureCosmetic(CosmeticGridCell anchor, PlacedStructureCosmetic cosmetic, List<CosmeticGridCell> footprintCells) {
        structureCosmetics.put(anchor, cosmetic);
        for (CosmeticGridCell cell : footprintCells) {
            structureCellIndex.put(cell, anchor);
        }
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Removes the structure anchored at {@code anchor} and every footprint cell pointing at it. */
    public void removeStructureCosmetic(CosmeticGridCell anchor) {
        if (structureCosmetics.remove(anchor) == null) {
            return;
        }
        structureCellIndex.values().removeIf(a -> a.equals(anchor));
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * Calculate tank dimensions by flood-filling through connected tanks.
     * Returns a TankDimensions object with width (X), height (Y), and depth (Z).
     *
     * @param maxManhattanDistance Maximum Manhattan distance to search from the starting position
     */
    private TankDimensions calculateTankDimensions(int maxManhattanDistance) {
        if (level == null) {
            return new TankDimensions(1, 1, 1);
        }

        Set<BlockPos> visited = new HashSet<>();
        Set<BlockPos> toVisit = new HashSet<>();
        toVisit.add(worldPosition);

        int minX = worldPosition.getX();
        int maxX = worldPosition.getX();
        int minY = worldPosition.getY();
        int maxY = worldPosition.getY();
        int minZ = worldPosition.getZ();
        int maxZ = worldPosition.getZ();

        // Flood fill through connected tanks
        while (!toVisit.isEmpty()) {
            BlockPos current = toVisit.iterator().next();
            toVisit.remove(current);

            if (visited.contains(current)) {
                continue;
            }
            visited.add(current);

            // Update bounds
            minX = Math.min(minX, current.getX());
            maxX = Math.max(maxX, current.getX());
            minY = Math.min(minY, current.getY());
            maxY = Math.max(maxY, current.getY());
            minZ = Math.min(minZ, current.getZ());
            maxZ = Math.max(maxZ, current.getZ());

            // Check all adjacent positions for connected tanks
            for (Direction direction : Direction.values()) {
                BlockPos adjacent = current.relative(direction);
                if (!visited.contains(adjacent) && !toVisit.contains(adjacent)) {
                    // Check Manhattan distance from starting position
                    int dx = Math.abs(adjacent.getX() - worldPosition.getX());
                    int dy = Math.abs(adjacent.getY() - worldPosition.getY());
                    int dz = Math.abs(adjacent.getZ() - worldPosition.getZ());
                    int manhattanDistance = dx + dy + dz;

                    if (manhattanDistance <= maxManhattanDistance) {
                        BlockEntity be = level.getBlockEntity(adjacent);
                        if (be instanceof FishTankBlockEntity) {
                            toVisit.add(adjacent);
                        }
                    }
                }
            }
        }

        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int depth = maxZ - minZ + 1;

        return new TankDimensions(width, height, depth, visited);
    }

    /**
     * Check if an item can be inserted based on its size and the tank dimensions.
     * Large items require a minimum 3x3 tank (in the plane perpendicular to the item's orientation).
     *
     * @param stack The item to check
     * @param itemRotation The rotation of the item (used to determine orientation)
     * @param player The player attempting to insert (for error messages), can be null
     * @return true if the item can be inserted, false otherwise
     */
    private boolean canInsertItem(ItemStack stack, float itemRotation, @Nullable Player player) {
        float itemSize = ItemSizeHelper.getSize(stack);

        // If item has no size or is below threshold, allow insertion
        if (itemSize <= MAX_ITEM_SIZE_WITHOUT_REQUIREMENTS) {
            return true;
        }

        // Large item - check tank dimensions
        TankDimensions dimensions = calculateTankDimensions(2); // Search up to 2 blocks Manhattan distance

        // Flat, floor-sitting creatures (e.g. starfish) have negligible height - they need
        // floor area to lie flat on, not vertical swimming clearance.
        if (isFloorSitting(stack)) {
            boolean hasFloorArea = hasSolidRectangle(dimensions.visited, Direction.Axis.X, Direction.Axis.Z,
                    MIN_TANK_WIDTH_FOR_LARGE_ITEMS, MIN_TANK_WIDTH_FOR_LARGE_ITEMS);

            if (!hasFloorArea && player != null && level != null && !level.isClientSide()) {
                player.sendSystemMessage(
                        Component.literal(String.format(
                                "Item too large! Items over %.0f cm require a minimum %dx%d clear floor area (current: %dx%d)",
                                MAX_ITEM_SIZE_WITHOUT_REQUIREMENTS,
                                MIN_TANK_WIDTH_FOR_LARGE_ITEMS,
                                MIN_TANK_WIDTH_FOR_LARGE_ITEMS,
                                dimensions.width,
                                dimensions.depth
                        ))
                );
            }

            return hasFloorArea;
        }

        // Determine which axes the item is aligned with based on rotation
        // Rotation is around Y axis, so:
        // - Y (height) is always "across" one dimension
        // - The item's length extends along the horizontal plane based on rotation

        // Normalize rotation to 0-360 range
        float normalizedRotation = ((itemRotation % 360) + 360) % 360;

        // Determine primary horizontal direction
        // 0/360 degrees = South (+Z), 90 = West (-X), 180 = North (-Z), 270 = East (+X)
        boolean isAlignedWithX = (normalizedRotation >= 45 && normalizedRotation < 135) ||
                (normalizedRotation >= 225 && normalizedRotation < 315);

        // Check for solid 3x3 areas in the appropriate planes
        boolean hasRequiredArea;
        int actualWidth, actualHeight;

        if (isAlignedWithX) {
            // Item extends along X axis
            // Need solid 3x3 in Y (height) and Z (depth)
            hasRequiredArea = hasSolidRectangle(dimensions.visited, Direction.Axis.Y, Direction.Axis.Z, MIN_TANK_HEIGHT_FOR_LARGE_ITEMS, MIN_TANK_WIDTH_FOR_LARGE_ITEMS);
            actualWidth = dimensions.depth;  // Z axis
            actualHeight = dimensions.height; // Y axis
        } else {
            // Item extends along Z axis
            // Need solid 3x3 in Y (height) and X (width)
            hasRequiredArea = hasSolidRectangle(dimensions.visited, Direction.Axis.Y, Direction.Axis.X, MIN_TANK_HEIGHT_FOR_LARGE_ITEMS, MIN_TANK_WIDTH_FOR_LARGE_ITEMS);
            actualWidth = dimensions.width;  // X axis
            actualHeight = dimensions.height; // Y axis
        }

        if (!hasRequiredArea && player != null && level != null && !level.isClientSide()) {
            player.sendSystemMessage(
                    Component.literal(String.format(
                            "Item too large! Items over %.0f cm require a minimum %dx%d solid tank area (current: %dx%d)",
                            MAX_ITEM_SIZE_WITHOUT_REQUIREMENTS,
                            MIN_TANK_WIDTH_FOR_LARGE_ITEMS,
                            MIN_TANK_HEIGHT_FOR_LARGE_ITEMS,
                            actualWidth,
                            actualHeight
                    ))
            );
        }

        return hasRequiredArea;
    }

    /**
     * Whether this fish/item uses the floor-sitting animation mode (e.g. starfish) - flat
     * benthic creatures that rest on the sand rather than needing vertical swimming clearance.
     */
    private boolean isFloorSitting(ItemStack stack) {
        if (level == null || stack.isEmpty()) {
            return false;
        }

        var itemKey = BuiltInRegistries.ITEM.getResourceKey(stack.getItem());
        if (itemKey.isEmpty()) {
            return false;
        }

        ResourceKey<FishProfile> profileKey = ResourceKey.create(
                FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, itemKey.get().identifier());

        return level.registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                .getOptional(profileKey)
                .flatMap(FishProfile::animation)
                .filter(anim -> anim instanceof FishAnimationConfig.FloorSit)
                .isPresent();
    }

    /**
     * Check if there's a solid rectangular area of the specified size in the given plane.
     *
     * @param tankPositions Set of all tank block positions
     * @param axis1 First axis of the plane (e.g., Y for height)
     * @param axis2 Second axis of the plane (e.g., X for width)
     * @param size1 Size required along axis1
     * @param size2 Size required along axis2
     * @return true if a solid rectangle of the required size exists
     */
    private boolean hasSolidRectangle(Set<BlockPos> tankPositions, Direction.Axis axis1, Direction.Axis axis2, int size1, int size2) {
        if (tankPositions.isEmpty()) {
            return false;
        }

        Direction.Axis fixedAxis = getFixedAxis(axis1, axis2);

        // Find the bounds of the tank structure along all three axes
        int min1 = Integer.MAX_VALUE, max1 = Integer.MIN_VALUE;
        int min2 = Integer.MAX_VALUE, max2 = Integer.MIN_VALUE;
        int minFixed = Integer.MAX_VALUE, maxFixed = Integer.MIN_VALUE;

        for (BlockPos pos : tankPositions) {
            int val1 = getAxisValue(pos, axis1);
            int val2 = getAxisValue(pos, axis2);
            int valFixed = getAxisValue(pos, fixedAxis);

            min1 = Math.min(min1, val1);
            max1 = Math.max(max1, val1);
            min2 = Math.min(min2, val2);
            max2 = Math.max(max2, val2);
            minFixed = Math.min(minFixed, valFixed);
            maxFixed = Math.max(maxFixed, valFixed);
        }

        // Check if the overall bounds are large enough
        if (max1 - min1 + 1 < size1 || max2 - min2 + 1 < size2) {
            return false;
        }

        // Check all slices along the fixed axis
        // We need to find at least one slice that has a solid size1 x size2 rectangle
        for (int fixedValue = minFixed; fixedValue <= maxFixed; fixedValue++) {
            // Check all possible positions for a solid rectangle in this slice
            for (int start1 = min1; start1 <= max1 - size1 + 1; start1++) {
                for (int start2 = min2; start2 <= max2 - size2 + 1; start2++) {
                    if (isSolidRectangle(tankPositions, axis1, axis2, fixedValue, start1, start2, size1, size2)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if a specific rectangular area is solid (all positions contain tanks).
     */
    private boolean isSolidRectangle(Set<BlockPos> tankPositions, Direction.Axis axis1, Direction.Axis axis2, int fixedValue,
                                     int start1, int start2, int size1, int size2) {
        for (int i = 0; i < size1; i++) {
            for (int j = 0; j < size2; j++) {
                BlockPos pos = createBlockPos(axis1, axis2, start1 + i, start2 + j, fixedValue);
                if (!tankPositions.contains(pos)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Get the axis value from a BlockPos.
     */
    private int getAxisValue(BlockPos pos, Direction.Axis axis) {
        return switch (axis) {
            case X -> pos.getX();
            case Y -> pos.getY();
            case Z -> pos.getZ();
        };
    }

    /**
     * Get the fixed axis (the one not in the plane).
     */
    private Direction.Axis getFixedAxis(Direction.Axis axis1, Direction.Axis axis2) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            if (axis != axis1 && axis != axis2) {
                return axis;
            }
        }
        throw new IllegalArgumentException("Invalid axes");
    }

    /**
     * Create a BlockPos from axis values.
     */
    private BlockPos createBlockPos(Direction.Axis axis1, Direction.Axis axis2, int val1, int val2, int fixedVal) {
        int x = 0, y = 0, z = 0;

        if (axis1 == Direction.Axis.X) x = val1;
        else if (axis1 == Direction.Axis.Y) y = val1;
        else if (axis1 == Direction.Axis.Z) z = val1;

        if (axis2 == Direction.Axis.X) x = val2;
        else if (axis2 == Direction.Axis.Y) y = val2;
        else if (axis2 == Direction.Axis.Z) z = val2;

        // Set the fixed axis
        Direction.Axis fixedAxis = getFixedAxis(axis1, axis2);
        if (fixedAxis == Direction.Axis.X) x = fixedVal;
        else if (fixedAxis == Direction.Axis.Y) y = fixedVal;
        else if (fixedAxis == Direction.Axis.Z) z = fixedVal;

        return new BlockPos(x, y, z);
    }

    /**
     * Helper class to store tank dimensions
     */
    private static class TankDimensions {
        final int width;  // X axis
        final int height; // Y axis
        final int depth;  // Z axis
        final Set<BlockPos> visited; // Visited positions

        TankDimensions(int width, int height, int depth) {
            this(width, height, depth, new HashSet<>());
        }

        TankDimensions(int width, int height, int depth, Set<BlockPos> visited) {
            this.width = width;
            this.height = height;
            this.depth = depth;
            this.visited = visited;
        }
    }
}
