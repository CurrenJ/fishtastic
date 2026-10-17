package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.companion.CompanionBondGate;
import grill24.fishtastic.companion.CompanionBondUnlocks;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.companion.Companions;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishEncyclopediaEntry;
import grill24.fishtastic.server.FishCatchSavedData;
import grill24.fishtastic.server.PlayerQuestState;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * The Phase 4 companion bond gate (docs/fish-companions.md §8.3, §8.4): a normal species unlocks
 * on a fully complete encyclopedia entry and never re-locks; an unlisted species never unlocks by
 * catch volume at all — only a quest carrying {@code unlocks_companion} can do it. Also the
 * rename path and the untrusted-description rule.
 */
public final class CompanionBondGameTests {
    private CompanionBondGameTests() {}

    /** The unlisted species used in tests; any of the six behaves the same. */
    private static final ResourceLocation UNLISTED = grill24.fishtastic.util.Ids.of("fishtastic", "molten_moorish_idol");
    private static final ResourceLocation BLUEGILL = grill24.fishtastic.util.Ids.of("fishtastic", "bluegill");

    private static ServerPlayer setUp(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        CompanionStorage.clear(player);
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(player.level().getServer());
        data.getOrCreateQuestState(player).clearCompanionBonds();
        return player;
    }

    private static FishCatchSavedData data(ServerPlayer player) {
        return FishCatchSavedData.getOrCreate(player.level().getServer());
    }

    private static int loreThreshold(ServerPlayer player, ResourceLocation species) {
        FishEncyclopediaEntry.UnlockThresholds thresholds =
                EncyclopediaTiers.thresholdsFor(player.level().registryAccess(), species);
        int max = 0;
        for (grill24.fishtastic.data.EncyclopediaRewardSection section
                : grill24.fishtastic.data.EncyclopediaRewardSection.values()) {
            max = Math.max(max, section.threshold(thresholds));
        }
        return max;
    }

    private static ItemStack bluegill(float sizeCm) {
        return ItemSizeHelper.setSize(new ItemStack(FishtasticItems.BLUEGILL.value()), sizeCm);
    }

