package grill24.fishtastic.fabric.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.data.FishProfile;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Fabric implementation of the item tag data provider.
 * Generates item tags for Fishtastic items.
 */
public class FishtasticItemTagProvider extends FabricTagsProvider.ItemTagsProvider {
    private static final String FISH_PROFILE_RESOURCE_DIR = "data/fishtastic/fishtastic/fish_profile";

    public FishtasticItemTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Fishing rods tag
        valueLookupBuilder(FishtasticItemTags.FISHING_RODS)
                .add(FishtasticItems.COPPER_FISHING_ROD.value())
                .add(FishtasticItems.OBSIDIAN_FISHING_ROD.value());

        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, provider);
        Map<Identifier, FishProfile> fishProfiles = loadFishProfiles(ops);

        // Fish tag: every fish_profile entry is, by definition, a fish, so this is derived
        // rather than hand-listed - a hardcoded list previously drifted out of sync (missing
        // 12 fish) as new fish were added without updating it. See addZoneTags for the same
        // derive-don't-hardcode rationale.
        TagAppender<Item, Item> fishTagBuilder = valueLookupBuilder(FishtasticItemTags.FISH);
        for (Identifier id : fishProfiles.keySet()) {
            fishTagBuilder.add(itemForFishProfile(id));
        }

        // Fishing bait
        valueLookupBuilder(FishtasticItemTags.FISHING_BAIT)
                .add(FishtasticItems.WORMS.value())
                .add(FishtasticItems.GUMMY_WORMS.value())
                .add(FishtasticItems.BLAZED_GRUB.value())
                .add(FishtasticItems.SMALL_FISH_BAIT.value())
                .add(FishtasticItems.CALM_BAIT.value())
                .add(FishtasticItems.FRENZY_BAIT.value())
                .add(FishtasticItems.TROPHY_BAIT.value());

        valueLookupBuilder(ItemTags.FISHING_ENCHANTABLE)
                .addTag(FishtasticItemTags.FISHING_RODS);

        valueLookupBuilder(ItemTags.FISHES)
                .addTag(FishtasticItemTags.FISH);

        valueLookupBuilder(ItemTags.DURABILITY_ENCHANTABLE)
                .addTag(FishtasticItemTags.FISHING_RODS);

        // Exotic fish: eligible when using blazed grub bait
        valueLookupBuilder(FishtasticItemTags.EXOTIC_FISH)
                .add(FishtasticItems.GIANT_MANTA_RAY.value())
                .add(FishtasticItems.LONGNOSE_GAR.value())
                .add(FishtasticItems.NORTHERN_PIKE.value())
                .add(FishtasticItems.OCEAN_SUNFISH.value())
                .add(FishtasticItems.PORTUGUESE_MAN_O_WAR.value())
                .add(FishtasticItems.GLASS_SQUID.value())
                .add(FishtasticItems.LEAFY_SEA_DRAGON.value())
                .add(FishtasticItems.FLAPJACK_OCTOPUS.value())
                .add(FishtasticItems.GOLDEN_TROUT.value())
                .add(FishtasticItems.CLOWN_LOACH.value())
                .add(FishtasticItems.ELECTRIC_EEL.value())
                .add(FishtasticItems.ORNATE_BICHIR.value())
                .add(FishtasticItems.AMERICAN_PADDLEFISH.value())
                .add(FishtasticItems.LARGETOOTH_SAWFISH.value());

        // Unlisted fish: secret one-off variants, no encyclopedia silhouette until first catch
        valueLookupBuilder(FishtasticItemTags.UNLISTED_FISH)
                .add(FishtasticItems.MOLTEN_MOORISH_IDOL.value())
                .add(FishtasticItems.FROZEN_GIANT_MANTA_RAY.value())
                .add(FishtasticItems.ROYAL_GARDEN_EEL.value())
                .add(FishtasticItems.FRIED_SHRIMP.value())
                .add(FishtasticItems.ACUTE_IASPIS.value());

        // Small fish: bait-affinity/encyclopedia cluster for small or delicate species.
        // Curated membership (not derivable from fish_profile), so listed here rather than
        // hand-edited JSON - same rationale as EXOTIC_FISH/UNLISTED_FISH above.
        valueLookupBuilder(FishtasticItemTags.SMALL_FISH)
                .add(FishtasticItems.TRAPANIA_SCURRA.value())
                .add(FishtasticItems.YELLOWLINE_GOBY.value())
                .add(FishtasticItems.FRIED_SHRIMP.value())
                .add(FishtasticItems.GREENSTRIPE_BARB.value())
                .add(FishtasticItems.RED_BELLIED_PIRAHNA.value())
                .add(FishtasticItems.SHRIMP.value())
                .add(FishtasticItems.WILLANS_CHROMODORIS.value())
                .add(FishtasticItems.FLAPJACK_OCTOPUS.value())
                .add(FishtasticItems.BLAZED_GRUB.value())
                .add(FishtasticItems.BLUEGILL.value())
                .add(FishtasticItems.NEON_TETRA.value())
                .add(FishtasticItems.BETTA.value())
                .add(FishtasticItems.GARDEN_EEL.value())
                .add(FishtasticItems.BLIND_CAVEFISH.value())
                .add(FishtasticItems.RAINFORDIA.value())
                .add(FishtasticItems.DEVILS_HOLE_PUPFISH.value())
                .add(FishtasticItems.SULPHUR_MOLLY.value())
                .add(FishtasticItems.WATERFALL_CLIMBING_CAVE_FISH.value())
                .add(FishtasticItems.BLIND_CAVE_TETRA.value())
                .add(FishtasticItems.GLASS_CATFISH.value())
                .add(FishtasticItems.NEON_GOBY.value())
                .add(FishtasticItems.BRIDLE_SHINER.value())
                .add(FishtasticItems.INDIAN_GLASSY_FISH.value())
                .add(FishtasticItems.CLOWN_LOACH.value())
                .add(FishtasticItems.FIRE_DARTFISH.value())
                .add(FishtasticItems.ORANGE_AUSTRALE_KILLIFISH.value());

        // Calm fish: bait-affinity/encyclopedia cluster for docile, easy catches.
        // Curated membership (not derivable from fish_profile), same rationale as above.
        valueLookupBuilder(FishtasticItemTags.CALM_FISH)
                .add(FishtasticItems.BLUEGILL.value())
                .add(FishtasticItems.FRIED_SHRIMP.value())
                .add(FishtasticItems.SHRIMP.value())
                .add(FishtasticItems.STARFISH.value())
                .add(FishtasticItems.TRAPANIA_SCURRA.value())
                .add(FishtasticItems.WILLANS_CHROMODORIS.value())
                .add(FishtasticItems.FLAPJACK_OCTOPUS.value())
                .add(FishtasticItems.GIANT_MANTA_RAY.value())
                .add(FishtasticItems.GLASS_SQUID.value())
                .add(FishtasticItems.OCEAN_SUNFISH.value())
                .add(FishtasticItems.GARDEN_EEL.value())
                .add(FishtasticItems.DISCUS.value())
                .add(FishtasticItems.GLASS_CATFISH.value())
                .add(FishtasticItems.NEON_GOBY.value())
                .add(FishtasticItems.BRIDLE_SHINER.value())
                .add(FishtasticItems.AMERICAN_PADDLEFISH.value())
                .add(FishtasticItems.ORANGE_AUSTRALE_KILLIFISH.value());

        // Big fish: bait-affinity/encyclopedia cluster for trophy-scale catches.
        // Curated membership (not derivable from fish_profile), same rationale as above.
        valueLookupBuilder(FishtasticItemTags.BIG_FISH)
                .add(FishtasticItems.GLASS_SQUID.value())
                .add(FishtasticItems.PORTUGUESE_MAN_O_WAR.value())
                .add(FishtasticItems.ROYAL_GARDEN_EEL.value())
                .add(FishtasticItems.STARFISH.value())
                .add(FishtasticItems.ACUTE_IASPIS.value())
                .add(FishtasticItems.LEAFY_SEA_DRAGON.value())
                .add(FishtasticItems.LIZARDFISH.value())
                .add(FishtasticItems.PARROTFISH.value())
                .add(FishtasticItems.RAINFORDIA.value())
                .add(FishtasticItems.MOLTEN_MOORISH_IDOL.value())
                .add(FishtasticItems.MOORISH_IDOL.value())
                .add(FishtasticItems.LONGNOSE_GAR.value())
                .add(FishtasticItems.NORTHERN_PIKE.value())
                .add(FishtasticItems.GIANT_MANTA_RAY.value())
                .add(FishtasticItems.FROZEN_GIANT_MANTA_RAY.value())
                .add(FishtasticItems.JAPANESE_SPIDER_CRAB.value())
                .add(FishtasticItems.OCEAN_SUNFISH.value())
                .add(FishtasticItems.ARCTIC_CHAR.value())
                .add(FishtasticItems.GIRAFFE_CICHLID.value())
                .add(FishtasticItems.COMMON_OCTOPUS.value())
                .add(FishtasticItems.ELECTRIC_EEL.value())
                .add(FishtasticItems.GOLDEN_MAHSEER.value())
                .add(FishtasticItems.ORNATE_BICHIR.value())
                .add(FishtasticItems.AMERICAN_PADDLEFISH.value())
                .add(FishtasticItems.LARGETOOTH_SAWFISH.value());

        // Steady fish: bait-affinity/encyclopedia cluster for mid-difficulty reef/river fish.
        // Curated membership (not derivable from fish_profile), same rationale as above.
        valueLookupBuilder(FishtasticItemTags.STEADY_FISH)
                .add(FishtasticItems.JAPANESE_SPIDER_CRAB.value())
                .add(FishtasticItems.LEAFY_SEA_DRAGON.value())
                .add(FishtasticItems.LIZARDFISH.value())
                .add(FishtasticItems.PARROTFISH.value())
                .add(FishtasticItems.RAINFORDIA.value())
                .add(FishtasticItems.ROYAL_GARDEN_EEL.value())
                .add(FishtasticItems.GREENSTRIPE_BARB.value())
                .add(FishtasticItems.MOORISH_IDOL.value())
                .add(FishtasticItems.ARCTIC_CHAR.value())
                .add(FishtasticItems.COMMON_OCTOPUS.value());

        // Frenzy fish: bait-affinity/encyclopedia cluster for aggressive/high-energy species.
        // Curated membership (not derivable from fish_profile), same rationale as above.
        valueLookupBuilder(FishtasticItemTags.FRENZY_FISH)
                .add(FishtasticItems.FROZEN_GIANT_MANTA_RAY.value())
                .add(FishtasticItems.ACUTE_IASPIS.value())
                .add(FishtasticItems.LONGNOSE_GAR.value())
                .add(FishtasticItems.NORTHERN_PIKE.value())
                .add(FishtasticItems.PORTUGUESE_MAN_O_WAR.value())
                .add(FishtasticItems.RED_BELLIED_PIRAHNA.value())
                .add(FishtasticItems.NEON_TETRA.value())
                .add(FishtasticItems.YELLOWLINE_GOBY.value())
                .add(FishtasticItems.BETTA.value())
                .add(FishtasticItems.BLAZED_GRUB.value())
                .add(FishtasticItems.MOLTEN_MOORISH_IDOL.value())
                .add(FishtasticItems.DEVILS_HOLE_PUPFISH.value())
                .add(FishtasticItems.INDIAN_GLASSY_FISH.value())
                .add(FishtasticItems.LARGETOOTH_SAWFISH.value());

        // Taxonomic groups: real-world clades backing the "catch every species in this family"
        // explorer quests. Not derivable from fish_profile (which knows nothing about taxonomy),
        // so curated here - same rationale as EXOTIC_FISH/SMALL_FISH above.
        valueLookupBuilder(FishtasticItemTags.CEPHALOPODS)
                .add(FishtasticItems.COMMON_OCTOPUS.value())
                .add(FishtasticItems.FLAPJACK_OCTOPUS.value())
                .add(FishtasticItems.GLASS_SQUID.value());

        valueLookupBuilder(FishtasticItemTags.SALMONIDS)
                .add(FishtasticItems.ARCTIC_CHAR.value())
                .add(FishtasticItems.BULL_TROUT.value())
                .add(FishtasticItems.GOLDEN_TROUT.value())
                .add(FishtasticItems.RAINBOW_TROUT.value())
                .add(FishtasticItems.EUROPEAN_GRAYLING.value());

        // Ancient non-teleost ray-finned lineages that have changed little since the Mesozoic.
        valueLookupBuilder(FishtasticItemTags.LIVING_FOSSILS)
                .add(FishtasticItems.AMERICAN_PADDLEFISH.value())
                .add(FishtasticItems.LONGNOSE_GAR.value())
                .add(FishtasticItems.ORNATE_BICHIR.value());

        // Cypriniformes: carps, barbs, minnows and loaches.
        valueLookupBuilder(FishtasticItemTags.CYPRINIFORMES)
                .add(FishtasticItems.GOLDEN_MAHSEER.value())
                .add(FishtasticItems.GREENSTRIPE_BARB.value())
                .add(FishtasticItems.BRIDLE_SHINER.value())
                .add(FishtasticItems.CLOWN_LOACH.value())
                .add(FishtasticItems.WATERFALL_CLIMBING_CAVE_FISH.value());

        addZoneTags(fishProfiles);
    }

    /**
     * Zone tags (fishtastic:zone_ocean, etc.) are derived from each fish's real fish_profile
     * JSON rather than hand-listed, so they can't drift out of sync as fish are added, removed,
     * or reassigned to a different zone.
     */
    private void addZoneTags(Map<Identifier, FishProfile> fishProfiles) {
        Map<FishProfile.Zone, TagAppender<Item, Item>> zoneBuilders = new EnumMap<>(FishProfile.Zone.class);
        for (FishProfile.Zone zone : FishProfile.Zone.values()) {
            zoneBuilders.put(zone, valueLookupBuilder(zoneTag(zone)));
        }

        for (Map.Entry<Identifier, FishProfile> entry : fishProfiles.entrySet()) {
            Item item = itemForFishProfile(entry.getKey());
            for (FishProfile.Zone zone : entry.getValue().zones()) {
                zoneBuilders.get(zone).add(item);
            }
        }
    }

    private static Item itemForFishProfile(Identifier id) {
        return BuiltInRegistries.ITEM.getOptional(id)
                .orElseThrow(() -> new IllegalStateException(
                        "fish_profile/" + id.getPath() + ".json has no matching item"));
    }

    private static TagKey<Item> zoneTag(FishProfile.Zone zone) {
        return switch (zone) {
            case OCEAN -> FishtasticItemTags.ZONE_OCEAN;
            case DEEP_OCEAN -> FishtasticItemTags.ZONE_DEEP_OCEAN;
            case RIVER -> FishtasticItemTags.ZONE_RIVER;
            case NETHER -> FishtasticItemTags.ZONE_NETHER;
            case CAVE -> FishtasticItemTags.ZONE_CAVE;
            case HIGH_ALTITUDE -> FishtasticItemTags.ZONE_HIGH_ALTITUDE;
        };
    }

    /** Reads every fish_profile/*.json off the classpath and decodes it with the real {@link FishProfile#CODEC}. */
    private static Map<Identifier, FishProfile> loadFishProfiles(RegistryOps<JsonElement> ops) {
        URL dirUrl = FishtasticItemTagProvider.class.getClassLoader().getResource(FISH_PROFILE_RESOURCE_DIR);
        if (dirUrl == null) {
            throw new IllegalStateException("Could not locate " + FISH_PROFILE_RESOURCE_DIR + " on the classpath");
        }

        Path dir;
        try {
            dir = Path.of(dirUrl.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Malformed fish_profile classpath URL: " + dirUrl, e);
        }

        Map<Identifier, FishProfile> profiles = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            // Sorted for deterministic tag output - Files.list() order isn't guaranteed.
            List<Path> sortedFiles = files.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path file : sortedFiles) {
                String fileName = file.getFileName().toString();
                Identifier id = Fishtastic.id(fileName.substring(0, fileName.length() - ".json".length()));
                JsonElement element = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                FishProfile profile = FishProfile.CODEC.parse(ops, element)
                        .getOrThrow(msg -> new IllegalStateException("Failed to parse fish_profile/" + fileName + ": " + msg));
                profiles.put(id, profile);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return profiles;
    }
}
