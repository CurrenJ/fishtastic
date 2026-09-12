package grill24.fishtastic.server;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

/**
 * How much experience a finished minigame catch is worth.
 *
 * <p>Fishtastic rods never reach vanilla's {@code FishingHook#retrieve} loot branch (see
 * {@code FishingHookMixin}), so vanilla's flat "1-6 xp per catch" never fired for them and the
 * minigame awarded no xp at all. This restores xp on the mod's own terms: the payout tracks how
 * hard the fish was to hook rather than being flat.
 *
 * <p><b>Rarity measure.</b> A species' {@link FishProfile#baseWeight()} is its intrinsic slice of
 * the loot pool before any environment/bait/charm math ({@code FishtasticFishItem#getFishingLootWeight}),
 * so it is the one rarity number that doesn't move with where or how the player is fishing —
 * a legendary stays legendary-priced even when a biome boost made it likely this cast. Shipped
 * weights span 2 (rarest) to 100 (commonest) around {@link FishProfile#DEFAULT_BASE_WEIGHT}.
 */
public final class FishingXpAward {

    /** XP for a fish sitting exactly at {@link FishProfile#DEFAULT_BASE_WEIGHT}. */
    private static final double BASE_XP = 4.0;

    /**
     * Softens the rarity curve. The raw weight ratio spans 50x across the shipped pool (100 -> 2);
     * at 0.75 that compresses to a ~19x xp spread (1 xp for the commonest, 13 for the rarest),
     * which reads as a meaningful bonus without making common fish feel worthless.
     */
    private static final double RARITY_EXPONENT = 0.75;

    /** Ceiling so a datapack species with an absurdly low base_weight can't mint xp. */
    private static final int MAX_FISH_XP = 40;

    /** Non-fish, non-trash catches (treasure, junk-tier vanilla drops) get a flat token payout. */
    private static final int OTHER_CATCH_XP = 1;

    private FishingXpAward() {
    }

    /**
     * Experience for one reward stack, scaled by count so a stacked catch pays per fish.
     * Trash is worth nothing — the cleanup goal is already its own reward.
     */
    public static int forRewardStack(ItemStack stack, Registry<FishProfile> fishProfiles) {
        if (stack.isEmpty() || stack.is(FishtasticItemTags.TRASH)) {
            return 0;
        }
        return perItemXp(stack, fishProfiles) * stack.getCount();
    }

    private static int perItemXp(ItemStack stack, Registry<FishProfile> fishProfiles) {
        FishProfile profile = BuiltInRegistries.ITEM.getResourceKey(stack.getItem())
                .map(key -> ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, key.identifier()))
                .flatMap(fishProfiles::getOptional)
                .orElse(null);
        if (profile == null) {
            return OTHER_CATCH_XP;
        }

        // max(1, ...) guards a datapack profile that declares base_weight 0 or negative.
        double weight = Math.max(1, profile.baseWeight());
        double rarityScale = Math.pow(FishProfile.DEFAULT_BASE_WEIGHT / weight, RARITY_EXPONENT);
        double xp = BASE_XP * rarityScale * qualityMultiplier(stack);
        return Math.max(1, Math.min(MAX_FISH_XP, (int) Math.round(xp)));
    }

    /**
     * Quality is rolled per catch on top of species rarity, so it multiplies rather than replaces
     * the rarity term — a legendary common fish and a common rare fish stay distinguishable.
     */
    private static float qualityMultiplier(ItemStack stack) {
        FishQuality quality = stack.get(FishtasticDataComponents.FISH_QUALITY.value());
        if (quality == null) {
            return 1.0f;
        }
        return switch (quality.quality()) {
            case COMMON -> 1.0f;
            case UNCOMMON -> 1.15f;
            case RARE -> 1.35f;
            case EPIC -> 1.6f;
            case LEGENDARY -> 2.0f;
        };
    }
}
