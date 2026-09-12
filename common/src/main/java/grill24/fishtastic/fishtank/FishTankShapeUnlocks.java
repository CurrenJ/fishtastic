package grill24.fishtastic.fishtank;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestCategory;
import grill24.fishtastic.data.QuestReward;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Derives which quests unlock each {@link FishTankShape}, straight from quest reward data: a quest
 * "unlocks" whatever shape(s) its reward items explicitly carry a {@code fishtastic:fish_tank_shape}
 * component for. This is the single source of truth for shape unlocking — there is no separate
 * hand-authored list anywhere, so adding a new unlock path only ever means editing that quest's
 * reward JSON.
 *
 * <p>Daily quests are excluded: they reset and re-roll, so they can never permanently unlock
 * anything (mirrors the same rule enforced for {@link grill24.fishtastic.data.ShopEntry#unlockQuests}
 * by {@code CapstoneRewardGameTests}).
 *
 * <p>The scan result is cached per {@link Registry} instance — a fresh one is handed out whenever
 * datapacks reload or a world (re)loads — so repeated lookups (every gallery tick, every packet)
 * don't re-walk the whole quest registry.
 */
public final class FishTankShapeUnlocks {
    private static final Map<Registry<Quest>, Map<FishTankShape, List<ResourceKey<Quest>>>> CACHE = new WeakHashMap<>();

    private FishTankShapeUnlocks() {
    }

    public static List<ResourceKey<Quest>> unlockQuestsFor(Registry<Quest> quests, FishTankShape shape) {
        return indexFor(quests).getOrDefault(shape, List.of());
    }

    private static synchronized Map<FishTankShape, List<ResourceKey<Quest>>> indexFor(Registry<Quest> quests) {
        return CACHE.computeIfAbsent(quests, FishTankShapeUnlocks::buildIndex);
    }

    private static Map<FishTankShape, List<ResourceKey<Quest>>> buildIndex(Registry<Quest> quests) {
        Map<FishTankShape, List<ResourceKey<Quest>>> index = new EnumMap<>(FishTankShape.class);
        for (Map.Entry<ResourceKey<Quest>, Quest> entry : quests.entrySet()) {
            Quest quest = entry.getValue();
            if (quest.category() == QuestCategory.DAILY) continue;

            for (QuestReward.RewardItem rewardItem : quest.reward().items()) {
                FishTankShape shape = explicitShape(rewardItem);
                if (shape == null) continue;
                index.computeIfAbsent(shape, s -> new ArrayList<>()).add(entry.getKey());
            }
        }
        index.replaceAll((shape, keys) -> List.copyOf(keys));
        return index;
    }

    /**
     * The shape a reward item's component patch <em>explicitly</em> carries, or null if the patch
     * doesn't mention {@code fishtastic:fish_tank_shape} at all — deliberately not the item's
     * default (STANDARD), since a bare fish tank reward isn't meant to "unlock" the default shape.
     */
    @SuppressWarnings("unchecked")
    private static FishTankShape explicitShape(QuestReward.RewardItem rewardItem) {
        DataComponentType<FishTankShape> shapeType = FishtasticDataComponents.FISH_TANK_SHAPE.value();
        for (Map.Entry<DataComponentType<?>, Optional<?>> patchEntry : rewardItem.components().entrySet()) {
            if (patchEntry.getKey() == shapeType && patchEntry.getValue().isPresent()) {
                return (FishTankShape) patchEntry.getValue().get();
            }
        }
        return null;
    }
}
