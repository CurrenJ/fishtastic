package grill24.fishtastic.client.encyclopedia.page;

import grill24.fishtastic.client.encyclopedia.CatchVerdict;
import grill24.fishtastic.client.encyclopedia.FishPageModel;
import grill24.fishtastic.data.FishProfile;
import grill24.fishtastic.client.encyclopedia.portrait.PortraitTheme;
import io.github.currenj.gelatinui.gui.UIElement;
import grill24.fishtastic.client.FreeformContainer;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;


/**
 * The narrow-window fallback for the encyclopedia. The full spread is 416x232, which is wider
 * than a small 4:3 or windowed GUI. This 300x228 card keeps the same live verdict, tabs and
 * actions without asking the player to scroll sideways or clipping the portrait page.
 */
public final class CompactPageLayout {
    public static final int W = 300, H = 228;
    public static final int TOP = 17, BODY_H = 185, FOOTER_Y = 204;
    /** Top-left of the 32px hero item inside the card. */
    public static final int FISH_X = 9, FISH_Y = TOP + 7;
    public static final float FISH_SCALE = 2f;

    private static final int WHITE = 0xFFFFFF, GREY = 0xAAAAAA, GOLD = 0xFFAA00;
    private static final int YELLOW = 0xFFFF55, MUTED = 0x8C7F7A;
    private static final int RED = 0xFF5555, GREEN = 0x55FF55, BLUE_GREY = 0xAAB8C8;
    private static final int HERO_BG = 0x183F54, LOCK_BG = 0x3A2422;

    private final EncyclopediaPageLayout.Params p;
    private final FishPageModel m;
    private final PageSurface surface = new PageSurface(W, H);
    private final List<UIElement<?>> hits = new ArrayList<>();
    private UIElement<?> bondButton;

    private CompactPageLayout(EncyclopediaPageLayout.Params p) {
        this.p = p;
        this.m = p.model();
    }

    /** The Bond button (when available) and the lowest content pixel. */
    public record Result(UIElement<?> bondButton, int bottom) {}

    public static Result build(FreeformContainer host, EncyclopediaPageLayout.Params params) {
        CompactPageLayout layout = new CompactPageLayout(params);
        layout.layout();
        host.addChild(layout.surface);
        for (UIElement<?> hit : layout.hits) {
            if (hit instanceof HitArea area) area.bind(layout.surface);
            host.addChild(hit);
        }
        return new Result(layout.bondButton, H);
    }

    private void layout() {
        header();
        hero();
        tabs();
        if (p.tab() == EncyclopediaPageLayout.Tab.NOTES) notes();
        else catchGuide();
        footer();
    }

    private void header() {
        button(0, 0, 34, 14, "<", p.actions()::close);
        surface.textCentered(W / 2, 3, PageSurface.truncate(m.displayName().getString(), 210), GOLD);
        surface.textRight(W - 34, 3, translated("index", p.index() + 1, p.total()), GREY);
        button(W - 31, 0, 14, 14, "<", () -> p.actions().browse(-1));
        // Keep the edge-aligned next arrow easy to hit at every GUI scale.
        button(W - 15, 0, 15, 14, ">", () -> p.actions().browse(1))
                .hitPadding(1, 1, 1, 1);
    }

