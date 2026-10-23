package grill24.fishtastic.client.encyclopedia.page;

import grill24.fishtastic.client.encyclopedia.CatchVerdict;
import grill24.fishtastic.client.encyclopedia.EncyclopediaCoins;
import grill24.fishtastic.client.encyclopedia.EncyclopediaWords;
import grill24.fishtastic.client.encyclopedia.EnvSnapshot;
import grill24.fishtastic.client.encyclopedia.FishPageModel;
import grill24.fishtastic.client.encyclopedia.portrait.Portrait;
import grill24.fishtastic.client.renderer.ZoneIconTextures;
import grill24.fishtastic.data.FishMoonPhase;
import grill24.fishtastic.data.FishProfile;
import io.github.currenj.gelatinui.gui.UI;
import io.github.currenj.gelatinui.gui.UIElement;
import io.github.currenj.gelatinui.gui.UIScreen;
import grill24.fishtastic.client.FreeformContainer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static grill24.fishtastic.client.encyclopedia.page.ScaledTooltip.line;

/**
 * Lays out the info page on the fixed 416x232 canvas, in logical GUI pixels, following
 * docs/encyclopedia-redesign/README.md ({@code concept_a} / {@code catch_guide})
 * number for number. Everything static goes into one {@link PageSurface}; item icons and coins are
 * child elements above it; buttons and hover tooltips are {@link HitArea}s / {@link PageButton}s on
 * top. Build is cheap and pure, so the screen simply rebuilds the host whenever the model, the tab
 * or the live environment changes.
 */
public final class EncyclopediaPageLayout {
    public static final int W = 416, H = 232;
    public static final int TOP = 17, BODY_H = 178, LW = 168;
    /** Plate top-left on the canvas, and the fish slot inside it. */
    public static final int PLATE_X = 6, PLATE_Y = TOP + 28;
    private static final int RX = LW + 4, RW = W - LW - 4;

    private static final int WHITE = 0xFFFFFF, GREY = 0xAAAAAA, DGREY = 0x555555, BLACK = 0x000000;
    private static final int GOLD = 0xFFAA00, YELLOW = 0xFFFF55, GREEN = 0x55FF55, RED = 0xFF5555, PINK = 0xFF8FA8;
    private static final int MUTED = 0x8C7F7A;
    private static final int BOOST_BG = 0x2E7834, PEN_BG = 0x8C2C2C, NEU_BG = 0x3A2422;

    public enum Tab { CATCH, NOTES }

    /** What the page's buttons do; implemented by the screen. */
    public interface Actions {
        void close();

        void browse(int dir);

        void selectTab(Tab tab);

        void claim(UIElement<?> origin);

        void openBond(ItemStack species);
    }

    /** Companion status line for the bond strip. */
    public record Bond(String text, boolean unlocked, ItemStack species) {}

    public record Params(FishPageModel model, FishProfile profile, EnvSnapshot env, Tab tab, int index, int total,
                         Bond bond, int qualityRgb, UIScreen ui, int k, Actions actions) {}

    /** The Bond button (for the tutorial highlight; may be null) and the lowest content pixel of the right page. */
    public record Result(UIElement<?> bondButton, int rightPageBottom) {}

    private final Params p;
    private final FishPageModel m;
    private final PageSurface s = new PageSurface(W, H);
    private final List<UIElement<?>> overlays = new ArrayList<>();
    private final List<UIElement<?>> hits = new ArrayList<>();
    private UIElement<?> bondButton;
    private int rightBottom;

    private EncyclopediaPageLayout(Params p) {
        this.p = p;
        this.m = p.model();
    }

    /** Clears nothing: the caller owns {@code host} and clears it first. */
    public static Result build(FreeformContainer host, Params p) {
        EncyclopediaPageLayout layout = new EncyclopediaPageLayout(p);
        layout.layout();
        host.addChild(layout.s);
        for (UIElement<?> overlay : layout.overlays) host.addChild(overlay);
        for (UIElement<?> hit : layout.hits) {
            if (hit instanceof HitArea area) area.bind(layout.s);
            host.addChild(hit);
        }
        return new Result(layout.bondButton, layout.rightBottom);
    }

    // ------------------------------------------------------------------ helpers

    private static String t(String key, Object... args) {
        return Component.translatable("screen.fishtastic.encyclopedia." + key, args).getString();
    }

