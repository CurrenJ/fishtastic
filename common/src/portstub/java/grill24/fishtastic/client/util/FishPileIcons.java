// PORT STUB: deleted in A5.3
package grill24.fishtastic.client.util;

import grill24.fishtastic.network.RecentCatch;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Stand-in for the fish-pile icon builder (A5.3, item models). The real one packs each group of
 * catches into a {@code pile_of_fish} stack whose BEWLR draws the pile; that item is
 * {@code builtin/entity} on 1.21.1 and renders nothing until A5.3 brings the BEWLRs back, so an
 * empty column is what the leaderboard would draw either way.
 */
public final class FishPileIcons {
    public static List<ItemStack> pileBlocks(List<RecentCatch> catches, int maxFish) {
        return List.of();
    }

    private FishPileIcons() {}
}
