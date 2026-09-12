package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.blockentity.FishTankBlockEntity;
import grill24.fishtastic.fishtank.TankEntryKind;
import grill24.fishtastic.network.FishtasticPackets;
import grill24.fishtastic.network.RemoveTankEntryPacket;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Supplier;

/**
 * Regression coverage for {@link RemoveTankEntryPacket#giveOrDrop}, the delivery path used when a
 * player removes an entry from the fish tank browser GUI (see docs/fish-tank-interaction-redesign.md).
 * Mirrors {@code ShopEntryGameTests}'s full-inventory fixture (relocate out of the mock player's
 * shared (0,0,0) spot, force survival mode so the creative-mode "silently zero the stack" shortcut
 * in {@code Inventory.add} doesn't mask a real leak, fill every main slot, measure dropped-item
 * delta rather than an absolute count).
 */
public final class RemoveTankEntryPacketGameTests {

    private RemoveTankEntryPacketGameTests() {}

    private static void forceSurvivalMode(ServerPlayer player) {
        player.getAbilities().instabuild = false;
    }

    private static void relocateIntoTestStructure(GameTestHelper helper, ServerPlayer player) {
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    private static void fillInventoryCompletely(ServerPlayer player, net.minecraft.world.item.Item fillerItem) {
        for (int i = 0; i < player.getInventory().getNonEquipmentItems().size(); i++) {
            player.getInventory().setItem(i, new ItemStack(fillerItem, fillerItem.getDefaultMaxStackSize()));
        }
    }

    private static int countNearbyItemsOf(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (Entity entity : ((ServerLevel) player.level()).getAllEntities()) {
            if (entity instanceof ItemEntity itemEntity && itemEntity.getItem().is(item)) {
                total += itemEntity.getItem().getCount();
            }
        }
        return total;
    }

    private static ItemStack sizedFish() {
        // Any item with the ITEM_SIZE component is pile-eligible per PileOfFishItem#canInsertInPile,
        // regardless of the fishtastic:fish tag — matches how a real caught fish is stamped.
        return ItemSizeHelper.setSize(new ItemStack(Items.COD, 1), 30f);
    }

    /**
     * Regression test for the reported bug: removing a fish from a completely full inventory made
     * it vanish instead of dropping at the player's feet. giveOrDrop's fish branch goes through
     * PileOfFishItem#fillOrCreatePiles (piling) rather than the plain Inventory.add() the
     * non-fish branch (and PurchaseShopEntryPacket#grantRewards) use — this exercises that path
     * specifically, at both member counts fillOrCreatePiles ever creates a pile at (1, then 2+).
     */
    public static void giveOrDropDropsFishWhenInventoryIsFull(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        relocateIntoTestStructure(helper, player);
        forceSurvivalMode(player);
        fillInventoryCompletely(player, Items.DIRT);
        int baseline = countNearbyItemsOf(player, FishtasticItems.PILE_OF_FISH.value());
        int baselineLoose = countNearbyItemsOf(player, Items.COD);

        RemoveTankEntryPacket.giveOrDrop(player, sizedFish());

        int pilesInInventory = 0;
        int looseInInventory = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(FishtasticItems.PILE_OF_FISH.value())) pilesInInventory++;
            if (stack.is(Items.COD)) looseInInventory++;
        }
        helper.assertTrue(pilesInInventory == 0 && looseInInventory == 0,
            "Sanity check: a completely full inventory must have no room for the fish, got "
                + pilesInInventory + " piles and " + looseInInventory + " loose in inventory");

