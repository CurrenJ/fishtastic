package grill24.fishtastic.companion;

import grill24.FishtasticRegistries;
import grill24.fishsim.core.Locomotion;
import grill24.fishtastic.data.FishAnimationConfig;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How a party's fish group up (docs/fish-companions-party.md §3). Fish of the same species form a
 * group only when the species schools or walks the ground: FREE_SWIM (a pair or a school) or
 * BENTHIC (a pair, or a line or crowd of three, the formation from the species' profile). Duplicates
 * of every other class (GLIDE, DRIFT, ANCHORED) stay separate members (D7). Size never matters (D8).
 *
 * <p>Shared by the server (scene scheduling), the companion menu's party preview and the client's
 * companion wiring, so all three agree on what a party makes.
 */
public final class CompanionGroups {
    private CompanionGroups() {}

    public enum Kind {
        /** One fish of its species in the party. */
        SOLO,
        /** Two of a grouping species. */
        PAIR,
        /** Three FREE_SWIM fish of one species. */
        SCHOOL,
        /** Three BENTHIC fish whose profile walks them in a line. */
        LINE,
        /** Three BENTHIC fish whose profile walks them as a crowd. */
        CROWD,
        /** Two or three of a species that doesn't group; each is its own member. */
        SEPARATE
    }

    /**
     * One species' share of the party. {@code slots} are in slot order; the first is the group's
     * leader, so a group that includes the lead slot is led by the lead.
     */
    public record Group(Item species, Kind kind, Locomotion locomotion, List<Integer> slots) {
        public int leader() { return slots.get(0); }
        public int count() { return slots.size(); }
    }

    /** The party's groups, ordered by their first slot. Empty slots are skipped. */
    public static List<Group> of(CompanionParty party, RegistryAccess registries) {
        List<Group> groups = new ArrayList<>();
        boolean[] used = new boolean[CompanionParty.SIZE];
        for (int i = 0; i < CompanionParty.SIZE; i++) {
            ItemStack first = party.get(i);
            if (first.isEmpty() || used[i]) continue;
            List<Integer> slots = new ArrayList<>();
            for (int j = i; j < CompanionParty.SIZE; j++) {
                if (!used[j] && party.get(j).is(first.getItem())) {
                    slots.add(j);
                    used[j] = true;
                }
            }
            Optional<FishProfile> profile = profileOf(registries, first);
            Locomotion locomotion = locomotionOf(profile);
            groups.add(new Group(first.getItem(), kindOf(locomotion, profile, slots.size()), locomotion, List.copyOf(slots)));
        }
        return groups;
    }

    private static Kind kindOf(Locomotion locomotion, Optional<FishProfile> profile, int count) {
        if (count == 1) return Kind.SOLO;
        boolean groups = locomotion == Locomotion.FREE_SWIM || locomotion == Locomotion.BENTHIC;
        if (!groups) return Kind.SEPARATE;
        if (count == 2) return Kind.PAIR;
        if (locomotion == Locomotion.FREE_SWIM) return Kind.SCHOOL;
        boolean crowd = profile.flatMap(FishProfile::companionGroup)
                .map(c -> c.formation() == FishProfile.CompanionGroupConfig.Formation.CROWD).orElse(false);
        return crowd ? Kind.CROWD : Kind.LINE;
    }

    /** The species' fish profile, if it has one. */
    public static Optional<FishProfile> profileOf(RegistryAccess registries, ItemStack fish) {
        return BuiltInRegistries.ITEM.getResourceKey(fish.getItem())
                .flatMap(key -> registries.lookupOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY)
                        .getOptional(ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, key.identifier())));
    }

    /**
     * The species' locomotion class, resolved from its fish profile the same way the client's
     * renderer resolves it. Deliberately a mirror of {@code TankFlockAdapter.locomotionOf} (which
     * lives in the client renderer and must stay off the server's classpath): pose and locomotion
     * are linked by this table and only this table, so the two copies must be changed together.
     */
    public static Locomotion locomotionOf(RegistryAccess registries, ItemStack fish) {
        return locomotionOf(profileOf(registries, fish));
    }

    private static Locomotion locomotionOf(Optional<FishProfile> profile) {
        FishAnimationConfig animation = profile
                .map(p -> p.animation().orElse(FishAnimationConfig.HorizontalSwim.DEFAULT))
                .orElse(FishAnimationConfig.HorizontalSwim.DEFAULT);
        return switch (animation) {
            case FishAnimationConfig.HorizontalSwim ignored -> Locomotion.FREE_SWIM;
            case FishAnimationConfig.BellyDown ignored -> Locomotion.GLIDE;
            case FishAnimationConfig.UprightFloat ignored -> Locomotion.DRIFT;
            case FishAnimationConfig.FloorSit ignored -> Locomotion.BENTHIC;
            case FishAnimationConfig.UprightSit ignored -> Locomotion.BENTHIC;
            case FishAnimationConfig.Planted ignored -> Locomotion.ANCHORED;
        };
    }
}
