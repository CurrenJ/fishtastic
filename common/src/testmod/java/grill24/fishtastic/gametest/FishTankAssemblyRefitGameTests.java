package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticBlocks;
import grill24.fishtastic.FishtasticDataComponents;
import grill24.fishtastic.FishtasticItemData;
import grill24.fishtastic.blockentity.FishTankAssemblyBlockEntity;
import grill24.fishtastic.component.FishTankMaterials;
import grill24.fishtastic.fishtank.FishTankShape;
import grill24.fishtastic.menu.FishTankAssemblyMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Supplier;

/**
 * Coverage for the Fish Tank Assembly menu's refit mode: a fish tank item dropped into the tank slot
 * can have its shape and/or materials changed, and the output stays blocked until something differs.
 * Drives a real {@link FishTankAssemblyMenu} opened on a real block entity, and takes the result
 * through the real {@code clicked} path so consume/refund behaviour is the shipped behaviour.
 */
public final class FishTankAssemblyRefitGameTests {

    private FishTankAssemblyRefitGameTests() {}

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static final FishTankMaterials ORIGINAL =
            new FishTankMaterials(Blocks.OAK_PLANKS, Blocks.SAND, Blocks.GLASS);

    private record Fixture(FishTankAssemblyBlockEntity be, FishTankAssemblyMenu menu, ServerPlayer player) {
        ItemStack result() {
            return menu.slots.get(FishTankAssemblyMenu.RESULT_SLOT).getItem();
        }

        void stage(int slot, Block block) {
            be.setItem(slot, new ItemStack(block));
            menu.slotsChanged(be);
        }

        void putTank(ItemStack tank) {
            // Through the real click path so the menu re-seeds the selected shape from the tank.
            menu.setCarried(tank);
            menu.clicked(FishTankAssemblyMenu.TANK_SLOT, 0, ContainerInput.PICKUP, player);
        }

        void takeResult() {
            menu.clicked(FishTankAssemblyMenu.RESULT_SLOT, 0, ContainerInput.PICKUP, player);
        }
    }

    private static Fixture open(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        helper.setBlock(POS, FishtasticBlocks.FISH_TANK_ASSEMBLY.value());
        FishTankAssemblyBlockEntity be = helper.getBlockEntity(POS, FishTankAssemblyBlockEntity.class);
        ServerPlayer player = mockPlayer.get();
        player.getInventory().clearContent();
        player.openMenu(be);
        helper.assertTrue(player.containerMenu instanceof FishTankAssemblyMenu,
                "Setup: opening the assembly block must open a FishTankAssemblyMenu");
        return new Fixture(be, (FishTankAssemblyMenu) player.containerMenu, player);
    }

    private static ItemStack tank(FishTankShape shape, FishTankMaterials materials) {
        ItemStack stack = new ItemStack(FishtasticBlocks.FISH_TANK.value());
        FishtasticItemData.set(stack, FishtasticDataComponents.FISH_TANK_SHAPE, shape);
        FishtasticItemData.set(stack, FishtasticDataComponents.FISH_TANK_MATERIALS, materials);
        return stack;
    }

    private static FishTankShape shapeOf(ItemStack stack) {
        return FishtasticItemData.getOrDefault(stack, FishtasticDataComponents.FISH_TANK_SHAPE, FishTankShape.STANDARD);
    }

    private static FishTankMaterials materialsOf(ItemStack stack) {
        return FishtasticItemData.getOrDefault(stack, FishtasticDataComponents.FISH_TANK_MATERIALS, FishTankMaterials.defaultMaterials());
    }

