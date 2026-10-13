package grill24.fishtastic.companion;

import grill24.FishtasticRegistries;
import grill24.fishsim.companion.BenthicGroup;
import grill24.fishsim.companion.GroundGait;
import grill24.fishsim.companion.PartyGrouping;
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
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;

/**
 * How a party's fish group up (docs/fish-companions-party.md §3), as the game sees it. The rule
 * itself lives in {@code :fishsim}'s {@link PartyGrouping} and only there: this class turns each
 * slot's stack into a {@link PartyGrouping.Member} (species, class, gait, formation) and reports
 * what PartyGrouping decided, in the shapes the menu preview and the scene scheduler want.
 *
 * <p>Shared by the server (scene scheduling), the companion menu's party preview and the client's
 * companion wiring ({@link #members}), so all three agree on what a party makes.
 */
public final class CompanionGroups {
    private CompanionGroups() {}

    public enum Kind {
        /** One fish of its species in the party. */
        SOLO,
        /** Two of a grouping species: a school pair or a ground pair. */
        PAIR,
        /** Three FREE_SWIM fish of one species. */
        SCHOOL,
        /** Three ground-walking BENTHIC fish whose profile walks them in a line. */
        LINE,
        /** Three ground-walking BENTHIC fish whose profile walks them as a crowd. */
        CROWD,
        /** Two or three of a species that doesn't group (D7); each is its own member. */
        SEPARATE
    }

    /**
     * One species' share of the party. {@code slots} are in formation order: the first is the
     * group's leader (its smallest fish, ties to the lower slot, D9), so the party lead may follow
     * in its own school. A {@link Kind#SEPARATE} entry gathers same-species fish that each swim on
     * their own, in slot order, for the preview's sake.
     */
    public record Group(Item species, Kind kind, Locomotion locomotion, List<Integer> slots) {
        public int leader() { return slots.get(0); }
        public int count() { return slots.size(); }
    }

    /** The party's groups, ordered by their first slot. Empty slots are skipped. */
    public static List<Group> of(CompanionParty party, RegistryAccess registries) {
        // Seed never decides grouping. Length can pick a group's leader, so pass a length that
        // orders same-species fish the way the client's rendered length does: their size (within
        // a species the render calibration is shared, so size and rendered length rank alike).
        List<PartyGrouping.Group> grouped = PartyGrouping.group(members(party, registries,
                s -> grill24.fishtastic.util.ItemSizeHelper.hasSize(s) ? grill24.fishtastic.util.ItemSizeHelper.getSize(s) / 100.0 : 1.0,
                s -> 0L));
        List<Group> out = new ArrayList<>();
        boolean[] used = new boolean[grouped.size()];
        for (int i = 0; i < grouped.size(); i++) {
            if (used[i]) continue;
            PartyGrouping.Group g = grouped.get(i);
            ItemStack lead = party.get(g.leader().slot());
            List<Integer> slots = new ArrayList<>();
            for (int s : g.slots()) slots.add(s);
            Kind kind;
            if (g.kind() == PartyGrouping.Kind.SOLO) {
                // Same-species solos are duplicates PartyGrouping chose not to group (D7).
                for (int j = i + 1; j < grouped.size(); j++) {
                    PartyGrouping.Group other = grouped.get(j);
                    if (!used[j] && other.kind() == PartyGrouping.Kind.SOLO
                            && other.leader().species().equals(g.leader().species())) {
                        slots.add(other.leader().slot());
                        used[j] = true;
                    }
                }
                kind = slots.size() == 1 ? Kind.SOLO : Kind.SEPARATE;
            } else if (g.members().size() == 2) {
                kind = Kind.PAIR;
            } else if (g.kind() == PartyGrouping.Kind.SCHOOL) {
                kind = Kind.SCHOOL;
            } else {
                kind = g.formation() == BenthicGroup.Formation.CROWD ? Kind.CROWD : Kind.LINE;
            }
            out.add(new Group(lead.getItem(), kind, g.leader().locomotion(), List.copyOf(slots)));
        }
        return out;
    }

    /**
     * The party's non-empty slots as the sim's members, for {@link PartyGrouping#group}. The client
     * passes each fish's rendered length and its stack seed; grouping alone needs neither.
     */
    public static List<PartyGrouping.Member> members(CompanionParty party, RegistryAccess registries,
                                                     ToDoubleFunction<ItemStack> length, ToLongFunction<ItemStack> stackSeed) {
        List<PartyGrouping.Member> members = new ArrayList<>();
        for (int slot = 0; slot < CompanionParty.SIZE; slot++) {
            ItemStack fish = party.get(slot);
            if (fish.isEmpty()) continue;
            Optional<FishProfile> profile = profileOf(registries, fish);
            Locomotion locomotion = locomotionOf(profile);
            members.add(new PartyGrouping.Member(BuiltInRegistries.ITEM.getKey(fish.getItem()).toString(), locomotion,
                    gaitOf(locomotion, profile), formationOf(locomotion, profile),
                    (float) length.applyAsDouble(fish), stackSeed.applyAsLong(fish), slot));
        }
        return members;
    }

    /**
     * A benthic's ground gait from its profile (CRAWL unless it names the flatfish ripple), or null
     * for every other class. Every benthic species walks the ground today
     * (docs/fish-companions-benthic-follow.md §21), so none is a gait-less pure percher.
     */
    public static GroundGait gaitOf(Locomotion locomotion, Optional<FishProfile> profile) {
        if (locomotion != Locomotion.BENTHIC) return null;
        return profile.map(FishProfile::companionGait).orElse(FishProfile.CompanionGait.CRAWL)
                == FishProfile.CompanionGait.RIPPLE ? GroundGait.RIPPLE : GroundGait.CRAWL;
    }

    /** A ground benthic's formation style from its profile's {@code companion_group} (LINE when it names none). */
    public static BenthicGroup.Formation formationOf(Locomotion locomotion, Optional<FishProfile> profile) {
        if (locomotion != Locomotion.BENTHIC) return null;
        return profile.flatMap(FishProfile::companionGroup)
                .map(c -> c.formation() == FishProfile.CompanionGroupConfig.Formation.CROWD
                        ? BenthicGroup.Formation.CROWD : BenthicGroup.Formation.LINE)
                .orElse(BenthicGroup.Formation.LINE);
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
