package grill24.fishtastic.server;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import grill24.FishtasticRegistries;
import grill24.fishtastic.client.ambient.VisualSampler;
import grill24.fishtastic.data.EncyclopediaTiers;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.ecology.NaturalPool;
import grill24.fishtastic.item.AcuteIapsisItem;
import grill24.fishtastic.item.FishtasticFishItem;
import grill24.fishtastic.client.ambient.VisualSampler.Candidate;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ambient presence report (world fish exploration plan, Step 1b / E18): the predicted ambient
 * roster of every location pool ({@link EncyclopediaTiers#LOCATION_POOLS}) under every time of day,
 * weather and moon phase, built on {@link NaturalPool} and the <em>shipped</em> {@code fish_profile}
 * JSONs, for the owner to review against species lore.
 *
 * <p>The fast sanity test runs in the normal pass. The report itself is written only when asked:
 * {@code ./gradlew :common:test --tests '*AmbientPresenceReport*' -PambientPresenceReport}
 * (output: {@code build/reports/ambient-presence/}).
 *
 * <h2>How the real data is loaded</h2>
 * Each shipped profile is parsed with {@link FishProfile#CODEC} over {@link JsonOps} into a
 * {@link MappedRegistry}, next to a headless item registry holding the four vanilla fish and one
 * {@link FishtasticFishItem} per species in {@code #fishtastic:fish} (the {@code #minecraft:fishes}
 * contents). The one thing a bare JVM cannot decode is {@code biome_weights}, whose codec needs a
 * biome registry; those entries are stripped before decoding. The report is therefore driven by zone
 * sets, not biomes: the environment is built directly from a zone set with a neutral biome that no
 * biome weight matches, so biome multipliers do not apply (they are listed per species instead).
 *
 * <h2>The ambient filter</h2>
 * Ambient fish today are the natural pool, minus anything the client cannot represent (no item, or a
 * locomotion disagreeing with the animation; neither can happen for shipped data), weighted by
 * {@link VisualSampler}'s 75% raw plus 25% square-root mixture. There is no per-species ambient flag:
 * {@code fish_encounter_disabled}, the school-ready locomotion check and the nether exclusion apply
 * to interactive visits only ({@code FishSchoolManager#encounterEligible}). Both the catch share and
 * the ambient (mixture) share are reported.
 */
class AmbientPresenceReportTest {

    private static final Path RESOURCES = resolveResourceRoot();

    private static Path resolveResourceRoot() {
        for (Path candidate : List.of(Path.of("src/main/resources/data/fishtastic"),
                Path.of("common/src/main/resources/data/fishtastic"))) {
            if (Files.isDirectory(candidate.resolve("fishtastic/fish_profile"))) return candidate;
        }
        throw new IllegalStateException("Could not locate common/src/main/resources/data/fishtastic from "
                + Path.of("").toAbsolutePath());
    }
    private static final Path PROFILE_DIR = RESOURCES.resolve("fishtastic/fish_profile");
    private static final String PROPERTY = "ambientPresenceReport";

    /** Day-fraction of each period (DAWN 22000-24000, DAY 0-12000, DUSK 12000-14000, NIGHT 14000-22000). */
    private static final Map<FishProfile.TimeOfDay, Double> TIME_P = Map.of(
            FishProfile.TimeOfDay.DAWN, 2000 / 24000.0, FishProfile.TimeOfDay.DAY, 12000 / 24000.0,
            FishProfile.TimeOfDay.DUSK, 2000 / 24000.0, FishProfile.TimeOfDay.NIGHT, 8000 / 24000.0);
    /** Assumed weather frequencies (an assumption: real weather depends on world and biome). */
    private static final Map<FishProfile.WeatherCondition, Double> WEATHER_P = Map.of(
            FishProfile.WeatherCondition.CLEAR, 0.70, FishProfile.WeatherCondition.RAIN, 0.20,
            FishProfile.WeatherCondition.THUNDER, 0.05, FishProfile.WeatherCondition.SNOW, 0.05);

    // ------------------------------------------------------------------ real data

    record Loaded(MappedRegistry<Item> items, MappedRegistry<FishProfile> profiles,
                  Map<String, FishProfile> byName, Map<String, Integer> biomeWeightCounts, Set<String> unlisted) {
    }

    private static Loaded loaded;

    static synchronized Loaded load() throws IOException {
        if (loaded != null) return loaded;
        FishingPoolFixture.ensureDataComponents();

        MappedRegistry<FishProfile> profiles =
                new MappedRegistry<>(FishtasticRegistries.FISH_PROFILE_REGISTRY_KEY, Lifecycle.stable());
        Map<String, FishProfile> byName = new TreeMap<>();
        Map<String, Integer> biomeWeightCounts = new HashMap<>();
        try (Stream<Path> files = Files.list(PROFILE_DIR)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                JsonElement biomes = json.remove("biome_weights");
                biomeWeightCounts.put(name, biomes == null ? 0 : biomes.getAsJsonArray().size());
                FishProfile profile = FishProfile.CODEC.parse(JsonOps.INSTANCE, json)
                        .getOrThrow(false, message -> { throw new IllegalStateException(name + ": " + message); });
                byName.put(name, profile);
            }
        }
        for (var entry : byName.entrySet()) {
            profiles.register(FishingPoolFixture.profileKey(FishingPoolFixture.id("fishtastic", entry.getKey())),
                    entry.getValue(), Lifecycle.stable());
        }
        profiles.freeze();

        Set<String> fishTag = tagValues(RESOURCES.resolve("tags/items/fish.json"));
        assertEquals(fishTag, byName.keySet(), "#fishtastic:fish and the fish_profile files disagree");

        MappedRegistry<Item> items = new MappedRegistry<>(Registries.ITEM, Lifecycle.stable());
        List<ResourceLocation> poolOrder = new ArrayList<>(List.of(FishingPoolFixture.COD, FishingPoolFixture.SALMON,
                FishingPoolFixture.TROPICAL_FISH, FishingPoolFixture.PUFFERFISH));
        for (String name : byName.keySet()) poolOrder.add(FishingPoolFixture.id("fishtastic", name));
        for (ResourceLocation id : poolOrder) {
            Item item = switch (id.toString()) {
                case "minecraft:cod" -> Items.COD;
                case "minecraft:salmon" -> Items.SALMON;
                case "minecraft:tropical_fish" -> Items.TROPICAL_FISH;
                case "minecraft:pufferfish" -> Items.PUFFERFISH;
                case "fishtastic:acute_iaspis" -> new AcuteIapsisItem(new Item.Properties());
                default -> new FishtasticFishItem(new Item.Properties());
            };
            items.register(FishingPoolFixture.itemKey(id), item, Lifecycle.stable());
        }
        Map<TagKey<Item>, List<Holder<Item>>> tags = new HashMap<>();
        tags.put(ItemTags.FISHES, poolOrder.stream()
                .<Holder<Item>>map(id -> items.getHolderOrThrow(FishingPoolFixture.itemKey(id))).toList());
        items.bindTags(tags);
        items.freeze();

        loaded = new Loaded(items, profiles, byName, biomeWeightCounts,
                tagValues(RESOURCES.resolve("tags/items/unlisted_fish.json")));
        return loaded;
    }

    private static Set<String> tagValues(Path file) throws IOException {
        Set<String> out = new TreeSet<>();
        for (JsonElement e : JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonArray("values")) {
            out.add(e.getAsString().substring("fishtastic:".length()));
        }
        return out;
    }

    // ------------------------------------------------------------------ computation

    record Combo(FishProfile.TimeOfDay time, FishProfile.WeatherCondition weather, FishMoonPhase moon, double p) {
    }

    /** One species' numbers in one zone set. Shares are expectations over the combo distribution. */
    static final class Stat {
        final String id;
        double expCatch, expAmbient;
        double minAmbient = 1, maxAmbient = 0;
        Combo peak;
        double massNearPeak;

        Stat(String id) {
            this.id = id;
        }
    }

    static final class ZoneReport {
        final java.util.Set<FishProfile.Zone> zones;
        final List<Combo> combos;
        /** species id -> value per combo (same order as combos). */
        final Map<String, double[]> catchShare = new LinkedHashMap<>();
        final Map<String, double[]> ambientShare = new LinkedHashMap<>();
        final Map<String, int[]> weight = new LinkedHashMap<>();
        final Map<String, Stat> stats = new LinkedHashMap<>();

        ZoneReport(java.util.Set<FishProfile.Zone> zones, List<Combo> combos) {
            this.zones = zones;
            this.combos = combos;
        }

        String label() {
            return zones.stream().map(z -> z.name().toLowerCase(Locale.ROOT)).sorted()
                    .reduce((a, b) -> a + "+" + b).orElse("?");
        }
    }

    static List<Combo> combos() {
        List<Combo> out = new ArrayList<>();
        for (var t : FishProfile.TimeOfDay.values()) {
            for (var w : FishProfile.WeatherCondition.values()) {
                for (var m : FishMoonPhase.values()) {
                    out.add(new Combo(t, w, m, TIME_P.get(t) * WEATHER_P.get(w) / FishMoonPhase.values().length));
                }
            }
        }
        return out;
    }

    static List<ZoneReport> compute(Loaded data, boolean includeVanilla) {
        Holder<net.minecraft.world.level.biome.Biome> neutralBiome = FishingPoolFixture.riverBiome();
        List<Combo> combos = combos();
        List<ZoneReport> reports = new ArrayList<>();
        for (Set<FishProfile.Zone> zoneSet : EncyclopediaTiers.LOCATION_POOLS) {
            ZoneReport report = new ZoneReport(zoneSet, combos);
            for (int c = 0; c < combos.size(); c++) {
                Combo combo = combos.get(c);
                var env = new FishingPoolResolver.EnvironmentContext(neutralBiome, combo.time(), combo.weather(),
                        combo.moon(), Set.copyOf(zoneSet));
                var pool = NaturalPool.resolve(data.items(), data.profiles(), env);
                List<Candidate> candidates = new ArrayList<>();
                long total = 0;
                for (var fish : pool.candidates()) {
                    if (!includeVanilla && !(fish.item().value() instanceof FishtasticFishItem)) continue;
                    candidates.add(new Candidate(fish.item().unwrapKey().orElseThrow().location(), fish.weight()));
                    total += fish.weight();
                }
                VisualSampler sampler = VisualSampler.build(candidates, id -> true);
                assertTrue(sampler.omitted().isEmpty(), "ambient filter dropped " + sampler.omitted());
                for (var entry : sampler.entries()) {
                    String id = entry.species();
                    report.ambientShare.computeIfAbsent(id, k -> new double[combos.size()])[c] = entry.share();
                    report.catchShare.computeIfAbsent(id, k -> new double[combos.size()])[c] = entry.weight() / (double) total;
                    report.weight.computeIfAbsent(id, k -> new int[combos.size()])[c] = entry.weight();
                }
            }
            for (String id : report.ambientShare.keySet()) {
                report.stats.put(id, stat(report, id));
            }
            reports.add(report);
        }
        return reports;
    }

    private static Stat stat(ZoneReport report, String id) {
        Stat s = new Stat(id);
        double[] amb = report.ambientShare.get(id);
        double[] cat = report.catchShare.get(id);
        for (int c = 0; c < amb.length; c++) {
            double p = report.combos.get(c).p();
            s.expCatch += p * cat[c];
            s.expAmbient += p * amb[c];
            s.minAmbient = Math.min(s.minAmbient, amb[c]);
            if (amb[c] > s.maxAmbient) {
                s.maxAmbient = amb[c];
                s.peak = report.combos.get(c);
            }
        }
        for (int c = 0; c < amb.length; c++) {
            if (amb[c] >= 0.5 * s.maxAmbient) s.massNearPeak += report.combos.get(c).p();
        }
        return s;
    }

    /** Expected ambient share over the combos matching a condition, averaged over the other axes. */
    static double marginal(ZoneReport report, String id, Predicate<Combo> match) {
        double[] amb = report.ambientShare.get(id);
        double num = 0, den = 0;
        for (int c = 0; c < amb.length; c++) {
            Combo combo = report.combos.get(c);
            if (!match.test(combo)) continue;
            num += combo.p() * amb[c];
            den += combo.p();
        }
        return den == 0 ? 0 : num / den;
    }

    // ------------------------------------------------------------------ tests

    @Test
    void realProfilesYieldAPlausibleRosterForEveryLocationPool() throws IOException {
        Loaded data = load();
        assertTrue(data.byName().size() >= 60, "expected the shipped fish profiles, got " + data.byName().size());
        List<ZoneReport> reports = compute(data, true);
        assertEquals(EncyclopediaTiers.LOCATION_POOLS.size(), reports.size());
        for (ZoneReport modOnly : compute(data, false)) {
            assertFalse(modOnly.stats.keySet().stream().anyMatch(id -> id.startsWith("minecraft:")));
            assertEquals(1.0, modOnly.stats.values().stream().mapToDouble(s -> s.expAmbient).sum(), 1e-4, modOnly.label());
        }
        for (ZoneReport report : reports) {
            assertFalse(report.stats.isEmpty(), report.label() + " has an empty roster");
            double catchSum = report.stats.values().stream().mapToDouble(s -> s.expCatch).sum();
            double ambientSum = report.stats.values().stream().mapToDouble(s -> s.expAmbient).sum();
            assertEquals(1.0, catchSum, 1e-6, report.label());
            assertEquals(1.0, ambientSum, 1e-4, report.label());
        }
        // Zone gating works against real data: a river-only species is absent from the nether.
        assertFalse(reports.get(0).stats.containsKey("fishtastic:bluegill"));
        assertTrue(reports.get(3).stats.containsKey("fishtastic:bluegill"));
    }

    @Test
    void writesTheAmbientPresenceReport() throws IOException {
        String out = System.getProperty(PROPERTY);
        Assumptions.assumeTrue(out != null && !out.isBlank(), "pass -P" + PROPERTY + " to write the report");
        Loaded data = load();
        List<ZoneReport> reports = compute(data, true);
        List<ZoneReport> modOnly = compute(data, false);
        Path dir = Path.of(out);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("ambient-presence.csv"), csv(data, reports, modOnly));
        Files.writeString(dir.resolve("ambient-presence.md"), markdown(data, reports, modOnly));
    }

    // ------------------------------------------------------------------ output

    private static String pct(double v) {
        if (v == 0) return "0";
        if (v < 0.0005) return "<0.05";
        return String.format(Locale.ROOT, v < 0.01 ? "%.2f" : "%.1f", v * 100);
    }

    private static String shortName(String id) {
        return id.startsWith("fishtastic:") ? id.substring("fishtastic:".length())
                : id.replace("minecraft:", "") + " (vanilla)";
    }

    private static int baseWeight(Loaded data, String id) {
        if (id.startsWith("fishtastic:")) return data.byName().get(shortName(id)).baseWeight();
        return switch (id) {
            case "minecraft:cod", "minecraft:salmon" -> 200;
            case "minecraft:tropical_fish" -> 50;
            default -> 25;
        };
    }

    private static String csv(Loaded data, List<ZoneReport> reports, List<ZoneReport> modOnly) {
        StringBuilder sb = new StringBuilder(
                "zone_set,time,weather,moon,species,base_weight,catch_weight,catch_share,ambient_share,ambient_share_no_vanilla\n");
        for (int z = 0; z < reports.size(); z++) {
            ZoneReport r = reports.get(z);
            ZoneReport mod = modOnly.get(z);
            for (int c = 0; c < r.combos.size(); c++) {
                Combo combo = r.combos.get(c);
                for (String id : r.ambientShare.keySet()) {
                    sb.append(r.label()).append(',').append(combo.time().name().toLowerCase(Locale.ROOT)).append(',')
                            .append(combo.weather().name().toLowerCase(Locale.ROOT)).append(',')
                            .append(combo.moon().name().toLowerCase(Locale.ROOT)).append(',').append(id).append(',')
                            .append(baseWeight(data, id)).append(',').append(r.weight.get(id)[c]).append(',')
                            .append(String.format(Locale.ROOT, "%.6f,%.6f,%s", r.catchShare.get(id)[c], r.ambientShare.get(id)[c],
                                    mod.ambientShare.containsKey(id)
                                            ? String.format(Locale.ROOT, "%.6f", mod.ambientShare.get(id)[c]) : ""))
                            .append('\n');
                }
            }
        }
        return sb.toString();
    }

    private static String markdown(Loaded data, List<ZoneReport> reports, List<ZoneReport> modOnly) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Ambient presence report\n\n");
        sb.append("Predicted ambient roster per location pool, from `NaturalPool` and the shipped `fish_profile` data.\n");
        sb.append("Generated by `AmbientPresenceReportTest`; regenerate with\n");
        sb.append("`./gradlew :common:test --tests '*AmbientPresenceReport*' -PambientPresenceReport`.\n");
        sb.append("The full per-condition data is in `ambient-presence.csv` next to this file.\n\n");
        sb.append("## How to read this\n\n");
        sb.append("- **Ambient %** is the share of ambient individuals: 75% raw catch share plus 25% square-root share\n");
        sb.append("  (`VisualSampler`, the mixture ambient fish are drawn from today). **Catch %** is the plain weight share\n");
        sb.append("  of the natural pool (no bait, no charm, vanilla fish included).\n");
        sb.append("- Both are expectations over assumed condition frequencies: time of day by tick span\n");
        sb.append("  (dawn 8.3%, day 50%, dusk 8.3%, night 33.3%), weather clear 70% / rain 20% / thunder 5% / snow 5%,\n");
        sb.append("  moon phases equal. The moon only matters on a clear night. The weather split is an assumption.\n");
        sb.append("- **Zone sets, not biomes.** Each section is one `EncyclopediaTiers.LOCATION_POOLS` zone set, built with a\n");
        sb.append("  neutral biome, so biome multipliers (the `biome_weights` of ")
                .append(data.biomeWeightCounts().values().stream().filter(n -> n > 0).count())
                .append(" species) are NOT applied; they are listed in flag 5.\n");
        sb.append("- **Ambient filter.** The ambient code drops only species it cannot represent (no item, or animation and\n");
        sb.append("  locomotion disagreeing). That drops nothing in shipped data. There is no per-species ambient flag.\n");
        sb.append("  `#fishtastic:fish_encounter_disabled`, the school-ready locomotion check and the nether exclusion\n");
        sb.append("  (`FishSchoolManager#encounterEligible`) apply to interactive visits only, so they are not applied here.\n");
        sb.append("- Every zone-eligible species is always in the pool (a weight is floored at 1), so \"conditional\" means a\n");
        sb.append("  large swing in share, not presence or absence.\n\n");

        sb.append("- **Two views.** View A is the natural pool as it is, vanilla fish included. Vanilla fish carry weights\n");
        sb.append("  of 200/200/50/25 against 1-100 for Fishtastic species and have no zone gate, so they swamp every\n");
        sb.append("  roster. View B (no vanilla) drops them and renormalises, to show what the Fishtastic\n");
        sb.append("  species and their conditions look like on their own.\n\n");

        sb.append("## Flags for owner review\n\n");
        sb.append("### View A: natural pool as is (vanilla fish included)\n\n");
        flags(sb, data, reports, true);
        sb.append("### View B: Fishtastic species only (no vanilla)\n\n");
        flags(sb, data, modOnly, false);
        sb.append("### Biome-weighted species (biome multipliers not applied in either view)\n\n");
        List<String> biomed = new ArrayList<>();
        data.biomeWeightCounts().forEach((name, n) -> {
            if (n > 0) biomed.add(name + " (" + n + ")");
        });
        biomed.sort(null);
        sb.append(String.join(", ", biomed)).append("\n\n");

        for (int zi = 0; zi < reports.size(); zi++) {
            ZoneReport r = reports.get(zi);
            ZoneReport mod = modOnly.get(zi);
            sb.append("## Zone set: ").append(r.label()).append("\n\n");
            sb.append(r.stats.size()).append(" species. Sorted by ambient share. Time and weather columns are ambient %,\n");
            sb.append("averaged over the other axes.\n\n");
            sb.append("| Species | Base wt | Catch % | Ambient % | No-vanilla % | Min-max % | Dawn | Day | Dusk | Night | Clear | Rain | Snow | Thunder |\n");
            sb.append("|---|---:|---:|---:|---:|---|---:|---:|---:|---:|---:|---:|---:|---:|\n");
            List<Stat> sorted = new ArrayList<>(r.stats.values());
            sorted.sort(Comparator.comparingDouble((Stat s) -> -s.expAmbient));
            for (Stat s : sorted) {
                sb.append("| ").append(shortName(s.id)).append(" | ").append(baseWeight(data, s.id))
                        .append(" | ").append(pct(s.expCatch)).append(" | ").append(pct(s.expAmbient))
                        .append(" | ").append(mod.stats.containsKey(s.id) ? pct(mod.stats.get(s.id).expAmbient) : "-")
                        .append(" | ").append(pct(s.minAmbient)).append("-").append(pct(s.maxAmbient));
                for (var t : FishProfile.TimeOfDay.values()) {
                    sb.append(" | ").append(pct(marginal(r, s.id, c -> c.time() == t)));
                }
                for (var w : List.of(FishProfile.WeatherCondition.CLEAR, FishProfile.WeatherCondition.RAIN,
                        FishProfile.WeatherCondition.SNOW, FishProfile.WeatherCondition.THUNDER)) {
                    sb.append(" | ").append(pct(marginal(r, s.id, c -> c.weather() == w)));
                }
                sb.append(" |\n");
            }
            List<String> moonSpecies = new ArrayList<>();
            for (Stat s : sorted) {
                double lo = 1, hi = 0;
                for (var m : FishMoonPhase.values()) {
                    double v = marginal(r, s.id, clearNight(m));
                    lo = Math.min(lo, v);
                    hi = Math.max(hi, v);
                }
                if (hi > lo * 1.05 + 1e-9) moonSpecies.add(s.id);
            }
            if (!moonSpecies.isEmpty()) {
                sb.append("\nMoon phase (ambient %, clear night only), species whose share changes with the moon:\n\n");
                sb.append("| Species |");
                for (var m : FishMoonPhase.values()) sb.append(' ').append(m.name().toLowerCase(Locale.ROOT)).append(" |");
                sb.append("\n|---|").append("---:|".repeat(FishMoonPhase.values().length)).append('\n');
                for (String id : moonSpecies) {
                    sb.append("| ").append(shortName(id)).append(" |");
                    for (var m : FishMoonPhase.values()) {
                        sb.append(' ').append(pct(marginal(r, id, clearNight(m)))).append(" |");
                    }
                    sb.append('\n');
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /** The moon only matters on a clear night; any other peak is the same for every phase. */
    private static String peakMoon(Combo peak) {
        return peak.time() == FishProfile.TimeOfDay.NIGHT && peak.weather() == FishProfile.WeatherCondition.CLEAR
                ? peak.moon().name().toLowerCase(Locale.ROOT) : "any moon";
    }

    private static Predicate<Combo> clearNight(FishMoonPhase moon) {
        return c -> c.moon() == moon && c.time() == FishProfile.TimeOfDay.NIGHT
                && c.weather() == FishProfile.WeatherCondition.CLEAR;
    }

    private static void flags(StringBuilder sb, Loaded data, List<ZoneReport> reports, boolean withVanilla) {
        sb.append("#### 1. Species missing from every roster, or nearly invisible\n\n");
        Map<String, Double> bestAmbient = new TreeMap<>();
        for (String name : data.byName().keySet()) bestAmbient.put("fishtastic:" + name, 0.0);
        for (ZoneReport r : reports) {
            for (Stat s : r.stats.values()) bestAmbient.merge(s.id, s.expAmbient, Math::max);
        }
        List<String> never = new ArrayList<>(), faint = new ArrayList<>();
        for (var e : bestAmbient.entrySet()) {
            if (!e.getKey().startsWith("fishtastic:")) continue;
            if (e.getValue() == 0.0) never.add(shortName(e.getKey()));
            else if (e.getValue() < 0.01) faint.add(shortName(e.getKey()) + " (best " + pct(e.getValue()) + "%)");
        }
        sb.append("- Never in any roster: ").append(never.isEmpty() ? "none" : String.join(", ", never)).append('\n');
        sb.append("- Best ambient share under 1% in every zone set: ")
                .append(faint.isEmpty() ? "none" : String.join(", ", faint)).append("\n\n");

        sb.append("#### 2. Ambient share far from catch rarity\n\n");
        sb.append("Catch rarity here is the encyclopedia tier (`EncyclopediaTiers.derive`: best base-weight share over the\n");
        sb.append("listed Fishtastic species of a pool; very rare <1.5%, rare <4%, uncommon <12%, common <30%, abundant),\n");
        sb.append("compared with the tier the species' ambient share lands in, in that same pool. The ambient mixture\n");
        sb.append("flattens weights, so a shift of one tier is expected; two or more are listed.\n\n");
        List<EncyclopediaTiers.Species> speciesInputs = new ArrayList<>();
        for (var e : data.byName().entrySet()) {
            speciesInputs.add(new EncyclopediaTiers.Species(FishingPoolFixture.id("fishtastic", e.getKey()),
                    e.getValue().baseWeight(), e.getValue().zones(), data.unlisted().contains(e.getKey())));
        }
        var rarities = EncyclopediaTiers.derive(speciesInputs);
        List<String> jumps = new ArrayList<>();
        int oneTier = 0;
        for (String name : data.byName().keySet()) {
            var rarity = rarities.get(FishingPoolFixture.id("fishtastic", name));
            if (rarity == null || rarity.pool() == null) continue;
            ZoneReport r = reports.get(EncyclopediaTiers.LOCATION_POOLS.indexOf(rarity.pool()));
            Stat s = r.stats.get("fishtastic:" + name);
            var ambientTier = EncyclopediaTiers.Tier.forShare((float) s.expAmbient);
            int diff = Math.abs(rarity.tier().ordinal() - ambientTier.ordinal());
            if (diff == 1) oneTier++;
            if (diff >= 2) {
                jumps.add(name + (data.unlisted().contains(name) ? " (unlisted)" : "") + " in " + r.label()
                        + ": encyclopedia " + pct(rarity.share()) + "% (" + rarity.tier().name().toLowerCase(Locale.ROOT)
                        + ") vs ambient " + pct(s.expAmbient) + "% (" + ambientTier.name().toLowerCase(Locale.ROOT) + ")");
            }
        }
        for (String j : jumps) sb.append("- ").append(j).append('\n');
        if (jumps.isEmpty()) sb.append("- none\n");
        sb.append("- (").append(oneTier).append(" more species move by exactly one tier.)\n\n");

        sb.append("#### 3. Zone sets dominated by one species (over 50%)\n\n");
        boolean any = false;
        for (ZoneReport r : reports) {
            Stat topAmbient = r.stats.values().stream().max(Comparator.comparingDouble(s -> s.expAmbient)).orElseThrow();
            Stat topCatch = r.stats.values().stream().max(Comparator.comparingDouble(s -> s.expCatch)).orElseThrow();
            if (topAmbient.expAmbient > 0.5 || topCatch.expCatch > 0.5) {
                any = true;
                sb.append("- ").append(r.label()).append(": top ambient ").append(shortName(topAmbient.id)).append(' ')
                        .append(pct(topAmbient.expAmbient)).append("%, top catch ").append(shortName(topCatch.id)).append(' ')
                        .append(pct(topCatch.expCatch)).append("%\n");
            }
        }
        if (!any) sb.append("- none over 50%\n");
        if (withVanilla) {
            sb.append("\nVanilla fish (cod, salmon, tropical fish, pufferfish) have no `fish_profile`, so the zone gate fails\n");
            sb.append("open and they appear in every zone set, including the nether and caves. Their combined share of each\n");
            sb.append("natural pool:\n\n");
        }
        for (ZoneReport r : reports) {
            if (!withVanilla) break;
            double vanilla = r.stats.values().stream().filter(s -> !s.id.startsWith("fishtastic:"))
                    .mapToDouble(s -> s.expCatch).sum();
            double vanillaAmbient = r.stats.values().stream().filter(s -> !s.id.startsWith("fishtastic:"))
                    .mapToDouble(s -> s.expAmbient).sum();
            sb.append("- ").append(r.label()).append(": ").append(pct(vanilla)).append("% catch, ")
                    .append(pct(vanillaAmbient)).append("% ambient\n");
        }
        sb.append('\n');

        sb.append("#### 4. Conditional species (share swings with time, weather or moon)\n\n");
        sb.append("Species whose ambient share changes 2x or more between their worst and best condition, in the zone\n");
        sb.append("set where the swing is largest. Presence never switches off (weights floor at 1); this is the share\n");
        sb.append("swing. Peak is the single best time/weather/moon combination; near-peak is the share of time spent\n");
        sb.append("within half of it, so a low figure means the species is only strong in a rare combination.\n\n");
        Map<String, String> bestLine = new TreeMap<>();
        Map<String, Double> bestSwing = new HashMap<>();
        for (ZoneReport r : reports) {
            for (Stat s : r.stats.values()) {
                if (s.minAmbient <= 0) continue;
                double swing = s.maxAmbient / s.minAmbient;
                if (swing < 2 || swing <= bestSwing.getOrDefault(s.id, 0.0)) continue;
                bestSwing.put(s.id, swing);
                bestLine.put(s.id, String.format(Locale.ROOT,
                        "- %s in %s: %s%% to %s%% (%.1fx), peak %s/%s/%s, near peak %.0f%% of the time%s",
                        shortName(s.id), r.label(), pct(s.minAmbient), pct(s.maxAmbient), swing,
                        s.peak.time().name().toLowerCase(Locale.ROOT), s.peak.weather().name().toLowerCase(Locale.ROOT),
                        peakMoon(s.peak), s.massNearPeak * 100,
                        s.massNearPeak <= 0.15 ? "  **rare combination**" : ""));
            }
        }
        bestSwing.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> sb.append(bestLine.get(e.getKey())).append('\n'));
        if (bestSwing.isEmpty()) sb.append("- none\n");
        sb.append('\n');
    }
}
