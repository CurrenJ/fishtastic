package grill24.fishtastic.companion;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The player's companion party: three slots of bonded fish (docs/fish-companions-party.md §5.1).
 * On 1.20.1 the server side lives in {@link CompanionPartySavedData} (no attachment API on Fabric
 * 0.92 / Forge 47 — see that class); the call sites are identical to the other versions.
 *
 * <p>Writes go through {@link Companions} on the server; nothing else should call the setters.
 */
public final class CompanionStorage {
    private CompanionStorage() {}

    /**
     * What a client knows about parties. TODO(F09): filled by the party sync packet once the client
     * companion layer lands; until then a client reads every party as empty.
     */
    private static final Map<UUID, CompanionParty> CLIENT_PARTIES = new ConcurrentHashMap<>();

    /** The player's party, never null. */
    public static CompanionParty party(Player player) {
        MinecraftServer server = player.getServer();
        if (server == null) return CLIENT_PARTIES.getOrDefault(player.getUUID(), CompanionParty.EMPTY);
        return CompanionPartySavedData.getOrCreate(server).get(player.getUUID());
    }

    /** Replaces the player's whole party. */
    public static void setParty(Player player, CompanionParty party) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            if (party.isEmpty()) CLIENT_PARTIES.remove(player.getUUID());
            else CLIENT_PARTIES.put(player.getUUID(), party);
            return;
        }
        CompanionPartySavedData.getOrCreate(server).set(player.getUUID(), party);
    }

    /** The fish in {@code slot}, or {@link ItemStack#EMPTY}. Never mutate the returned stack. */
    public static ItemStack get(Player player, int slot) {
        return party(player).get(slot);
    }

    /** The lead (slot 0), or {@link ItemStack#EMPTY}. */
    public static ItemStack lead(Player player) {
        return get(player, CompanionParty.LEAD);
    }

    /** Puts {@code stack} in {@code slot} ({@link ItemStack#EMPTY} clears it). */
    public static void set(Player player, int slot, ItemStack stack) {
        setParty(player, party(player).with(slot, stack));
    }

    /** Empties every slot. */
    public static void clear(Player player) {
        setParty(player, CompanionParty.EMPTY);
    }
}