    private void hero() {
        surface.panel(m.complete() ? PageSurface.PANEL_GOLD : PageSurface.PANEL, 0, TOP, W, BODY_H);
        surface.texture(PortraitTheme.forZone(m.themeZone()).plateTexture, FISH_X - 3, TOP + 4, 38, 36, 156, 66);

        String title = PageSurface.truncate(m.displayName().getString(), 210);
        surface.text(50, TOP + 6, title, m.nameRevealed() ? WHITE : GREY);
        if (m.nameRevealed() && m.rarity() != null) {
            for (int i = 0; i < 5; i++) {
                surface.rect(50 + i * 6, TOP + 17, 4, 6, i < m.rarity().gems() ? 0x6ECDE1 : 0x462E34);
            }
            surface.text(82, TOP + 16, PageSurface.truncate(
                    "~1 in " + m.rarity().oneInN() + " " + m.rarity().pool().getString(), 105), MUTED);
        } else {
            surface.text(50, TOP + 18, translated("not_yet_caught"), GREY);
        }

        String size = m.sizeUnlocked()
                ? String.format(java.util.Locale.ROOT, "%.0f-%.0f cm", m.sizeMean() - 2 * m.sizeStdDev(), m.sizeMean() + 2 * m.sizeStdDev())
                : translated("size_locked.one", Math.max(1, m.thresholds().statsCatches()));
        surface.text(50, TOP + 28, PageSurface.truncate(size, 105), GREY);
        String you = m.personalBest().map(best -> "You " + String.format(java.util.Locale.ROOT, "%.0f cm", best.size())).orElse("");
        if (!you.isEmpty()) surface.text(158, TOP + 28, PageSurface.truncate(you, 58), WHITE);
        String server = m.serverBest().map(best -> "Best " + String.format(java.util.Locale.ROOT, "%.0f cm", best.size())).orElse("");
        if (!server.isEmpty()) surface.text(50, TOP + 39, PageSurface.truncate(server, 160), GOLD);
    }

    private void tabs() {
        int x = 6;
        x = tab(x, translated("tab.catch"), EncyclopediaPageLayout.Tab.CATCH);
        tab(x + 3, translated("tab.notes"), EncyclopediaPageLayout.Tab.NOTES);
    }

    private int tab(int x, String label, EncyclopediaPageLayout.Tab target) {
        int w = Math.min(124, PageSurface.width(label) + 9);
        boolean selected = p.tab() == target;
        surface.rect(x, TOP + 45, w, 13, selected ? 0x000000 : 0x3C2422, selected ? 110 : 255);
        if (selected) surface.rect(x, TOP + 57, w, 1, GOLD);
        surface.text(x + 4, TOP + 47, PageSurface.truncate(label, w - 8), selected ? YELLOW : GREY);
        HitArea hit = new HitArea(w, 14, true).label(label).click(() -> p.actions().selectTab(target));
        hits.add(place(hit, x, TOP + 44));
        return x + w;
    }

    private void catchGuide() {
        int x = 6, y = TOP + 62, w = W - 12;
        CatchVerdict verdict = m.habitsUnlocked() && p.env() != null && p.profile() != null
                ? CatchVerdict.compute(p.profile(), true, p.env())
                : p.profile() == null || p.env() == null
                ? new CatchVerdict(CatchVerdict.Kind.CAN_LOCKED, literal("Can bite here"), literal("Learn its habits to judge this spot"), List.of(), List.of())
                : CatchVerdict.compute(p.profile(), false, p.env());
        int verdictBg = verdict.kind() == CatchVerdict.Kind.CANT ? 0x8C2C2C : 0x2E7834;
        int verdictText = verdict.kind() == CatchVerdict.Kind.CANT ? RED : GREEN;
        surface.rect(x, y, w, 31, verdictBg);
        surface.text(x + 6, y + 4, PageSurface.truncate(verdict.headline().getString(), w - 12), verdictText);
        surface.text(x + 6, y + 16, PageSurface.truncate(verdict.detail().getString(), w - 12), WHITE);

        int rowY = y + 38;
        surface.text(x, rowY, translated("row.where"), GREY);
        surface.text(x + 43, rowY, PageSurface.truncate(zoneText(), w - 43), WHITE);
        rowY += 18;

        if (m.habitsUnlocked()) {
            surface.text(x, rowY, translated("row.when"), GREY);
            surface.text(x + 43, rowY, PageSurface.truncate(verdict.detail().getString(), w - 43), BLUE_GREY);
        } else {
            surface.rect(x, rowY - 2, w, 24, LOCK_BG);
            surface.text(x + 7, rowY, "? " + translated("habits_locked"), GREY);
            surface.text(x + 7, rowY + 10, PageSurface.truncate(
                    translated("unlock_at.one", m.thresholds().spawnConditionsCatches()), w - 14), WHITE);
        }
        rowY += 27;

        surface.text(x, rowY, "Bait", GREY);
        String bait = m.baitUnlocked() && !m.baits().isEmpty()
                ? joinItems(m.baits())
                : m.baitUnlocked() ? translated("no_special_bait")
                : translated("unlock_at.one", m.thresholds().typesCatches());
        surface.text(x + 43, rowY, PageSurface.truncate(bait, w - 43), m.baitUnlocked() ? WHITE : GREY);
    }

