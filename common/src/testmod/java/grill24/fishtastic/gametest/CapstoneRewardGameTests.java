package grill24.fishtastic.gametest;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.data.Quest;
import grill24.fishtastic.data.QuestReward;
import grill24.fishtastic.data.ShopEntry;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Covers the capstone exclusive rewards and the unlock-gated shop entries that let a player
 * re-buy them.
 *
 * <p>Two things need guarding: the gate must actually keep a locked entry out of the draw (it is
 * the only thing making the reward exclusive), and the item a gated entry sells must match the
 * one its quest granted — otherwise a re-buy silently hands over a plain default fish tank.
 */
public final class CapstoneRewardGameTests {

    private CapstoneRewardGameTests() {}

    /** A gated entry never appears for a player who has claimed nothing. */
    public static void gatedEntriesAreAbsentUntilTheirQuestIsClaimed(GameTestHelper helper) {
        Registry<ShopEntry> shop = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY);

        Set<ResourceKey<ShopEntry>> gated = new HashSet<>();
        for (Map.Entry<ResourceKey<ShopEntry>, ShopEntry> e : shop.entrySet()) {
            if (e.getValue().unlockQuest().isPresent()) gated.add(e.getKey());
        }
        helper.assertTrue(!gated.isEmpty(), "Expected at least one unlock-gated shop entry to exist");

