package grill24.fishtastic.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import grill24.fishtastic.util.Ids;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The encyclopedia tier derivation (docs/fish-companions.md): a species' tier comes from its
 * best share of base weight across the location pools it can be caught in, unlisted fish stay out
 * of the totals, and no species ever needs more than 50 catches of itself to complete its entry.
 */
class EncyclopediaTiersTest {

    private static final Path RESOURCES = Path.of("src/main/resources/data/fishtastic");
    /** Expected casts in a species' best pool to complete its entry; a listed fish past this is an outlier. */
    private static final float MAX_EXPECTED_CASTS = 500f;

    private static EncyclopediaTiers.Species species(String path, int weight, boolean unlisted, FishProfile.Zone... zones) {
        return new EncyclopediaTiers.Species(Ids.of("fishtastic", path), weight, List.of(zones), unlisted);
    }

    private static ResourceLocation id(String path) {
        return Ids.of("fishtastic", path);
    }

    @Test
    void tierBoundariesAreInclusiveLowerBounds() {
        assertEquals(EncyclopediaTiers.Tier.VERY_RARE, EncyclopediaTiers.Tier.forShare(0f));
        assertEquals(EncyclopediaTiers.Tier.VERY_RARE, EncyclopediaTiers.Tier.forShare(0.0149f));
        assertEquals(EncyclopediaTiers.Tier.RARE, EncyclopediaTiers.Tier.forShare(0.015f));
        assertEquals(EncyclopediaTiers.Tier.UNCOMMON, EncyclopediaTiers.Tier.forShare(0.04f));
        assertEquals(EncyclopediaTiers.Tier.COMMON, EncyclopediaTiers.Tier.forShare(0.12f));
        assertEquals(EncyclopediaTiers.Tier.ABUNDANT, EncyclopediaTiers.Tier.forShare(0.3f));
        assertEquals(EncyclopediaTiers.Tier.ABUNDANT, EncyclopediaTiers.Tier.forShare(1f));
    }

    @Test
    void tiersGetSlowerAsTheyGetCommonerAndNeverPassFifty() {
        EncyclopediaTiers.Tier[] tiers = EncyclopediaTiers.Tier.values();
        for (int i = 0; i < tiers.length; i++) {
            FishEncyclopediaEntry.UnlockThresholds t = tiers[i].thresholds;
            assertTrue(t.loreCatches() <= 50, tiers[i] + " lore must be <= 50");
            assertTrue(t.loreCatches() >= Math.max(Math.max(t.nameRevealCatches(), t.statsCatches()),
                    Math.max(t.typesCatches(), t.spawnConditionsCatches())), tiers[i] + " lore must be the last section to unlock");
            if (i > 0) {
                assertTrue(tiers[i].minShare > tiers[i - 1].minShare, "tiers must be ordered by share");
                assertTrue(t.loreCatches() > tiers[i - 1].thresholds.loreCatches(), tiers[i] + " must ask for more catches than " + tiers[i - 1]);
            }
        }
    }

    @Test
    void shareIsMeasuredAgainstTheCombinedLocationPool() {
        // A deep ocean draws from OCEAN and DEEP_OCEAN together, so a deep-ocean fish is measured
        // against both; its best pool is the combined one because it can't be caught in plain OCEAN.
        Map<ResourceLocation, EncyclopediaTiers.Rarity> r = EncyclopediaTiers.derive(List.of(
                species("ocean_fish", 90, false, FishProfile.Zone.OCEAN),
                species("deep_fish", 10, false, FishProfile.Zone.DEEP_OCEAN)));
        assertEquals(0.10f, r.get(id("deep_fish")).share(), 1e-6);
        assertEquals(EnumSet.of(FishProfile.Zone.OCEAN, FishProfile.Zone.DEEP_OCEAN), r.get(id("deep_fish")).pool());
        assertEquals(1.0f, r.get(id("ocean_fish")).share(), 1e-6, "ocean fish is alone in plain ocean");
    }

    @Test
    void unlistedFishStayOutOfOtherSpeciesTotals() {
        Map<ResourceLocation, EncyclopediaTiers.Rarity> r = EncyclopediaTiers.derive(List.of(
                species("listed", 50, false, FishProfile.Zone.NETHER),
                species("other", 50, false, FishProfile.Zone.NETHER),
                species("secret", 100, true, FishProfile.Zone.NETHER)));
        assertEquals(0.5f, r.get(id("listed")).share(), 1e-6, "an unlisted fish must not dilute listed shares");
        assertEquals(0.5f, r.get(id("secret")).share(), 1e-6, "an unlisted fish is measured against the listed pool plus itself");
    }

    @Test
    void shippedFishProfilesDeriveSensibleTiers() throws IOException {
        Set<String> unlisted = new HashSet<>();
        JsonObject tag = JsonParser.parseString(Files.readString(RESOURCES.resolve("tags/items/unlisted_fish.json"))).getAsJsonObject();
        for (JsonElement e : tag.getAsJsonArray("values")) unlisted.add(e.getAsString().substring("fishtastic:".length()));

        List<EncyclopediaTiers.Species> all = new ArrayList<>();
        try (Stream<Path> files = Files.list(RESOURCES.resolve("fishtastic/fish_profile"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonObject profile = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                int weight = profile.has("base_weight") ? profile.get("base_weight").getAsInt() : FishProfile.DEFAULT_BASE_WEIGHT;
                List<FishProfile.Zone> zones = new ArrayList<>();
                for (JsonElement z : profile.getAsJsonArray("zones")) zones.add(FishProfile.Zone.valueOf(z.getAsString().toUpperCase(Locale.ROOT)));
                all.add(new EncyclopediaTiers.Species(id(name), weight, zones, unlisted.contains(name)));
            }
        }
        assertTrue(all.size() >= 60, "expected the shipped roster, found " + all.size());

        Map<ResourceLocation, EncyclopediaTiers.Rarity> rarities = EncyclopediaTiers.derive(all);
        for (EncyclopediaTiers.Species s : all) {
            EncyclopediaTiers.Rarity rarity = rarities.get(s.id());
            assertNotNull(rarity.pool(), s.id() + " must be catchable in some location pool");
            int lore = rarity.tier().thresholds.loreCatches();
            assertTrue(lore <= 50, s.id() + " must complete within 50 catches of itself");
            if (!s.unlisted()) {
                float casts = lore / rarity.share();
                assertTrue(casts <= MAX_EXPECTED_CASTS, s.id() + " needs ~" + Math.round(casts)
                        + " casts in its best pool to complete; adjust its weight or the tier table");
            }
        }

        // blazed_grub is 4 of the Nether's listed weight — rare there, not the common preset it used to carry.
        assertEquals(EncyclopediaTiers.Tier.RARE, rarities.get(id("blazed_grub")).tier());
        assertEquals(EncyclopediaTiers.Tier.ABUNDANT, rarities.get(id("sulphur_molly")).tier());
        assertEquals(EncyclopediaTiers.Tier.VERY_RARE, rarities.get(id("acute_iaspis")).tier());
    }
}
