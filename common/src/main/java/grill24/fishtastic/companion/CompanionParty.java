package grill24.fishtastic.companion;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import grill24.fishtastic.FishtasticItemData;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
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
            fixed.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
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

    /** Same fish in the same slots, component for component. */
    public boolean matches(CompanionParty other) {
        for (int i = 0; i < SIZE; i++) if (!FishtasticItemData.matches(slots.get(i), other.slots.get(i))) return false;
        return true;
    }

    // ---------------------------------------------------------------- codecs

    /** NeoForge's persistent form, {@code {slots: [...]}}. */
    public static final MapCodec<CompanionParty> MAP_CODEC = ItemStack.OPTIONAL_CODEC.listOf()
            .fieldOf("slots").xmap(CompanionParty::new, CompanionParty::slots);

    /** Fabric's persistent form: the same {@code {slots: [...]}}. */
    public static final Codec<CompanionParty> CODEC = MAP_CODEC.codec();

    /** Synced to the owner and everyone tracking them: always exactly {@link #SIZE} stacks. */
    public static final StreamCodec<RegistryFriendlyByteBuf, CompanionParty> STREAM_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(SIZE))
                    .map(CompanionParty::new, CompanionParty::slots);
}