        // 200 rotations x 4 rerolls, with nothing claimed — a gated entry must never surface.
        for (long day = 0; day < 200; day++) {
            for (int nonce = 0; nonce < 4; nonce++) {
                Set<ResourceKey<ShopEntry>> active =
                        ShopEntry.getActiveDailyShop(shop, day, nonce, questKey -> false);
                for (ResourceKey<ShopEntry> key : active) {
                    helper.assertFalse(gated.contains(key),
                            "Gated entry " + key.identifier() + " appeared in the shop with nothing claimed");
                }
            }
        }
        helper.succeed();
    }

    /** With every quest claimed, gated entries do join the draw — the gate opens, not just closes. */
    public static void gatedEntriesCanAppearOnceTheirQuestIsClaimed(GameTestHelper helper) {
        Registry<ShopEntry> shop = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY);

        Set<ResourceKey<ShopEntry>> gated = new HashSet<>();
        for (Map.Entry<ResourceKey<ShopEntry>, ShopEntry> e : shop.entrySet()) {
            if (e.getValue().unlockQuest().isPresent()) gated.add(e.getKey());
        }

        Set<ResourceKey<ShopEntry>> seen = new HashSet<>();
        for (long day = 0; day < 400; day++) {
            for (ResourceKey<ShopEntry> key : ShopEntry.getActiveDailyShop(shop, day, 0, questKey -> true)) {
                if (gated.contains(key)) seen.add(key);
            }
        }
        helper.assertTrue(!seen.isEmpty(),
                "No gated entry ever appeared across 400 rotations even with everything claimed");
        helper.succeed();
    }

    /**
     * Locked entries are filtered before the weighted pick, so a player who has unlocked nothing
     * still gets a full shop rather than one short a slot for every gated entry.
     */
    public static void lockedEntriesDoNotConsumeShopSlots(GameTestHelper helper) {
        Registry<ShopEntry> shop = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY);

        for (long day = 0; day < 50; day++) {
            int size = ShopEntry.getActiveDailyShop(shop, day, 0, questKey -> false).size();
            helper.assertTrue(size == ShopEntry.DAILY_SHOP_COUNT,
                    "Shop must still offer " + ShopEntry.DAILY_SHOP_COUNT + " entries when gated ones "
                            + "are locked; day " + day + " offered " + size);
        }
        helper.succeed();
    }

    /**
     * Every gated entry must sell exactly what its unlocking quest granted. A mismatch would mean
     * paying a premium for a differently-configured — usually plain — item.
     */
    public static void gatedEntrySellsTheSameItemItsQuestGranted(GameTestHelper helper) {
        Registry<ShopEntry> shop = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY);
        Registry<Quest> quests = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);

        List<String> failures = new ArrayList<>();
        for (Map.Entry<ResourceKey<ShopEntry>, ShopEntry> e : shop.entrySet()) {
            ShopEntry entry = e.getValue();
            if (entry.unlockQuest().isEmpty()) continue;

            Quest quest = quests.getOptional(entry.unlockQuest().get()).orElse(null);
            if (quest == null) {
                failures.add(e.getKey().identifier() + ": unlock_quest does not resolve");
                continue;
            }
            List<QuestReward.RewardItem> granted = quest.reward().items();
            if (granted.isEmpty()) {
                failures.add(e.getKey().identifier() + ": unlocking quest grants no item, so there is "
                        + "nothing exclusive for this entry to re-sell");
                continue;
            }

            // "Among", not "the first": a quest may hand out several things, and a gated entry
            // only has to correspond to one of them.
            ItemStack fromShop = entry.reward().getFirst().toItemStack();
            boolean matched = granted.stream()
                    .map(QuestReward.RewardItem::toStack)
                    .anyMatch(fromQuest -> ItemStack.isSameItemSameComponents(fromQuest, fromShop));

            if (!matched) {
                failures.add(e.getKey().identifier() + ": sells " + fromShop
                        + " but its quest grants none of " + granted.stream()
                        .map(r -> r.toStack().toString()).toList());
            }
        }

        helper.assertTrue(failures.isEmpty(), "Gated entry mismatch: " + String.join(" | ", failures));
        helper.succeed();
    }

    /**
     * An unlock gate must never point at a daily quest. {@code resetDailyIfNeeded} rewrites daily
     * progress as {@code QuestProgress(0, day, false, false, ...)} every rotation — including
     * {@code claimed} — so a daily-gated entry would silently re-lock itself every 20 minutes and
     * the player would lose access to something they had already earned.
     */
    public static void unlockGatesNeverPointAtDailyQuests(GameTestHelper helper) {
        Registry<ShopEntry> shop = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.SHOP_ENTRY_REGISTRY_KEY);
        Registry<Quest> quests = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);

        List<String> failures = new ArrayList<>();
        for (Map.Entry<ResourceKey<ShopEntry>, ShopEntry> e : shop.entrySet()) {
            ResourceKey<Quest> gate = e.getValue().unlockQuest().orElse(null);
            if (gate == null) continue;
            Quest quest = quests.getOptional(gate).orElse(null);
            if (quest != null && quest.category() == grill24.fishtastic.data.QuestCategory.DAILY) {
                failures.add(e.getKey().identifier() + ": gated behind daily quest " + gate.identifier()
                        + ", which clears its claimed flag every rotation");
            }
        }

        helper.assertTrue(failures.isEmpty(), "Daily-gated shop entry: " + String.join(" | ", failures));
        helper.succeed();
    }

    /**
     * The component patch on a reward item actually survives to the granted stack — this is what
     * makes a capstone tank distinctive rather than a default one.
     */
    public static void capstoneTanksCarryTheirMaterialsComponent(GameTestHelper helper) {
        Registry<Quest> quests = helper.getLevel().registryAccess()
                .lookupOrThrow(FishtasticRegistries.QUEST_REGISTRY_KEY);

        Set<FishTankMaterials> distinctConfigs = new HashSet<>();
        List<String> failures = new ArrayList<>();

        for (Map.Entry<ResourceKey<Quest>, Quest> e : quests.entrySet()) {
            for (QuestReward.RewardItem item : e.getValue().reward().items()) {
                ItemStack stack = item.toStack();
                if (!stack.is(net.minecraft.world.item.Items.AIR)
                        && stack.getItem() == grill24.fishtastic.FishtasticBlocks.FISH_TANK.value().asItem()) {
                    FishTankMaterials materials =
                            stack.get(FishtasticDataComponents.FISH_TANK_MATERIALS.value());
                    if (materials == null) {
                        failures.add(e.getKey().identifier() + ": grants a fish tank with no materials component");
                    } else {
                        distinctConfigs.add(materials);
                    }
                }
            }
        }

        helper.assertTrue(failures.isEmpty(), "Tank reward(s) missing materials: " + String.join(" | ", failures));
        helper.assertTrue(distinctConfigs.size() >= 5,
                "Capstone tanks must be visually distinct from each other; found only "
                        + distinctConfigs.size() + " unique frame/sand/glass combinations");
        helper.succeed();
    }
}
