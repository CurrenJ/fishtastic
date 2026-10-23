package grill24.fishtastic.gametest;

import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.FishtasticItems;
import grill24.fishtastic.companion.CompanionGroups;
import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.util.ItemSizeHelper;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * How the game groups a party (docs/fish-companions.md): {@link CompanionGroups} maps each
 * slot to a sim member and reports what {@code :fishsim}'s {@code PartyGrouping} decided, against the
 * real fish profiles. The leader choice belongs to PartyGrouping, so these tests only require that
 * the leader is one of the group's own slots.
 */
public final class CompanionGroupsGameTests {
    private CompanionGroupsGameTests() {}

    private static ItemStack fish(Holder holder, float sizeCm) {
        return ItemSizeHelper.withSize(new ItemStack(holder.item()), sizeCm);
    }

    /** A tiny indirection so the species list reads as names. */
    private record Holder(Item item) {}

    private static Holder bluegill() { return new Holder(FishtasticItems.BLUEGILL.value()); }
    private static Holder starfish() { return new Holder(FishtasticItems.STARFISH.value()); }
    private static Holder nudibranch() { return new Holder(FishtasticItems.TRAPANIA_SCURRA.value()); }
    private static Holder gardenEel() { return new Holder(FishtasticItems.GARDEN_EEL.value()); }
    private static Holder glassSquid() { return new Holder(FishtasticItems.GLASS_SQUID.value()); }
    private static Holder bridleShiner() { return new Holder(FishtasticItems.BRIDLE_SHINER.value()); }

    private static List<CompanionGroups.Group> groups(GameTestHelper helper, ItemStack... party) {
        List<ItemStack> slots = new ArrayList<>(List.of(party));
        return CompanionGroups.of(new CompanionParty(slots), helper.getLevel().registryAccess());
    }

    private static void expect(GameTestHelper helper, List<CompanionGroups.Group> groups, String what,
                               CompanionGroups.Kind... kinds) {
        helper.assertTrue(groups.size() == kinds.length, what + ": expected " + kinds.length + " groups, got " + groups);
        for (int i = 0; i < kinds.length; i++) {
            CompanionGroups.Group g = groups.get(i);
            helper.assertTrue(g.kind() == kinds[i], what + ": group " + i + " expected " + kinds[i] + ", got " + g);
            helper.assertTrue(g.slots().contains(g.leader()), what + ": leader outside its group " + g);
        }
    }

    /** Three of one free swimmer are a school, of any sizes (D8); two are a pair. */
    public static void freeSwimmerDuplicatesSchool(GameTestHelper helper) {
        List<CompanionGroups.Group> school = groups(helper, fish(bluegill(), 9f), fish(bluegill(), 16f), fish(bluegill(), 24f));
        expect(helper, school, "3 bluegill", CompanionGroups.Kind.SCHOOL);
        helper.assertTrue(school.get(0).count() == 3 && school.get(0).locomotion() == Locomotion.FREE_SWIM,
                "school of 3 free swimmers, got " + school);
        // D9: the smallest leads, wherever it sits; the rest follow smallest first.
        List<CompanionGroups.Group> bySize = groups(helper, fish(bluegill(), 24f), fish(bluegill(), 9f), fish(bluegill(), 16f));
        helper.assertTrue(bySize.size() == 1 && bySize.get(0).slots().equals(List.of(1, 2, 0)),
                "smallest leads (slot 1), then 16 cm (slot 2), then the 24 cm lead; got " + bySize);
        expect(helper, groups(helper, fish(bluegill(), 12f), fish(bluegill(), 30f)), "2 bluegill", CompanionGroups.Kind.PAIR);
        expect(helper, groups(helper, fish(bluegill(), 12f)), "1 small bluegill", CompanionGroups.Kind.SOLO);
        helper.succeed();
    }

    /** Ground benthics form their profile's formation; a single one walks alone. */
    public static void groundBenthicsFormTheirFormation(GameTestHelper helper) {
        expect(helper, groups(helper, fish(starfish(), 30f), fish(starfish(), 40f), fish(starfish(), 50f)),
                "3 starfish", CompanionGroups.Kind.LINE);
        expect(helper, groups(helper, fish(nudibranch(), 3f), fish(nudibranch(), 4f), fish(nudibranch(), 5f)),
                "3 nudibranchs", CompanionGroups.Kind.CROWD);
        expect(helper, groups(helper, fish(starfish(), 40f), ItemStack.EMPTY, fish(starfish(), 30f)),
                "2 starfish around a gap", CompanionGroups.Kind.PAIR);
        expect(helper, groups(helper, fish(starfish(), 40f)), "1 starfish", CompanionGroups.Kind.SOLO);
        helper.succeed();
    }

    /** Duplicates of a class that doesn't group stay separate members (D7), gathered for the preview. */
    public static void nonGroupingDuplicatesStaySeparate(GameTestHelper helper) {
        List<CompanionGroups.Group> eels = groups(helper, fish(gardenEel(), 40f), fish(gardenEel(), 40f), fish(gardenEel(), 40f));
        expect(helper, eels, "3 garden eels", CompanionGroups.Kind.SEPARATE);
        helper.assertTrue(eels.get(0).count() == 3, "all three eels listed, got " + eels);
        expect(helper, groups(helper, fish(glassSquid(), 20f), fish(glassSquid(), 20f)), "2 squid",
                CompanionGroups.Kind.SEPARATE);
        helper.succeed();
    }

    /**
     * D13: free swimmers of different species form one mixed shoal, led by the smallest by true
     * rendered length (size times render calibration), not by raw size: an 8 cm bridle shiner
     * (calibration 1.18) draws bigger than a 10 cm bluegill (0.89).
     */
    public static void mixedFreeSwimmersShoal(GameTestHelper helper) {
        List<CompanionGroups.Group> shoal = groups(helper, fish(bridleShiner(), 8f), fish(bluegill(), 10f));
        expect(helper, shoal, "shiner + bluegill", CompanionGroups.Kind.SHOAL);
        helper.assertTrue(shoal.get(0).slots().equals(List.of(1, 0)),
                "the bluegill draws smaller and leads, got " + shoal);
        List<CompanionGroups.Group> three = groups(helper, fish(bluegill(), 12f), fish(starfish(), 40f),
                fish(bridleShiner(), 9f));
        expect(helper, three, "bluegill + starfish + shiner", CompanionGroups.Kind.SHOAL, CompanionGroups.Kind.SOLO);
        helper.assertTrue(three.get(0).count() == 2 && three.get(1).leader() == 1, "shoal of slots 0 and 2, got " + three);
        helper.succeed();
    }

    /** Only one free swimmer: different species of other classes never group, one solo each, in slot order. */
    public static void mixedPartyIsAllSolos(GameTestHelper helper) {
        List<CompanionGroups.Group> mixed = groups(helper, fish(bluegill(), 12f), fish(starfish(), 40f), fish(gardenEel(), 40f));
        expect(helper, mixed, "bluegill + starfish + eel",
                CompanionGroups.Kind.SOLO, CompanionGroups.Kind.SOLO, CompanionGroups.Kind.SOLO);
        helper.assertTrue(mixed.get(0).leader() == 0 && mixed.get(1).leader() == 1 && mixed.get(2).leader() == 2,
                "solos in slot order, got " + mixed);
        List<CompanionGroups.Group> school = groups(helper, fish(starfish(), 40f), fish(bluegill(), 12f), fish(bluegill(), 20f));
        expect(helper, school, "starfish + 2 bluegill", CompanionGroups.Kind.SOLO, CompanionGroups.Kind.PAIR);
        helper.succeed();
    }
}