    private static String fmtMult(float m) {
        String str = String.format(Locale.ROOT, "%.2f", m);
        if (str.contains(".")) str = str.replaceAll("0+$", "").replaceAll("\\.$", "");
        return str;
    }

    private static String cm(float v) {
        return t("cm", String.format(Locale.ROOT, "%.0f", v));
    }

    private static String catchesText(String keyBase, int n) {
        return t(keyBase + (n == 1 ? ".one" : ".other"), n);
    }

    private <E extends UIElement<?>> E place(E el, int x, int y) {
        el.setPosition(new Vector2f(x, y));
        return el;
    }

    /** A hover-tooltip spot over {@code x,y,w,h}. */
    private HitArea tipNow(int x, int y, int w, int h, boolean now, ScaledTooltip.Line... lines) {
        if (!now) return tip(x, y, w, h, lines);
        ScaledTooltip.Line[] all = java.util.Arrays.copyOf(lines, lines.length + 1);
        all[lines.length] = line(t("tooltip.now"), 0x8CE68C);
        return tip(x, y, w, h, all);
    }

    private HitArea tip(int x, int y, int w, int h, ScaledTooltip.Line... lines) {
        HitArea area = new HitArea(w, h, false);
        area.tip(p.ui(), ScaledTooltip.of(p.k(), lines));
        hits.add(place(area, x, y));
        return area;
    }

    private PageButton button(int x, int y, int w, int h, String label, Runnable action) {
        PageButton b = new PageButton(w, h, label, action);
        hits.add(place(b, x, y));
        return b;
    }

    private static String effectWord(FishPageModel.Condition c) {
        return t0(EncyclopediaWords.effect(c.multiplier(), c.tier()).langKey());
    }

    private static String t0(String fullKey) {
        return Component.translatable(fullKey).getString();
    }

    private static String effectLine(FishProfile.ConditionTier tier, float mult) {
        return tier == FishProfile.ConditionTier.SECONDARY
                ? t("effect.tooltip_secondary", Math.round((mult - 1f) * 100f))
                : t("effect.tooltip", fmtMult(mult));
    }

    private static int cellBg(FishPageModel.Condition c) {
        if (c == null || c.multiplier() == 1f) return NEU_BG;
        return c.multiplier() > 1f ? BOOST_BG : PEN_BG;
    }

    // ------------------------------------------------------------------ the page

    private void layout() {
        header();
        leftPage();
        rightPage();
        footer();
    }

    private void header() {
        button(0, 0, 52, 14, t("all_fish"), p.actions()::close);
        s.textCentered(W / 2, 3, Component.translatable("screen.fishtastic.encyclopedia.title").getString(), GOLD);
        s.textRight(W - 34, 3, t("index", p.index() + 1, p.total()), GREY);
        button(W - 31, 0, 14, 14, "<", () -> p.actions().browse(-1));
        // The next button ends exactly on the canvas edge; give its hitbox one logical pixel of
        // tolerance so the half-open, scaled bounds do not drop edge-adjacent mouse positions.
        button(W - 15, 0, 15, 14, ">", () -> p.actions().browse(1))
                .hitPadding(1, 1, 1, 1);
    }

    // ---- left page