    /** A species with an incomplete entry cannot bond, and nothing changes. */
    public static void lockedSpeciesRefusesBond(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        FishCatchSavedData data = data(player);
        data.setCatchCount(data.resolvePlayerKey(player), player.getName().getString(), BLUEGILL, 1);

        ItemStack fish = bluegill(25f);
        player.getInventory().setItem(4, fish.copy());
        helper.assertTrue(!CompanionBondGate.isUnlocked(player, fish), "1 catch must not unlock the bond");

        Companions.BondResult result = Companions.bond(player, fish.copy());

        helper.assertTrue(result == Companions.BondResult.LOCKED, "bond result " + result);
        helper.assertTrue(CompanionStorage.lead(player).isEmpty(), "companion must stay empty");
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(4), fish), "the fish must stay in the inventory");
        helper.succeed();
    }

    /** A complete entry unlocks the bond — and it stays unlocked after the counts are rolled back. */
    public static void completeEntryUnlocksAndNeverReLocks(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        FishCatchSavedData data = data(player);
        data.setCatchCount(data.resolvePlayerKey(player), player.getName().getString(), BLUEGILL, loreThreshold(player, BLUEGILL));

        ItemStack fish = bluegill(25f);
        helper.assertTrue(CompanionBondGate.isUnlocked(player, fish), "a complete entry must unlock the bond");
        helper.assertTrue(data.getOrCreateQuestState(player).isCompanionBondUnlocked(BLUEGILL),
                "the unlock must be stored, not just derived");

        // Grandfathering: roll the counts back to zero; the stored bond survives (R6.4).
        data.setCatchCount(data.resolvePlayerKey(player), player.getName().getString(), BLUEGILL, 0);
        helper.assertTrue(CompanionBondGate.isUnlocked(player, fish), "an unlocked bond must never re-lock");

        player.getInventory().setItem(4, fish.copy());
        helper.assertTrue(Companions.bond(player, fish.copy()) == Companions.BondResult.BONDED,
                "an unlocked bond must accept the fish");
        helper.succeed();
    }

    /** Unlisted species are quest-only: no catch count ever unlocks them. */
    public static void unlistedSpeciesNeverUnlocksByCounts(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        helper.assertTrue(CompanionBondUnlocks.isUnlisted(UNLISTED), "test species must be unlisted");
        FishCatchSavedData data = data(player);
        data.setCatchCount(data.resolvePlayerKey(player), player.getName().getString(), UNLISTED, 9999);

        ItemStack fish = new ItemStack(BuiltInRegistries.ITEM.getOptional(UNLISTED).orElseThrow());
        helper.assertTrue(!CompanionBondGate.isUnlocked(player, fish), "an unlisted species must not unlock by catches");

        Companions.BondResult result = Companions.bond(player, fish.copy());
        helper.assertTrue(result == Companions.BondResult.LOCKED, "bond result " + result);
        helper.succeed();
    }

    /** A new unlock queues exactly one banner; repeat refreshes are no-ops. */
    public static void unlockQueuesOneBanner(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        FishCatchSavedData data = data(player);
        PlayerQuestState state = data.getOrCreateQuestState(player);

        data.setCatchCount(data.resolvePlayerKey(player), player.getName().getString(), BLUEGILL, loreThreshold(player, BLUEGILL));
        CompanionBondUnlocks.refresh(player);
        helper.assertTrue(state.hasPendingCompanionBanners(), "a new unlock must queue a banner");
        helper.assertTrue(state.drainPendingCompanionBanners().contains(BLUEGILL), "the banner must name the species");

        helper.assertTrue(CompanionBondUnlocks.refresh(player).isEmpty(), "a repeat refresh must find nothing new");
        helper.assertTrue(!state.hasPendingCompanionBanners(), "a repeat refresh must not re-queue a banner");
        helper.succeed();
    }

    /** The bond packet only describes a fish; a description matching nothing carried does nothing. */
    public static void fakeDescriptionCannotBondAnUncarriedFish(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        FishCatchSavedData data = data(player);
        PlayerQuestState state = data.getOrCreateQuestState(player);
        state.unlockCompanionBond(BLUEGILL);
        state.drainPendingCompanionBanners();

        ItemStack carried = bluegill(25f);
        player.getInventory().setItem(4, carried.copy());
        ItemStack faked = bluegill(31f); // same species, different size — not what the player carries

        Companions.BondResult result = Companions.bond(player, faked);

        helper.assertTrue(result == Companions.BondResult.NOT_CARRIED, "bond result " + result);
        helper.assertTrue(CompanionStorage.lead(player).isEmpty(), "companion must stay empty");
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(4), carried), "the carried fish must be untouched");
        helper.succeed();
    }

    /** Renaming rewrites the stored fish's custom name; blank clears it; long names clamp. */
    public static void renameSetsAndClearsTheName(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        FishCatchSavedData data = data(player);
        PlayerQuestState state = data.getOrCreateQuestState(player);
        state.unlockCompanionBond(BLUEGILL);
        state.drainPendingCompanionBanners();
        ItemStack fish = bluegill(25f);
        player.getInventory().setItem(4, fish.copy());
        Companions.bond(player, fish.copy());

        helper.assertTrue(Companions.rename(player, 0, "  Bubbles  "), "rename must succeed with a companion");
        ItemStack renamed = CompanionStorage.lead(player);
        helper.assertTrue(renamed.getHoverName().getString().equals("Bubbles"),
                "name must be trimmed and set, got " + renamed.getHoverName().getString());
        helper.assertTrue(renamed.is(FishtasticItems.BLUEGILL.value()), "renaming must keep the species");
        helper.assertTrue(ItemSizeHelper.getSize(renamed) == 25f,
                "renaming must keep every other component, size=" + ItemSizeHelper.getSize(renamed));

        Companions.rename(player, 0, "x".repeat(64));
        helper.assertTrue(CompanionStorage.lead(player).getHoverName().getString().length()
                        == grill24.fishtastic.network.RenameCompanionPacket.MAX_NAME_LENGTH,
                "long names must clamp to the wire limit");

        Companions.rename(player, 0, "");
        helper.assertTrue(!CompanionStorage.lead(player).has(net.minecraft.core.component.DataComponents.CUSTOM_NAME),
                "a blank rename must clear the custom name");
        helper.succeed();
    }
}
