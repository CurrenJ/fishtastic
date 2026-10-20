package grill24.fishtastic.client.encyclopedia;

import grill24.fishtastic.data.FishProfile;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain-word answer to "can I catch this here, and how good is the spot?" for one fish and the
 * player's current environment. The combined odds come from
 * {@link FishProfile#computeEnvironmentMultiplier}, the same function the server rolls with, so
 * the verdict cannot drift from the real catch odds.
 *
 * @param kind     which of the six outcomes this is
 * @param headline the short title, e.g. "Great spot"
 * @param detail   the one-line reason, e.g. "Night helps; Rain hurts"
 * @param helps    names of the conditions currently raising the odds (empty unless habits are known)
 * @param hurts    names of the conditions currently lowering the odds (empty unless habits are known)
 */
public record CatchVerdict(Kind kind, Component headline, Component detail, List<Component> helps, List<Component> hurts) {

    /** CANT: wrong water. CAN_LOCKED: right water but habits unknown. The rest grade a known-habits spot. */
    public enum Kind { CANT, CAN_LOCKED, GREAT, GOOD, FAIR, POOR }

    private static final String KEY = "screen.fishtastic.encyclopedia.verdict.";

    /**
     * Computes the verdict. The zone check mirrors the server's eligibility rule (any overlap
     * between the fish's zones and the zones at the player; no zones means anywhere). Until
     * {@code habitsUnlocked}, a catchable spot never reveals any condition.
     */
    public static CatchVerdict compute(FishProfile profile, boolean habitsUnlocked, EnvSnapshot env) {
        if (!zoneMet(profile, env)) {
            List<Component> zoneNames = new ArrayList<>();
            for (FishProfile.Zone zone : profile.zones()) {
                zoneNames.add(Component.translatable("screen.fishtastic.encyclopedia.zone." + zone.getSerializedName()));
            }
            return new CatchVerdict(Kind.CANT, Component.translatable(KEY + "cant"),
                    Component.translatable(KEY + "fish_in", join(zoneNames, Component.translatable(KEY + "or"))),
                    List.of(), List.of());
        }
        if (!habitsUnlocked) {
            return new CatchVerdict(Kind.CAN_LOCKED, Component.translatable(KEY + "can"),
                    Component.translatable(KEY + "learn"), List.of(), List.of());
        }

        float combined = profile.computeEnvironmentMultiplier(env.biome(), env.time(), env.weather(), env.moon());
        EncyclopediaWords.Spot spot = EncyclopediaWords.spot(combined);
        Kind kind = switch (spot) {
            case GREAT -> Kind.GREAT;
            case GOOD -> Kind.GOOD;
            case FAIR -> Kind.FAIR;
            case POOR -> Kind.POOR;
        };

        List<Component> helps = new ArrayList<>();
        List<Component> hurts = new ArrayList<>();
        for (FishProfile.ConditionTier tier : FishProfile.ConditionTier.values()) {
            for (FishProfile.BiomeWeight bw : profile.biomeWeights()) {
                if (bw.tier() == tier && bw.biome().contains(env.biome())) {
                    sort(FishPageModel.biomeName(bw), bw.multiplier(), helps, hurts);
                    break;
                }
            }
            for (FishProfile.TimeWeight tw : profile.timeWeights()) {
                if (tw.tier() == tier && tw.time() == env.time()) {
                    sort(Component.translatable("screen.fishtastic.encyclopedia.time." + tw.time().getSerializedName()),
                            tw.multiplier(), helps, hurts);
                    break;
                }
            }
            for (FishProfile.WeatherWeight ww : profile.weatherWeights()) {
                if (ww.tier() == tier && ww.weather() == env.weather()) {
                    sort(Component.translatable("screen.fishtastic.encyclopedia.weather." + ww.weather().getSerializedName()),
                            ww.multiplier(), helps, hurts);
                    break;
                }
            }
            if (env.moonVisible()) {
                for (FishProfile.MoonWeight mw : profile.moonWeights()) {
                    if (mw.tier() == tier && mw.phase() == env.moon()) {
                        sort(Component.translatable("screen.fishtastic.encyclopedia.moon." + mw.phase().getSerializedName()),
                                mw.multiplier(), helps, hurts);
                        break;
                    }
                }
            }
        }

        List<Component> sentences = new ArrayList<>();
        if (!helps.isEmpty()) {
            sentences.add(Component.translatable(KEY + (helps.size() == 1 ? "helps" : "help"), joinList(helps)));
        }
        if (!hurts.isEmpty()) {
            sentences.add(Component.translatable(KEY + (hurts.size() == 1 ? "hurts" : "hurt"), joinList(hurts)));
        }
        Component detail = sentences.isEmpty()
                ? Component.translatable(KEY + "neutral")
                : join(sentences, Component.translatable(KEY + "join"));
        return new CatchVerdict(kind, Component.translatable(spot.langKey()), detail, List.copyOf(helps), List.copyOf(hurts));
    }

    /** Any zone overlap, or no zone restriction at all — same rule as the server's eligibility check. */
    static boolean zoneMet(FishProfile profile, EnvSnapshot env) {
        if (profile.zones().isEmpty()) return true;
        for (FishProfile.Zone zone : profile.zones()) {
            if (env.zones().contains(zone)) return true;
        }
        return false;
    }

    private static void sort(Component name, float multiplier, List<Component> helps, List<Component> hurts) {
        if (multiplier > 1.0f) helps.add(name);
        else if (multiplier < 1.0f) hurts.add(name);
    }

    private static Component joinList(List<Component> parts) {
        return join(parts, Component.translatable(KEY + "list_sep"));
    }

    private static MutableComponent join(List<Component> parts, Component separator) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) out.append(separator);
            out.append(parts.get(i));
        }
        return out;
    }
}
