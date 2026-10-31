package grill24.fishtastic.ecology;

import grill24.FishtasticRegistries;
import grill24.fishtastic.component.BaitEffect;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.client.ambient.VisualSampler;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.server.FishingPoolResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

import java.util.ArrayList;
import java.util.List;

/**
 * The natural pool: which species live in the water at a position with no tackle at all (no bait,
 * no charm, {@link FishingPoolResolver.PersonalEquipment#NEUTRAL}). The server (interactive visits)
 * and every client (ambient fish) call this, so they read the same pool.
 *
 * <p>Everything it reads is an explicit parameter of {@link #environment} and {@link #resolve}:
 * biome, time of day, weather, moon phase, height and sea level for the environment, then the item
 * and {@code fish_profile} registries. {@link #environmentAt} / {@link #resolveAt} are the only
 * {@link Level} touching entry points; they gather those inputs from the level exactly as the
 * server's real fishing path does ({@link FishingPoolResolver#resolveEnvironment}), and nothing in
 * the chain reads server-only state: the biome, the overworld clock, rain/thunder levels and the
 * moon-phase attribute are all synced to clients, and {@code fish_profile} is a synced registry.
 */
public final class NaturalPool {

    /** No bait (so vanilla fish stay in the pool), no charm, neutral personal equipment. */
    public static final FishingPoolResolver.Inputs INPUTS = FishingPoolResolver.Inputs.of(
            BaitEffect.NO_BAIT, null, FishingPoolResolver.PersonalEquipment.NEUTRAL);

    private NaturalPool() {
    }

    /**
     * Builds the environment from explicit inputs; zones follow from the biome and height. The single
     * place an {@link FishingPoolResolver.EnvironmentContext} is assembled, shared with the real
     * fishing path.
     */
    public static FishingPoolResolver.EnvironmentContext environment(Holder<Biome> biome,
                                                                    FishProfile.TimeOfDay timeOfDay,
                                                                    FishProfile.WeatherCondition weather,
                                                                    FishMoonPhase moonPhase,
                                                                    int y, int seaLevel) {
        return new FishingPoolResolver.EnvironmentContext(biome, timeOfDay, weather, moonPhase,
                FishProfile.Zone.resolve(biome, y, seaLevel));
    }

    /** The natural pool for an environment, in pool order with final raw weights. Pure; consumes no RNG. */
    public static FishingPoolResolver.SpeciesContext resolve(Registry<Item> itemRegistry,
                                                            Registry<FishProfile> fishProfileRegistry,
                                                            FishingPoolResolver.EnvironmentContext environment) {
        return FishingPoolResolver.resolve(itemRegistry, fishProfileRegistry, environment, INPUTS);
    }

    /** The environment at a water position, gathered the same way on server and client. */
    public static FishingPoolResolver.EnvironmentContext environmentAt(Level level, BlockPos pos) {
        return FishingPoolResolver.resolveEnvironment(level, pos, null);
    }

    /** The natural pool at a water position, using the level's own registries. */
    public static FishingPoolResolver.SpeciesContext resolveAt(Level level, BlockPos pos) {
        Registry<Item> items = level.registryAccess().registryOrThrow(Registries.ITEM);
        Registry<FishProfile> profiles = level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        return resolve(items, profiles, environmentAt(level, pos));
    }

    /**
     * The environment an ambient fish is drawn under: the conditions at its <em>arrival</em>. Time
     * of day and moon phase follow from the arrival's clock time ({@code gameTime} plus the level's
     * clock offset, which the caller reads); weather is whatever the caller passes, which on a client
     * is the weather it sees now (a fish that arrived before the weather changed is still drawn
     * under today's, the documented drift risk).
     */
    public static FishingPoolResolver.EnvironmentContext ambientEnvironment(Holder<Biome> biome, long arrivalClockTime,
                                                                           FishProfile.WeatherCondition weather,
                                                                           int y, int seaLevel) {
        return environment(biome, FishProfile.TimeOfDay.fromGameTime(Math.floorMod(arrivalClockTime, 24000L)),
                weather, FishMoonPhase.fromIndex((int) Math.floorMod(Math.floorDiv(arrivalClockTime, 24000L), 8L)),
                y, seaLevel);
    }

    /**
     * The pool's candidates an ambient fish may be: Fishtastic species with a {@code fish_profile}.
     * Vanilla fish (cod, salmon, tropical fish, pufferfish: no profile, outside {@code #fishtastic:fish})
     * stay in the catch and visit pools but are never ambient (owner decision 2026-10-30).
     */
    public static List<VisualSampler.Candidate> ambientCandidates(Registry<FishProfile> profiles,
                                                                 FishingPoolResolver.SpeciesContext pool) {
        List<VisualSampler.Candidate> out = new ArrayList<>(pool.candidates().size());
        for (FishingPoolResolver.WeightedFish fish : pool.candidates()) {
            if (!(fish.item().value() instanceof FishtasticFishItem)) continue;
            var key = fish.item().unwrapKey();
            if (key.isEmpty() || !profiles.containsKey(net.minecraft.resources.ResourceKey.create(
                    FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, key.get().location()))) continue;
            out.add(new VisualSampler.Candidate(key.get().location(), fish.weight()));
        }
        return out;
    }
}