    private static int countInInventory(ServerPlayer player, Block block) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.is(block.asItem())) total += stack.getCount();
        }
        return total;
    }

    /** Placing a tank selects its shape, and with nothing else changed the output stays blocked. */
    public static void refitOutputIsBlockedUntilSomethingChanges(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        Fixture f = open(helper, mockPlayer);
        f.putTank(tank(FishTankShape.SKYLIGHT, ORIGINAL));

        helper.assertTrue(f.menu.getShape() == FishTankShape.SKYLIGHT,
                "Inserting a tank must select its shape, got " + f.menu.getShape());
        helper.assertTrue(f.result().isEmpty(), "A tank with nothing changed must produce no result");

        // Staging the block the tank already has is not a change.
        f.stage(FishTankAssemblyMenu.FRAME_SLOT, Blocks.OAK_PLANKS);
        helper.assertTrue(f.result().isEmpty(), "Staging the tank's existing frame must still produce no result");

        f.stage(FishTankAssemblyMenu.FRAME_SLOT, Blocks.SPRUCE_PLANKS);
        helper.assertTrue(!f.result().isEmpty(), "Staging a different frame must produce a result");
        helper.succeed();
    }

    /** A materials-only refit replaces just the staged part and refunds the block it replaced. */
    public static void refitReplacesOnlyTheStagedMaterialAndRefundsTheOldOne(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        Fixture f = open(helper, mockPlayer);
        f.putTank(tank(FishTankShape.STANDARD, ORIGINAL));
        f.stage(FishTankAssemblyMenu.FRAME_SLOT, Blocks.SPRUCE_PLANKS);

        ItemStack preview = f.result();
        FishTankMaterials m = materialsOf(preview);
        helper.assertTrue(m.frame() == Blocks.SPRUCE_PLANKS && m.sand() == Blocks.SAND && m.glass() == Blocks.GLASS,
                "Only the frame should change, got " + m);
        helper.assertTrue(shapeOf(preview) == FishTankShape.STANDARD, "Shape must be untouched");

        f.takeResult();

        ItemStack taken = f.menu.getCarried();
        helper.assertTrue(materialsOf(taken).frame() == Blocks.SPRUCE_PLANKS, "Taken tank must carry the new frame");
        helper.assertTrue(f.be.getItem(FishTankAssemblyBlockEntity.TANK_SLOT).isEmpty(), "The input tank must be consumed");
        helper.assertTrue(f.be.getItem(FishTankAssemblyBlockEntity.FRAME_SLOT).isEmpty(), "The staged frame must be consumed");
        helper.assertTrue(countInInventory(f.player, Blocks.OAK_PLANKS) == 1,
                "The replaced oak planks must be refunded, got " + countInInventory(f.player, Blocks.OAK_PLANKS));
        helper.succeed();
    }

    /** A shape-only refit changes the shape and consumes no materials. */
    public static void refitShapeOnlyChangesShapeAndConsumesNoMaterials(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        Fixture f = open(helper, mockPlayer);
        f.putTank(tank(FishTankShape.STANDARD, ORIGINAL));
        f.menu.setShape(FishTankShape.SKYLIGHT);

        ItemStack preview = f.result();
        helper.assertTrue(shapeOf(preview) == FishTankShape.SKYLIGHT, "Result must carry the newly selected shape");
        helper.assertTrue(materialsOf(preview).equals(ORIGINAL), "Materials must be untouched, got " + materialsOf(preview));

        f.takeResult();

        helper.assertTrue(shapeOf(f.menu.getCarried()) == FishTankShape.SKYLIGHT, "Taken tank must be the new shape");
        helper.assertTrue(f.be.getItem(FishTankAssemblyBlockEntity.TANK_SLOT).isEmpty(), "The input tank must be consumed");
        helper.assertTrue(countInInventory(f.player, Blocks.OAK_PLANKS) == 0, "Nothing changed material-wise, so nothing is refunded");
        helper.succeed();
    }

    /** Shape and every material can change in one refit, each refunded. */
    public static void refitCombinedChangesEverythingAndRefundsEachReplacedBlock(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        Fixture f = open(helper, mockPlayer);
        f.putTank(tank(FishTankShape.STANDARD, ORIGINAL));
        f.menu.setShape(FishTankShape.SKYLIGHT);
        f.stage(FishTankAssemblyMenu.FRAME_SLOT, Blocks.SPRUCE_PLANKS);
        f.stage(FishTankAssemblyMenu.SAND_SLOT, Blocks.RED_SAND);
        f.stage(FishTankAssemblyMenu.GLASS_SLOT, Blocks.BLUE_STAINED_GLASS);

        f.takeResult();

        ItemStack taken = f.menu.getCarried();
        FishTankMaterials m = materialsOf(taken);
        helper.assertTrue(shapeOf(taken) == FishTankShape.SKYLIGHT, "Shape must change");
        helper.assertTrue(m.frame() == Blocks.SPRUCE_PLANKS && m.sand() == Blocks.RED_SAND && m.glass() == Blocks.BLUE_STAINED_GLASS,
                "All three parts must change, got " + m);
        helper.assertTrue(countInInventory(f.player, Blocks.OAK_PLANKS) == 1
                        && countInInventory(f.player, Blocks.SAND) == 1
                        && countInInventory(f.player, Blocks.GLASS) == 1,
                "Each replaced block must be refunded");
        helper.succeed();
    }

    /** Components other than shape/materials (e.g. a custom name) survive the refit. */
    public static void refitPreservesUnrelatedComponents(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        Fixture f = open(helper, mockPlayer);
        ItemStack named = tank(FishTankShape.STANDARD, ORIGINAL);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("Reef Tank"));
        f.putTank(named);
        f.stage(FishTankAssemblyMenu.GLASS_SLOT, Blocks.BLUE_STAINED_GLASS);

        Component name = f.result().get(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        helper.assertTrue(name != null && name.getString().equals("Reef Tank"),
                "The custom name must carry over to the refit result, got " + name);
        helper.succeed();
    }

    /** A shape that never renders sand ignores the sand slot: no result from it, nothing consumed, no refund. */
    public static void refitToSandlessShapeIgnoresTheSandSlot(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        Fixture f = open(helper, mockPlayer);
        f.putTank(tank(FishTankShape.STANDARD, ORIGINAL));
        f.menu.setShape(FishTankShape.VITRINE);
        f.stage(FishTankAssemblyMenu.SAND_SLOT, Blocks.RED_SAND);

        ItemStack preview = f.result();
        helper.assertTrue(shapeOf(preview) == FishTankShape.VITRINE, "Shape change must still apply");
        helper.assertTrue(materialsOf(preview).sand() == Blocks.SAND, "The sand slot must be ignored for a sand-less shape");

        f.takeResult();

        helper.assertTrue(!f.be.getItem(FishTankAssemblyBlockEntity.SAND_SLOT).isEmpty(),
                "The ignored sand block must not be consumed");
        helper.assertTrue(countInInventory(f.player, Blocks.SAND) == 0, "No sand refund for an ignored sand slot");
        helper.succeed();
    }

    /** Leaving a sand-less tank must not refund its placeholder sand. */
    public static void refitFromSandlessShapeDoesNotRefundPlaceholderSand(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        Fixture f = open(helper, mockPlayer);
        f.putTank(tank(FishTankShape.VITRINE, ORIGINAL));
        f.menu.setShape(FishTankShape.STANDARD);
        f.stage(FishTankAssemblyMenu.SAND_SLOT, Blocks.RED_SAND);

        f.takeResult();

        helper.assertTrue(materialsOf(f.menu.getCarried()).sand() == Blocks.RED_SAND, "The new sand must be applied");
        helper.assertTrue(countInInventory(f.player, Blocks.SAND) == 0,
                "The old sand was only a placeholder for VITRINE and must not be refunded");
        helper.succeed();
    }

    /** With no tank in the slot the menu still crafts a brand-new tank exactly as before. */
    public static void craftingWithoutATankIsUnchanged(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        Fixture f = open(helper, mockPlayer);
        f.stage(FishTankAssemblyMenu.FRAME_SLOT, Blocks.OAK_PLANKS);
        f.stage(FishTankAssemblyMenu.SAND_SLOT, Blocks.SAND);
        f.stage(FishTankAssemblyMenu.GLASS_SLOT, Blocks.GLASS);

        helper.assertTrue(materialsOf(f.result()).equals(ORIGINAL), "Crafting must still stamp exactly the staged materials");

        f.takeResult();

        helper.assertTrue(f.be.getItem(FishTankAssemblyBlockEntity.FRAME_SLOT).isEmpty()
                        && f.be.getItem(FishTankAssemblyBlockEntity.GLASS_SLOT).isEmpty(),
                "Crafting must consume the inputs");
        helper.succeed();
    }

    /** The refit slot only accepts fish tank items. */
    public static void tankSlotOnlyAcceptsFishTanks(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        Fixture f = open(helper, mockPlayer);
        var slot = f.menu.slots.get(FishTankAssemblyMenu.TANK_SLOT);
        helper.assertTrue(slot.mayPlace(new ItemStack(FishtasticBlocks.FISH_TANK.value())), "A fish tank must be accepted");
        helper.assertTrue(!slot.mayPlace(new ItemStack(Blocks.OAK_PLANKS)), "Other blocks must be rejected");
        helper.succeed();
    }
}
