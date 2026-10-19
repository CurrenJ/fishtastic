package grill24.fishtastic.companion;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The player's companion party: three slots of bonded fish, held in a platform player attachment
 * (docs/fish-companions-party.md §5.1). Each platform's attachment is saved in the player's own
 * file, copied on death, and synced to the player and everyone tracking them, so the same call
 * answers "what is this player's party" on the server and on any client that can see them.
 *
 * <p>Deliberately not in {@code FishCatchSavedData}: a backup restore rewinds that independently of
 * inventories, which would let a bond → backup → unequip → restore duplicate the fish.
 *
 * <p>Writes go through {@link Companions} on the server; nothing else should call the setters.
 */
public final class CompanionStorage {
    private CompanionStorage() {}

    /** The player's party, never null. */
    @ExpectPlatform
    public static CompanionParty party(Player player) {
        throw new AssertionError();
    }

    /** Replaces the player's whole party and syncs the change. */
    @ExpectPlatform
    public static void setParty(Player player, CompanionParty party) {
        throw new AssertionError();
    }

    /** The fish in {@code slot}, or {@link ItemStack#EMPTY}. Never mutate the returned stack. */
    public static ItemStack get(Player player, int slot) {
        return party(player).get(slot);
    }

    /** The lead (slot 0), or {@link ItemStack#EMPTY}. */
    public static ItemStack lead(Player player) {
        return get(player, CompanionParty.LEAD);
    }

    /** Puts {@code stack} in {@code slot} ({@link ItemStack#EMPTY} clears it) and syncs. */
    public static void set(Player player, int slot, ItemStack stack) {
        setParty(player, party(player).with(slot, stack));
    }

    /** Empties every slot. */
    public static void clear(Player player) {
        setParty(player, CompanionParty.EMPTY);
    }
}
