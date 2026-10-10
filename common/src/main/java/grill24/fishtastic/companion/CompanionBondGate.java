package grill24.fishtastic.companion;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Whether a player may bond with a fish of this species (docs/fish-companions.md §8.3, §8.4):
 * a fully complete encyclopedia entry for a normal species, the bond quest for an unlisted one.
 *
 * <p>Phase 2 stub — everything is allowed. Phase 4 (bonding UI) implements the real gate, and it
 * must stay server-side: the client only ever asks.
 */
public final class CompanionBondGate {
    private CompanionBondGate() {}

    public static boolean isUnlocked(ServerPlayer player, ItemStack fish) {
        return true;
    }
}
