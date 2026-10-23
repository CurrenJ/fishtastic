package grill24.fishtastic.gametest;

import net.minecraft.core.BlockPos;
import io.netty.handler.codec.EncoderException;
import io.netty.handler.codec.DecoderException;
import grill24.fishtastic.network.AmbientContextResponsePacket;
import grill24.fishtastic.network.AmbientContextRequestPacket;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.component.ItemSize;
import grill24.fishtastic.component.FishQuality;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.data.PhaseRule;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.LeaderboardEntry;
import grill24.fishtastic.network.PurchaseShopEntryPacket;
import grill24.fishtastic.network.QuestSyncPacket;
import grill24.fishtastic.network.RequestFishEncyclopediaPacket;
import grill24.fishtastic.network.StartFishingMinigamePacket;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.util.FishingBarContext;
import grill24.fishtastic.util.FishingTarget;
import grill24.fishtastic.util.Ids;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

        FishQuality expectedQuality = FishtasticItemData.get(expected, FishtasticDataComponents.FISH_QUALITY);
        FishQuality actualQuality = FishtasticItemData.get(actual, FishtasticDataComponents.FISH_QUALITY);
        helper.assertTrue(
            (expectedQuality == null) == (actualQuality == null) && (expectedQuality == null || expectedQuality.equals(actualQuality)),
            label + ": FISH_QUALITY component must round-trip"
        );

        ItemSize expectedSize = FishtasticItemData.get(expected, FishtasticDataComponents.ITEM_SIZE);
        ItemSize actualSize = FishtasticItemData.get(actual, FishtasticDataComponents.ITEM_SIZE);
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
        FishtasticItemData.set(rewardFish, FishtasticDataComponents.FISH_QUALITY, new FishQuality(FishQuality.Quality.RARE));
        FishtasticItemData.set(rewardFish, FishtasticDataComponents.ITEM_SIZE, new ItemSize(18.5f));

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
        List<ItemStack> topWeightedFish = List.of(
            new ItemStack(FishtasticItems.ACUTE_IASPIS.value()),
            new ItemStack(FishtasticItems.BLUEGILL.value())
        );
        Set<FishProfile.Zone> zones = Set.of(FishProfile.Zone.RIVER, FishProfile.Zone.HIGH_ALTITUDE);
        Set<ResourceLocation> undiscovered = Set.of(
            BuiltInRegistries.ITEM.getKey(FishtasticItems.ACUTE_IASPIS.value()),
            BuiltInRegistries.ITEM.getKey(FishtasticItems.BLUEGILL.value())
        );
        // Non-default bar context on purpose — a DEFAULT-only value would pass even if the enum
        // encoding were dropped from the codec entirely.
        StartFishingMinigamePacket original =
            new StartFishingMinigamePacket(42, List.of(target), true, topWeightedFish, zones, undiscovered, true,
                FishingBarContext.LAVA);

        RegistryFriendlyByteBuf buf = newBuf(helper);
        StartFishingMinigamePacket.STREAM_CODEC.encode(buf, original);
        StartFishingMinigamePacket decoded = StartFishingMinigamePacket.STREAM_CODEC.decode(buf);

        helper.assertTrue(decoded.sessionId() == original.sessionId(), "sessionId must round-trip");
        helper.assertTrue(decoded.isTutorial() == original.isTutorial(), "isTutorial must round-trip");
        helper.assertTrue(decoded.targets().size() == 1, "targets list size must round-trip");
        helper.assertTrue(decoded.topWeightedFishPreviews().size() == topWeightedFish.size(), "topWeightedFishPreviews size must round-trip");
        for (int i = 0; i < topWeightedFish.size(); i++) {
            assertStacksMatch(helper, topWeightedFish.get(i), decoded.topWeightedFishPreviews().get(i), "topWeightedFishPreviews[" + i + "]");
        }
        helper.assertTrue(decoded.zones().equals(original.zones()), "zones must round-trip");
        helper.assertTrue(decoded.undiscoveredSpecies().equals(original.undiscoveredSpecies()),
            "undiscoveredSpecies must round-trip, got " + decoded.undiscoveredSpecies());
        helper.assertTrue(decoded.baitWillBeSaved() == original.baitWillBeSaved(), "baitWillBeSaved must round-trip");
        helper.assertTrue(decoded.barContext() == FishingBarContext.LAVA,
            "barContext must round-trip, got " + decoded.barContext());

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
        PurchaseShopEntryPacket original = new PurchaseShopEntryPacket(Ids.of("fishtastic", "daily/some_entry"));

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
        ResourceLocation questA = Ids.of("fishtastic", "daily/quest_a");
        ResourceLocation questB = Ids.of("fishtastic", "tutorial/first_catch");

        Map<ResourceLocation, PlayerQuestState.QuestProgress> progress = new HashMap<>();
        progress.put(questA, new PlayerQuestState.QuestProgress(3, 7L, false, false, List.of()));
        progress.put(questB, new PlayerQuestState.QuestProgress(1, 1L, true, true, List.of()));

        ItemStack triggeringStack = new ItemStack(FishtasticItems.BLUEGILL.value(), 2);
        Map<ResourceLocation, ItemStack> triggeringItems = new HashMap<>();
        triggeringItems.put(questA, triggeringStack);

        Map<ResourceLocation, Integer> purchaseCounts = new HashMap<>();
        purchaseCounts.put(Ids.of("fishtastic", "shop_entry_x"), 2);

        ItemStack baitStack = new ItemStack(FishtasticItems.BLUEGILL.value(), 1);
        List<ItemStack> firstCatchItems = List.of(new ItemStack(FishtasticItems.BLUEGILL.value(), 1));

        List<grill24.fishtastic.network.CleanupGoalProgress.Contributor> contributors = List.of(
                new grill24.fishtastic.network.CleanupGoalProgress.Contributor(UUID.randomUUID(), "Steve", 90),
                new grill24.fishtastic.network.CleanupGoalProgress.Contributor(UUID.randomUUID(), "Alex", 30));

        List<ResourceLocation> unlockedCompanions = List.of(Ids.of("fishtastic", "bluegill"), Ids.of("fishtastic", "oscar"));
        List<ResourceLocation> newlyUnlockedCompanions = List.of(Ids.of("fishtastic", "oscar"));
        QuestSyncPacket original = new QuestSyncPacket(progress, 150, triggeringItems, purchaseCounts,
                new grill24.fishtastic.network.CleanupGoalProgress(120, 200, 0, contributors), 12345L,
                baitStack, firstCatchItems, new QuestSyncPacket.ShopRerollState(3, 4, false),
                unlockedCompanions, newlyUnlockedCompanions);

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

        helper.assertTrue(decoded.unlockedCompanions().equals(original.unlockedCompanions()),
            "unlockedCompanions must round-trip, expected " + original.unlockedCompanions() + " got " + decoded.unlockedCompanions());
        helper.assertTrue(decoded.newlyUnlockedCompanions().equals(original.newlyUnlockedCompanions()),
            "newlyUnlockedCompanions must round-trip, expected " + original.newlyUnlockedCompanions() + " got " + decoded.newlyUnlockedCompanions());
        helper.assertTrue(decoded.cleanupGoal().equals(original.cleanupGoal()),
            "cleanupGoal (CleanupGoalProgress) must round-trip via equals(), expected " + original.cleanupGoal() + " got " + decoded.cleanupGoal());

        helper.assertTrue(decoded.serverGameTime() == original.serverGameTime(),
            "serverGameTime must round-trip, expected " + original.serverGameTime() + " got " + decoded.serverGameTime());

        assertStacksMatch(helper, baitStack, decoded.baitDepletedItem(), "baitDepletedItem");

        helper.assertTrue(decoded.firstCatchItems().size() == 1, "firstCatchItems list size must round-trip");
        assertStacksMatch(helper, firstCatchItems.get(0), decoded.firstCatchItems().get(0), "firstCatchItems[0]");

        helper.assertTrue(decoded.shopRefreshCount() == original.shopRefreshCount(),
            "shopRefreshCount must round-trip, expected " + original.shopRefreshCount() + " got " + decoded.shopRefreshCount());

        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // QuestSyncPacket.cleanupGoal — milestoneReached is the field most likely to be
    // accidentally dropped, since it's 0 on most syncs and only non-zero on announcements.
    // -------------------------------------------------------------------------

    public static void questSyncPacketCleanupGoalMilestoneRoundTrips(GameTestHelper helper) {
        QuestSyncPacket original = new QuestSyncPacket(
            Map.of(), 0, Map.of(), Map.of(),
            new grill24.fishtastic.network.CleanupGoalProgress(400, 200, 400, List.of()), 0L,
                ItemStack.EMPTY, List.of(), new QuestSyncPacket.ShopRerollState(0, 0, true), List.of(), List.of());

        RegistryFriendlyByteBuf buf = newBuf(helper);
        QuestSyncPacket.STREAM_CODEC.encode(buf, original);
        QuestSyncPacket decoded = QuestSyncPacket.STREAM_CODEC.decode(buf);

        helper.assertTrue(decoded.cleanupGoal().total() == 400, "cleanupGoal.total must round-trip, got " + decoded.cleanupGoal().total());
        helper.assertTrue(decoded.cleanupGoal().threshold() == 200, "cleanupGoal.threshold must round-trip, got " + decoded.cleanupGoal().threshold());
        helper.assertTrue(decoded.cleanupGoal().milestoneReached() == 400,
            "cleanupGoal.milestoneReached must round-trip non-zero, got " + decoded.cleanupGoal().milestoneReached());
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // FishEncyclopediaSyncPacket — Map<ResourceLocation, Integer> and two List<LeaderboardEntry>
    // -------------------------------------------------------------------------

    public static void fishEncyclopediaSyncPacketRoundTrips(GameTestHelper helper) {
        ResourceLocation bluegill = Ids.of("fishtastic", "bluegill");
        ResourceLocation trout = Ids.of("fishtastic", "trout");

        Map<ResourceLocation, Integer> catchCounts = new HashMap<>();
        catchCounts.put(bluegill, 12);
        catchCounts.put(trout, 3);

        List<LeaderboardEntry> personalBest = List.of(
            LeaderboardEntry.personalBestSize(bluegill, 18.5f, FishQuality.Quality.RARE)
        );
        UUID otherPlayer = UUID.randomUUID();
        List<LeaderboardEntry> globalBest = List.of(
            LeaderboardEntry.globalBestSize(trout, otherPlayer, "SomePlayer", 24f, FishQuality.Quality.LEGENDARY)
        );

        List<String> claimedRewardKeys = List.of(bluegill.toString() + "#0");

        FishEncyclopediaSyncPacket original = new FishEncyclopediaSyncPacket(catchCounts, personalBest, globalBest, claimedRewardKeys);

        RegistryFriendlyByteBuf buf = newBuf(helper);
        FishEncyclopediaSyncPacket.STREAM_CODEC.encode(buf, original);
        FishEncyclopediaSyncPacket decoded = FishEncyclopediaSyncPacket.STREAM_CODEC.decode(buf);

        helper.assertTrue(decoded.personalCatchCounts().equals(original.personalCatchCounts()), "personalCatchCounts map must round-trip via equals()");
        helper.assertTrue(decoded.personalBestSizes().equals(original.personalBestSizes()), "personalBestSizes (clean record list) must round-trip via equals()");
        helper.assertTrue(decoded.globalBestSizes().equals(original.globalBestSizes()), "globalBestSizes (clean record list) must round-trip via equals()");
        helper.assertTrue(decoded.claimedRewardKeys().equals(original.claimedRewardKeys()), "claimedRewardKeys list must round-trip via equals()");

        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // RequestFishEncyclopediaPacket — single boolean field
    // -------------------------------------------------------------------------

    public static void requestFishEncyclopediaPacketRoundTrips(GameTestHelper helper) {
        RequestFishEncyclopediaPacket original = new RequestFishEncyclopediaPacket(true);

        RegistryFriendlyByteBuf buf = newBuf(helper);
        RequestFishEncyclopediaPacket.STREAM_CODEC.encode(buf, original);
        RequestFishEncyclopediaPacket decoded = RequestFishEncyclopediaPacket.STREAM_CODEC.decode(buf);

        helper.assertTrue(decoded.equals(original), "RequestFishEncyclopediaPacket must round-trip (unit codec)");
        helper.succeed();
    }


    // -------------------------------------------------------------------------
    // AmbientContextRequestPacket (Fish in World, Phase 4) — nested Origin records, a bounded
    // list, ResourceLocation + BlockPos codecs and signed longs/ints
    // -------------------------------------------------------------------------

    public static void ambientContextRequestPacketRoundTrips(GameTestHelper helper) {
        // A negative session exercises the VAR_LONG sign, negative coordinates exercise the
        // BlockPos codec, and the second origin carries a modded dimension and non-trivial
        // generation counters.
        AmbientContextRequestPacket original =
                new AmbientContextRequestPacket(
                        -0x5E5510ABC01L, 7,
                        List.of(
                                new AmbientContextRequestPacket.Origin(
                                        Ids.of("minecraft", "overworld"), 77L, 1, 0,
                                        new BlockPos(-64, 62, 1234)),
                                new AmbientContextRequestPacket.Origin(
                                        Ids.of("fishtastic", "the_deep"), 0x1234_5678_9ABCL, 999, 42,
                                        new BlockPos(0, -60, 0))));

        RegistryFriendlyByteBuf buf = newBuf(helper);
        AmbientContextRequestPacket.STREAM_CODEC.encode(buf, original);
        AmbientContextRequestPacket decoded =
                AmbientContextRequestPacket.STREAM_CODEC.decode(buf);

        helper.assertTrue(decoded.equals(original),
                "AmbientContextRequestPacket must round-trip via equals(), got " + decoded);
        helper.succeed();
    }

    public static void ambientContextResponsePacketRoundTrips(GameTestHelper helper) {
        // The OK entry carries every boolean non-default and two candidates.
        AmbientContextResponsePacket.Entry ok = new AmbientContextResponsePacket.Entry(
                77L, 1, 0, AmbientContextResponsePacket.Status.OK,
                true, true, true, 0x1234ABCDL,
                List.of(
                        new AmbientContextResponsePacket.Candidate(Ids.of("fishtastic", "bluegill"), 10),
                        new AmbientContextResponsePacket.Candidate(Ids.of("fishtastic", "plaice"), 1)));
        roundTripResponse(helper, "OK with candidates",
                new AmbientContextResponsePacket(-42L, 7, List.of(ok)));

        // One packet per rejection status: the status enum is the field most likely to be
        // silently dropped, and the wire bound of 4 entries (MAX_ENTRIES) means they cannot all
        // share one packet.
        for (AmbientContextResponsePacket.Status status : AmbientContextResponsePacket.Status.values()) {
            if (status == AmbientContextResponsePacket.Status.OK) {
                continue;
            }
            roundTripResponse(helper, status.name(), new AmbientContextResponsePacket(-42L, 7,
                    List.of(AmbientContextResponsePacket.Entry.rejected(77L, 1, 0, status))));
        }
        helper.succeed();
    }

    private static void roundTripResponse(GameTestHelper helper, String label,
                                          AmbientContextResponsePacket original) {
        RegistryFriendlyByteBuf buf = newBuf(helper);
        AmbientContextResponsePacket.STREAM_CODEC.encode(buf, original);
        AmbientContextResponsePacket decoded = AmbientContextResponsePacket.STREAM_CODEC.decode(buf);
        helper.assertTrue(decoded.equals(original),
                "AmbientContextResponsePacket must round-trip (" + label + "), got " + decoded);
    }

    // -------------------------------------------------------------------------
    // AmbientContext* wire bounds (Fish in World, Phase 4) — the whole
    // bounded-traffic argument rests on the codecs refusing an oversized list
    // on BOTH sides: encode (the client cannot even build the packet) and
    // decode (a hostile writer's oversized buffer is rejected, not read).
    // -------------------------------------------------------------------------

    private static List<AmbientContextRequestPacket.Origin> origins(int count) {
        List<AmbientContextRequestPacket.Origin> origins = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            origins.add(new AmbientContextRequestPacket.Origin(
                    Ids.of("minecraft", "overworld"), i, 1, 0, new BlockPos(i, 62, 0)));
        }
        return origins;
    }

    public static void oversizedOriginListIsRejectedOnEncodeAndDecode(GameTestHelper helper) {
        List<AmbientContextRequestPacket.Origin> tooMany = origins(AmbientContextRequestPacket.MAX_ORIGINS + 1);
        helper.assertTrue(tooMany.size() == 5, "Sanity check: MAX_ORIGINS is 4, so 5 origins is oversized");

        boolean encodeThrew = false;
        try {
            AmbientContextRequestPacket.STREAM_CODEC.encode(newBuf(helper),
                    new AmbientContextRequestPacket(7L, 1, tooMany));
        } catch (EncoderException expected) {
            encodeThrew = true;
        }
        helper.assertTrue(encodeThrew,
                "Encoding more than MAX_ORIGINS origins must throw EncoderException — this is what "
                        + "stops an over-large dispatch at the source");

        RegistryFriendlyByteBuf buf = newBuf(helper);
        buf.writeVarLong(7L);
        buf.writeVarInt(1);
        buf.writeVarInt(tooMany.size());
        for (AmbientContextRequestPacket.Origin origin : tooMany) {
            AmbientContextRequestPacket.Origin.STREAM_CODEC.encode(buf, origin);
        }
        boolean decodeThrew = false;
        try {
            AmbientContextRequestPacket.STREAM_CODEC.decode(buf);
        } catch (DecoderException expected) {
            decodeThrew = true;
        }
        helper.assertTrue(decodeThrew,
                "Decoding a hand-written oversized origins list must throw DecoderException");
        helper.succeed();
    }

    public static void oversizedEntryListIsRejectedOnEncodeAndDecode(GameTestHelper helper) {
        List<AmbientContextResponsePacket.Entry> tooMany = new ArrayList<>();
        for (int i = 0; i < AmbientContextResponsePacket.MAX_ENTRIES + 1; i++) {
            tooMany.add(AmbientContextResponsePacket.Entry.rejected(
                    i, 1, 0, AmbientContextResponsePacket.Status.REJECTED_NOT_WATER));
        }
        helper.assertTrue(tooMany.size() == 5, "Sanity check: MAX_ENTRIES is 4, so 5 entries is oversized");

        boolean encodeThrew = false;
        try {
            AmbientContextResponsePacket.STREAM_CODEC.encode(newBuf(helper),
                    new AmbientContextResponsePacket(-42L, 7, tooMany));
        } catch (EncoderException expected) {
            encodeThrew = true;
        }
        helper.assertTrue(encodeThrew, "Encoding more than MAX_ENTRIES entries must throw EncoderException");

        RegistryFriendlyByteBuf buf = newBuf(helper);
        buf.writeVarLong(-42L);
        buf.writeVarInt(7);
        buf.writeVarInt(tooMany.size());
        for (AmbientContextResponsePacket.Entry entry : tooMany) {
            AmbientContextResponsePacket.Entry.STREAM_CODEC.encode(buf, entry);
        }
        boolean decodeThrew = false;
        try {
            AmbientContextResponsePacket.STREAM_CODEC.decode(buf);
        } catch (DecoderException expected) {
            decodeThrew = true;
        }
        helper.assertTrue(decodeThrew,
                "Decoding a hand-written oversized entries list must throw DecoderException");
        helper.succeed();
    }

    public static void oversizedCandidateListIsRejectedOnEncodeAndDecode(GameTestHelper helper) {
        List<AmbientContextResponsePacket.Candidate> tooMany = new ArrayList<>();
        for (int i = 0; i < AmbientContextResponsePacket.MAX_CANDIDATES + 1; i++) {
            tooMany.add(new AmbientContextResponsePacket.Candidate(Ids.of("minecraft", "candidate_" + i), i + 1));
        }
        helper.assertTrue(tooMany.size() == 97, "Sanity check: MAX_CANDIDATES is 96, so 97 candidates is oversized");
        AmbientContextResponsePacket.Entry oversized = new AmbientContextResponsePacket.Entry(
                77L, 1, 0, AmbientContextResponsePacket.Status.OK, false, false, true, 0x1111L, tooMany);

        boolean encodeThrew = false;
        try {
            AmbientContextResponsePacket.Entry.STREAM_CODEC.encode(newBuf(helper), oversized);
        } catch (EncoderException expected) {
            encodeThrew = true;
        }
        helper.assertTrue(encodeThrew, "Encoding more than MAX_CANDIDATES candidates must throw EncoderException");

        RegistryFriendlyByteBuf buf = newBuf(helper);
        buf.writeVarLong(77L);
        buf.writeVarInt(1);
        buf.writeVarInt(0);
        buf.writeVarInt(AmbientContextResponsePacket.Status.OK.ordinal());
        buf.writeBoolean(false);
        buf.writeBoolean(false);
        buf.writeBoolean(true);
        buf.writeVarLong(0x1111L);
        buf.writeVarInt(tooMany.size());
        for (AmbientContextResponsePacket.Candidate candidate : tooMany) {
            AmbientContextResponsePacket.Candidate.STREAM_CODEC.encode(buf, candidate);
        }
        boolean decodeThrew = false;
        try {
            AmbientContextResponsePacket.Entry.STREAM_CODEC.decode(buf);
        } catch (DecoderException expected) {
            decodeThrew = true;
        }
        helper.assertTrue(decodeThrew,
                "Decoding a hand-written oversized candidate list must throw DecoderException");
        helper.succeed();
    }

    /**
     * An unknown status id on the wire is a hostile or broken writer's doing; it must decode to a
     * rejection (never OK, never an exception). The trivially wrong implementation throws
     * ArrayIndexOutOfBoundsException here — an extra crash surface reachable from the network.
     */
    public static void unknownStatusIdDecodesToARejectionNotAnError(GameTestHelper helper) {
        for (int statusId : new int[]{AmbientContextResponsePacket.Status.values().length + 83, -5}) {
            RegistryFriendlyByteBuf buf = newBuf(helper);
            buf.writeVarLong(77L);
            buf.writeVarInt(1);
            buf.writeVarInt(0);
            buf.writeVarInt(statusId);
            buf.writeBoolean(false);
            buf.writeBoolean(false);
            buf.writeBoolean(false);
            buf.writeVarLong(0L);
            buf.writeVarInt(0);
            AmbientContextResponsePacket.Entry decoded = AmbientContextResponsePacket.Entry.STREAM_CODEC.decode(buf);
            helper.assertTrue(decoded.status() != AmbientContextResponsePacket.Status.OK,
                    "An unknown status id (" + statusId + ") must never decode to OK, got " + decoded.status());
            helper.assertTrue(decoded.status() == AmbientContextResponsePacket.Status.REJECTED_NOT_WATER,
                    "An unknown status id (" + statusId + ") must decode to the safe rejection default, got "
                            + decoded.status());
        }
        helper.succeed();
    }
}
