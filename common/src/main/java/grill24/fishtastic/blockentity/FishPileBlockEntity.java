package grill24.fishtastic.blockentity;

import grill24.fishtastic.FishtasticBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Holds the individual fish stacked in a placed {@link grill24.fishtastic.block.FishPileBlock},
 * up to {@link #MAX_FISH}. Unlike {@link FishTankBlockEntity} this isn't a {@code Container} —
 * fish only ever move in and out one at a time (see {@link grill24.fishtastic.block.FishPileBlock}'s
 * interaction handlers), so a plain list is enough.
 */
public class FishPileBlockEntity extends BlockEntity {
    // Single source of truth for the render geometry FishPileBlockEntityRenderer uses to lay out
    // stacked fish, kept here (rather than in that client-only class) so MAX_FISH below — a
    // server-relevant capacity — can be derived from it without the block entity depending on any
    // client rendering class.
    //
    // Every fish item model parents to minecraft:item/generated (see e.g. models/item/betta.json),
    // whose geometry is baked by ItemModelGenerator.bakeExtrudedSprite: a quad from (0,0,7.5) to
    // (16,16,8.5) in 16-unit model space — i.e. the depth (Z) axis is exactly 1px out of 16 thick,
    // centred. FIXED display context defaults to ItemTransform.NO_TRANSFORM (no model here
    // overrides it — see ItemTransforms.NO_TRANSFORMS), whose apply() does nothing but
    // pose.translate(-0.5,-0.5,-0.5): so the baked quad ends up centred exactly on the poseStack's
    // current origin, with that depth axis spanning ±(1/16)/2 = ±1/32 block.
    public static final float RENDER_SCALE = 0.55f;
    private static final float ITEM_HALF_THICKNESS_AT_SCALE_1 = 1f / 32f;
    // After the renderer's 90°-about-X "lay flat" rotation, this depth axis becomes the item's
    // vertical extent, so its scaled half-thickness is what both the base offset and layer spacing
    // need.
    private static final float ITEM_HALF_THICKNESS = ITEM_HALF_THICKNESS_AT_SCALE_1 * RENDER_SCALE;
    // ItemStackRenderState.submit renders every item centred on the poseStack's current origin, so
    // translating a layer's origin up by its own half-thickness puts that layer's *bottom* — not
    // its centre — at y=0: flush with the top of whatever block this pile is placed on.
    public static final float RENDER_BASE_Y = ITEM_HALF_THICKNESS;
    // A full scaled thickness between layer origins stacks each fish flush against the last
    // (previous layer's top == next layer's bottom), with no gap or overlap.
    public static final float RENDER_LAYER_HEIGHT = 2f * ITEM_HALF_THICKNESS;

    // As many flush-stacked layers as fit under y=1 (one block tall): the topmost layer's own top
    // is at RENDER_BASE_Y + MAX_FISH * RENDER_LAYER_HEIGHT, so the largest MAX_FISH keeping that
    // at or under 1 block is floor((1 - RENDER_BASE_Y) / RENDER_LAYER_HEIGHT).
    public static final int MAX_FISH = (int) Math.floor((1f - RENDER_BASE_Y) / RENDER_LAYER_HEIGHT);

    private final NonNullList<ItemStack> fish = NonNullList.create();

    public FishPileBlockEntity(BlockPos pos, BlockState state) {
        super(FishtasticBlockEntityTypes.FISH_PILE.value(), pos, state);
    }

    /** Read-only snapshot of the piled fish, in insertion order (oldest first). */
    public List<ItemStack> getFish() {
        return Collections.unmodifiableList(fish);
    }

    public boolean isFull() {
        return fish.size() >= MAX_FISH;
    }

    public boolean isEmptyPile() {
        return fish.isEmpty();
    }

    /** Adds a single fish to this pile if there's room. Returns whether it fit. */
    public boolean insertSingle(ItemStack stack) {
        if (isFull() || stack.isEmpty()) {
            return false;
        }
        ItemStack single = stack.copy();
        single.setCount(1);
        fish.add(single);
        setChanged();
        return true;
    }

    /** Removes and returns the most recently added fish, or {@link ItemStack#EMPTY} if the pile is empty. */
    public ItemStack removeTopFish() {
        if (fish.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = fish.remove(fish.size() - 1);
        setChanged();
        return removed;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ValueOutput.ValueOutputList fishList = output.childrenList("Fish");
        for (ItemStack stack : fish) {
            fishList.addChild().store("Stack", ItemStack.CODEC, stack);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        fish.clear();
        input.childrenListOrEmpty("Fish").forEach(child ->
                child.read("Stack", ItemStack.CODEC).ifPresent(fish::add));
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
