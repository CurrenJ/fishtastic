package grill24.fishtastic.fabric.datagen;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.Quest;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Data generator for {@link Quest} datapack entries. Currently covers the Daily category: each
 * family is authored once in Java as a bronze/silver/gold trio via {@link DailyQuestFamily}, so
 * the harder variants stay easy to regenerate and retune alongside the base quest. Explorer,
 * Mastery, Challenge and Tutorial quests remain hand-written JSON for now.
 */
public class QuestProvider implements DataProvider {
    private static final TagKey<Item> TREASURE = TagKey.create(Registries.ITEM, Fishtastic.id("treasure"));

    private static Block glass(DyeColor color) {
        return FishtasticBlocks.CLEAR_STAINED_GLASS.get(color).value();
    }

    private final PackOutput.PathProvider pathProvider;

    public QuestProvider(FabricPackOutput output) {
        this.pathProvider = output.createRegistryElementsPathProvider(FishtasticRegistries.QUEST_REGISTRY_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (DailyQuestFamily family : dailyFamilies()) {
            for (Map.Entry<String, Quest> entry : family.build().entrySet()) {
                futures.add(generate(cache, "daily/" + entry.getKey(), entry.getValue()));
            }
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private static List<DailyQuestFamily> dailyFamilies() {
        return List.of(
                DailyQuestFamily.of("night_bite")
                        .displayName("Night Bite")
                        .description("Catch %d fish after dark.")
                        .objective(o -> o.time(FishProfile.TimeOfDay.NIGHT))
                        .bronze(3, 5)
                        .silver(5, 9)
                        .gold(8, 16, Blocks.DEEPSLATE_BRICKS, Blocks.SOUL_SAND, glass(DyeColor.BLACK)),

                DailyQuestFamily.of("treasure_haul")
                        .displayName("Treasure Haul")
                        .description("Catch %d treasure items.")
                        .objective(o -> o.speciesTag(TREASURE))
                        .bronze(3, 7)
                        .silver(5, 12)
                        .gold(7, 21, Blocks.COPPER_BLOCK, Blocks.RED_SAND, glass(DyeColor.YELLOW)),

                DailyQuestFamily.of("storm_chaser")
                        .displayName("Storm Chaser")
                        .description(count -> count == 1 ? "Catch a fish during a thunderstorm." : "Catch %d fish during a thunderstorm.".formatted(count))
                        .objective(o -> o.weather(FishProfile.WeatherCondition.THUNDER))
                        .bronze(1, 5)
                        .silver(2, 9)
                        .gold(3, 16, Blocks.TUFF_BRICKS, Blocks.GRAVEL, glass(DyeColor.GRAY)),

                DailyQuestFamily.of("freshwater_morning")
                        .displayName("Morning Cast")
                        .description("Catch %d freshwater fish at dawn.")
                        .objective(o -> o.speciesTag(FishtasticItemTags.FISH).biome(BiomeTags.IS_RIVER).time(FishProfile.TimeOfDay.DAWN))
                        .bronze(4, 6)
                        .silver(6, 11)
                        .gold(9, 19, Blocks.BIRCH_PLANKS, Blocks.SAND, glass(DyeColor.PINK)),

                DailyQuestFamily.of("tidal_catch")
                        .displayName("Tidal Catch")
                        .description("Catch %d fish from ocean waters.")
                        .objective(o -> o.speciesTag(FishtasticItemTags.FISH).biome(BiomeTags.IS_OCEAN))
                        .bronze(5, 5)
                        .silver(8, 9)
                        .gold(12, 16, Blocks.TUBE_CORAL_BLOCK, Blocks.SAND, glass(DyeColor.BLUE)),

                DailyQuestFamily.of("bright_catch")
                        .displayName("Bright Catch")
                        .description("Catch %d yellow-hued fish.")
                        .objective(o -> o.speciesTag(FishtasticItemTags.COLOR_YELLOW))
                        .bronze(3, 6)
                        .silver(5, 11)
                        .gold(8, 19, Blocks.OCHRE_FROGLIGHT, Blocks.SAND, glass(DyeColor.ORANGE)),

                DailyQuestFamily.of("clean_sweep")
                        .displayName("Clean Sweep")
                        .description(count -> "Fish %d piece%s of junk out of the water.".formatted(count, count == 1 ? "" : "s"))
                        .objective(o -> o.speciesTag(FishtasticItemTags.TRASH))
                        .bronze(2, 6)
                        .silver(4, 11)
                        .gold(6, 19, Blocks.MUD_BRICKS, Blocks.CLAY, glass(DyeColor.BROWN)),

                DailyQuestFamily.of("golden_hour")
                        .displayName("Golden Hour")
                        .description("Catch %d fish at dusk.")
                        .objective(o -> o.time(FishProfile.TimeOfDay.DUSK))
                        .bronze(3, 4)
                        .silver(5, 7)
                        .gold(8, 13, Blocks.PURPUR_BLOCK, Blocks.SAND, glass(DyeColor.MAGENTA)),

                DailyQuestFamily.of("heavy_hitter")
                        .displayName("Heavy Hitter")
                        .description("Catch %d large fish.")
                        .objective(o -> o.speciesTag(FishtasticItemTags.BIG_FISH))
                        .bronze(2, 7)
                        .silver(3, 12)
                        .gold(5, 21, Blocks.IRON_BLOCK, Blocks.GRAVEL, glass(DyeColor.RED)),

                DailyQuestFamily.of("quality_control")
                        .displayName("Quality Control")
                        .description((count, quality) -> "Catch %d fish at %s quality or better."
                                .formatted(count, quality.map(FishQuality.Quality::getDisplayName).orElse("")))
                        .bronze(2, FishQuality.Quality.UNCOMMON, 5)
                        .silver(2, FishQuality.Quality.RARE, 9)
                        .gold(3, FishQuality.Quality.EPIC, 16, Blocks.QUARTZ_BLOCK, Blocks.SAND, glass(DyeColor.WHITE)),

                DailyQuestFamily.of("small_fry")
                        .displayName("Small Fry")
                        .description("Catch %d small fish.")
                        .objective(o -> o.speciesTag(FishtasticItemTags.SMALL_FISH))
                        .bronze(6, 5)
                        .silver(9, 9)
                        .gold(14, 16, Blocks.POLISHED_ANDESITE, Blocks.SAND, glass(DyeColor.LIGHT_GRAY)),

                DailyQuestFamily.of("cave_dweller")
                        .displayName("Cave Dweller")
                        .description("Find %d fish in cave waters.")
                        .objective(o -> o.speciesTag(FishtasticItemTags.ZONE_CAVE).zone(FishProfile.Zone.CAVE))
                        .bronze(5, 6)
                        .silver(8, 11)
                        .gold(12, 19, Blocks.MOSS_BLOCK, Blocks.GRAVEL, glass(DyeColor.GREEN)),

                DailyQuestFamily.of("deep_cuts")
                        .displayName("Deep Cuts")
                        .description("Find %d fish in the deep ocean.")
                        .objective(o -> o.speciesTag(FishtasticItemTags.ZONE_DEEP_OCEAN).zone(FishProfile.Zone.DEEP_OCEAN))
                        .bronze(5, 6)
                        .silver(8, 11)
                        .gold(12, 19, Blocks.SEA_LANTERN, Blocks.GRAVEL, glass(DyeColor.LIGHT_BLUE))
        );
    }

    private CompletableFuture<?> generate(CachedOutput cache, String name, Quest quest) {
        JsonElement json = Quest.CODEC.encodeStart(JsonOps.INSTANCE, quest)
                .result()
                .orElseThrow(() -> new IllegalStateException("Failed to encode quest '" + name + "'"));
        return DataProvider.saveStable(cache, json, pathProvider.json(Fishtastic.id(name)));
    }

    @Override
    public String getName() {
        return "Fishtastic Quests";
    }
}