    private void leftPage() {
        s.panel(m.complete() ? PageSurface.PANEL_GOLD : PageSurface.PANEL, 0, TOP, LW, BODY_H);

        String title = m.displayName().getString();
        s.text(7, TOP + 6, PageSurface.truncate(title, LW - 14), m.nameRevealed() ? WHITE : GREY);
        if (m.nameRevealed()) {
            if (m.rarity() != null) {
                for (int i = 0; i < 5; i++) {
                    s.rect(7 + i * 6, TOP + 17, 4, 6, i < m.rarity().gems() ? 0x6ECDE1 : 0x462E34);
                }
                s.text(39, TOP + 16, PageSurface.truncate(
                        t("rarity_pool", m.rarity().oneInN(), m.rarity().pool().getString()), LW - 46), MUTED);
                tip(7, TOP + 15, LW - 14, 10, line(t("rarity.tooltip"), WHITE));
            }
        } else {
            s.text(7, TOP + 16, t("not_yet_caught"), MUTED);
        }

        // The portrait plate is its own set of layers above this surface; nothing is drawn here.
        int ry = PLATE_Y + Portrait.H + 5;
        s.text(7, ry, t("size"), GREY);
        int barX = 34, barW = LW - 42;
        if (m.sizeUnlocked()) {
            float mean = m.sizeMean(), sd = m.sizeStdDev();
            float lo = Math.max(0f, mean - 2 * sd), hi = mean + 2 * sd;
            float spanLo = lo - sd, spanHi = hi + 2 * sd;
            s.rect(barX, ry + 3, barW, 3, 0x281A1A);
            int xLo = toX(lo, spanLo, spanHi, barX, barW), xHi = toX(hi, spanLo, spanHi, barX, barW);
            s.rect(xLo, ry + 3, xHi - xLo, 3, 0x82AAC8);
            s.rect(toX(mean, spanLo, spanHi, barX, barW), ry + 2, 1, 5, WHITE);
            s.text(xLo - 2, ry + 9, String.format(Locale.ROOT, "%.0f", lo), MUTED);
            s.textRight(xHi + 2, ry + 9, cm(hi), MUTED);
            if (m.personalBest().isPresent()) {
                int yx = toX(m.personalBest().get().size(), spanLo, spanHi, barX, barW);
                s.icon(PageIcons.MARKER, yx - 2, ry - 3, WHITE);
            }
            if (m.serverBest().isPresent()) {
                int sx = toX(m.serverBest().get().size(), spanLo, spanHi, barX, barW);
                s.icon(PageIcons.MARKER, sx - 2, ry - 3, GOLD);
            }
            int recY = ry + 20;
            if (m.personalBest().isPresent()) {
                FishPageModel.PersonalBest pb = m.personalBest().get();
                s.icon(PageIcons.MARKER, 7, recY + 2, WHITE);
                String you = t("record.you", String.format(Locale.ROOT, "%.0f", pb.size()));
                s.text(14, recY, you, WHITE);
                int qx = 14 + PageSurface.width(you + " ");
                s.rect(qx, recY + 1, 6, 6, BLACK, 160);
                s.rect(qx + 1, recY + 2, 4, 4, p.qualityRgb());
                s.text(qx + 8, recY, pb.quality().getString(), WHITE);
            }
            if (m.serverBest().isPresent()) {
                FishPageModel.ServerBest sb = m.serverBest().get();
                s.icon(PageIcons.MARKER, 7, recY + 12, GOLD);
                String lead = t("record.best", String.format(Locale.ROOT, "%.0f", sb.size())) + " ";
                s.text(14, recY + 10, lead, GOLD);
                int avail = LW - 20 - PageSurface.width(lead);
                String holder = sb.holder().getString();
                String shown = PageSurface.truncate(holder, avail);
                s.text(14 + PageSurface.width(lead), recY + 10, shown, 0xFFD27A);
                if (!shown.equals(holder)) {
                    tip(14 + PageSurface.width(lead), recY + 9, avail, 10, line(holder, 0xFFD27A));
                }
            }
        } else {
            s.rect(barX, ry + 3, barW, 3, 0x281A1A);
            s.icon(PageIcons.LOCK, 7, ry + 20, MUTED);
            s.text(15, ry + 20, PageSurface.truncate(catchesText("size_locked", m.thresholds().statsCatches()), LW - 22), MUTED);
        }

        // Fight
        int fy = TOP + BODY_H - 37;
        s.text(7, fy, t("fight"), GREY);
        if (m.fight().isPresent()) {
            s.text(34, fy, m.fight().get().temperament().getString(), WHITE);
            int segs = Math.max(0, Math.min(5, Math.round(m.fight().get().difficulty() * 5)));
            for (int i = 0; i < 5; i++) {
                s.rect(LW - 45 + i * 8, fy + 1, 6, 6, i < segs ? 0xE6783C : 0x3C2624);
            }
        } else {
            s.text(34, fy, t0("screen.fishtastic.encyclopedia.locked_placeholder"), MUTED);
        }

        bondStrip(TOP + BODY_H - 22);
    }

    private static int toX(float v, float spanLo, float spanHi, int barX, int barW) {
        int x = barX + (int) ((v - spanLo) / (spanHi - spanLo) * barW);
        return Math.max(barX, Math.min(barX + barW, x));
    }

