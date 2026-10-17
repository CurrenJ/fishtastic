package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.companion.CompanionStorage;
import grill24.fishtastic.companion.Companions;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;

import java.util.List;
import java.util.function.Supplier;

/**
 * Fish companion storage and the server-side bond/unequip operations (docs/fish-companions.md §8):
 * the fish item is the companion, so every operation has to move exactly one fish between the
 * inventory (or a Pile of Fish) and the companion slot — never duplicate it, never lose it.
 */
public final class CompanionGameTests {

    private CompanionGameTests() {}

    private static ServerPlayer setUp(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        // Survival, so Inventory.add's creative "silently zero the stack" shortcut can't hide a loss.
        player.getAbilities().instabuild = false;
        player.getInventory().clearContent();
        CompanionStorage.clear(player);
        // These tests exercise moving the fish, not the gate — unlock the species directly so the
        // Phase 4 gate can never mask a storage failure. Gate behaviour has its own tests
        // (CompanionBondGameTests).
        var data = grill24.fishtastic.server.FishCatchSavedData.getOrCreate(player.level().getServer());
        var state = data.getOrCreateQuestState(player);
        state.unlockCompanionBond(net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(FishtasticItems.BLUEGILL.value()));
        state.drainPendingCompanionBanners();
        return player;
    }

    private static ItemStack bluegill(float sizeCm) {
        return ItemSizeHelper.setSize(new ItemStack(FishtasticItems.BLUEGILL.value()), sizeCm);
    }

    private static ItemStack pileOf(ItemStack... fish) {
        ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
        FishtasticItemData.setBundleContents(pile, new BundleContents(
                java.util.Arrays.stream(fish).map(ItemStack::copy).toList()));
        return pile;
    }

