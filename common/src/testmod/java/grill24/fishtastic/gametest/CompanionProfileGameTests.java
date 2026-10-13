package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;

/**
 * The shipped data that puts ground benthics into the game (docs/fish-companions-benthic-follow.md
 * §21): which species walk in groups and which glide, as loaded from the real datapack.
 */
public final class CompanionProfileGameTests {
    private CompanionProfileGameTests() {}

    private static FishProfile profile(Registry<FishProfile> registry, String path) {
        return registry.getValue(grill24.fishtastic.util.Ids.of("fishtastic", path));
    }

    /**
     * Plaice ripples; starfish walk in a line and the two sea slugs in crowds when the party holds
     * several (docs/fish-companions-party.md §3: groups come from the party, and the profile's
     * deprecated {@code count} is no longer shipped).
     */
    public static void benthicCompanionDataLoads(GameTestHelper helper) {
        Registry<FishProfile> registry = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        FishProfile plaice = profile(registry, "plaice");
        helper.assertTrue(plaice != null && plaice.companionGait() == FishProfile.CompanionGait.RIPPLE,
                "plaice must ripple-glide");
        FishProfile starfish = profile(registry, "starfish");
        helper.assertTrue(starfish != null && starfish.companionGroup().isPresent()
                        && starfish.companionGroup().get().formation() == FishProfile.CompanionGroupConfig.Formation.LINE,
                "starfish must walk in a line");
        for (String slug : new String[] {"trapania_scurra", "willans_chromodoris"}) {
            FishProfile p = profile(registry, slug);
            helper.assertTrue(p != null && p.companionGroup().isPresent()
                            && p.companionGroup().get().formation() == FishProfile.CompanionGroupConfig.Formation.CROWD,
                    slug + " must walk as a crowd");
        }
        FishProfile spider = profile(registry, "japanese_spider_crab");
        helper.assertTrue(spider != null && spider.companionGait() == FishProfile.CompanionGait.CRAWL
                && spider.companionGroup().isEmpty(), "the spider crab must crawl alone");
        helper.succeed();
    }

    /**
     * A group or a ripple only means anything for a benthic (a floor- or upright-sitter); on any
     * other species it would be silently ignored, so none may declare one.
     */
    public static void onlyBenthicsDeclareGroundCompanionData(GameTestHelper helper) {
        Registry<FishProfile> registry = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        for (Identifier id : registry.keySet()) {
            FishProfile p = registry.getValue(id);
            boolean benthic = p.animation().map(a -> a instanceof FishAnimationConfig.FloorSit
                    || a instanceof FishAnimationConfig.UprightSit).orElse(false);
            if (benthic) continue;
            helper.assertTrue(p.companionGroup().isEmpty(), id + " is not benthic but declares companion_group");
            helper.assertTrue(p.companionGait() == FishProfile.CompanionGait.CRAWL,
                    id + " is not benthic but declares companion_gait");
        }
        helper.succeed();
    }
}
