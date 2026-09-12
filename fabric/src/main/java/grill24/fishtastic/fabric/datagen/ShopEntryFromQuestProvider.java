package grill24.fishtastic.fabric.datagen;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import grill24.FishtasticRegistries;
import grill24.fishtastic.Fishtastic;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestReward;
import grill24.fishtastic.data.ShopEntry;
import grill24.fishtastic.fishtank.FishTankShape;
import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

/**
 * Generates {@link ShopEntry} datapack entries from quest reward data, so a quest's fish tank
 * reward and its "buy another one" shop listing can never drift apart the way hand-authored pairs
 * repeatedly did (a shop entry selling one quest's materials while a second or third unlock-path
 * quest granted different ones — see the 2026-09-12 fix that prompted this provider). Every
 * non-daily quest reward item that is a {@code fishtastic:fish_tank} becomes its own shop entry,
 * gated on that one quest, selling the exact stack (shape and materials included) the quest
 * grants. Daily quests are skipped entirely — {@code unlockGatesNeverPointAtDailyQuests} forbids
 * gating anything behind a quest whose {@code claimed} flag resets every rotation.
 *
 * <p>Explorer/Mastery/Challenge/Tutorial/Collector quests have no Java-side bootstrap data (unlike
 * the Daily family — see {@link QuestProvider}), so this provider reads their hand-authored JSON
 * straight off disk, at the path the {@code fishtastic.quest-source-dir} system property names
 * (set by the {@code datagen} run config in {@code fabric/build.gradle}).
 */
public class ShopEntryFromQuestProvider implements DataProvider {
    private static final int COST = 100;
    private static final float WEIGHT = 0.5f;
    /** Shape entries stay freely re-buyable (matches the old hand-authored shape shop entries). */
    private static final int SHAPE_DAILY_MAX_PURCHASES = 0;
    /** Plain-material capstone entries are a one-a-day premium re-buy (matches the old ones). */
    private static final int MATERIAL_DAILY_MAX_PURCHASES = 1;

    private final PackOutput.PathProvider pathProvider;

    public ShopEntryFromQuestProvider(FabricPackOutput output) {
        this.pathProvider = output.createRegistryElementsPathProvider(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY);
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        String sourceDirProperty = System.getProperty("fishtastic.quest-source-dir");
        if (sourceDirProperty == null) {
            throw new IllegalStateException(
                    "ShopEntryFromQuestProvider needs -Dfishtastic.quest-source-dir, set by the "
                            + "'datagen' run config in fabric/build.gradle — run via :fabric:runDatagen.");
        }
        Path questDir = Path.of(sourceDirProperty);

        List<CompletableFuture<?>> futures = new ArrayList<>();
        try (Stream<Path> files = Files.walk(questDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String questPath = toQuestPath(questDir, file);
                Quest quest = readQuest(file, questPath);
                if (quest.category() == QuestCategory.DAILY) continue;

                ResourceKey<Quest> questKey = ResourceKey.create(FishtasticRegistries.QUEST_REGISTRY_KEY, Fishtastic.id(questPath));
                List<QuestReward.RewardItem> tankRewards = quest.reward().items().stream()
                        .filter(ShopEntryFromQuestProvider::isFishTank)
                        .toList();

                for (int i = 0; i < tankRewards.size(); i++) {
                    QuestReward.RewardItem rewardItem = tankRewards.get(i);
                    FishTankShape shape = explicitShape(rewardItem);
                    String suffix = shape != null
                            ? "_" + shape.getSerializedName()
                            : (tankRewards.size() > 1 ? "_" + (i + 1) : "");
                    String entryId = questPath + suffix;
                    ShopEntry entry = buildEntry(quest, questKey, rewardItem, shape);
                    futures.add(generate(cache, entryId, entry));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
    }

    private static String toQuestPath(Path questDir, Path file) {
        String relative = questDir.relativize(file).toString().replace('\\', '/');
        return relative.substring(0, relative.length() - ".json".length());
    }

    private static Quest readQuest(Path file, String questPath) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            return Quest.CODEC.parse(JsonOps.INSTANCE, json)
                    .result()
                    .orElseThrow(() -> new IllegalStateException("Failed to decode quest '" + questPath + "'"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean isFishTank(QuestReward.RewardItem rewardItem) {
        Identifier id = BuiltInRegistries.ITEM.getKey(rewardItem.item());
        return id.equals(Fishtastic.id("fish_tank"));
    }

    /**
     * The shape a reward item's component patch <em>explicitly</em> carries, or null if the patch
     * doesn't mention {@code fishtastic:fish_tank_shape} at all — mirrors
     * {@code FishTankShapeUnlocks.explicitShape}.
     */
    private static FishTankShape explicitShape(QuestReward.RewardItem rewardItem) {
        DataComponentType<FishTankShape> shapeType = FishtasticDataComponents.FISH_TANK_SHAPE.value();
        for (var patchEntry : rewardItem.components().entrySet()) {
            if (patchEntry.getKey() == shapeType && patchEntry.getValue().isPresent()) {
                return (FishTankShape) patchEntry.getValue().get();
            }
        }
        return null;
    }

    private static ShopEntry buildEntry(Quest quest, ResourceKey<Quest> questKey, QuestReward.RewardItem rewardItem, FishTankShape shape) {
        boolean isTankShape = shape != null;
        String displayName = isTankShape
                ? capitalize(shape.getSerializedName()) + " Fish Tank"
                : quest.displayName() + " Fish Tank";
        String description = "Earned by completing \"" + quest.displayName() + "\".";

        ShopEntry.ShopReward reward = new ShopEntry.ShopReward(
                Fishtastic.id("fish_tank"), rewardItem.count(), rewardItem.components());

        return new ShopEntry(
                displayName,
                description,
                COST,
                WEIGHT,
                List.of(reward),
                isTankShape ? SHAPE_DAILY_MAX_PURCHASES : MATERIAL_DAILY_MAX_PURCHASES,
                false,
                isTankShape,
                List.of(questKey)
        );
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private CompletableFuture<?> generate(CachedOutput cache, String name, ShopEntry entry) {
        JsonElement json = ShopEntry.CODEC.encodeStart(JsonOps.INSTANCE, entry)
                .result()
                .orElseThrow(() -> new IllegalStateException("Failed to encode generated shop entry '" + name + "'"));
        return DataProvider.saveStable(cache, json, pathProvider.json(Fishtastic.id(name)));
    }

    @Override
    public String getName() {
        return "Fishtastic Shop Entries (from Quest rewards)";
    }
}