    private void bondStrip(int cy) {
        s.rect(5, cy - 2, LW - 10, 18, BLACK, 60);
        Bond bond = p.bond();
        boolean unlocked = bond != null && bond.unlocked();
        s.icon(PageIcons.HEART, 9, cy + 3, unlocked ? PINK : DGREY);
        String text = bond == null ? t("bond_when_complete") : bond.text().replaceFirst("^\\u2665\\s*", "");
        int textX = 19;
        int avail = (unlocked ? LW - 56 - 4 : LW - 12) - textX;
        int color = unlocked ? PINK : MUTED;
        if (PageSurface.width(text) - 1 <= avail) {
            s.text(textX, cy + 3, text, color);
        } else {
            List<String> lines = PageSurface.wrap(text, avail);
            String first = lines.get(0);
            String second = lines.size() > 1 ? String.join(" ", lines.subList(1, lines.size())) : "";
            s.text(textX, cy, PageSurface.truncate(first, avail), color);
            s.text(textX, cy + 8, PageSurface.truncate(second, avail), color);
            tip(textX, cy - 1, avail, 17, line(text, WHITE));
        }
        if (unlocked) {
            ItemStack species = bond.species();
            bondButton = button(LW - 56, cy - 1, 49, 14, t("bond"), () -> p.actions().openBond(species));
        }
    }

    // ---- right page

    private void rightPage() {
        s.panel(PageSurface.PANEL, RX, TOP, RW, BODY_H);
        Tab tab = p.tab() == Tab.NOTES && !m.loreUnlocked() ? Tab.CATCH : p.tab();

        int tx = RX + 6;
        for (Tab t : Tab.values()) {
            boolean locked = t == Tab.NOTES && !m.loreUnlocked();
            String label = t("tab." + (t == Tab.CATCH ? "catch" : "notes"));
            int tw = PageSurface.width(label) + 9 + (locked ? 8 : 0);
            boolean active = t == tab;
            s.rect(tx, TOP + 4, tw, 13, active ? 0x000000 : 0x3C2422, active ? 110 : 255);
            if (active) s.rect(tx, TOP + 16, tw, 1, GOLD);
            s.text(tx + 4, TOP + 6, label, active ? YELLOW : (locked ? MUTED : GREY));
            if (locked) s.icon(PageIcons.LOCK, tx + tw - 10, TOP + 6, MUTED);
            final Tab target = t;
            if (locked) {
                tip(tx, TOP + 4, tw, 13, line(catchesText("unlock_at", m.thresholds().loreCatches()), GREY));
            } else if (!active) {
                HitArea hit = new HitArea(tw, 13, true).label(label);
                hit.click(() -> p.actions().selectTab(target));
                hits.add(place(hit, tx, TOP + 4));
            }
            tx += tw + 3;
        }

        int ix = RX + 6, iw = RW - 12, y = TOP + 21;
        if (tab == Tab.CATCH) {
            y = catchGuide(ix, y, iw);
        } else {
            s.rect(ix, y, iw, BODY_H - 25, BLACK, 60);
            String lore = m.lore().orElseGet(() -> t("no_lore"));
            List<String> lines = PageSurface.wrap(lore, iw - 10);
            int maxLines = (BODY_H - 35) / 10;
            for (int i = 0; i < Math.min(lines.size(), maxLines); i++) {
                String line = lines.get(i);
                if (i == maxLines - 1 && lines.size() > maxLines) line = PageSurface.truncate(line + "...", iw - 10);
                s.text(ix + 5, y + 5 + i * 10, line, 0xE8DCD0);
            }
            y += 5 + Math.min(lines.size(), maxLines) * 10;
        }
        rightBottom = y;
    }

