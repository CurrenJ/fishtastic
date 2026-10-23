package grill24.fishtastic.client.companion;

import grill24.fishsim.companion.Ambient;
import grill24.fishsim.companion.Habitat;
import grill24.fishsim.companion.Vec;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The Minecraft side of the Phase 5 moods (docs/fish-companions.md, spec §6): the
 * {@link Ambient} around an owner, read from the level, and a species' {@link Habitat}, read from
 * its fish profile. Both speak the sim's place tokens: {@code "minecraft:plains"},
 * {@code "#minecraft:is_ocean"}, {@code "dim:minecraft:the_nether"}, and of the fishing zones only
 * {@code "zone:nether"}.
 */
public final class CompanionAmbient {
    private CompanionAmbient() {}

    /** Warm things are looked for this far round the owner, blocks (the mood's bask reaches ~5 out). */
    private static final int WARM_RADIUS = 6, WARM_HEIGHT = 3;
    /** Warm blocks are merged into one point per cell this size, so a lava pool isn't a hundred of them. */
    private static final int WARM_CELL = 4;
    /** At most this many warm points, the nearest. */
    private static final int WARM_POINTS = 4;
    /** Ticks between warmth scans (a scan reads ~1,200 blocks). */
    private static final int WARM_SCAN_TICKS = 20;
    /** Below this a biome snows ({@code Biome#warmEnoughToRain}). */
    private static final float SNOW_BELOW = 0.15f;

    // ── The ambient ────────────────────────────────────────────────────────────────────────────

    /** Legacy W0 sampler used exclusively by the equal-ground dev cost harness. */
    public static final class Sampler {
        public long reads, nanos;
        public Ambient sampleForCost(Level level, net.minecraft.world.phys.Vec3 eye) { return sample(level, eye); }
        private List<Vec> warmth = List.of();
        private long nextScan = Long.MIN_VALUE;
        private BlockPos scannedAt;

        /** The ambient at {@code eye} (the owner's eye, or a phantom's). */
        Ambient sample(Level level, net.minecraft.world.phys.Vec3 eye) {
            BlockPos pos = BlockPos.containing(eye);
            long now = level.getGameTime();
            if (now >= nextScan || scannedAt == null || scannedAt.distManhattan(pos) > 3) {
                long start = System.nanoTime();
                warmth = scanWarmth(level, pos);
                reads += 1183; nanos += System.nanoTime() - start;
                nextScan = now + WARM_SCAN_TICKS;
                scannedAt = pos;
            }
            return at(level, eye, warmth);
        }
    }

