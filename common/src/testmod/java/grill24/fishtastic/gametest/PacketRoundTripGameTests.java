package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.ItemSize;
import grill24.fishtastic.data.PhaseRule;
import grill24.fishtastic.network.PurchaseShopEntryPacket;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.network.StartFishingMinigamePacket;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.util.FishingTarget;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Round-trip tests for Fishtastic's custom StreamCodec payloads.
 *
 * Codecs here are typed StreamCodec<RegistryFriendlyByteBuf, T>, so encoding/decoding needs a
 * registry-aware buffer: new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess()).
 * ItemStack does not override equals(), so stacks are never compared directly — only by
 * getItem()/getCount()/relevant components, matching how FishCatchDataGameTests already avoids
 * whole-record equality for fields that embed ItemStack.
 * All methods are pure GameTestHelper consumers — no platform annotations here.
 */
public final class PacketRoundTripGameTests {

    private PacketRoundTripGameTests() {}

    private static RegistryFriendlyByteBuf newBuf(GameTestHelper helper) {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
    }

    private static void assertStacksMatch(GameTestHelper helper, ItemStack expected, ItemStack actual, String label) {
        helper.assertTrue(actual.getItem() == expected.getItem(), label + ": item must round-trip, expected " + expected.getItem() + " got " + actual.getItem());
        helper.assertTrue(actual.getCount() == expected.getCount(), label + ": count must round-trip, expected " + expected.getCount() + " got " + actual.getCount());

        FishQuality expectedQuality = expected.get(FishtasticDataComponents.FISH_QUALITY.value());
        FishQuality actualQuality = actual.get(FishtasticDataComponents.FISH_QUALITY.value());
        helper.assertTrue(
            (expectedQuality == null) == (actualQuality == null) && (expectedQuality == null || expectedQuality.equals(actualQuality)),
            label + ": FISH_QUALITY component must round-trip"
        );

        ItemSize expectedSize = expected.get(FishtasticDataComponents.ITEM_SIZE.value());
        ItemSize actualSize = actual.get(FishtasticDataComponents.ITEM_SIZE.value());
        helper.assertTrue(
            (expectedSize == null) == (actualSize == null) && (expectedSize == null || expectedSize.equals(actualSize)),
            label + ": ITEM_SIZE component must round-trip"
        );
    }

    // -------------------------------------------------------------------------
    // StartFishingMinigamePacket — nested record, List<>, enum-by-ordinal, ItemStack list
    // -------------------------------------------------------------------------