    private void notes() {
        int x = 6, y = TOP + 62, w = W - 12;
        if (!m.loreUnlocked() || m.lore().isEmpty()) {
            surface.rect(x, y, w, 26, LOCK_BG);
            surface.text(x + 7, y + 5, "? " + translated("milestone.notes"), GREY);
            surface.text(x + 7, y + 15, PageSurface.truncate(
                    translated("unlock_at.other", m.thresholds().loreCatches()), w - 14), WHITE);
            return;
        }
        int lineY = y;
        for (String line : PageSurface.wrap(m.lore().get(), w - 12)) {
            if (lineY > FOOTER_Y - 12) break;
            surface.text(x + 6, lineY, line, WHITE);
            lineY += 9;
        }
    }

    private void footer() {
        surface.panel(PageSurface.PANEL, 0, FOOTER_Y, W, 24);
        String progress;
        FishPageModel.Milestone next = m.milestones().stream().filter(milestone -> !milestone.reached()).findFirst().orElse(null);
        if (next == null) {
            progress = translated("all_unlocks_done");
        } else {
            progress = translated("milestone.progress", next.label().getString(), m.catches(), next.threshold());
        }
        surface.text(6, FOOTER_Y + 8, PageSurface.truncate(progress, 210), WHITE);

        if (m.unclaimedCoins() > 0) {
            int bw = 66;
            PageButton[] claim = new PageButton[1];
            claim[0] = new PageButton(bw, 14, translated("claim", m.unclaimedCoins()), () -> p.actions().claim(claim[0]));
            hits.add(place(claim[0], W - bw - 6, FOOTER_Y + 5));
        } else if (p.bond() != null && p.bond().unlocked()) {
            int bw = 48;
            PageButton bond = new PageButton(bw, 14, translated("bond"), () -> p.actions().openBond(p.bond().species()));
            bondButton = bond;
            hits.add(place(bond, W - bw - 6, FOOTER_Y + 5));
        } else if (p.bond() != null) {
            surface.textRight(W - 6, FOOTER_Y + 8, PageSurface.truncate(p.bond().text(), 76), MUTED);
        }
    }

    private String zoneText() {
        if (m.zones().isEmpty()) return translated("verdict.fish_in", translated("zone.river"));
        List<String> names = new ArrayList<>();
        for (FishProfile.Zone zone : m.zones()) names.add(translated("zone." + zone.getSerializedName()));
        return String.join(" / ", names);
    }

    private static String joinItems(List<ItemStack> items) {
        List<String> names = new ArrayList<>();
        for (ItemStack item : items) names.add(item.getHoverName().getString());
        return String.join(", ", names);
    }

    private PageButton button(int x, int y, int w, int h, String label, Runnable action) {
        PageButton button = new PageButton(w, h, label, action);
        hits.add(place(button, x, y));
        return button;
    }

    private static <E extends UIElement<?>> E place(E element, int x, int y) {
        element.setPosition(new Vector2f(x, y));
        return element;
    }

    private static String translated(String key, Object... args) {
        return net.minecraft.network.chat.Component.translatable("screen.fishtastic.encyclopedia." + key, args).getString();
    }

    private static net.minecraft.network.chat.Component literal(String text) {
        return net.minecraft.network.chat.Component.literal(text);
    }
}