        int pileDelta = countNearbyItemsOf(player, FishtasticItems.PILE_OF_FISH.value()) - baseline;
        int looseDelta = countNearbyItemsOf(player, Items.COD) - baselineLoose;
        helper.assertTrue(pileDelta > 0 || looseDelta > 0,
            "A fish that doesn't fit in a full inventory must be dropped at the player's feet "
                + "(as a pile or loose) rather than vanishing, got pileDelta=" + pileDelta
                + " looseDelta=" + looseDelta);
        helper.succeed();
    }

    /**
     * Regression test for the actual reported bug: it only reproduces in creative mode.
     * {@code GameTestHelper.makeMockServerPlayerInLevel} starts the mock player with
     * {@code abilities.instabuild == true} by default (creative-like) — deliberately left in
     * place here, unlike the sibling test above, since that default IS the bug condition.
     * {@code Inventory.add} special-cases {@code hasInfiniteMaterials()}: once nothing fits, it
     * zeroes the stack's count outright instead of leaving a leftover to drop, on the (here wrong)
     * assumption the caller doesn't need "their" item back. Without the instabuild-suppression fix
     * in {@code giveOrDrop}, this fish is simply gone — not in inventory, not on the ground.
     */
    public static void giveOrDropDropsFishWhenInventoryIsFullInCreativeMode(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        relocateIntoTestStructure(helper, player);
        helper.assertTrue(player.getAbilities().instabuild, "Setup: mock player must start creative for this to be the bug scenario");
        fillInventoryCompletely(player, Items.DIRT);
        int baseline = countNearbyItemsOf(player, FishtasticItems.PILE_OF_FISH.value());
        int baselineLoose = countNearbyItemsOf(player, Items.COD);

        RemoveTankEntryPacket.giveOrDrop(player, sizedFish());

        int pilesInInventory = 0;
        int looseInInventory = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(FishtasticItems.PILE_OF_FISH.value())) pilesInInventory++;
            if (stack.is(Items.COD)) looseInInventory++;
        }
        int pileDelta = countNearbyItemsOf(player, FishtasticItems.PILE_OF_FISH.value()) - baseline;
        int looseDelta = countNearbyItemsOf(player, Items.COD) - baselineLoose;

        helper.assertTrue(pilesInInventory > 0 || looseInInventory > 0 || pileDelta > 0 || looseDelta > 0,
            "A fish removed while creative with a full inventory must land in inventory or drop at "
                + "the player's feet, not vanish. inventoryPiles=" + pilesInInventory
                + " inventoryLoose=" + looseInInventory + " pileDelta=" + pileDelta + " looseDelta=" + looseDelta);
        // The instabuild-suppression in giveOrDrop must be transient — it must not leak the
        // player's actual creative status.
        helper.assertTrue(player.getAbilities().instabuild, "giveOrDrop must restore instabuild afterward, not leave the player in survival");
        helper.succeed();
    }

    /** With free space, the fish must go straight into the inventory (as a new pile) rather than being dropped. */
    public static void giveOrDropDeliversFishNormallyWhenInventoryHasSpace(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        int baseline = countNearbyItemsOf(player, FishtasticItems.PILE_OF_FISH.value());

        RemoveTankEntryPacket.giveOrDrop(player, sizedFish());

        int pilesInInventory = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(FishtasticItems.PILE_OF_FISH.value())) pilesInInventory++;
        }
        helper.assertTrue(pilesInInventory == 1,
            "With room available, the fish must land in inventory as a new pile, got " + pilesInInventory + " piles");
        helper.assertTrue(countNearbyItemsOf(player, FishtasticItems.PILE_OF_FISH.value()) == baseline,
            "With room available, nothing new should be dropped on the ground");
        helper.succeed();
    }

    /**
     * A second fish removed right after the first must fold into the existing pile rather than
     * spawning its own slot — the actual "form a pile" behavior requested alongside the
     * full-inventory fix.
     */
    public static void giveOrDropPilesASecondFishIntoTheExistingPile(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();

        RemoveTankEntryPacket.giveOrDrop(player, sizedFish());
        RemoveTankEntryPacket.giveOrDrop(player, sizedFish());

        int pilesInInventory = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(FishtasticItems.PILE_OF_FISH.value())) pilesInInventory++;
        }
        helper.assertTrue(pilesInInventory == 1,
            "Two fish removed back to back must share one pile slot, got " + pilesInInventory + " pile slots");
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // Full pipeline — real FishTankBrowserMenu open, real handleClientToServer, real pickup
    // -------------------------------------------------------------------------

    private static final BlockPos FLOOR = new BlockPos(1, 0, 1);
    private static final BlockPos TANK_POS = new BlockPos(1, 1, 1);

    /** Runs the packet handler's enqueued work immediately, synchronously — matches the real
     * server's single-threaded execute() closely enough for a test that never yields mid-handler. */
    private static FishtasticPackets.IPacketContext syncContext(ServerPlayer player) {
        return new FishtasticPackets.IPacketContext() {
            @Override
            public Player getPlayer() {
                return player;
            }

            @Override
            public void enqueueWork(Runnable runnable) {
                runnable.run();
            }
        };
    }

    /**
     * Reproduces the exact reported sequence: player has a completely full inventory, opens the
     * REAL {@link grill24.fishtastic.menu.FishTankBrowserMenu} (not a bare call to
     * {@code giveOrDrop} — so this exercises {@code handleClientToServer}'s own guard checks and
     * the real {@code fishTank.removeItem} call too), drops one stack to free a slot, picks that
     * exact item back up via the real {@link ItemEntity} pickup path while the menu is still open,
     * and only then sends the remove-fish packet. If the fish vanishes instead of landing in
     * inventory or dropping at the player's feet, this fails.
     */
    public static void giveOrDropThroughRealMenuAfterPickupWhileOpenDoesNotLoseTheFish(
            GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(TANK_POS, FishtasticBlocks.FISH_TANK.value());
        FishTankBlockEntity tank = helper.getBlockEntity(TANK_POS, FishTankBlockEntity.class);
        ItemStack fishInTank = sizedFish();
        helper.assertTrue(tank.addItem(fishInTank), "Setup: inserting the test fish into the tank must succeed");

        ServerPlayer player = mockPlayer.get();
        relocateIntoTestStructure(helper, player);
        forceSurvivalMode(player);
        fillInventoryCompletely(player, Items.DIRT);

        // Real vanilla menu-open path — sets player.containerMenu to an actual FishTankBrowserMenu,
        // exactly like right-clicking the tank empty-handed does.
        player.openMenu(tank);

        // Free exactly one slot...
        ItemStack toDrop = player.getInventory().getItem(0).copy();
        player.getInventory().setItem(0, ItemStack.EMPTY);
        // ...then pick it back up through the real ItemEntity path while the menu is still open,
        // refilling the inventory to genuinely full — the step the bug report pinned as the trigger.
        BlockPos playerPos = BlockPos.containing(player.getX(), player.getY(), player.getZ());
        ItemEntity dropped = new ItemEntity((ServerLevel) player.level(),
                playerPos.getX() + 0.5, playerPos.getY(), playerPos.getZ() + 0.5, toDrop.copy());
        dropped.setNoPickUpDelay();
        player.level().addFreshEntity(dropped);
        dropped.playerTouch(player);
        // Not dropped.getItem().isEmpty() — playerTouch's own success branch resets its local
        // stack's count back to the original after zeroing it for the empty-check, so the entity's
        // synced item data reads as "full" again even once picked up. isRemoved() (it calls
        // discard() on success) is the real signal.
        helper.assertTrue(dropped.isRemoved(), "Setup: the real pickup must actually succeed (entity discarded)");

        int freeSlots = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.isEmpty()) freeSlots++;
        }
        helper.assertTrue(freeSlots == 0,
            "Setup: inventory must be genuinely full again after the real pickup, got " + freeSlots + " free slots");

        int baselinePileDrops = countNearbyItemsOf(player, FishtasticItems.PILE_OF_FISH.value());
        int baselineLooseDrops = countNearbyItemsOf(player, fishInTank.getItem());

        RemoveTankEntryPacket packet = new RemoveTankEntryPacket(tank.getBlockPos(), TankEntryKind.FISH, 0);
        RemoveTankEntryPacket.handleClientToServer(packet, syncContext(player));

        helper.assertTrue(tank.getItem(0).isEmpty(), "Sanity check: the fish must actually have left the tank");

        // "When we close inv, ... the extracted fish item is deleted" — close the browser menu
        // (the actual trigger named in the report) before reading final state, in case closing
        // itself is where something goes wrong rather than the give/drop call.
        player.closeContainer();

        int pilesInInventory = 0;
        int looseInInventory = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(FishtasticItems.PILE_OF_FISH.value())) pilesInInventory++;
            if (stack.is(fishInTank.getItem())) looseInInventory++;
        }
        int pileDropDelta = countNearbyItemsOf(player, FishtasticItems.PILE_OF_FISH.value()) - baselinePileDrops;
        int looseDropDelta = countNearbyItemsOf(player, fishInTank.getItem()) - baselineLooseDrops;

        helper.assertTrue(pilesInInventory > 0 || looseInInventory > 0 || pileDropDelta > 0 || looseDropDelta > 0,
            "The fish removed through the real menu/packet path after a mid-open pickup must land "
                + "in inventory or drop at the player's feet, not vanish. inventoryPiles=" + pilesInInventory
                + " inventoryLoose=" + looseInInventory + " pileDropDelta=" + pileDropDelta
                + " looseDropDelta=" + looseDropDelta);
        helper.succeed();
    }
}