    /** Every fish the player holds, loose or piled, as single stacks. */
    private static List<ItemStack> allFish(ServerPlayer player) {
        List<ItemStack> fish = new java.util.ArrayList<>();
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(FishtasticItems.PILE_OF_FISH.value())) {
                FishtasticItemData.bundleContentsOrEmpty(stack).itemCopyStream().forEach(fish::add);
            } else if (stack.is(FishtasticItems.BLUEGILL.value())) {
                fish.add(stack.copy());
            }
        }
        return fish;
    }

    private static int nearbyBluegills(ServerPlayer player) {
        int total = 0;
        for (Entity entity : ((ServerLevel) player.level()).getAllEntities()) {
            if (entity instanceof ItemEntity item) {
                ItemStack stack = item.getItem();
                if (stack.is(FishtasticItems.BLUEGILL.value())) total += stack.getCount();
                if (stack.is(FishtasticItems.PILE_OF_FISH.value())) total += FishtasticItemData.bundleContentsOrEmpty(stack).size();
            }
        }
        return total;
    }

    public static void bondTakesTheFishFromTheInventory(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack fish = bluegill(25f);
        player.getInventory().setItem(4, fish.copy());

        Companions.BondResult result = Companions.bond(player, fish.copy());

        helper.assertTrue(result == Companions.BondResult.BONDED, "bond result " + result);
        helper.assertTrue(ItemStack.matches(CompanionStorage.lead(player), fish), "companion " + CompanionStorage.lead(player));
        helper.assertTrue(player.getInventory().getItem(4).isEmpty(), "the fish must leave the inventory");
        helper.succeed();
    }

    public static void bondTakesTheFishFromInsideAPile(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack small = bluegill(12f), wanted = bluegill(25f), big = bluegill(31f);
        player.getInventory().setItem(0, pileOf(small, wanted, big));

        Companions.BondResult result = Companions.bond(player, wanted.copy());

        helper.assertTrue(result == Companions.BondResult.BONDED, "bond result " + result);
        helper.assertTrue(ItemStack.matches(CompanionStorage.lead(player), wanted), "companion " + CompanionStorage.lead(player));
        List<ItemStack> left = allFish(player);
        helper.assertTrue(left.size() == 2 && left.stream().anyMatch(s -> ItemStack.matches(s, small))
                && left.stream().anyMatch(s -> ItemStack.matches(s, big)), "the pile must keep the other two, got " + left);
        helper.succeed();
    }

    /** A pile left with one fish becomes that fish, as PileOfFishItem does when it is emptied by hand. */
    public static void bondFromATwoFishPileLeavesTheOtherFishLoose(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack wanted = bluegill(25f), other = bluegill(18f);
        player.getInventory().setItem(0, pileOf(wanted, other));

        Companions.bond(player, wanted.copy());

        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), other),
                "the pile must collapse into the remaining fish, got " + player.getInventory().getItem(0));
        helper.succeed();
    }

    public static void bondWithAFishThePlayerDoesNotCarryChangesNothing(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack carried = bluegill(25f);
        player.getInventory().setItem(2, carried.copy());

        // Same species, different size: a description that matches nothing in the inventory.
        Companions.BondResult result = Companions.bond(player, bluegill(26f));

        helper.assertTrue(result == Companions.BondResult.NOT_CARRIED, "bond result " + result);
        helper.assertTrue(CompanionStorage.lead(player).isEmpty(), "nothing may be equipped");
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(2), carried), "the carried fish must stay put");
        helper.succeed();
    }

    public static void bondRefusesSomethingThatIsNotAFish(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        player.getInventory().setItem(0, new ItemStack(Items.DIRT));

        Companions.BondResult result = Companions.bond(player, new ItemStack(Items.DIRT));

        helper.assertTrue(result == Companions.BondResult.NOT_A_FISH, "bond result " + result);
        helper.assertTrue(CompanionStorage.lead(player).isEmpty() && player.getInventory().getItem(0).is(Items.DIRT),
                "nothing may move");
        helper.succeed();
    }

    public static void unequipReturnsTheIdenticalFish(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack fish = bluegill(25f);
        fish.set(DataComponents.CUSTOM_NAME, Component.literal("Gilbert"));
        player.getInventory().setItem(0, fish.copy());
        Companions.bond(player, fish.copy());

        boolean unequipped = Companions.unequip(player, 0);

        helper.assertTrue(unequipped, "unequip must report the companion it returned");
        helper.assertTrue(CompanionStorage.lead(player).isEmpty(), "the slot must be empty afterwards");
        List<ItemStack> held = allFish(player);
        helper.assertTrue(held.size() == 1 && ItemStack.matches(held.get(0), fish),
                "the exact fish (name and size) must come back, got " + held);
        helper.succeed();
    }

    public static void unequipWithAFullInventoryDropsTheFish(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        Companions.equip(player, 0, bluegill(25f));
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        int before = nearbyBluegills(player);

        Companions.unequip(player, 0);

        helper.assertTrue(CompanionStorage.lead(player).isEmpty(), "the slot must be empty afterwards");
        helper.assertTrue(nearbyBluegills(player) - before == 1,
                "a companion that doesn't fit must drop at the player's feet, delta=" + (nearbyBluegills(player) - before));
        helper.succeed();
    }

    /** Party spec §5.1: a second bond fills the next empty slot; nothing comes back. */
    public static void bondingASecondFishFillsTheNextSlot(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack first = bluegill(20f), second = bluegill(30f);
        player.getInventory().setItem(0, first.copy());
        player.getInventory().setItem(1, second.copy());
        Companions.bond(player, first.copy());

        Companions.bond(player, second.copy());

        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 0), first), "lead " + CompanionStorage.get(player, 0));
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 1), second), "slot 1 " + CompanionStorage.get(player, 1));
        helper.assertTrue(allFish(player).isEmpty(), "both fish must have left the inventory, got " + allFish(player));
        helper.succeed();
    }

    /** Party spec §5.1: a full party refuses a first-empty bond and changes nothing. */
    public static void bondIntoAFullPartyIsRefused(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        for (int i = 0; i < 3; i++) Companions.equip(player, i, bluegill(20f + i));
        ItemStack extra = bluegill(30f);
        player.getInventory().setItem(0, extra.copy());

        Companions.BondResult result = Companions.bond(player, extra.copy());

        helper.assertTrue(result == Companions.BondResult.PARTY_FULL, "bond result " + result);
        helper.assertTrue(grill24.fishtastic.network.CompanionFeedbackPacket.of(result)
                == grill24.fishtastic.network.CompanionFeedbackPacket.Outcome.PARTY_FULL, "the player must be told the party is full");
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(0), extra), "the fish must stay in the inventory");
        for (int i = 0; i < 3; i++) {
            helper.assertTrue(ItemSizeHelper.getSize(CompanionStorage.get(player, i)) == 20f + i, "slot " + i + " changed");
        }
        helper.succeed();
    }

    /** Naming a slot replaces its fish, which comes back to the inventory. */
    public static void bondIntoANamedSlotReturnsItsFish(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack old = bluegill(20f), lead = bluegill(22f), next = bluegill(30f);
        Companions.equip(player, 0, lead.copy());
        Companions.equip(player, 2, old.copy());
        player.getInventory().setItem(0, next.copy());

        Companions.BondResult result = Companions.bond(player, next.copy(), 2);

        helper.assertTrue(result == Companions.BondResult.BONDED, "bond result " + result);
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 2), next), "slot 2 " + CompanionStorage.get(player, 2));
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 0), lead), "the lead must not move");
        List<ItemStack> held = allFish(player);
        helper.assertTrue(held.size() == 1 && ItemStack.matches(held.get(0), old), "the replaced fish must come back, got " + held);
        helper.succeed();
    }

    /** Party spec §3 (D1): three of one species, from one stack or one pile, fill the party — a school. */
    public static void threeOfOneSpeciesFillTheParty(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack fish = bluegill(18f), piled = bluegill(24f);
        player.getInventory().setItem(0, fish.copyWithCount(2));
        player.getInventory().setItem(1, pileOf(piled, bluegill(40f)));

        helper.assertTrue(Companions.bond(player, fish.copy()) == Companions.BondResult.BONDED, "first from the stack");
        helper.assertTrue(Companions.bond(player, fish.copy()) == Companions.BondResult.BONDED, "second from the stack");
        helper.assertTrue(Companions.bond(player, piled.copy()) == Companions.BondResult.BONDED, "third from the pile");

        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player, 0), fish)
                && ItemStack.matches(CompanionStorage.get(player, 1), fish)
                && ItemStack.matches(CompanionStorage.get(player, 2), piled), "party " + CompanionStorage.party(player));
        List<ItemStack> held = allFish(player);
        helper.assertTrue(held.size() == 1 && ItemSizeHelper.getSize(held.get(0)) == 40f,
                "only the 40 cm fish stays (the emptied pile collapses into it), got " + held);
        helper.succeed();
    }

    /**
     * {@code respawn} reproduces the loader's own death-respawn hand-off from the old player to the
     * new one: NeoForge copies attachments inside {@code restoreFrom} (its Clone event), Fabric
     * afterwards on {@code ServerPlayerEvents.AFTER_RESPAWN}.
     */
    public static void companionIsKeptOnDeath(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer,
                                              java.util.function.BiConsumer<ServerPlayer, ServerPlayer> respawn) {
        ServerPlayer before = setUp(helper, mockPlayer);
        ItemStack fish = bluegill(25f), last = bluegill(31f);
        Companions.equip(before, 0, fish.copy());
        Companions.equip(before, 2, last.copy());
        ServerPlayer after = mockPlayer.get();
        CompanionStorage.clear(after);

        respawn.accept(before, after);

        helper.assertTrue(ItemStack.matches(CompanionStorage.get(after, 0), fish)
                        && CompanionStorage.get(after, 1).isEmpty()
                        && ItemStack.matches(CompanionStorage.get(after, 2), last),
                "the whole party must survive death, slots in place, got " + CompanionStorage.party(after));
        helper.succeed();
    }
}
