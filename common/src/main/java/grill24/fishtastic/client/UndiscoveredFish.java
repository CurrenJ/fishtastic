package grill24.fishtastic.client;

import grill24.FishtasticRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;

/**
 * Client-side "is this fish a mystery to the local player?" check, shared by every place an item
 * is shown: GUI icons render as the encyclopedia's black silhouette ({@code ItemModelResolverMixin})
 * and the name reads "???" ({@code ItemStackMixin}) — creative tabs, JEI's list and recipe pages,
 * inventories, the hotbar name popup, anything that draws an item in a GUI.
 *
 * <p>A species is hidden until it is {@linkplain FishEncyclopediaClientCache#isDiscovered discovered}
 * (caught or spyglass-observed), matching the encyclopedia. Nothing is hidden before the first
 * encyclopedia sync of the session arrives, so a slow join never flashes every fish black.
 */
public final class UndiscoveredFish {
    /**
     * Set around a render that must show species as they are (the fishing minigame, whose
     * first-catch celebration stages its own silhouette and reveal).
     */
    public static final ThreadLocal<Boolean> SUPPRESSED = new ThreadLocal<>();

    private UndiscoveredFish() {}

    public static boolean isHidden(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemTags.FISHES)) return false;
        if (Boolean.TRUE.equals(SUPPRESSED.get())) return false;
        if (!FishEncyclopediaClientCache.hasSynced()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.isSameThread()) return false;
        Identifier id = stack.getItem().builtInRegistryHolder().key().identifier();
        // Only species the encyclopedia tracks: a fish-tagged item with no profile (another mod's
        // fish, say) can never be discovered, so it must never be hidden.
        Registry<?> profiles = mc.level.registryAccess()
                .lookup(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY).orElse(null);
        if (profiles == null || !profiles.containsKey(id)) return false;
        return !FishEncyclopediaClientCache.isDiscovered(id);
    }

    public static Component hiddenName() {
        return Component.translatable("screen.fishtastic.encyclopedia.locked_placeholder");
    }
}