    private int catchGuide(int x, int y, int w) {
        final int lbl = 46;
        EnvSnapshot env = p.env();

        // Verdict
        if (env != null) {
            CatchVerdict v = CatchVerdict.compute(p.profile(), m.habitsUnlocked(), env);
            int bg, fg;
            switch (v.kind()) {
                case CANT -> { bg = 0x6E2222; fg = RED; }
                case POOR -> { bg = 0x6E4C14; fg = 0xFFCC55; }
                case FAIR -> { bg = 0x34464C; fg = 0xB8E0E8; }
                default -> { bg = 0x225C28; fg = GREEN; }
            }
            s.rect(x, y, w, 23, bg, 235);
            s.rect(x, y, 2, 23, fg);
            s.text(x + 6, y + 3, v.headline().getString(), fg);
            s.textRight(x + w - 4, y + 3, t("verdict.now"), v.kind() == CatchVerdict.Kind.CANT ? 0xE0A0A0 : MUTED);
            s.text(x + 6, y + 13, PageSurface.truncate(v.detail().getString(), w - 10), WHITE);
            tip(x, y, w, 23, line(t("verdict.tooltip"), GREY));
            y += 27;
        }

        // Where
        s.text(x, y + 4, t("row.where"), GREY);
        int zx = x + lbl;
        if (m.zones().isEmpty()) {
            s.text(zx, y + 4, t0("screen.fishtastic.encyclopedia.no_zone_restrictions"), MUTED);
        }
        for (FishProfile.Zone zone : m.zones()) {
            boolean here = env != null && env.zones().contains(zone);
            String name = t("zone." + zone.getSerializedName());
            s.rect(zx - 1, y - 1, 18, 18, here ? GREEN : BLACK, here ? 160 : 70);
            Identifier icon = ZoneIconTextures.get(zone);
            if (icon != null) s.texture(icon, zx, y, 16, 16, ZoneIconTextures.TEXTURE_PX, ZoneIconTextures.TEXTURE_PX);
            if (here) s.outline(zx - 1, y - 1, 18, 18, WHITE);
            int startX = zx;
            zx += 19;
            s.text(zx, y + 4, name, here ? WHITE : 0xD8D0C8);
            zx += PageSurface.width(name) + 6;
            tipNow(startX - 1, y - 1, zx - startX - 5, 18, here, line(name, WHITE));
        }
        y += 20;

        boolean habits = m.habitsUnlocked();
        if (habits && !m.biomes().isEmpty()) {
            int bx = x;
            List<FishProfile.BiomeWeight> weights = p.profile().biomeWeights();
            for (int i = 0; i < m.biomes().size(); i++) {
                FishPageModel.BiomeCondition b = m.biomes().get(i);
                String name = b.name().getString();
                FishPageModel.Condition cond = new FishPageModel.Condition(b.multiplier(), b.tier());
                String word = effectWord(cond);
                int cw = PageSurface.width(name) + PageSurface.width(word) + 10;
                if (bx + cw > x + w) {
                    bx = x;
                    y += 13;
                }
                boolean boost = b.multiplier() >= 1f;
                s.rect(bx, y, cw, 11, boost ? BOOST_BG : PEN_BG, 200);
                s.text(bx + 2, y + 2, name, WHITE);
                s.rect(bx + PageSurface.width(name) + 3, y + 2, 1, 7, BLACK, 90);
                s.text(bx + 6 + PageSurface.width(name), y + 2, word, boost ? 0xC8FFC0 : 0xFFC0C0);
                boolean here = env != null && i < weights.size() && weights.get(i).biome().contains(env.biome());
                if (here) s.outline(bx - 1, y - 1, cw + 2, 13, WHITE);
                tipNow(bx, y, cw, 11, here, line(name, WHITE), line(effectLine(b.tier(), b.multiplier()), GREY));
                bx += cw + 3;
            }
            y += 15;
        }
        y += 1;

        if (!habits) {
            s.rect(x, y, w, 30, BLACK, 60);
            s.icon(PageIcons.LOCK, x + 5, y + 6, MUTED);
            s.text(x + 14, y + 6, t("habits_locked"), MUTED);
            s.text(x + 14, y + 17, catchesText("unlock_at", m.thresholds().spawnConditionsCatches()), GREY);
            y += 34;
        } else {
            y = habitStrips(x, y, w, lbl, env);
        }

        // Bait
        y += 2;
        s.text(x, y + 4, t("row.bait"), GREY);
        if (m.baitUnlocked()) {
            if (m.baits().isEmpty()) {
                s.text(x + lbl, y + 4, t("no_special_bait"), MUTED);
                y += 16;
            } else {
                int colW = m.baits().size() > 1 ? (w - lbl) / 2 : w - lbl;
                for (int i = 0; i < m.baits().size(); i++) {
                    ItemStack bait = m.baits().get(i);
                    int bx = x + lbl + (i % 2) * colW;
                    int by = y + (i / 2) * 18;
                    String full = Component.translatable(bait.getItem().getDescriptionId()).getString();
                    String shown = PageSurface.truncate(full, colW - 21);
                    s.rect(bx - 1, by - 1, 18, 18, BLACK, 70);
                    s.text(bx + 19, by + 4, shown, WHITE);
                    overlays.add(place(UI.itemRenderer(bait).showCount(false), bx, by));
                    if (!shown.equals(full)) tip(bx - 1, by - 1, colW - 2, 18, line(full, WHITE));
                }
                y += 18 * ((m.baits().size() + 1) / 2) + 2;
            }
        } else {
            s.icon(PageIcons.LOCK, x + lbl, y + 4, MUTED);
            s.text(x + lbl + 8, y + 4, catchesText("at_catches", m.thresholds().typesCatches()), MUTED);
            y += 16;
        }
        return y;
    }

