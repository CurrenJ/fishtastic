package grill24.fishtastic.gametest;

import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionStorage;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.function.Supplier;

/**
 * Companion party storage through the platform attachment. Pure GameTestHelper consumers — no
 * platform annotations here; the mock player comes from the shared supplier.
 */
public final class CompanionStorageGameTests {
    private CompanionStorageGameTests() {}

    /** An untouched player has an empty party; slots set, read back and clear through the attachment. */
    public static void partyStorageSetsReadsAndClears(GameTestHelper helper, Supplier<ServerPlayer> mockPlayer) {
        ServerPlayer player = mockPlayer.get();
        helper.assertTrue(CompanionStorage.party(player).isEmpty(), "an untouched player must have an empty party");

        ItemStack bluegill = new ItemStack(FishtasticItems.BLUEGILL.value());
        CompanionStorage.set(player, 1, bluegill);
        helper.assertTrue(CompanionStorage.lead(player).isEmpty(), "slot 0 must stay empty when slot 1 is set");
        helper.assertTrue(CompanionStorage.get(player, 1).is(bluegill.getItem()), "slot 1 must hold the bluegill");
        helper.assertTrue(CompanionStorage.party(player).size() == 1, "party must report exactly one fish");
        helper.assertTrue(CompanionStorage.party(player).firstEmpty() == 0, "first empty slot must be the lead");

        CompanionStorage.clear(player);
        helper.assertTrue(CompanionStorage.party(player).isEmpty(), "clear must empty the party");
        helper.assertTrue(CompanionStorage.party(player).matches(CompanionParty.EMPTY), "a cleared party must match EMPTY");
        helper.succeed();
    }
}