    /** Ambient at the exact eye; water-zone homes use the same chest sample as OwnerState. */
    /**
     * The precipitation falling on {@code pos} right now: none when it is dry or the position is
     * roofed over. PORT-ONLY: 26.1 has {@code Level.precipitationAt}; 1.21.1 splits it between
     * {@code Level.isRaining} and the biome's own precipitation type.
     */
    private static Biome.Precipitation precipitationAt(Level level, Biome biome, BlockPos pos) {
        if (!level.isRaining()) return Biome.Precipitation.NONE;
        if (level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, pos).getY() > pos.getY())
            return Biome.Precipitation.NONE;
        return biome.getPrecipitationAt(pos);
    }

    /**
     * How much the sky is darkened (0 by day .. 11 at night), as the server computes it. PORT-ONLY: 26.1
     * keeps this in step on the client; on 1.21.1 {@code Level.getSkyDarken()} is only maintained server-side
     * (it reads 0 at midnight on the client), so it is rebuilt from the float {@code ClientLevel.getSkyDarken}
     * the sky renderer uses (1.0 by day .. 0.2 at night).
     */
    public static int skyDarken(Level level) {
        if (level instanceof net.minecraft.client.multiplayer.ClientLevel client)
            return Math.round(Math.max(0f, Math.min(1f, (1f - client.getSkyDarken(1f)) / 0.8f)) * 11f);
        return level.getSkyDarken();
    }

    static Ambient at(Level level, net.minecraft.world.phys.Vec3 eye, List<Vec> warmth) {
        BlockPos pos = BlockPos.containing(eye);
        Holder<Biome> biome = level.getBiome(pos);
        Set<String> place = new HashSet<>();
        biome.unwrapKey().ifPresent(k -> place.add(k.location().toString()));
        biome.tags().forEach(tag -> place.add("#" + tag.location()));
        boolean wet = level.getFluidState(BlockPos.containing(eye.subtract(0, grill24.fishsim.companion.OwnerState.CHEST_DROP, 0))).is(FluidTags.WATER);
        for (FishProfile.Zone z : FishProfile.Zone.resolve(biome, pos.getY(), level.getSeaLevel())) {
            if (z == FishProfile.Zone.NETHER || z == FishProfile.Zone.CAVE || z == FishProfile.Zone.HIGH_ALTITUDE || wet)
                place.add(zoneToken(z));
        }
        place.add("dim:" + level.dimension().location());
        if (!level.canSeeSky(pos)) place.add("sky:blocked");

        float base = biome.value().getBaseTemperature();
        boolean snows = biome.value().coldEnoughToSnow(pos);
        // The base temperature, corrected for height: a temperate biome high enough to snow is cold.
        float temperature = snows ? Math.min(base, SNOW_BELOW - 0.05f) : Math.max(base, SNOW_BELOW);

        Ambient.Weather weather = switch (precipitationAt(level, biome.value(), pos)) {
            case NONE -> Ambient.Weather.CLEAR; // dry, or under a roof: it isn't rained on
            case SNOW -> Ambient.Weather.SNOW;
            case RAIN -> level.isThundering() ? Ambient.Weather.THUNDER : Ambient.Weather.RAIN;
        };
        Ambient.DayPart part = Ambient.DayPart.valueOf(
                FishProfile.TimeOfDay.fromGameTime(level.getDayTime()).name());
        int moon = FishMoonPhase.at(level, pos).index();
        return new Ambient(place, temperature, weather, part, moon, warmth)
                .withDarkness(1f - level.getMaxLocalRawBrightness(pos, skyDarken(level)) / 15f);
    }

    /** Lit campfires, fire and lava near {@code pos}: the nearest block of each cell, the nearest few cells. */
    static List<Vec> scanWarmth(Level level, BlockPos pos) {
        Map<Long, BlockPos> nearestByCell = new HashMap<>();
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        for (int dx = -WARM_RADIUS; dx <= WARM_RADIUS; dx++) {
            for (int dy = -WARM_HEIGHT; dy <= WARM_HEIGHT; dy++) {
                for (int dz = -WARM_RADIUS; dz <= WARM_RADIUS; dz++) {
                    c.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!warm(level.getBlockState(c))) continue;
                    long cell = BlockPos.asLong(Math.floorDiv(c.getX(), WARM_CELL), Math.floorDiv(c.getY(), WARM_CELL),
                            Math.floorDiv(c.getZ(), WARM_CELL));
                    BlockPos best = nearestByCell.get(cell);
                    if (best == null || c.distSqr(pos) < best.distSqr(pos)) nearestByCell.put(cell, c.immutable());
                }
            }
        }
        List<BlockPos> blocks = new ArrayList<>(nearestByCell.values());
        blocks.sort((a, b) -> Double.compare(a.distSqr(pos), b.distSqr(pos)));
        List<Vec> out = new ArrayList<>(Math.min(WARM_POINTS, blocks.size()));
        for (int i = 0; i < blocks.size() && i < WARM_POINTS; i++) {
            BlockPos b = blocks.get(i);
            out.add(new Vec(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5));
        }
        return out;
    }

    static boolean warm(BlockState state) {
        if (state.is(BlockTags.CAMPFIRES)) return state.getValue(CampfireBlock.LIT);
        return state.is(BlockTags.FIRE) || state.getFluidState().is(FluidTags.LAVA);
    }

    // ── The habitat ────────────────────────────────────────────────────────────────────────────

    /** What a species likes, from its fish profile; {@link Habitat#NONE} for a fish without one. */
    static Habitat habitatOf(Optional<FishProfile> profile) {
        if (profile.isEmpty()) return Habitat.NONE;
        FishProfile p = profile.get();
        Set<String> homes = new HashSet<>();
        // Water-zone homes match only wet owner tokens; cave/altitude/Nether are real land places too.
        boolean nether = p.zones().contains(FishProfile.Zone.NETHER);
        if (nether) homes.add(zoneToken(FishProfile.Zone.NETHER));
        for (FishProfile.Zone z : p.zones()) homes.add(zoneToken(z));
        boolean coldHome = p.zones().contains(FishProfile.Zone.HIGH_ALTITUDE);
        for (FishProfile.BiomeWeight w : p.biomeWeights()) {
            if (w.multiplier() < 1f) continue; // a biome it's less likely in isn't a home
            w.biome().tag().ifPresent(tag -> homes.add("#" + tag.location()));
            w.biome().keys().forEach(k -> homes.add(k.location().toString()));
            // Resolved against the live client registry when a world is loaded; the profile's
            // biome names alone decide nether and cold homes otherwise.
            var level = net.minecraft.client.Minecraft.getInstance().level;
            for (Holder<Biome> b : level == null ? List.<Holder<Biome>>of() : w.biome().resolve(level.registryAccess())) {
                if (b.is(BiomeTags.IS_NETHER)) nether = true;
                if (b.value().getBaseTemperature() < SNOW_BELOW) coldHome = true;
            }
            if (w.biome().keys().stream().anyMatch(k -> Set.of("minecraft:nether_wastes", "minecraft:soul_sand_valley",
                    "minecraft:crimson_forest", "minecraft:warped_forest", "minecraft:basalt_deltas").contains(k.location().toString()))
                    || w.biome().tag().map(t -> t.location().toString().equals("minecraft:is_nether")).orElse(false)) nether = true;
            if (w.biome().keys().stream().anyMatch(k -> Set.of("minecraft:cold_ocean", "minecraft:deep_cold_ocean",
                    "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean").contains(k.location().toString()))) coldHome = true;
        }
        Map<Ambient.Weather, Float> weather = new EnumMap<>(Ambient.Weather.class);
        for (FishProfile.WeatherWeight w : p.weatherWeights()) {
            weather.merge(Ambient.Weather.valueOf(w.weather().name()), w.multiplier(), (a, b) -> a * b);
        }
        Map<Ambient.DayPart, Float> hours = new EnumMap<>(Ambient.DayPart.class);
        for (FishProfile.TimeWeight w : p.timeWeights()) {
            hours.merge(Ambient.DayPart.valueOf(w.time().name()), w.multiplier(), (a, b) -> a * b);
        }
        float fullMoon = 1f;
        for (FishProfile.MoonWeight w : p.moonWeights()) if (w.phase() == FishMoonPhase.FULL_MOON) fullMoon *= w.multiplier();
        Habitat.Climate climate = nether ? Habitat.Climate.HOT
                : coldHome ? Habitat.Climate.COLD
                : Habitat.Climate.TEMPERATE;
        return new Habitat(homes, weather, hours, fullMoon, climate);
    }

    static String zoneToken(FishProfile.Zone z) {
        return "zone:" + z.name().toLowerCase(Locale.ROOT);
    }

    /** The overlay's line for an ambient and a habitat. */
    static String describe(Ambient a, Habitat h) {
        return String.format(Locale.ROOT, "ambient %.2f° %s %s moon %d warm %d  habitat %s homes %d%s",
                a.temperature(), a.weather().name().toLowerCase(Locale.ROOT),
                a.dayPart() == null ? "-" : a.dayPart().name().toLowerCase(Locale.ROOT), a.moonPhase(),
                a.warmth().size(), h.climate().name().toLowerCase(Locale.ROOT), h.homes().size(),
                h.atHome(a) ? " (home)" : "");
    }
}