    private int habitStrips(int x, int y, int w, int lbl, EnvSnapshot env) {
        int cw = (w - lbl) / 4;

        // When
        s.text(x, y + 3, t("row.when"), GREY);
        if (!m.times().isEmpty()) {
            FishProfile.TimeOfDay[] order = {FishProfile.TimeOfDay.DAWN, FishProfile.TimeOfDay.DAY,
                    FishProfile.TimeOfDay.DUSK, FishProfile.TimeOfDay.NIGHT};
            String[][] icons = {PageIcons.DAWN, PageIcons.SUN, PageIcons.DUSK, PageIcons.NIGHT};
            int[] rgbs = {PageIcons.DAWN_RGB, PageIcons.SUN_RGB, PageIcons.DUSK_RGB, PageIcons.NIGHT_RGB};
            for (int i = 0; i < 4; i++) {
                FishPageModel.Condition c = m.times().get(order[i]);
                boolean now = env != null && env.time() == order[i];
                strip(x + lbl + i * cw, y, cw, c, icons[i], rgbs[i], t("time." + order[i].getSerializedName()), now, true);
            }
            y += 18;
        } else {
            s.text(x + lbl, y + 3, t("time.any"), MUTED);
            y += 14;
        }

        // Weather / sky
        boolean weather = !m.weathers().isEmpty(), moon = !m.moons().isEmpty();
        if (!weather && !moon) {
            s.text(x, y + 3, t("row.sky"), GREY);
            s.text(x + lbl, y + 3, t("sky.any"), MUTED);
            y += 14;
        } else if (weather) {
            s.text(x, y + 3, t("row.weather"), GREY);
            FishProfile.WeatherCondition[] order = FishProfile.WeatherCondition.values();
            String[][] icons = {PageIcons.SUN, PageIcons.RAIN, PageIcons.SNOW, PageIcons.THUNDER};
            int[] rgbs = {PageIcons.SUN_RGB, PageIcons.RAIN_RGB, PageIcons.SNOW_RGB, PageIcons.THUNDER_RGB};
            for (int i = 0; i < 4; i++) {
                FishPageModel.Condition c = m.weathers().get(order[i]);
                boolean now = env != null && env.weather() == order[i];
                strip(x + lbl + i * cw, y, cw, c, icons[i], rgbs[i], t("weather." + order[i].getSerializedName()), now, false);
            }
            y += 18;
        } else {
            s.text(x, y + 3, t("row.weather"), GREY);
            s.text(x + lbl, y + 3, t("weather.any"), MUTED);
            y += 14;
        }

        // Moon: only when it matters
        if (moon) {
            s.text(x, y + 3, t("row.moon"), GREY);
            int mw = (w - lbl) / 8;
            FishMoonPhase[] phases = FishMoonPhase.values();
            for (int ph = 0; ph < 8; ph++) {
                FishPageModel.Condition c = m.moons().get(phases[ph]);
                int cx = x + lbl + ph * mw;
                s.rect(cx, y, mw - 1, 14, cellBg(c), 230);
                s.icon(PageIcons.moon(ph), cx + (mw - 8) / 2, y + 3, 0xF4F0D0, 0x464250);
                boolean now = env != null && env.moonVisible() && env.moon() == phases[ph];
                if (now) s.outline(cx - 1, y - 1, mw + 1, 16, WHITE);
                String name = t("moon." + phases[ph].getSerializedName());
                if (c != null) {
                    tipNow(cx, y, mw - 1, 14, now, line(name, WHITE), line(effectLine(c.tier(), c.multiplier()), GREY));
                } else {
                    tipNow(cx, y, mw - 1, 14, now, line(name, WHITE));
                }
            }
            y += 16;
            s.text(x + lbl, y, PageSurface.truncate(t("moon.note"), w - lbl), MUTED);
            y += 11;
        }
        return y;
    }

