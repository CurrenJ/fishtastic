package grill24.fishtastic.companion;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The equipped fish companion: one {@link ItemStack} per player, held in a platform player
 * attachment (docs/fish-companions.md §8.1). Each platform's attachment is saved in the player's own
 * file, copied on death, and synced to the player and everyone tracking them — so the same call
 * answers "what is this player's companion" on the server and on any client that can see them.
 *
 * <p>Deliberately not in {@code FishCatchSavedData}: a backup restore rewinds that independently of
 * inventories, which would let a bond → backup → unequip → restore duplicate the fish.
 *
 * <p>Writes go through {@link Companions} on the server; nothing else should call {@link #set}.
 */
public final class CompanionStorage {
    private CompanionStorage() {}

    /** The player's companion, or {@link ItemStack#EMPTY}. Never mutate the returned stack. */
    @ExpectPlatform
    public static ItemStack get(Player player) {
        throw new AssertionError();
    }

    /** Replaces the player's companion ({@link ItemStack#EMPTY} clears it) and syncs the change. */
    @ExpectPlatform
    public static void set(Player player, ItemStack stack) {
        throw new AssertionError();
    }
}
