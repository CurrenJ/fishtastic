package grill24.fishtastic.server;

import grill24.FishtasticRegistries;
import grill24.fishtastic.FishtasticItemTags;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.ecology.AmbientFluid;
import grill24.fishtastic.ecology.AmbientRoster;
import grill24.fishtastic.ecology.NaturalPool;
import grill24.fishtastic.ecology.ObservationRules;
import grill24.fishtastic.network.FishEncyclopediaSyncPacket;
import grill24.fishtastic.network.ObserveFishPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of spyglass observation (world fish exploration E7, E16, E17, E19): checks a player's
 * claim that they held a spyglass on a fish and, if it holds up, records the species as observed.
 *
 * <p>An observation unlocks the Fishopedia's name, portrait and broad habitat and nothing else. It
 * records into {@link FishCatchSavedData}'s observed set only: no catch count, no quest event, no
 * reward, no token. That is what makes the checks below deliberately loose.
 *
 * <h2>What is checked</h2>
 * <ul>
 *   <li>Always: not faster than {@link ObservationRules#MIN_INTERVAL_TICKS} per player; the species
 *       has a fish profile and is listed; the player does not already know it; and, once the fish
 *       itself checks out, the player is looking through a spyglass right now.</li>
 *   <li>{@link ObserveFishPacket.Kind#VISIT}: the school exists in the player's level with that species,
 *       the member is a live fish in the water, its authoritative position is within range of the
 *       player's eye, and the player is looking toward it (cone plus the slack of a few ticks of
 *       swimming).</li>
 *   <li>{@link ObserveFishPacket.Kind#AMBIENT}: the fish is derived again from the ecology seed, the chunk,
 *       the slot and the lifespan index, and must equal the claimed ID; that lifespan must be present
 *       now (within a clock tolerance); the claimed home cell must be in the chunk, in loaded ambient
 *       fluid, within range; the player must be looking toward the chunk's fish (cone plus the 20 block
 *       slack of E12, where clients agree on a home chunk but not the exact spot); and the species must
 *       be in the natural pool at that cell for the lifespan's arrival conditions.</li>
 * </ul>
 *
 * <h2>Weather and ambient species</h2>
 * A client draws an ambient fish's species once, at arrival, under the weather <em>it</em> sees
 * then, so the server cannot know which weather the claimant used (and two clients can disagree).
 * The server therefore accepts the species if it is in the natural pool under <em>any</em> weather
 * (clear, rain, snow, thunder) at the claimed home cell's biome and height, with time of day and
 * moon phase taken from the lifespan's arrival time. That is loose on weather only: a modified
 * client can claim any species the cell could hold in some weather, for any fish that exists there
 * now, and that gains it a name, a portrait and a habitat. It cannot claim a species that cannot live
 * there, a fish that does not exist, or one it cannot see.
 */
public final class FishObservation {

    public enum Result {
        RECORDED, ALREADY_KNOWN, RATE_LIMITED, UNKNOWN_SPECIES, NO_SUCH_FISH, WRONG_SPECIES, OUT_OF_RANGE, NOT_LOOKING,
        NOT_SCOPING
    }

    /** The tick of each player's last observation packet, on the clock of the level that handled it. */
    private static final Map<UUID, Long> LAST_PACKET = new HashMap<>();

    private FishObservation() {
    }

    /** Test seam: forgets every rate-limit entry. */
    public static void resetForTest() {
        LAST_PACKET.clear();
    }

    /** Handles a packet at the level's game time. */
    public static Result handle(ServerPlayer player, ObserveFishPacket packet) {
        return handle(player, packet, ((ServerLevel) player.level()).getGameTime());
    }

    /** As {@link #handle(ServerPlayer, ObserveFishPacket)} at an explicit tick, so a test can drive the rate limit. */
    public static Result handle(ServerPlayer player, ObserveFishPacket packet, long now) {
        Long last = LAST_PACKET.get(player.getUUID());
        if (last != null && now >= last && now - last < ObservationRules.MIN_INTERVAL_TICKS) return Result.RATE_LIMITED;
        LAST_PACKET.put(player.getUUID(), now);
        // Bounded by the players observing right now: an entry past its interval says nothing.
        if (LAST_PACKET.size() > 64) {
            LAST_PACKET.values().removeIf(t -> t > now || now - t >= ObservationRules.MIN_INTERVAL_TICKS);
        }

        ServerLevel level = (ServerLevel) player.level();
        if (level.getServer() == null) return Result.NO_SUCH_FISH;
        FishCatchSavedData data = FishCatchSavedData.getOrCreate(level.getServer());
        UUID key = data.resolvePlayerKey(player);
        ResourceLocation species = packet.species();
        if (!isObservableSpecies(level, species)) return Result.UNKNOWN_SPECIES;
        if (data.hasObserved(key, species) || data.getCatchCount(key, species) > 0) return Result.ALREADY_KNOWN;

        Result verdict = packet.kind() == ObserveFishPacket.Kind.VISIT
                ? checkVisit(level, player, packet)
                : checkAmbient(level, player, packet, now);
        if (verdict != null) return verdict;
        // Last, so a refused claim still says what was wrong with the fish; the client only sends
        // while scoping, and this keeps a modified client from observing without a spyglass.
        if (!player.isScoping()) return Result.NOT_SCOPING;
        grant(player, data, species);
        return Result.RECORDED;
    }

    /**
     * Records an observation and tells the client (which shows one toast). Used by the packet path
     * and by {@code /fishtastic observe}. @return false when the player already had it.
     */
    public static boolean grant(ServerPlayer player, FishCatchSavedData data, ResourceLocation species) {
        UUID key = data.resolvePlayerKey(player);
        if (!data.recordObservation(key, species)) return false;
        FishEncyclopediaSyncPacket.sendToPlayer(player, data, List.of(species));
        return true;
    }

    /** A species with a fish profile that the Fishopedia lists (secret one-off variants are not observable). */
    public static boolean isObservableSpecies(ServerLevel level, ResourceLocation species) {
        Registry<FishProfile> profiles = level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        if (!profiles.containsKey(ResourceKey.create(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, species))) return false;
        return BuiltInRegistries.ITEM.getOptional(species)
                .map(item -> !item.builtInRegistryHolder().is(FishtasticItemTags.UNLISTED_FISH))
                .orElse(false);
    }

    // ------------------------------------------------------------------ visits

    /** @return null when the claim holds, else why not. */
    private static Result checkVisit(ServerLevel level, ServerPlayer player, ObserveFishPacket packet) {
        FishSchoolSavedData.School school = null;
        for (FishSchoolSavedData.School candidate : FishSchoolSavedData.get(level).schools()) {
            if (candidate.id().equals(packet.school())) {
                school = candidate;
                break;
            }
        }
        if (school == null) return Result.NO_SUCH_FISH;
        if (!school.species().equals(packet.species())) return Result.WRONG_SPECIES;
        FishSchoolSavedData.Member member = null;
        for (FishSchoolSavedData.Member candidate : school.members()) {
            if (candidate.id().equals(packet.member())) {
                member = candidate;
                break;
            }
        }
        if (member == null || !inWater(member.state())) return Result.NO_SUCH_FISH;

        FishSchoolSavedData.Position at = member.position();
        Vec3 eye = player.getEyePosition();
        double dx = at.x() - eye.x, dy = at.y() - eye.y, dz = at.z() - eye.z;
        if (!ObservationRules.inRange(Math.sqrt(dx * dx + dy * dy + dz * dz), ObservationRules.VISIT_POSITION_SLACK)) {
            return Result.OUT_OF_RANGE;
        }
        Vec3 look = player.getLookAngle();
        if (!ObservationRules.serverCone(look.x, look.y, look.z, dx, dy, dz, ObservationRules.VISIT_POSITION_SLACK)) {
            return Result.NOT_LOOKING;
        }
        return null;
    }

    /** Member states in which the fish is swimming in its water where a spyglass could see it. */
    private static boolean inWater(FishSchoolSavedData.MemberState state) {
        return switch (state) {
            case AVAILABLE, APPROACHING, BITE_READY, RECOVERING, DEPARTING -> true;
            case IN_SESSION, CAUGHT, DEPARTED -> false;
        };
    }

    // ------------------------------------------------------------------ ambient

    /** @return null when the claim holds, else why not. */
    private static Result checkAmbient(ServerLevel level, ServerPlayer player, ObserveFishPacket packet, long now) {
        AmbientRoster.Params roster = AmbientRoster.DEFAULT;
        if (packet.slot() < 0 || packet.slot() >= roster.maxSlotsPerChunk()) return Result.NO_SUCH_FISH;
        long seed = EcologySeedSync.seedFor(level);
        int cx = packet.chunkX(), cz = packet.chunkZ();
        if (AmbientRoster.fishId(seed, cx, cz, packet.slot(), packet.lifespanIndex()) != packet.fishId()) {
            return Result.NO_SUCH_FISH;
        }
        AmbientRoster.Lifespan lifespan = AmbientRoster.lifespan(seed, cx, cz, packet.slot(), packet.lifespanIndex(), roster);
        if (!ObservationRules.lifespanPresent(lifespan.arrivalTick(), lifespan.departureTick(), now)) {
            return Result.NO_SUCH_FISH;
        }

        BlockPos home = packet.home();
        if ((home.getX() >> 4) != cx || (home.getZ() >> 4) != cz || !level.hasChunkAt(home)) return Result.NO_SUCH_FISH;
        var fluidTag = AmbientFluid.forDimension(level.dimension().location().toString()) == AmbientFluid.LAVA
                ? FluidTags.LAVA : FluidTags.WATER;
        if (!level.getFluidState(home).is(fluidTag)) return Result.NO_SUCH_FISH;

        Vec3 eye = player.getEyePosition();
        double dx = home.getX() + 0.5 - eye.x, dy = home.getY() + 0.5 - eye.y, dz = home.getZ() + 0.5 - eye.z;
        if (!ObservationRules.inRange(Math.sqrt(dx * dx + dy * dy + dz * dz), ObservationRules.AMBIENT_POSITION_SLACK)) {
            return Result.OUT_OF_RANGE;
        }
        Vec3 look = player.getLookAngle();
        if (!ObservationRules.serverCone(look.x, look.y, look.z, dx, dy, dz, ObservationRules.AMBIENT_POSITION_SLACK)) {
            return Result.NOT_LOOKING;
        }

        return poolHoldsAmbient(level, home, lifespan.arrivalTick(), packet.species()) ? null : Result.WRONG_SPECIES;
    }

    /**
     * Whether {@code species} is an ambient candidate at {@code home} for a fish that arrived at
     * {@code arrivalTick}, under any weather. Public so a test can state the rule.
     */
    public static boolean poolHoldsAmbient(ServerLevel level, BlockPos home, long arrivalTick, ResourceLocation species) {
        Registry<Item> items = level.registryAccess().registryOrThrow(Registries.ITEM);
        Registry<FishProfile> profiles = level.registryAccess().registryOrThrow(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY);
        long clockOffset = level.getDayTime() - level.getGameTime();
        var biome = level.getBiome(home);
        for (FishProfile.WeatherCondition weather : FishProfile.WeatherCondition.values()) {
            var env = NaturalPool.ambientEnvironment(biome, arrivalTick + clockOffset, weather, home.getY(), level.getSeaLevel());
            var pool = NaturalPool.resolve(items, profiles, env);
            for (var candidate : NaturalPool.ambientCandidates(profiles, pool)) {
                if (candidate.species().equals(species)) return true;
            }
        }
        return false;
    }
}