    /** One time-of-day / weather cell: fill = effect on the odds, white outline (+ pin) = true right now. */
    private void strip(int cx, int y, int cw, FishPageModel.Condition c, String[] icon, int iconRgb, String name,
                       boolean now, boolean pin) {
        s.rect(cx, y, cw - 2, 14, cellBg(c), 230);
        s.icon(icon, cx + 2, y + 3, iconRgb);
        String word = c == null ? t("effect.none") : effectWord(c);
        s.text(cx + 11, y + 3, PageSurface.truncate(word, cw - 13), c == null ? MUTED : WHITE);
        if (now) {
            s.outline(cx - 1, y - 1, cw, 16, WHITE);
            if (pin) s.icon(PageIcons.PIN, cx + cw / 2 - 3, y - 4, WHITE);
        }
        if (c != null) {
            tipNow(cx, y, cw - 2, 14, now, line(name, WHITE), line(effectLine(c.tier(), c.multiplier()), GREY));
        } else {
            tipNow(cx, y, cw - 2, 14, now, line(name, WHITE));
        }
    }

    // ---- footer

    private void footer() {
        int fy0 = TOP + BODY_H + 3;
        int fh = H - fy0;
        s.panel(PageSurface.PANEL, 0, fy0, W, fh);

        List<FishPageModel.Milestone> ms = m.milestones();
        int claimW = 70;
        int trackX0 = 18, trackX1 = W - claimW - 30;
        float step = (trackX1 - trackX0) / (float) ms.size();
        int nodeY = fy0 + 7;
        s.rect(trackX0, nodeY + 2, (int) (step * ms.size()), 1, 0x3C2624);
        int next = -1;
        for (int i = 0; i < ms.size(); i++) {
            if (!ms.get(i).reached()) {
                next = i;
                break;
            }
        }
        for (int i = 0; i < ms.size(); i++) {
            FishPageModel.Milestone ms1 = ms.get(i);
            int nx = (int) (trackX0 + step * i + step / 2);
            if (ms1.reached() && i > 0) {
                s.rect((int) (trackX0 + step * (i - 1) + step / 2), nodeY + 2, (int) step, 1, GREEN);
            }
            s.rect(nx - 3, nodeY - 1, 7, 7, BLACK, 160);
            s.rect(nx - 2, nodeY, 5, 5, ms1.reached() ? 0x55DC5A : i == next ? 0xFFD250 : 0x5A403C);
            String label = ms1.label().getString();
            if (i == next) label = t("milestone.progress", label, m.catches(), ms1.threshold());
            int col = ms1.reached() ? WHITE : i == next ? YELLOW : MUTED;
            s.textCentered(nx, nodeY + 8, label, col);
            int half = (PageSurface.width(label) - 1) / 2;
            tip(nx - Math.max(half, 5), nodeY - 1, Math.max(half, 5) * 2 + 1, 18,
                    line(ms1.label().getString(), WHITE), line(catchesText("unlock_at", ms1.threshold()), GREY));
        }
        int bx = trackX1 + 10;
        s.icon(PageIcons.HEART, bx - 3, nodeY - 1, m.complete() ? PINK : DGREY);
        s.textCentered(bx, nodeY + 8, t("milestone.bond"), m.complete() ? PINK : MUTED);
        s.textCentered(9, nodeY - 1, String.valueOf(m.catches()), YELLOW);

        // Claim
        int cx0 = W - claimW - 6;
        int unclaimed = m.unclaimedCoins();
        if (unclaimed > 0) {
            String label = t("claim", unclaimed);
            int bw = Math.max(claimW, 6 + PageSurface.width(label) + 3 + 16 + 5);
            int bh = fh - 8;
            int bxx = W - 6 - bw;
            int by = fy0 + 4;
            s.panel(PageSurface.PANEL_GREEN, bxx, by, bw, bh);
            s.text(bxx + 6, by + (bh - 8) / 2, label, WHITE);
            overlays.add(place(EncyclopediaCoins.icon(unclaimed), bxx + bw - 5 - 16, by + (bh - 16) / 2));
            HitArea hit = new HitArea(bw, bh, true).label(label);
            hit.tip(p.ui(), ScaledTooltip.of(p.k(), line(Component.translatable("tutorial.fishtastic.rewards.help").getString(), WHITE)));
            hit.click(() -> p.actions().claim(hit));
            hits.add(place(hit, bxx, by));
        } else if (m.catches() > 0) {
            s.icon(PageIcons.CHECK, cx0 + 8, fy0 + (fh - 7) / 2, GREEN);
            s.text(cx0 + 18, fy0 + (fh - 8) / 2, PageSurface.truncate(t("claimed"), claimW - 20), MUTED);
        }
    }

}
