package grill24.fishtastic.data;

import grill24.fishtastic.util.Ids;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * The four vanilla fish. They stay catchable on a bare hook (and keep their {@code fish_profile},
 * whose zones gate where they bite), but they are outside the mod's progression: no Fishopedia
 * entry, no catch records and no quest credit.
 */
public final class VanillaFish {
    private static final Set<Identifier> IDS = Set.of(
            Ids.withDefaultNamespace("cod"),
            Ids.withDefaultNamespace("salmon"),
            Ids.withDefaultNamespace("tropical_fish"),
            Ids.withDefaultNamespace("pufferfish"));

    private VanillaFish() {}

    public static boolean is(Identifier species) {
        return IDS.contains(species);
    }

    public static boolean is(ItemStack stack) {
        return !stack.isEmpty() && is(stack.getItem().builtInRegistryHolder().key().identifier());
    }
}
