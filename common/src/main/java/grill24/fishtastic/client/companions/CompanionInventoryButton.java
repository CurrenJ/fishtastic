package grill24.fishtastic.client.companions;

import grill24.fishtastic.client.FishtasticClientConfig;
import grill24.fishtastic.client.FishtasticKeyBinds;
import grill24.fishtastic.client.QuestClientCache;
import grill24.fishtastic.companion.CompanionParty;
import grill24.fishtastic.companion.CompanionStorage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The way into the companions screen from the inventory (layout-spec §6): a heart tab on the top
 * edge of the survival inventory, and an 18x18 heart button inside the creative Inventory tab (the
 * creative edges belong to the creative tabs). Always shown, locked-looking until the first species
 * unlocks, so players learn early that companions exist. Hidden by {@code showInventoryTab}.
 */
public final class CompanionInventoryButton {
    private CompanionInventoryButton() {}

    private static final int TAB_W = 22, TAB_H = 17, TAB_DX = 150, TAB_DY = -14;
    private static final int BTN = 18, BTN_DX = 150, BTN_DY = 19;
    private static final int SHELL = 0xFF201A24, HOT = 0xFF2E2430, EDGE = 0xFF72505B, GOLD = 0xFFFFAA00,
            PINK = 0xFFF27A9A, LOCKED = 0xFF6E5E66, MUTED = 0xFFC9B8BE, PANEL = 0xFFC6C6C6;
    private static final String[] HEART = CompanionsScreen.HEART, LOCK = CompanionsScreen.LOCK;

    private record Rect(int x, int y, int w, int h, boolean creative) {
        boolean contains(double px, double py) { return px >= x && px < x + w && py >= y && py < y + h; }
    }

    private static Rect rect(Screen screen, int leftPos, int topPos) {
        if (!FishtasticClientConfig.showInventoryTab()) return null;
        if (screen instanceof InventoryScreen) return new Rect(leftPos + TAB_DX, topPos + TAB_DY, TAB_W, TAB_H, false);
        if (screen instanceof CreativeModeInventoryScreen creative && creative.isInventoryOpen()) {
            return new Rect(leftPos + BTN_DX, topPos + BTN_DY, BTN, BTN, true);
        }
        return null;
    }

    private static boolean unlocked() {
        return !QuestClientCache.getUnlockedCompanions().isEmpty();
    }

    public static void render(Screen screen, GuiGraphics g, int leftPos, int topPos, int mouseX, int mouseY) {
        Rect r = rect(screen, leftPos, topPos);
        if (r == null) return;
        boolean hot = r.contains(mouseX, mouseY), open = unlocked();
        if (r.creative()) {
            g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), hot ? HOT : SHELL);
            g.renderOutline(r.x(), r.y(), r.w(), r.h(), hot ? GOLD : EDGE);
        } else {
            g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), hot ? HOT : SHELL);
            g.fill(r.x(), r.y(), r.x() + r.w(), r.y() + 1, hot ? GOLD : EDGE);
            g.fill(r.x(), r.y(), r.x() + 1, r.y() + r.h(), hot ? GOLD : EDGE);
            g.fill(r.x() + r.w() - 1, r.y(), r.x() + r.w(), r.y() + r.h(), hot ? GOLD : EDGE);
            // The lower 3 px merge into the panel's light top edge, so the tab reads as attached.
            g.fill(r.x() + 1, r.y() + r.h() - 3, r.x() + r.w() - 1, r.y() + r.h(), PANEL);
        }
        int hx = r.x() + (r.w() - 7) / 2 - (open ? 0 : 2), hy = r.y() + (r.creative() ? 6 : 4);
        icon(g, HEART, hx, hy, open ? PINK : LOCKED);
        if (!open) icon(g, LOCK, hx + 6, hy + 4, MUTED);
        if (hot) g.renderTooltip(Minecraft.getInstance().font, tooltip(), Optional.empty(), mouseX, mouseY);
    }

    /** True when the click landed on the button (the screen then does nothing else with it). */
    public static boolean click(Screen screen, int leftPos, int topPos, double mouseX, double mouseY, int button) {
        Rect r = rect(screen, leftPos, topPos);
        if (r == null || button != 0 || !r.contains(mouseX, mouseY)) return false;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        CompanionsScreen.open(ItemStack.EMPTY, false);
        return true;
    }

    private static List<Component> tooltip() {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = new ArrayList<>();
        String key = FishtasticKeyBinds.bondCompanion == null ? "B" : FishtasticKeyBinds.bondCompanion.getTranslatedKeyMessage().getString();
        lines.add(CompanionsScreen.tr("tab.title", key));
        if (unlocked()) {
            int equipped = 0;
            if (mc.player != null) for (int i = 0; i < CompanionParty.SIZE; i++) if (!CompanionStorage.get(mc.player, i).isEmpty()) equipped++;
            lines.add(CompanionsScreen.tr("count.equipped", equipped, CompanionParty.SIZE).withColor(MUTED));
        } else {
            lines.add(CompanionsScreen.tr("tab.locked").withColor(MUTED));
        }
        return lines;
    }

    private static void icon(GuiGraphics g, String[] rows, int x, int y, int c) {
        for (int j = 0; j < rows.length; j++) for (int i = 0; i < rows[j].length(); i++) {
            if (rows[j].charAt(i) == '#') g.fill(x + i, y + j, x + i + 1, y + j + 1, c);
        }
    }
}
