package grill24.fishtastic.companion;

import grill24.fishtastic.server.FishCatchSavedData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Whether a player may bond with a fish of this species (docs/fish-companions.md §8.4):
 * a fully complete encyclopedia entry for a normal species, a completed (or claimed) bond quest
 * for an unlisted one. Both are stored the moment they first hold, so a bond never re-locks.
 *
 * <p>Server-side only — the client only ever asks. {@link CompanionBondUnlocks#refresh} runs here
 * too, so a player whose save predates this phase (or whose completion was reached while nobody
 * was looking) is grandfathered in on their first check rather than being refused.
 */
public final class CompanionBondGate {
    private CompanionBondGate() {}

    public static boolean isUnlocked(ServerPlayer player, ItemStack fish) {
        ResourceLocation species = BuiltInRegistries.ITEM.getKey(fish.getItem());
        CompanionBondUnlocks.refresh(player);
        return FishCatchSavedData.getOrCreate(player.level().getServer())
                .getOrCreateQuestState(player)
                .isCompanionBondUnlocked(species);
    }
}
