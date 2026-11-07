package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A Pile of Fish in a furnace or smoker cooks one fish per cycle (FurnacePileOfFishMixin): the
 * pile shrinks by one fish per cooked cod and unpacks once a single entry is left. The furnace is
 * driven by calling {@link AbstractFurnaceBlockEntity#serverTick} directly so tick counts are exact.
 * Right-clicking a campfire with a pile places one fish from it per click (CampfirePileOfFishMixin).
 */
public final class PileOfFishSmeltingGameTests {
    private PileOfFishSmeltingGameTests() {}

    private static final int SMOKER_COOK_TICKS = 100;
    private static final int FURNACE_COOK_TICKS = 200;

    private static ItemStack bluegill(float sizeCm) {
        return ItemSizeHelper.setSize(new ItemStack(FishtasticItems.BLUEGILL.value()), sizeCm);
    }

    private static ItemStack oldTire(float sizeCm) {
        return ItemSizeHelper.setSize(new ItemStack(FishtasticItems.OLD_TIRE.value()), sizeCm);
    }

    private static ItemStack pileOf(ItemStack... items) {
        ItemStack pile = new ItemStack(FishtasticItems.PILE_OF_FISH.value());
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack item : items) contents.add(item.copy());
        FishtasticItemData.setBundleContents(pile, new BundleContents(contents));
        return pile;
    }

    private static int entriesIn(ItemStack pile) {
        BundleContents contents = FishtasticItemData.bundleContents(pile);
        return contents == null ? 0 : contents.size();
    }

    private static AbstractFurnaceBlockEntity placeFurnace(GameTestHelper helper, BlockState state, ItemStack input) {
        BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, state);
        AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getBlockEntity(rel);
        furnace.setItem(1, new ItemStack(Items.COAL, 8));
        furnace.setItem(0, input);
        return furnace;
    }

    private static void tick(GameTestHelper helper, AbstractFurnaceBlockEntity furnace, int ticks) {
        BlockPos pos = furnace.getBlockPos();
        for (int i = 0; i < ticks; i++) {
            AbstractFurnaceBlockEntity.serverTick(helper.getLevel(), pos, helper.getLevel().getBlockState(pos), furnace);
        }
    }

    /**
     * Three bluegills and an old tire in a smoker: one cooked cod per 100-tick cycle with the pile
     * shrinking by one fish each time, until only the tire is left and the pile unpacks into it.
     */
    public static void smokerCooksPileOneFishPerCycle(GameTestHelper helper) {
        AbstractFurnaceBlockEntity smoker = placeFurnace(helper, Blocks.SMOKER.defaultBlockState(),
                pileOf(bluegill(10f), bluegill(11f), bluegill(12f), oldTire(40f)));

        tick(helper, smoker, SMOKER_COOK_TICKS - 1);
        helper.assertTrue(smoker.getItem(2).isEmpty(), "Nothing may be cooked before one full cycle, got " + smoker.getItem(2));

        tick(helper, smoker, 1);
        helper.assertTrue(smoker.getItem(2).is(Items.COOKED_COD) && smoker.getItem(2).getCount() == 1,
                "One cycle must cook exactly one cod, got " + smoker.getItem(2));
        ItemStack input = smoker.getItem(0);
        helper.assertTrue(input.is(FishtasticItems.PILE_OF_FISH.value()) && entriesIn(input) == 3,
                "The pile must lose exactly one fish per cycle, got " + input);

        tick(helper, smoker, 4 * SMOKER_COOK_TICKS);
        helper.assertTrue(smoker.getItem(2).is(Items.COOKED_COD) && smoker.getItem(2).getCount() == 3,
                "All three fish (and only them) must cook, got " + smoker.getItem(2));
        input = smoker.getItem(0);
        helper.assertTrue(input.is(FishtasticItems.OLD_TIRE.value()) && input.getCount() == 1,
                "The pile must unpack into its last, uncookable entry, got " + input);
        helper.succeed();
    }

    /** A pile with no fishtastic:fish in it never starts cooking, and keeps its contents. */
    public static void furnaceIgnoresPileWithoutFish(GameTestHelper helper) {
        AbstractFurnaceBlockEntity furnace = placeFurnace(helper, Blocks.FURNACE.defaultBlockState(),
                pileOf(oldTire(40f), oldTire(45f)));

        tick(helper, furnace, 2 * FURNACE_COOK_TICKS);
        helper.assertTrue(furnace.getItem(2).isEmpty(), "A fishless pile must not cook, got " + furnace.getItem(2));
        helper.assertTrue(furnace.getItem(1).getCount() == 8, "A fishless pile must not burn fuel, got " + furnace.getItem(1));
        ItemStack input = furnace.getItem(0);
        helper.assertTrue(input.is(FishtasticItems.PILE_OF_FISH.value()) && entriesIn(input) == 2,
                "A fishless pile must stay intact, got " + input);
        helper.succeed();
    }

    private static ItemInteractionResult useOnCampfire(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        return helper.getLevel().getBlockState(pos).useItemOn(
                player.getItemInHand(InteractionHand.MAIN_HAND), helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    private static int itemsOnGrill(CampfireBlockEntity campfire) {
        int n = 0;
        for (ItemStack item : campfire.getItems()) if (!item.isEmpty()) n++;
        return n;
    }

    private static ServerPlayer holdingAtCampfire(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer,
                                                  boolean creative, ItemStack held) {
        ServerPlayer player = mockPlayer.get();
        player.getAbilities().instabuild = creative;
        player.getInventory().clearContent();
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        return player;
    }

    /**
     * Two bluegills and an old tire: each click grills one bluegill and takes it out of the pile,
     * the pile unpacking into the tire after the second; the tire alone is then refused.
     */
    public static void campfireTakesOneFishFromPilePerClick(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, Blocks.CAMPFIRE.defaultBlockState());
        BlockPos pos = helper.absolutePos(rel);
        CampfireBlockEntity campfire = (CampfireBlockEntity) helper.getBlockEntity(rel);
        ServerPlayer player = holdingAtCampfire(helper, mockPlayer, false,
                pileOf(bluegill(10f), bluegill(11f), oldTire(40f)));

        ItemInteractionResult first = useOnCampfire(helper, player, pos);
        helper.assertTrue(first.consumesAction(), "Placing fish from a pile must consume the click, got " + first);
        helper.assertTrue(itemsOnGrill(campfire) == 1 && campfire.getItems().get(0).is(FishtasticItems.BLUEGILL.value()),
                "One bluegill must be on the grill, got " + campfire.getItems());
        ItemStack held = player.getMainHandItem();
        helper.assertTrue(held.is(FishtasticItems.PILE_OF_FISH.value()) && entriesIn(held) == 2,
                "The pile must lose exactly one fish, got " + held);

        useOnCampfire(helper, player, pos);
        helper.assertTrue(itemsOnGrill(campfire) == 2, "Two fish must be on the grill, got " + campfire.getItems());
        held = player.getMainHandItem();
        helper.assertTrue(held.is(FishtasticItems.OLD_TIRE.value()),
                "The pile must unpack into its last, uncookable entry, got " + held);

        useOnCampfire(helper, player, pos);
        helper.assertTrue(itemsOnGrill(campfire) == 2, "An old tire must not be grilled, got " + campfire.getItems());
        helper.succeed();
    }

    /** In creative a pile places a fish without losing it, matching vanilla for loose food. */
    public static void campfireKeepsPileIntactInCreative(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        BlockPos rel = new BlockPos(1, 1, 1);
        helper.setBlock(rel, Blocks.CAMPFIRE.defaultBlockState());
        CampfireBlockEntity campfire = (CampfireBlockEntity) helper.getBlockEntity(rel);
        ServerPlayer player = holdingAtCampfire(helper, mockPlayer, true, pileOf(bluegill(10f), bluegill(11f)));

        useOnCampfire(helper, player, helper.absolutePos(rel));
        helper.assertTrue(itemsOnGrill(campfire) == 1, "One fish must be on the grill, got " + campfire.getItems());
        ItemStack held = player.getMainHandItem();
        helper.assertTrue(held.is(FishtasticItems.PILE_OF_FISH.value()) && entriesIn(held) == 2,
                "A creative pile must stay whole, got " + held);
        helper.succeed();
    }
}