    public static void startFishingMinigamePacketRoundTrips(GameTestHelper helper) {
        ItemStack rewardFish = new ItemStack(FishtasticItems.BLUEGILL.value());
        rewardFish.set(FishtasticDataComponents.FISH_QUALITY.value(), new FishQuality(FishQuality.Quality.RARE));
        rewardFish.set(FishtasticDataComponents.ITEM_SIZE.value(), new ItemSize(18.5f));

        PhaseRule phase = new PhaseRule(
            0f,
            List.of(FishingTarget.MovementPattern.DRIFT, FishingTarget.MovementPattern.DART),
            Optional.empty(),
            Optional.of(60f),
            Optional.of(120f),
            Optional.of(0.5f)
        );
        StartFishingMinigamePacket.TargetData target = new StartFishingMinigamePacket.TargetData(
            List.of(rewardFish),
            FishingTarget.TargetCategory.FISH,
            0.5f,
            0.25f,
            List.of(phase)
        );
        StartFishingMinigamePacket original = new StartFishingMinigamePacket(42, List.of(target), true);

        RegistryFriendlyByteBuf buf = newBuf(helper);
        StartFishingMinigamePacket.STREAM_CODEC.encode(buf, original);
        StartFishingMinigamePacket decoded = StartFishingMinigamePacket.STREAM_CODEC.decode(buf);

        helper.assertTrue(decoded.sessionId() == original.sessionId(), "sessionId must round-trip");
        helper.assertTrue(decoded.isTutorial() == original.isTutorial(), "isTutorial must round-trip");
        helper.assertTrue(decoded.targets().size() == 1, "targets list size must round-trip");

        StartFishingMinigamePacket.TargetData decodedTarget = decoded.targets().get(0);
        helper.assertTrue(decodedTarget.category() == target.category(), "TargetData.category must round-trip");
        helper.assertTrue(decodedTarget.initialPosition() == target.initialPosition(), "TargetData.initialPosition must round-trip");
        helper.assertTrue(decodedTarget.difficulty() == target.difficulty(), "TargetData.difficulty must round-trip");
        helper.assertTrue(decodedTarget.phases().equals(target.phases()), "TargetData.phases (a clean record list) must round-trip via equals()");
        helper.assertTrue(decodedTarget.rewardStacks().size() == 1, "rewardStacks size must round-trip");
        assertStacksMatch(helper, rewardFish, decodedTarget.rewardStacks().get(0), "rewardStacks[0]");

        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // PurchaseShopEntryPacket — simplest packet, smoke-tests the harness itself
    // -------------------------------------------------------------------------

    public static void purchaseShopEntryPacketRoundTrips(GameTestHelper helper) {
        PurchaseShopEntryPacket original = new PurchaseShopEntryPacket(Identifier.fromNamespaceAndPath("fishtastic", "daily/some_entry"));

        RegistryFriendlyByteBuf buf = newBuf(helper);
        PurchaseShopEntryPacket.STREAM_CODEC.encode(buf, original);
        PurchaseShopEntryPacket decoded = PurchaseShopEntryPacket.STREAM_CODEC.decode(buf);

        helper.assertTrue(decoded.entryId().equals(original.entryId()), "entryId must round-trip, expected " + original.entryId() + " got " + decoded.entryId());
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // QuestSyncPacket — maps of a custom record (clean equals) and maps with ItemStack values
    // -------------------------------------------------------------------------

    public static void questSyncPacketRoundTrips(GameTestHelper helper) {
        Identifier questA = Identifier.fromNamespaceAndPath("fishtastic", "daily/quest_a");
        Identifier questB = Identifier.fromNamespaceAndPath("fishtastic", "tutorial/first_catch");

        Map<Identifier, PlayerQuestState.QuestProgress> progress = new HashMap<>();
        progress.put(questA, new PlayerQuestState.QuestProgress(3, 7L, false, false));
        progress.put(questB, new PlayerQuestState.QuestProgress(1, 1L, true, true));

        ItemStack triggeringStack = new ItemStack(FishtasticItems.BLUEGILL.value(), 2);
        Map<Identifier, ItemStack> triggeringItems = new HashMap<>();
        triggeringItems.put(questA, triggeringStack);

        Map<Identifier, Integer> purchaseCounts = new HashMap<>();
        purchaseCounts.put(Identifier.fromNamespaceAndPath("fishtastic", "shop_entry_x"), 2);

        QuestSyncPacket original = new QuestSyncPacket(progress, 150, triggeringItems, purchaseCounts);

        RegistryFriendlyByteBuf buf = newBuf(helper);
        QuestSyncPacket.STREAM_CODEC.encode(buf, original);
        QuestSyncPacket decoded = QuestSyncPacket.STREAM_CODEC.decode(buf);

        helper.assertTrue(decoded.tokenBalance() == original.tokenBalance(), "tokenBalance must round-trip");
        helper.assertTrue(decoded.questProgress().equals(original.questProgress()), "questProgress map (clean record values) must round-trip via equals()");
        helper.assertTrue(decoded.purchaseCounts().equals(original.purchaseCounts()), "purchaseCounts map must round-trip via equals()");

        helper.assertTrue(decoded.triggeringItems().size() == 1, "triggeringItems map size must round-trip");
        ItemStack decodedTriggerStack = decoded.triggeringItems().get(questA);
        helper.assertTrue(decodedTriggerStack != null, "triggeringItems key must round-trip");
        assertStacksMatch(helper, triggeringStack, decodedTriggerStack, "triggeringItems[questA]");

        helper.succeed();
    }
}
