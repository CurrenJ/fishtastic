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
import net.minecraft.world.item.ItemStackTemplate;
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
        CompanionStorage.set(player, ItemStack.EMPTY);
        return player;
    }

    private static ItemStack bluegill(float sizeCm) {
        return ItemSizeHelper.setSize(new ItemStack(FishtasticItems.BLUEGILL.value()), sizeCm);
    }

    private static ItemStack pileOf(ItemStack... fish) {
        ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
        FishtasticItemData.setBundleContents(pile, new BundleContents(
                java.util.Arrays.stream(fish).map(ItemStackTemplate::fromNonEmptyStack).toList()));
        return pile;
    }

    /** Every fish the player holds, loose or piled, as single stacks. */
    private static List<ItemStack> allFish(ServerPlayer player) {
        List<ItemStack> fish = new java.util.ArrayList<>();
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
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
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player), fish), "companion " + CompanionStorage.get(player));
        helper.assertTrue(player.getInventory().getItem(4).isEmpty(), "the fish must leave the inventory");
        helper.succeed();
    }

    public static void bondTakesTheFishFromInsideAPile(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack small = bluegill(12f), wanted = bluegill(25f), big = bluegill(31f);
        player.getInventory().setItem(0, pileOf(small, wanted, big));

        Companions.BondResult result = Companions.bond(player, wanted.copy());

        helper.assertTrue(result == Companions.BondResult.BONDED, "bond result " + result);
        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player), wanted), "companion " + CompanionStorage.get(player));
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
        helper.assertTrue(CompanionStorage.get(player).isEmpty(), "nothing may be equipped");
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(2), carried), "the carried fish must stay put");
        helper.succeed();
    }

    public static void bondRefusesSomethingThatIsNotAFish(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        player.getInventory().setItem(0, new ItemStack(Items.DIRT));

        Companions.BondResult result = Companions.bond(player, new ItemStack(Items.DIRT));

        helper.assertTrue(result == Companions.BondResult.NOT_A_FISH, "bond result " + result);
        helper.assertTrue(CompanionStorage.get(player).isEmpty() && player.getInventory().getItem(0).is(Items.DIRT),
                "nothing may move");
        helper.succeed();
    }

    public static void unequipReturnsTheIdenticalFish(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack fish = bluegill(25f);
        fish.set(DataComponents.CUSTOM_NAME, Component.literal("Gilbert"));
        player.getInventory().setItem(0, fish.copy());
        Companions.bond(player, fish.copy());

        boolean unequipped = Companions.unequip(player);

        helper.assertTrue(unequipped, "unequip must report the companion it returned");
        helper.assertTrue(CompanionStorage.get(player).isEmpty(), "the slot must be empty afterwards");
        List<ItemStack> held = allFish(player);
        helper.assertTrue(held.size() == 1 && ItemStack.matches(held.get(0), fish),
                "the exact fish (name and size) must come back, got " + held);
        helper.succeed();
    }

    public static void unequipWithAFullInventoryDropsTheFish(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        Companions.equip(player, bluegill(25f));
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            player.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
        }
        int before = nearbyBluegills(player);

        Companions.unequip(player);

        helper.assertTrue(CompanionStorage.get(player).isEmpty(), "the slot must be empty afterwards");
        helper.assertTrue(nearbyBluegills(player) - before == 1,
                "a companion that doesn't fit must drop at the player's feet, delta=" + (nearbyBluegills(player) - before));
        helper.succeed();
    }

    public static void bondingASecondFishReturnsTheFirst(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = setUp(helper, mockPlayer);
        ItemStack first = bluegill(20f), second = bluegill(30f);
        player.getInventory().setItem(0, first.copy());
        player.getInventory().setItem(1, second.copy());
        Companions.bond(player, first.copy());

        Companions.bond(player, second.copy());

        helper.assertTrue(ItemStack.matches(CompanionStorage.get(player), second), "companion " + CompanionStorage.get(player));
        List<ItemStack> held = allFish(player);
        helper.assertTrue(held.size() == 1 && ItemStack.matches(held.get(0), first),
                "the first companion must come back, got " + held);
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
        ItemStack fish = bluegill(25f);
        Companions.equip(before, fish.copy());
        ServerPlayer after = mockPlayer.get();
        CompanionStorage.set(after, ItemStack.EMPTY);

        respawn.accept(before, after);

        helper.assertTrue(ItemStack.matches(CompanionStorage.get(after), fish),
                "the companion must survive death, got " + CompanionStorage.get(after));
        helper.succeed();
    }
}
