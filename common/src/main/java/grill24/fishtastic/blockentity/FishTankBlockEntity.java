package grill24.fishtastic.blockentity;

import grill24.fishtastic.FishtasticBlockEntityTypes;
import grill24.fishtastic.architectury.RegistrationApiSided;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Set;

public class FishTankBlockEntity extends BlockEntity {
    public FishTankBlockEntity(BlockPos blockPos, BlockState blockState) {
        super(FishtasticBlockEntityTypes.FISH_TANK.value(), blockPos, blockState);
    }

    // Store the frame block directly - can be any block
    private Block frameBlock = Blocks.OAK_PLANKS; // Default frame block

    // Store the sand block directly - can be any block
    private Block sandBlock = Blocks.SAND; // Default sand block

    // Store the glass block for edges
    private Block glassBlock = Blocks.BLUE_STAINED_GLASS; // Default glass block

    // Store which faces are connected to other fish tanks (open faces)
    private Set<Direction> openFaces = EnumSet.noneOf(Direction.class);

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
        if (level != null && !level.isClientSide) {
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
        if (level != null && !level.isClientSide) {
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
            if (!level.isClientSide) {
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
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        // Request model data update for re-rendering
        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    /**
     * Set the sand block for this fish tank.
     */
    public void setSandBlock(Block block) {
        this.sandBlock = block;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        // Request model data update for re-rendering
        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    /**
     * Set the glass block for this fish tank edges.
     */
    public void setGlassBlock(Block block) {
        this.glassBlock = block;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
        // Request model data update for re-rendering
        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("FrameBlock", BuiltInRegistries.BLOCK.getKey(frameBlock).toString());
        tag.putString("SandBlock", BuiltInRegistries.BLOCK.getKey(sandBlock).toString());
        tag.putString("GlassBlock", BuiltInRegistries.BLOCK.getKey(glassBlock).toString());

        // Save open faces as a bit field
        int openFacesBits = 0;
        for (Direction dir : openFaces) {
            openFacesBits |= (1 << dir.ordinal());
        }
        tag.putInt("OpenFaces", openFacesBits);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        // Load frame block
        if (tag.contains("FrameBlock")) {
            ResourceLocation blockId = ResourceLocation.tryParse(tag.getString("FrameBlock"));
            if (blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)) {
                frameBlock = BuiltInRegistries.BLOCK.get(blockId);
            } else {
                frameBlock = Blocks.OAK_PLANKS; // Graceful fallback
            }
        }

        // Load sand block
        if (tag.contains("SandBlock")) {
            ResourceLocation blockId = ResourceLocation.tryParse(tag.getString("SandBlock"));
            if (blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)) {
                sandBlock = BuiltInRegistries.BLOCK.get(blockId);
            } else {
                sandBlock = Blocks.SAND; // Graceful fallback
            }
        }

        // Load glass block
        if (tag.contains("GlassBlock")) {
            ResourceLocation blockId = ResourceLocation.tryParse(tag.getString("GlassBlock"));
            if (blockId != null && BuiltInRegistries.BLOCK.containsKey(blockId)) {
                glassBlock = BuiltInRegistries.BLOCK.get(blockId);
            } else {
                glassBlock = Blocks.BLUE_STAINED_GLASS; // Graceful fallback
            }
        }

        // Load open faces
        if (tag.contains("OpenFaces")) {
            int openFacesBits = tag.getInt("OpenFaces");
            openFaces.clear();
            for (Direction dir : Direction.values()) {
                if ((openFacesBits & (1 << dir.ordinal())) != 0) {
                    openFaces.add(dir);
                }
            }
        }

        RegistrationApiSided.getInstance().requestModelDataUpdate(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    @Nullable
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

}
