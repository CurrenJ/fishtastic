package grill24.fishtastic.blockentity;

import grill24.fishtastic.util.BlockEntityNbt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.architectury.RegistrationApiSided;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.data.TankCapacity;
import grill24.fishtastic.fishtank.CosmeticGridCell;
import grill24.fishtastic.fishtank.CosmeticStructure;
import grill24.fishtastic.fishtank.CosmeticStructures;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.fishtank.TankDiagonal;
import grill24.fishtastic.fishtank.TankEdgeDiagonal;
import grill24.fishtastic.fishtank.TankGroups;
import grill24.fishtastic.fishtank.HangingCosmetics;
import grill24.fishtastic.fishtank.SpanStructures;
import grill24.fishtastic.fishtank.PlacedCosmetic;
import grill24.fishtastic.item.FishTankCosmeticItem;
import grill24.fishtastic.item.FishTankStructureCosmeticItem;
import grill24.fishtastic.menu.FishTankBrowserMenu;
import grill24.fishtastic.util.Ids;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class FishTankBlockEntity extends BlockEntity implements Container, MenuProvider {
    /** A placed multi-block structure cosmetic, anchored at one grid cell. */
    public record PlacedStructureCosmetic(ResourceKey<CosmeticStructure> structureId, Rotation rotation) {}

    /**
     * This tank's place in a spanning structure anchored in another tank of its box
     * ({@link SpanStructures}): the offset to that anchor, and the floor cells the structure covers
     * here. Only trusted once {@link SpanStructures#resolve} confirms the anchor still agrees.
     */
    public record SpanLink(BlockPos toAnchor, List<CosmeticGridCell> cells) {}

    /** Identifies one part within a placed structure cosmetic, for per-part particle throttling. */
    public record FurnacePartKey(CosmeticGridCell anchor, int partIndex) {}

    public static final int CONTAINER_SIZE = 27; // 3x9 slots like a chest

    public FishTankBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(FishtasticBlockEntityTypes.FISH_TANK.value(), blockPos, blockState);
    }

    // Store the frame block directly - can be any block
    private Block frameBlock = Blocks.OAK_PLANKS; // Default frame block

    // Store the sand block directly - can be any block
    private Block sandBlock = Blocks.SAND; // Default sand block

    // Store the glass block for edges
    private Block glassBlock = FishtasticBlocks.CLEAR_STAINED_GLASS.get(DyeColor.BLUE).value(); // Default glass block

    // Body geometry (independent of frame/sand/glass texture) — which set of pre-generated
    // permutation models to composite. See FishTankShape for the connection-collection concept.
    private FishTankShape shape = FishTankShape.STANDARD;

    // Store which faces are connected to other fish tanks (open faces)
    private Set<Direction> openFaces = EnumSet.noneOf(Direction.class);

    // Which diagonal (horizontal corner-adjacent) neighbor cells are occupied by another tank in
    // the same connection collection. Independent of openFaces — a diagonal shares no wall with
    // this tank, it only matters for whether a corner post needs to render back in when both of
    // that corner's orthogonal faces are open (see FishTankCompositeModelData#getDiagonalOverrideMask).
    private Set<TankDiagonal> filledDiagonals = EnumSet.noneOf(TankDiagonal.class);

    // Which edge-diagonal (horizontal×vertical-adjacent) neighbor cells are occupied by another
    // tank in the same connection collection. Distinct from filledDiagonals (purely horizontal) —
    // this covers the case where a tank's lower/upper edge on one horizontal side needs a frame
    // beam rendered back in (see FishTankCompositeModelData#getEdgeDiagonalOverrideMask).
    private Set<TankEdgeDiagonal> filledEdgeDiagonals = EnumSet.noneOf(TankEdgeDiagonal.class);

    // Waxed tanks refuse to open NEW connections on any face (honeycomb/axe, mirroring vanilla
    // copper). Purely behavioral — no visual change — so it isn't part of getMaterials()/the
    // data components; it lives here like openFaces itself.
    private boolean waxed = false;

    // Item storage
    private NonNullList<ItemStack> items = NonNullList.withSize(CONTAINER_SIZE, ItemStack.EMPTY);

    // Per-slot left/right mirror flag for the upright-float animator, rolled fresh whenever a fish
    // is placed into an empty slot. Lets asymmetric upright fish (e.g. leafy sea dragon) face either
    // direction for natural variety; popping the fish out and placing it back re-rolls it.
    private final boolean[] itemMirrored = new boolean[CONTAINER_SIZE];

    // Store the rotation (in degrees) for the first item based on player direction when placed
    private float firstItemRotation = 0f;

    // Cosmetic decorations placed in the tank's 3×3 floor grid
    private Map<CosmeticGridCell, PlacedCosmetic> cosmetics = new HashMap<>();

    // Cosmetics hung from the lid, on a 3×3 grid mirroring the floor's (see HangingCosmetics)
    private Map<CosmeticGridCell, PlacedCosmetic> ceilingCosmetics = new HashMap<>();

    // Link to a spanning structure anchored in another tank of this one's box, or null
    @Nullable
    private SpanLink spanLink;

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

    // Client-side only: game time a lit campfire cosmetic last spawned a smoke particle. Same
    // reasoning as furnaceLastParticleTick above; single-cell cosmetics only need the grid cell
    // itself as the key, not a compound part key.
    private final transient Map<CosmeticGridCell, Long> campfireLastParticleTick = new HashMap<>();

    public Map<CosmeticGridCell, Long> getCampfireLastParticleTick() {
        return campfireLastParticleTick;
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
     * Get the body geometry (shape) for this fish tank.
     */
    public FishTankShape getShape() {
        return shape;
    }

    /**
     * Set the body geometry (shape) for this fish tank.
     */
    public void setShape(FishTankShape shape) {
        this.shape = shape;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
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

    /**
     * Writes this tank's materials/shape onto {@code stack} (B2.6: the shared body behind
     * {@code collectImplicitComponents} on 1.21.1; called from {@code FishTankBlock#getCloneItemStack}
     * and the {@code fishtastic:copy_tank_data} loot function on 1.20.1, which replace 1.21.1's
     * component-copy loot function since 1.20.1 has no components).
     */
    public void writeToItem(ItemStack stack) {
        FishtasticItemData.set(stack, FishtasticDataComponents.FISH_TANK_MATERIALS, getMaterials());
        FishtasticItemData.set(stack, FishtasticDataComponents.FISH_TANK_SHAPE, shape);
    }

    /**
     * Seeds this tank's materials/shape from a placing item stack (B2.6: the shared body behind
     * {@code applyImplicitComponents} on 1.21.1; called from {@code FishTankBlock#setPlacedBy} on
     * 1.20.1, which replaces 1.21.1's {@code BlockItem} implicit-component application).
     */
    public void applyFromItem(FishTankMaterials materials, FishTankShape shape) {
        this.frameBlock = materials.frame();
        this.sandBlock = materials.sand();
        this.glassBlock = materials.glass();
        this.shape = shape;
    }

    /**
     * Get the set of open faces (connected to other tanks)
     */
    public Set<Direction> getOpenFaces() {
        return EnumSet.copyOf(openFaces);
    }

    /**
     * Whether one face is open, without {@link #getOpenFaces()}'s defensive copy. The group
     * flood-fill asks this once per member per direction, which at
     * {@link TankGroups#RENDER_MAX_GROUP_SIZE} is thousands of {@code EnumSet} allocations a walk.
     */
    public boolean isFaceOpen(Direction face) {
        return openFaces.contains(face);
    }

    /**
     * Set a face as open (connected to another tank)
     */
    public void setFaceOpen(Direction face, boolean open) {
        boolean changed = open ? openFaces.add(face) : openFaces.remove(face);
        if (changed) TankGroups.bumpMembershipEpoch();
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    /**
     * Get the set of filled diagonals (diagonal-adjacent cell occupied by another tank in the
     * same connection collection).
     */
    public Set<TankDiagonal> getFilledDiagonals() {
        return EnumSet.copyOf(filledDiagonals);
    }

    /** Whether one diagonal neighbor cell is occupied, without {@link #getFilledDiagonals()}'s defensive copy. */
    public boolean isDiagonalFilled(TankDiagonal diagonal) {
        return filledDiagonals.contains(diagonal);
    }

    /**
     * Get the set of filled edge diagonals (edge-diagonal-adjacent cell occupied by another tank
     * in the same connection collection).
     */
    public Set<TankEdgeDiagonal> getFilledEdgeDiagonals() {
        return EnumSet.copyOf(filledEdgeDiagonals);
    }

    /** Whether one edge-diagonal neighbor cell is occupied, without {@link #getFilledEdgeDiagonals()}'s defensive copy. */
    public boolean isEdgeDiagonalFilled(TankEdgeDiagonal edgeDiagonal) {
        return filledEdgeDiagonals.contains(edgeDiagonal);
    }

    /**
     * Set all open faces at once
     */
    public void setOpenFaces(Set<Direction> faces) {
        if (!this.openFaces.equals(faces)) TankGroups.bumpMembershipEpoch();
        this.openFaces = EnumSet.copyOf(faces);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    /**
     * Whether this tank is waxed. A waxed tank keeps its existing connections but refuses to
     * open any new ones — honeycomb sets this, an axe clears it.
     */
    public boolean isWaxed() {
        return waxed;
    }

    /**
     * Set the waxed state for this tank.
     */
    public void setWaxed(boolean waxed) {
        this.waxed = waxed;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /**
     * A tank leaving the world changes group membership even when no surviving tank's open faces
     * move — the walk simply has one fewer node. Neighbours normally recompute and bump the epoch
     * themselves, but this does not depend on that ordering.
     */
    @Override
    public void setRemoved() {
        super.setRemoved();
        if (!openFaces.isEmpty()) TankGroups.bumpMembershipEpoch();
    }

    /**
     * Update connections by detecting adjacent fish tanks.
     * Called when the block is placed or when neighboring blocks change.
     */
    public void updateConnections(Level level, BlockPos pos) {
        // A neighbour changing is when a span's anchor may have gone (broken and carried off in
        // its item): drop a link that no longer resolves, freeing the floor cells it held.
        if (!level.isClientSide() && spanLink != null && SpanStructures.resolve(level, this) == null) {
            clearSpanLink();
        }

        Set<Direction> newOpenFaces = EnumSet.noneOf(Direction.class);

        // Check all 6 directions for adjacent fish tanks
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = pos.relative(direction);
            BlockEntity adjacentBE = level.getBlockEntity(adjacentPos);

            // Only open this face if the neighbor is a fish tank AND its shape is in the same
            // connection collection as this tank's — decoupled from exact shape identity so a
            // curated family of shapes can be grouped to connect without being the same shape.
            // Defaults to each shape's own id, so a new shape only connects to itself.
            if (adjacentBE instanceof FishTankBlockEntity other
                    && other.getShape().connectionCollection().equals(this.shape.connectionCollection())) {
                // This method recomputes every open face from scratch on every neighbor update,
                // not just when a connection first forms — so an already-open face must stay open
                // regardless of wax state (waxing never retroactively closes a connection; only
                // the adjacent tank disappearing does, via the instanceof check above failing).
                // A face that isn't open yet only opens if NEITHER side is waxed, since faces are
                // computed independently per tank and a one-sided open would leave the two tanks'
                // models disagreeing about whether the wall between them is there.
                boolean alreadyOpen = this.openFaces.contains(direction);
                if (alreadyOpen || (!this.waxed && !other.waxed)) {
                    newOpenFaces.add(direction);
                }
            }
        }

        // Check all 4 horizontal diagonal cells for adjacent fish tanks. Diagonals share no wall
        // with this tank — unlike the orthogonal loop above, waxing (which only ever gated a
        // shared wall opening) is irrelevant here; a diagonal is simply "is this corner cell
        // occupied by a same-collection tank", recomputed fresh every time like openFaces.
        Set<TankDiagonal> newFilledDiagonals = EnumSet.noneOf(TankDiagonal.class);
        for (TankDiagonal diagonal : TankDiagonal.values()) {
            BlockPos diagonalPos = pos.relative(diagonal.first()).relative(diagonal.second());
            BlockEntity diagonalBE = level.getBlockEntity(diagonalPos);
            if (diagonalBE instanceof FishTankBlockEntity other
                    && other.getShape().connectionCollection().equals(this.shape.connectionCollection())) {
                newFilledDiagonals.add(diagonal);
            }
        }

        // Check all 8 edge-diagonal cells (one horizontal step + one vertical step) for adjacent
        // fish tanks. Same reasoning as the horizontal-diagonal loop above: an edge diagonal
        // shares no wall with this tank, so waxing is irrelevant — just "is this cell occupied by
        // a same-collection tank", recomputed fresh every time.
        Set<TankEdgeDiagonal> newFilledEdgeDiagonals = EnumSet.noneOf(TankEdgeDiagonal.class);
        for (TankEdgeDiagonal edgeDiagonal : TankEdgeDiagonal.values()) {
            BlockPos edgeDiagonalPos = pos.relative(edgeDiagonal.horizontal()).relative(edgeDiagonal.vertical());
            BlockEntity edgeDiagonalBE = level.getBlockEntity(edgeDiagonalPos);
            if (edgeDiagonalBE instanceof FishTankBlockEntity other
                    && other.getShape().connectionCollection().equals(this.shape.connectionCollection())) {
                newFilledEdgeDiagonals.add(edgeDiagonal);
            }
        }

        boolean facesChanged = !newOpenFaces.equals(this.openFaces);
        boolean diagonalsChanged = !newFilledDiagonals.equals(this.filledDiagonals);
        boolean edgeDiagonalsChanged = !newFilledEdgeDiagonals.equals(this.filledEdgeDiagonals);

        // Only update if the connections have changed
        if (facesChanged || diagonalsChanged || edgeDiagonalsChanged) {
            this.openFaces = newOpenFaces;
            this.filledDiagonals = newFilledDiagonals;
            this.filledEdgeDiagonals = newFilledEdgeDiagonals;
            if (facesChanged) TankGroups.bumpMembershipEpoch();
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
    protected void saveAdditional(CompoundTag output) {
        super.saveAdditional(output);
        saveTankData(output, true);
    }

    /**
     * A pick-blocked item copy (ctrl+pick) ends up carrying connectivity (open faces / waxed state)
     * on 1.20.1 — unlike 26.1.2, which strips it via {@code saveCustomOnly(ValueOutput)}, a hook that
     * doesn't exist before 1.21.6, so there's no way to distinguish "saving for world persistence"
     * from "saving for item copy" here. Harmless in practice: {@link
     * grill24.fishtastic.block.FishTankBlock#onPlace} unconditionally recomputes every placed tank's
     * connections from its actual neighbors, discarding whatever bits the copied item's {@link
     * #load} seeded, so a stale copy never produces a stale placement.
     */
    private void saveTankData(CompoundTag output, boolean includeConnectivity) {
        String frameId = BuiltInRegistries.BLOCK.getKey(frameBlock).toString();
        String sandId = BuiltInRegistries.BLOCK.getKey(sandBlock).toString();
        String glassId = BuiltInRegistries.BLOCK.getKey(glassBlock).toString();
        output.putString("FrameBlock", frameId);
        output.putString("SandBlock", sandId);
        output.putString("GlassBlock", glassId);
        output.putString("Shape", shape.getSerializedName());

        if (includeConnectivity) {
            // Save open faces as a bit field
            int openFacesBits = 0;
            for (Direction dir : openFaces) {
                openFacesBits |= (1 << dir.ordinal());
            }
            output.putInt("OpenFaces", openFacesBits);
            int filledDiagonalsBits = 0;
            for (TankDiagonal diagonal : filledDiagonals) {
                filledDiagonalsBits |= (1 << diagonal.ordinal());
            }
            if (filledDiagonalsBits != 0) {
                output.putInt("FilledDiagonals", filledDiagonalsBits);
            }
            int filledEdgeDiagonalsBits = 0;
            for (TankEdgeDiagonal edgeDiagonal : filledEdgeDiagonals) {
                filledEdgeDiagonalsBits |= (1 << edgeDiagonal.ordinal());
            }
            if (filledEdgeDiagonalsBits != 0) {
                output.putInt("FilledEdgeDiagonals", filledEdgeDiagonalsBits);
            }
            if (waxed) {
                output.putBoolean("Waxed", true);
            }
        }

        // Save items as a list of {Slot, Stack} entries
        ListTag itemsList = BlockEntityNbt.childrenList(output, "Items");
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                CompoundTag child = BlockEntityNbt.addChild(itemsList);
                child.putInt("Slot", i);
                BlockEntityNbt.store(child, "Stack", ItemStack.CODEC, stack);
                if (itemMirrored[i]) {
                    child.putBoolean("Mirrored", true);
                }
            }
        }

        // Save first item rotation
        output.putFloat("FirstItemRotation", firstItemRotation);

        // Save cosmetics
        ListTag cosmeticsList = BlockEntityNbt.childrenList(output, "Cosmetics");
        for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : cosmetics.entrySet()) {
            CosmeticGridCell cell = entry.getKey();
            PlacedCosmetic cosmetic = entry.getValue();
            String blockId = BuiltInRegistries.BLOCK.getKey(cosmetic.block()).toString();
            CompoundTag child = BlockEntityNbt.addChild(cosmeticsList);
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

        // Save ceiling cosmetics: block and segment count only — every segment's state is derived
        // from those by HangingCosmetics, so nothing else needs to round-trip.
        ListTag ceilingList = BlockEntityNbt.childrenList(output, "CeilingCosmetics");
        for (Map.Entry<CosmeticGridCell, PlacedCosmetic> entry : ceilingCosmetics.entrySet()) {
            CompoundTag child = BlockEntityNbt.addChild(ceilingList);
            child.putInt("GridX", entry.getKey().gridX());
            child.putInt("GridZ", entry.getKey().gridZ());
            child.putString("Block", BuiltInRegistries.BLOCK.getKey(entry.getValue().block()).toString());
            child.putInt("Height", entry.getValue().height());
        }

        if (spanLink != null) {
            CompoundTag link = BlockEntityNbt.child(output, "SpanLink");
            link.putInt("DX", spanLink.toAnchor().getX());
            link.putInt("DY", spanLink.toAnchor().getY());
            link.putInt("DZ", spanLink.toAnchor().getZ());
            link.putIntArray("Cells", spanLink.cells().stream().mapToInt(CosmeticGridCell::packed).toArray());
        }

        // Save structure cosmetics (anchor cells only; footprint is re-derived from the structure
        // definition on load, not stored here).
        ListTag structureCosmeticsList = BlockEntityNbt.childrenList(output, "StructureCosmetics");
        for (Map.Entry<CosmeticGridCell, PlacedStructureCosmetic> entry : structureCosmetics.entrySet()) {
            CosmeticGridCell cell = entry.getKey();
            PlacedStructureCosmetic placed = entry.getValue();
            CompoundTag child = BlockEntityNbt.addChild(structureCosmeticsList);
            child.putInt("GridX", cell.gridX());
            child.putInt("GridZ", cell.gridZ());
            child.putString("StructureId", placed.structureId().location().toString());
            BlockEntityNbt.store(child, "Rotation", Rotation.CODEC, placed.rotation());
        }
    }

    @Override
    public void load(CompoundTag input) {
        super.load(input);
        // Load frame block
        String frameBlockStr = BlockEntityNbt.getStringOr(input, "FrameBlock", "");
        if (!frameBlockStr.isEmpty()) {
            ResourceLocation blockId = Ids.tryParse(frameBlockStr);
            if (blockId != null) {
                Block b = BuiltInRegistries.BLOCK.get(blockId);
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
        String sandBlockStr = BlockEntityNbt.getStringOr(input, "SandBlock", "");
        if (!sandBlockStr.isEmpty()) {
            ResourceLocation blockId = Ids.tryParse(sandBlockStr);
            if (blockId != null) {
                Block b = BuiltInRegistries.BLOCK.get(blockId);
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
        String glassBlockStr = BlockEntityNbt.getStringOr(input, "GlassBlock", "");
        if (!glassBlockStr.isEmpty()) {
            ResourceLocation blockId = Ids.tryParse(glassBlockStr);
            if (blockId != null) {
                Block b = BuiltInRegistries.BLOCK.get(blockId);
                if (b != null) {
                    glassBlock = b;
                } else {
                    Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, glassBlock registry lookup returned null for id={}", worldPosition, blockId);
                }
            } else {
                Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, failed to parse GlassBlock id='{}'", worldPosition, glassBlockStr);
            }
        }

        // Load shape (body geometry)
        shape = FishTankShape.bySerializedName(BlockEntityNbt.getStringOr(input, "Shape", FishTankShape.STANDARD.getSerializedName()));

        // Load open faces. Default is the CURRENT in-memory state, not 0 — a fresh block entity's
        // openFaces starts empty anyway, so this is a no-op for normal world load, but it matters
        // for the ctrl+pick-block item-data merge path: that path's item tag never contains
        // "OpenFaces" (see #saveCustomOnly), and the merge must not stomp the connections
        // updateConnections() just computed during placement with a reset to closed.
        int currentOpenFacesBits = 0;
        for (Direction dir : openFaces) {
            currentOpenFacesBits |= (1 << dir.ordinal());
        }
        int openFacesBits = BlockEntityNbt.getIntOr(input, "OpenFaces", currentOpenFacesBits);
        openFaces.clear();
        for (Direction dir : Direction.values()) {
            if ((openFacesBits & (1 << dir.ordinal())) != 0) {
                openFaces.add(dir);
            }
        }
        // Every content change reaches the client through this same path, so the epoch must move
        // only when the adjacency really did — otherwise adding one fish would invalidate the
        // group cache and rebuild the whole distance field, which is the hitch §5.3(a) removes.
        if (openFacesBits != currentOpenFacesBits) TankGroups.bumpMembershipEpoch();

        // Load filled diagonals (same preserve-current-if-absent reasoning as open faces above;
        // no membership-epoch bump — diagonals don't affect group membership, only corner-post
        // rendering).
        int currentFilledDiagonalsBits = 0;
        for (TankDiagonal diagonal : filledDiagonals) {
            currentFilledDiagonalsBits |= (1 << diagonal.ordinal());
        }
        int filledDiagonalsBits = BlockEntityNbt.getIntOr(input, "FilledDiagonals", currentFilledDiagonalsBits);
        filledDiagonals.clear();
        for (TankDiagonal diagonal : TankDiagonal.values()) {
            if ((filledDiagonalsBits & (1 << diagonal.ordinal())) != 0) {
                filledDiagonals.add(diagonal);
            }
        }

        // Load filled edge diagonals (same preserve-current-if-absent reasoning as open faces
        // above; no membership-epoch bump — edge diagonals don't affect group membership, only
        // frame-beam rendering).
        int currentFilledEdgeDiagonalsBits = 0;
        for (TankEdgeDiagonal edgeDiagonal : filledEdgeDiagonals) {
            currentFilledEdgeDiagonalsBits |= (1 << edgeDiagonal.ordinal());
        }
        int filledEdgeDiagonalsBits = BlockEntityNbt.getIntOr(input, "FilledEdgeDiagonals", currentFilledEdgeDiagonalsBits);
        filledEdgeDiagonals.clear();
        for (TankEdgeDiagonal edgeDiagonal : TankEdgeDiagonal.values()) {
            if ((filledEdgeDiagonalsBits & (1 << edgeDiagonal.ordinal())) != 0) {
                filledEdgeDiagonals.add(edgeDiagonal);
            }
        }

        // Load waxed state (same preserve-current-if-absent reasoning as open faces above)
        waxed = BlockEntityNbt.getBooleanOr(input, "Waxed", waxed);

        // Load items
        for (int i = 0; i < CONTAINER_SIZE; i++) {
            items.set(i, ItemStack.EMPTY);
            itemMirrored[i] = false;
        }
        BlockEntityNbt.childrenListOrEmpty(input, "Items").forEach(child -> {
            int slot = BlockEntityNbt.getIntOr(child, "Slot", -1);
            if (slot >= 0 && slot < CONTAINER_SIZE) {
                BlockEntityNbt.read(child, "Stack", ItemStack.CODEC).ifPresent(stack -> items.set(slot, stack));
                itemMirrored[slot] = BlockEntityNbt.getBooleanOr(child, "Mirrored", false);
            }
        });

        // Load first item rotation
        firstItemRotation = BlockEntityNbt.getFloatOr(input, "FirstItemRotation", 0f);

        // Load cosmetics
        cosmetics.clear();
        BlockEntityNbt.childrenListOrEmpty(input, "Cosmetics").forEach(child -> {
            int gridX = BlockEntityNbt.getIntOr(child, "GridX", -1);
            int gridZ = BlockEntityNbt.getIntOr(child, "GridZ", -1);
            String blockStr = BlockEntityNbt.getStringOr(child, "Block", "");
            if (CosmeticGridCell.isValid(gridX, gridZ) && !blockStr.isEmpty()) {
                ResourceLocation blockId = Ids.tryParse(blockStr);
                if (blockId != null) {
                    Block b = BuiltInRegistries.BLOCK.get(blockId);
                    if (b != null) {
                        BlockState state = b.defaultBlockState();
                        if (b instanceof SeaPickleBlock) {
                            int pickles = BlockEntityNbt.getIntOr(child, "Pickles", 1);
                            state = state.setValue(BlockStateProperties.PICKLES, Math.min(pickles, SeaPickleBlock.MAX_PICKLES));
                        }
                        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                            String facingStr = BlockEntityNbt.getStringOr(child, "Facing", "");
                            Direction facing = Direction.byName(facingStr);
                            if (facing != null) {
                                state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
                            }
                        }
                        int height = BlockEntityNbt.getIntOr(child, "Height", 1);
                        cosmetics.put(new CosmeticGridCell(gridX, gridZ), new PlacedCosmetic(state, height));
                    } else {
                        Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, cosmetic block lookup returned null for id={}", worldPosition, blockId);
                    }
                }
            }
        });

        // Load ceiling cosmetics
        ceilingCosmetics.clear();
        BlockEntityNbt.childrenListOrEmpty(input, "CeilingCosmetics").forEach(child -> {
            int gridX = BlockEntityNbt.getIntOr(child, "GridX", -1);
            int gridZ = BlockEntityNbt.getIntOr(child, "GridZ", -1);
            ResourceLocation blockId = Ids.tryParse(BlockEntityNbt.getStringOr(child, "Block", ""));
            if (!CosmeticGridCell.isValid(gridX, gridZ) || blockId == null) return;
            Block b = BuiltInRegistries.BLOCK.get(blockId);
            if (b == null || b == Blocks.AIR) {
                Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, ceiling cosmetic block lookup returned nothing for id={}", worldPosition, blockId);
                return;
            }
            ceilingCosmetics.put(new CosmeticGridCell(gridX, gridZ), new PlacedCosmetic(b.defaultBlockState(), Math.max(1, BlockEntityNbt.getIntOr(child, "Height", 1))));
        });

        // Load structure cosmetics. Only the raw id + rotation are recorded here — resolving each
        // id to its CosmeticStructure (for the footprint) needs registry access, and `level` isn't
        // set yet this early in the block entity's lifecycle (load() runs inside the static
        // BlockEntity.loadStatic factory, before LevelChunk attaches it via setLevel — MC20 has no
        // ValueInput-style registry-aware load hook to do this resolution inline, unlike 26.1.2's).
        // See #setLevel/#rebuildStructureCellIndex, which redo this once a level is available.
        structureCosmetics.clear();
        BlockEntityNbt.childrenListOrEmpty(input, "StructureCosmetics").forEach(child -> {
            int gridX = BlockEntityNbt.getIntOr(child, "GridX", -1);
            int gridZ = BlockEntityNbt.getIntOr(child, "GridZ", -1);
            String structureIdStr = BlockEntityNbt.getStringOr(child, "StructureId", "");
            if (!CosmeticGridCell.isValid(gridX, gridZ) || structureIdStr.isEmpty()) {
                return;
            }
            ResourceLocation structureId = Ids.tryParse(structureIdStr);
            if (structureId == null) {
                Fishtastic.LOGGER.warn("[FishTankBE.loadAdditional] pos={}, failed to parse StructureId '{}'", worldPosition, structureIdStr);
                return;
            }
            Rotation rotation = BlockEntityNbt.read(child, "Rotation", Rotation.CODEC).orElse(Rotation.NONE);
            ResourceKey<CosmeticStructure> key = ResourceKey.create(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY, structureId);
            structureCosmetics.put(new CosmeticGridCell(gridX, gridZ), new PlacedStructureCosmetic(key, rotation));
        });

        spanLink = BlockEntityNbt.readChild(input, "SpanLink").map(link -> {
            List<CosmeticGridCell> cells = new java.util.ArrayList<>();
            for (int packed : link.getIntArray("Cells")) {
                if (packed >= 0 && packed < CosmeticGridCell.GRID_SIZE * CosmeticGridCell.GRID_SIZE) {
                    cells.add(CosmeticGridCell.unpack(packed));
                }
            }
            return new SpanLink(new BlockPos(BlockEntityNbt.getIntOr(link, "DX", 0), BlockEntityNbt.getIntOr(link, "DY", 0), BlockEntityNbt.getIntOr(link, "DZ", 0)), List.copyOf(cells));
        }).orElse(null);
        // A span link's cells join the structure cell index in rebuildStructureCellIndex (setLevel),
        // with the rest of the index; on a client update the level is already set, so redo it now.
        if (level != null) rebuildStructureCellIndex();

        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
        // Cosmetics stored here are also meshed by other tanks (kelp above, hanging strands below,
        // a span's box): they need re-meshing too. Client only — the mesh class is client code.
        if (level != null && level.isClientSide()) {
            grill24.fishtastic.client.compositemodel.TankCosmeticMesh.refreshDependents(this);
        }
    }

    /**
     * Resolves every entry in {@link #structureCosmetics} against the level's registry access and
     * rebuilds {@link #structureCellIndex} from scratch. Called from {@link #setLevel} — the
     * earliest point a level (and so registry access) is available after {@link #load} parsed the
     * raw ids — and safe to call again any time the level is known.
     */
    private void rebuildStructureCellIndex() {
        structureCellIndex.clear();
        if (level == null) return;
        HolderLookup.Provider registries = level.registryAccess();
        for (Map.Entry<CosmeticGridCell, PlacedStructureCosmetic> entry : structureCosmetics.entrySet()) {
            CosmeticGridCell anchor = entry.getKey();
            PlacedStructureCosmetic placed = entry.getValue();
            Optional<CosmeticStructure> structure = registries.lookupOrThrow(FishtasticRegistries.COSMETIC_STRUCTURE_REGISTRY_KEY)
                    .get(placed.structureId())
                    .map(net.minecraft.core.Holder.Reference::value);
            if (structure.isEmpty()) {
                Fishtastic.LOGGER.warn("[FishTankBE.rebuildStructureCellIndex] pos={}, cosmetic structure lookup returned nothing for id={}; skipping", worldPosition, placed.structureId());
                continue;
            }
            List<CosmeticGridCell> footprintCells = structure.get().span().isPresent()
                    ? SpanStructures.footprint(structure.get(), placed.rotation()).getOrDefault(BlockPos.ZERO, List.of())
                    : rotatedFootprintCells(structure.get(), placed.rotation(), anchor);
            for (CosmeticGridCell footprintCell : footprintCells) {
                structureCellIndex.put(footprintCell, anchor);
            }
        }
        if (spanLink != null) {
            for (CosmeticGridCell cell : spanLink.cells()) structureCellIndex.put(cell, cell);
        }
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        rebuildStructureCellIndex();
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
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
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
            if (items.get(slot).isEmpty() && !stack.isEmpty()) {
                itemMirrored[slot] = level != null && level.getRandom().nextBoolean();
            }
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
        return addItem(stack, 0f);
    }

    /**
     * Try to add an item to the tank with a specific rotation. Returns true if successful.
     */
    public boolean addItem(ItemStack stack, float rotation) {
        if (stack.isEmpty()) {
            return false;
        }

        // Try to merge with existing stacks first
        for (int i = 0; i < items.size(); i++) {
            ItemStack existing = items.get(i);
            if (!existing.isEmpty() && FishtasticItemData.isSameItemSameData(existing, stack)) {
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

        // Reject filling a new slot once the tank's occupants would exceed the size-based capacity
        // budget (see TankCapacity) — a few large fish or a crowd of small ones, rather than a
        // flat per-slot count. SwarmConfig#count is a separate, render-side draw-count ceiling
        // (see FishTankBlockEntityRenderer) and no longer gates insertion.
        if (level != null) {
            List<ItemStack> occupants = new ArrayList<>();
            for (int i = 0; i < items.size(); i++) {
                ItemStack existing = items.get(i);
                if (!existing.isEmpty()) {
                    occupants.add(existing);
                }
            }
            if (!TankCapacity.canAdd(occupants, stack, level)) {
                return false;
            }
        }

        // Try to find an empty slot
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).isEmpty()) {
                items.set(i, stack.copy());
                itemMirrored[i] = level != null && level.getRandom().nextBoolean();
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

    /**
     * Get the slot index of the first non-empty item, or -1 if the tank is empty.
     */
    public int getFirstItemSlot() {
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether the item in the given slot should render left/right-mirrored by the upright-float
     * animator. Rolled fresh each time a fish is placed into an empty slot.
     */
    public boolean isItemMirrored(int slot) {
        return slot >= 0 && slot < itemMirrored.length && itemMirrored[slot];
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

    /** Cosmetics hung from this tank's lid, keyed by their ceiling-grid cell. */
    public Map<CosmeticGridCell, PlacedCosmetic> getCeilingCosmetics() {
        return Collections.unmodifiableMap(ceilingCosmetics);
    }

    public void setCeilingCosmetic(CosmeticGridCell cell, PlacedCosmetic cosmetic) {
        ceilingCosmetics.put(cell, cosmetic);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void removeCeilingCosmetic(CosmeticGridCell cell) {
        if (ceilingCosmetics.remove(cell) != null) {
            setChanged();
            if (level != null && !level.isClientSide()) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    /**
     * Takes one segment off the hanging cosmetic at {@code cell} (see
     * {@link HangingCosmetics#removeOne}) and returns its item, or {@link ItemStack#EMPTY} if the
     * cell held nothing.
     */
    public ItemStack removeCeilingCosmeticEntry(CosmeticGridCell cell) {
        PlacedCosmetic existing = ceilingCosmetics.get(cell);
        if (existing == null) {
            return ItemStack.EMPTY;
        }
        HangingCosmetics.Removal removal = HangingCosmetics.removeOne(existing);
        if (removal.remaining() != null) {
            setCeilingCosmetic(cell, removal.remaining());
        } else {
            removeCeilingCosmetic(cell);
        }
        Item returnItem = removal.returned().asItem();
        return returnItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(returnItem);
    }

    public Map<CosmeticGridCell, PlacedStructureCosmetic> getStructureCosmetics() {
        return Collections.unmodifiableMap(structureCosmetics);
    }

    /** This tank's link into a spanning structure anchored elsewhere, if any (unvalidated — see {@link SpanStructures#resolve}). */
    @Nullable
    public SpanLink getSpanLink() {
        return spanLink;
    }

    public void setSpanLink(SpanLink link) {
        clearSpanLinkCells();
        spanLink = link;
        for (CosmeticGridCell cell : link.cells()) structureCellIndex.put(cell, cell);
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void clearSpanLink() {
        if (spanLink == null) return;
        clearSpanLinkCells();
        spanLink = null;
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void clearSpanLinkCells() {
        if (spanLink != null) {
            for (CosmeticGridCell cell : spanLink.cells()) structureCellIndex.remove(cell, cell);
        }
    }

    /**
     * Every cell occupied by a placed structure (including anchors), mapped to that structure's
     * anchor cell — or, for cells a spanning structure anchored in another tank covers here, to
     * the cell itself.
     */
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
     * Removes one unit of the single-cell cosmetic at {@code cell} — one sea pickle, one kelp
     * segment, or the whole cosmetic for anything else — and returns the item that should be
     * given back to the player, or {@link ItemStack#EMPTY} if the cell held nothing. Mirrors the
     * old edit-mode removal branch's per-unit behavior (see docs/fish-tank-interaction-redesign.md).
     */
    public ItemStack removeCosmeticEntry(CosmeticGridCell cell) {
        PlacedCosmetic existing = cosmetics.get(cell);
        if (existing == null) {
            return ItemStack.EMPTY;
        }
        Item returnItem = FishTankCosmeticItem.forBlock(existing.block());
        if (returnItem == null) {
            returnItem = existing.block().asItem();
        }
        if (existing.block() instanceof SeaPickleBlock) {
            int current = existing.blockState().getValue(BlockStateProperties.PICKLES);
            if (current > 1) {
                setCosmetic(cell, new PlacedCosmetic(existing.blockState().setValue(BlockStateProperties.PICKLES, current - 1)));
            } else {
                removeCosmetic(cell);
            }
        } else if (existing.block() == Blocks.KELP && existing.height() > 1) {
            setCosmetic(cell, new PlacedCosmetic(existing.blockState(), existing.height() - 1));
        } else {
            removeCosmetic(cell);
        }
        return returnItem == Items.AIR ? ItemStack.EMPTY : new ItemStack(returnItem);
    }

    /**
     * Removes the whole structure cosmetic anchored at {@code anchor} and returns the item that
     * should be given back to the player, or {@link ItemStack#EMPTY} if there was nothing there.
     */
    public ItemStack removeStructureCosmeticEntry(CosmeticGridCell anchor) {
        PlacedStructureCosmetic placed = structureCosmetics.get(anchor);
        if (placed == null) {
            return ItemStack.EMPTY;
        }
        if (level != null && !level.isClientSide()) {
            SpanStructures.clearLinks(level, this, placed);
        }
        removeStructureCosmetic(anchor);
        FishTankStructureCosmeticItem returnItem = FishTankStructureCosmeticItem.forStructure(placed.structureId());
        return returnItem != null ? new ItemStack(returnItem) : ItemStack.EMPTY;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FishTankBrowserMenu(containerId, inventory, this);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.fishtastic.fish_tank");
    }
}
