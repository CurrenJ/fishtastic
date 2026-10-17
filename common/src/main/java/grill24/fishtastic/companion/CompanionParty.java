package grill24.fishtastic.companion;

import com.mojang.serialization.Codec;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.network.codec.BufCodec;
import grill24.fishtastic.network.codec.BufCodecs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A player's companion party: three slots, each holding one bonded fish or {@link ItemStack#EMPTY}
 * (docs/fish-companions-party.md §5.1). Slot 0 is the lead. Immutable: {@link #with} returns a new
 * party, and the stacks are private copies — never mutate a stack read from here.
 */
public record CompanionParty(List<ItemStack> slots) {
    public static final int SIZE = 3;
    public static final int LEAD = 0;
    public static final CompanionParty EMPTY = new CompanionParty(List.of());

    public CompanionParty {
        List<ItemStack> fixed = new ArrayList<>(SIZE);
        for (int i = 0; i < SIZE; i++) {
            ItemStack stack = i < slots.size() ? slots.get(i) : ItemStack.EMPTY;
            if (stack == null || stack.isEmpty()) {
                fixed.add(ItemStack.EMPTY);
            } else {
                ItemStack copy = stack.copy();
                copy.setCount(1);
                fixed.add(copy);
            }
        }
        slots = Collections.unmodifiableList(fixed);
    }

    public static boolean validSlot(int slot) {
        return slot >= 0 && slot < SIZE;
    }

    public ItemStack get(int slot) {
        return validSlot(slot) ? slots.get(slot) : ItemStack.EMPTY;
    }

    public CompanionParty with(int slot, ItemStack stack) {
        if (!validSlot(slot)) throw new IllegalArgumentException("companion slot " + slot);
        List<ItemStack> next = new ArrayList<>(slots);
        next.set(slot, stack);
        return new CompanionParty(next);
    }

    /** The first empty slot, or -1 when the party is full. */
    public int firstEmpty() {
        for (int i = 0; i < SIZE; i++) if (slots.get(i).isEmpty()) return i;
        return -1;
    }

    public boolean isEmpty() {
        for (ItemStack stack : slots) if (!stack.isEmpty()) return false;
        return true;
    }

    public int size() {
        int n = 0;
        for (ItemStack stack : slots) if (!stack.isEmpty()) n++;
        return n;
    }

    /** Same fish in the same slots, item data for item data. */
    public boolean matches(CompanionParty other) {
        for (int i = 0; i < SIZE; i++) if (!FishtasticItemData.matches(slots.get(i), other.slots.get(i))) return false;
        return true;
    }

    // ---------------------------------------------------------------- codecs

    /** 1.20.1 has no optional-stack codec: an empty slot is an empty compound, anything else is the stack's NBT. */
    private static final Codec<ItemStack> OPTIONAL_STACK = CompoundTag.CODEC.xmap(
            tag -> tag.isEmpty() ? ItemStack.EMPTY : ItemStack.of(tag),
            stack -> stack.isEmpty() ? new CompoundTag() : stack.save(new CompoundTag()));

    /** Persistent form, {@code {slots: [...]}} like the other versions. */
    public static final Codec<CompanionParty> CODEC = OPTIONAL_STACK.listOf()
            .fieldOf("slots").xmap(CompanionParty::new, CompanionParty::slots).codec();

    /** Sent to clients: always exactly {@link #SIZE} stacks. */
    public static final BufCodec<CompanionParty> STREAM_CODEC =
            BufCodecs.ITEM_STACK.apply(BufCodecs.list(SIZE))
                    .map(CompanionParty::new, CompanionParty::slots);
}
