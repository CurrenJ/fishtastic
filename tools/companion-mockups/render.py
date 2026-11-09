"""Companion screen mockups (docs/companion-ui-redesign/layout-spec.md).

Draws each screen at true logical GUI pixels (480x270 reference viewport, the Captain's Log
reference) with the vanilla font sheet and the mod's own textures, then upscales 3x nearest.

    python -I tools/companion-mockups/render.py <minecraft-client.jar> <out dir>
"""
import io
import sys
import zipfile
from pathlib import Path

from PIL import Image

REPO = Path(__file__).resolve().parents[2]
TEX = REPO / "common/src/main/resources/assets/fishtastic/textures"
SCALE = 3
W, H = 480, 270

# House palette (QuestLogScreen / quest-log design spec).
SHELL, RAIL, EDGE = 0xFF201A24, 0xFF2A202B, 0xFF72505B
MUTED, CLOSE_HINT, TITLE = 0xFFC9B8BE, 0xFF8E7E87, 0xFFEBDAD1
GOLD, READY, PROGRESS, WHITE = 0xFFFFAA00, 0xFF5DCC7A, 0xFFB86E88, 0xFFFFFFFF
RAIL_SEL, RAIL_HOVER, FILTER_SEL = 0xFF714452, 0xFF3A2B37, 0xFF704450
TRACK, THUMB, THUMB_HI = 0xFF352A32, 0xFFC58A58, 0xFFFFE0A0
TOKEN_BOX, FIELD, ROW_HOVER = 0xFF3B2A29, 0xFF15101A, 0xFF2E2430
PINK, RED = 0xFFF27A9A, 0xFFE02424
QUALITY = {"Common": 0xFFAAAAAA, "Uncommon": 0xFF55FF55, "Rare": 0xFF8888FF,
           "Epic": 0xFFFF55FF, "Legendary": 0xFFFFAA00}


def rgba(c, a=None):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255, (c >> 24) & 255 if a is None else a)


class Canvas:
    def __init__(self, font_png, w=W, h=H):
        self.im = Image.new("RGBA", (w, h), (0, 0, 0, 255))
        self.font = font_png
        self.widths = {}
        for code in range(256):
            gx, gy = (code % 16) * 8, (code // 16) * 8
            width = 0
            for x in range(8):
                if any(font_png.getpixel((gx + x, gy + y))[3] for y in range(8)):
                    width = x + 1
            self.widths[code] = width
        self.widths[32] = 3

    # -- primitives
    def fill(self, x0, y0, x1, y1, c):
        if x1 <= x0 or y1 <= y0:
            return
        layer = Image.new("RGBA", (x1 - x0, y1 - y0), rgba(c))
        self.im.alpha_composite(layer, (x0, y0))

    def outline(self, x, y, w, h, c):
        self.fill(x, y, x + w, y + 1, c)
        self.fill(x, y + h - 1, x + w, y + h, c)
        self.fill(x, y, x + 1, y + h, c)
        self.fill(x + w - 1, y, x + w, y + h, c)

    def pixels(self, x, y, rows, c, scale=1):
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "#":
                    self.fill(x + i * scale, y + j * scale, x + (i + 1) * scale, y + (j + 1) * scale, c)

    def sprite(self, img, x, y, w=None, h=None):
        img = img.convert("RGBA")
        if w:
            img = img.resize((w, h), Image.NEAREST)
        self.im.alpha_composite(img, (x, y))

    def nine(self, img, x, y, w, h, b=4):
        img = img.convert("RGBA")
        sw, sh = img.size
        cols = [(0, b, 0, b), (b, sw - b, b, w - b), (sw - b, sw, w - b, w)]
        rows = [(0, b, 0, b), (b, sh - b, b, h - b), (sh - b, sh, h - b, h)]
        for u0, u1, dx0, dx1 in cols:
            for v0, v1, dy0, dy1 in rows:
                if dx1 > dx0 and dy1 > dy0:
                    part = img.crop((u0, v0, u1, v1)).resize((dx1 - dx0, dy1 - dy0), Image.NEAREST)
                    self.im.alpha_composite(part, (x + dx0, y + dy0))

    # -- text (vanilla bitmap font, 1px drop shadow at 25% like vanilla)
    def width(self, s):
        return sum(self.widths.get(ord(ch), 5) + 1 for ch in s) - (1 if s else 0)

    def text(self, x, y, s, c=WHITE, shadow=True):
        if shadow:
            r, g, b, a = rgba(c)
            self._glyphs(x + 1, y + 1, s, (r // 4, g // 4, b // 4, a))
        self._glyphs(x, y, s, rgba(c))
        return x + self.width(s)

    def text_c(self, cx, y, s, c=WHITE, shadow=True):
        return self.text(cx - self.width(s) // 2, y, s, c, shadow)

    def text_r(self, rx, y, s, c=WHITE, shadow=True):
        return self.text(rx - self.width(s), y, s, c, shadow)

    def _glyphs(self, x, y, s, color):
        for ch in s:
            code = ord(ch)
            if code < 256:
                gx, gy = (code % 16) * 8, (code // 16) * 8
                glyph = self.font.crop((gx, gy, gx + 8, gy + 8))
                tint = Image.new("RGBA", (8, 8), color)
                tint.putalpha(glyph.split()[3])
                self.im.alpha_composite(tint, (x, y))
            x += self.widths.get(code, 5) + 1

    def save(self, path):
        self.im.resize((self.im.width * SCALE, self.im.height * SCALE), Image.NEAREST).save(path)


# -- pixel icons (no Mojang glyphs beyond ASCII)
HEART = [".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."]
STAR = ["..#..", ".###.", "#####", ".###.", ".#.#."]
CROWN = ["#.#.#", "#####", "#####"]
CHEV_R = ["#..", "##.", "###", "##.", "#.."]
CHEV_D = ["#####", ".###.", "..#.."]
CROSS = ["#...#", ".#.#.", "..#..", ".#.#.", "#...#"]
LENS = [".##..", "#..#.", "#..#.", ".##..", "...##"]
LOCK = [".###.", "#...#", "#####", "##.##", "#####"]
UP = ["..#..", ".###.", "#####", "..#..", "..#.."]
PILE = [".##.", "####", "####"]


def pencil(cv, x, y, enabled=True):
    tip, wood, barrel, shade, eraser = ((0xFF2B2B2B, 0xFFE3B778, 0xFFF4C430, 0xFFC9971C, 0xFFEE8899)
                                        if enabled else (0xFF6A6A6A,) + (0xFF8A8A8A,) * 4)
    for px, py, c in [(0, 5, tip), (1, 4, wood), (2, 3, barrel), (3, 2, barrel), (4, 1, barrel),
                      (2, 4, shade), (3, 3, shade), (4, 2, shade), (5, 0, eraser), (5, 1, eraser)]:
        cv.fill(x + px, y + py, x + px + 1, y + py + 1, c)


class Assets:
    def __init__(self, jar):
        with zipfile.ZipFile(jar) as z:
            def mc(path):
                return Image.open(io.BytesIO(z.read("assets/minecraft/textures/" + path))).convert("RGBA")
            self.font = mc("font/ascii.png")
            self.inventory = mc("gui/container/inventory.png")
            self.creative = mc("gui/container/creative_inventory/tab_inventory.png")
            self.tabs = {name: mc(f"gui/sprites/container/creative_inventory/{name}.png")
                         for name in [f"tab_{row}_{state}_{i}" for row in ("top", "bottom")
                                      for state in ("selected", "unselected") for i in range(1, 8)]}
        self.panel = Image.open(TEX / "gui/generic_item_panel.png")
        self.panel_gold = Image.open(TEX / "gui/generic_item_panel_gold_border.png")
        self.panel_silver = Image.open(TEX / "gui/generic_item_panel_silver_border.png")
        self.pile = Image.open(TEX / "item/pile_of_fish.png")

    def fish(self, name):
        img = Image.open(TEX / f"item/fish/{name}.png").convert("RGBA")
        return img.crop((0, 0, img.width, img.width))  # first frame of animated strips


def silhouette(img, c=0xFF140F16):
    out = Image.new("RGBA", img.size, rgba(c))
    out.putalpha(img.split()[3].point(lambda a: 255 if a > 40 else 0))
    return out


def dimmed(img, factor=0.45):
    img = img.convert("RGBA")
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            grey = int((r + g + b) / 3 * factor)
            px[x, y] = (grey, grey, grey, a)
    return img


# =====================================================================================
# Shell
SX, SY, SW, SH = 32, 19, 416, 232
RAIL_W, GAP = 74, 4
PX, PW = SX + RAIL_W + GAP, 338
INSET = 11
CX = PX + INSET            # content left
CW = PW - INSET * 2        # 316


def backdrop(cv):
    for y in range(H):
        t = y / H
        cv.fill(0, y, W, y + 1, 0xFF000000 | (int(18 + 10 * t) << 16) | (int(30 + 14 * t) << 8) | int(40 + 10 * t))
    cv.fill(0, 0, W, H, 0x9910080C)


def shell(cv, a, destination):
    backdrop(cv)
    cv.fill(SX, SY, SX + RAIL_W, SY + SH, RAIL)
    cv.outline(SX, SY, RAIL_W, SH, EDGE)
    cv.fill(PX, SY, PX + PW, SY + SH, SHELL)
    cv.outline(PX, SY, PW, SH, EDGE)
    cv.text(SX + 10, SY + 7, "FISH", TITLE)
    cv.text(SX + 10, SY + 17, "COMPANIONS", GOLD)
    entries = [("equipped", "Equipped", HEART), ("unlocks", "Unlocks", STAR)]
    y = SY + 34
    for key, label, icon in entries:
        active = key == destination
        if active:
            cv.fill(SX + 4, y, SX + RAIL_W - 4, y + 20, RAIL_SEL)
            cv.fill(SX + 4, y, SX + 6, y + 20, GOLD)
        cv.pixels(SX + 10, y + 7, icon, PINK if icon is HEART else (GOLD if active else MUTED))
        cv.text(SX + 20, y + 6, label, WHITE if active else MUTED)
        y += 24
    cv.fill(SX + 8, SY + SH - 46, SX + RAIL_W - 8, SY + SH - 45, EDGE)
    cv.text(SX + 10, SY + SH - 38, "[B]", MUTED)
    cv.text(SX + 10, SY + SH - 28, "opens this", CLOSE_HINT)
    cv.text(SX + 10, SY + SH - 14, "Esc  close", CLOSE_HINT)


def count_box(cv, label):
    w = max(87, cv.width(label) + 23)
    x, y = PX + PW - INSET - w, SY + 6
    cv.fill(x, y, x + w, y + 18, TOKEN_BOX)
    cv.pixels(x + 6, y + 6, HEART, PINK)
    cv.text(x + 17, y + 5, label, GOLD)


# -- cards ----------------------------------------------------------------------------
CARD_Y, CARD_W, CARD_H, CARD_GAP = SY + 26, 100, 60, 8


def card_x(i):
    return CX + i * (CARD_W + CARD_GAP)


def card(cv, a, i, fish=None, name="", sub="", sub_color=MUTED, hovered=False, mode=None, editing=None):
    x, y = card_x(i), CARD_Y
    if fish is None:
        cv.fill(x, y, x + CARD_W, y + CARD_H, 0xFF261E29)
        for dx in range(0, CARD_W, 4):                      # dashed empty frame
            cv.fill(x + dx, y, x + dx + 2, y + 1, EDGE)
            cv.fill(x + dx, y + CARD_H - 1, x + dx + 2, y + CARD_H, EDGE)
        for dy in range(0, CARD_H, 4):
            cv.fill(x, y + dy, x + 1, y + dy + 2, EDGE)
            cv.fill(x + CARD_W - 1, y + dy, x + CARD_W, y + dy + 2, EDGE)
        cv.pixels(x + CARD_W // 2 - 5, y + 16, ["..#..", "..#..", "#####", "..#..", "..#.."], MUTED, 2)
        cv.text_c(x + CARD_W // 2, y + 34, "Equip fish", MUTED)
        cv.text_c(x + CARD_W // 2, y + 45, "pick one below", CLOSE_HINT)
        if i == 0:
            cv.text(x + 5, y + 4, "LEAD", GOLD)
        return
    cv.nine(a.panel_gold if i == 0 else a.panel, x, y, CARD_W, CARD_H)
    if hovered:
        cv.fill(x + 3, y + 3, x + CARD_W - 3, y + CARD_H - 3, 0x18FFFFFF)
    if i == 0:
        cv.pixels(x + 5, y + 5, CROWN, GOLD)
        cv.text(x + 12, y + 4, "LEAD", GOLD)
    bob = 1 if i == 1 else 0
    cv.sprite(a.fish(fish), x + CARD_W // 2 - 16, y + 7 + bob)
    if editing is not None:
        fx, fy = x + 6, y + 39
        cv.fill(fx, fy, x + CARD_W - 6, fy + 12, FIELD)
        cv.outline(fx, fy, CARD_W - 12, 12, GOLD)
        end = cv.text(fx + 3, fy + 2, editing, WHITE)
        cv.fill(end + 1, fy + 2, end + 2, fy + 10, WHITE)   # caret
    else:
        nw = cv.width(name)
        nx = x + (CARD_W - nw - 9) // 2
        cv.text(nx, y + 40, name, WHITE)
        pencil(cv, nx + nw + 3, y + 41)
        size, _, quality = sub.partition("  ")
        sx = x + (CARD_W - cv.width(sub)) // 2
        end = cv.text(sx, y + 50, size + "  ", MUTED)
        cv.text(end, y + 50, quality, sub_color)
    if hovered and mode is None and editing is None:
        bx = x + CARD_W - 13                                 # unequip
        cv.fill(bx, y + 3, bx + 10, y + 13, 0xFF4A3540)
        cv.pixels(bx + 3, y + 6, ["#...#"[0:5], ".#.#.", "..#..", ".#.#.", "#...#"][0:5], WHITE)
        if i > 0:                                            # make lead
            cv.fill(x + 3, y + 3, x + 13, y + 13, 0xFF4A3540)
            cv.pixels(x + 6, y + 6, CROWN, GOLD)
    if mode == "replace":
        cv.fill(x, y, x + CARD_W, y + CARD_H, 0x66201A24)
        cv.outline(x - 1, y - 1, CARD_W + 2, CARD_H + 2, GOLD)
        if hovered:
            cv.outline(x - 2, y - 2, CARD_W + 4, CARD_H + 4, GOLD)
            cv.fill(x + 20, y + 22, x + CARD_W - 20, y + 36, GOLD)
            cv.text_c(x + CARD_W // 2, y + 25, "Replace", 0xFF201A24, False)
    if mode == "refused":
        cv.fill(x, y, x + CARD_W, y + CARD_H, rgba_int(RED, 0x70))
        cv.outline(x - 1, y - 1, CARD_W + 2, CARD_H + 2, RED)


def rgba_int(c, a):
    return (a << 24) | (c & 0xFFFFFF)


def formation(cv, text, y=CARD_Y + CARD_H + 6):
    cv.pixels(CX, y + 1, HEART, PINK)
    cv.text(CX + 10, y, text, MUTED)
    cv.text_r(CX + CW, y, "?", CLOSE_HINT)


# -- toolbar + list ----------------------------------------------------------------
TOOL_Y = CARD_Y + CARD_H + 20
LIST_Y = TOOL_Y + 18
LIST_BOTTOM = SY + SH - 20
ROW_W = CW - 8


def toolbar(cv, query="", selected="All", summary=""):
    cv.fill(CX, TOOL_Y - 4, CX + CW, TOOL_Y - 3, EDGE)
    cv.fill(CX, TOOL_Y, CX + 112, TOOL_Y + 14, FIELD)
    cv.outline(CX, TOOL_Y, 112, 14, EDGE)
    cv.pixels(CX + 4, TOOL_Y + 4, LENS, MUTED)
    if query:
        end = cv.text(CX + 13, TOOL_Y + 3, query, WHITE)
        cv.fill(end + 1, TOOL_Y + 3, end + 2, TOOL_Y + 11, WHITE)
    else:
        cv.text(CX + 13, TOOL_Y + 3, "Search fish", CLOSE_HINT, False)
    x = CX + 120
    for label in ["All", "Unlocked"]:
        w = cv.width(label) + 10
        if label == selected:
            cv.fill(x, TOOL_Y, x + w, TOOL_Y + 14, FILTER_SEL)
            cv.fill(x, TOOL_Y + 12, x + w, TOOL_Y + 14, GOLD)
        cv.text(x + 5, TOOL_Y + 3, label, GOLD if label == selected else MUTED)
        x += w + 4
    cv.text_r(CX + CW, TOOL_Y + 3, summary, CLOSE_HINT)


def scrollbar(cv, frac_top, frac_size):
    x = CX + CW - 2
    th = LIST_BOTTOM - LIST_Y
    cv.fill(x, LIST_Y, x + 2, LIST_BOTTOM, TRACK)
    ty, tl = LIST_Y + int(th * frac_top), max(8, int(th * frac_size))
    cv.fill(x, ty, x + 2, ty + tl, THUMB)
    cv.fill(x, ty, x + 1, ty + tl, THUMB_HI)


def species_row(cv, a, y, fish, name, count, state, expanded=False, hovered=False, progress=None):
    if y >= LIST_BOTTOM:
        return y
    x = CX
    locked = state != "unlocked"
    if expanded or hovered:
        cv.fill(x, y, x + ROW_W, y + 18, ROW_HOVER)
    if expanded:
        cv.pixels(x + 3, y + 8, CHEV_D, WHITE)
    else:
        cv.pixels(x + 4, y + 7, CHEV_R, MUTED if locked else WHITE)
    icon = a.fish(fish)
    icon = dimmed(icon) if locked else icon
    cv.sprite(icon, x + 13, y + 1, 16, 16)
    end = cv.text(x + 33, y + 5, name, MUTED if locked else WHITE)
    cv.text(end + 5, y + 5, f"x{count}", CLOSE_HINT)
    rx = x + ROW_W - 6
    if state == "unlocked":
        end = cv.text_r(rx, y + 5, "Unlocked", READY)
        cv.pixels(rx - cv.width("Unlocked") - 10, y + 6, HEART, PINK)
    elif state == "quest":
        cv.text_r(rx, y + 5, "Unlocks via a quest", MUTED)
        cv.pixels(rx - cv.width("Unlocks via a quest") - 9, y + 6, LOCK, MUTED)
    else:
        label = f"Catch {state} more"
        lx = rx - cv.width(label)
        cv.text(lx, y + 3, label, PROGRESS)
        bw = cv.width(label)
        cv.fill(lx, y + 13, lx + bw, y + 15, TRACK)
        cv.fill(lx, y + 13, lx + int(bw * progress), y + 15, PROGRESS)
    return y + 18


def fish_row(cv, a, y, quality, size, source, copies=1, equipped=False, hovered=False, flash=False, locked=False):
    if y >= LIST_BOTTOM:
        return y
    x = CX + 13
    if hovered:
        cv.fill(x, y, CX + ROW_W, y + 15, 0xFF3A2B37)
    if flash:
        cv.fill(x, y, CX + ROW_W, y + 15, rgba_int(RED, 0x60))
    cv.fill(CX + 7, y, CX + 8, y + 15, EDGE)              # tree line
    cv.pixels(x + 4, y + 5, STAR, QUALITY[quality])
    end = cv.text(x + 13, y + 4, quality, QUALITY[quality])
    cv.text(x + 70, y + 4, size, WHITE)
    sx = x + 110
    if source == "Pile":
        cv.sprite(a.pile, sx, y - 1 + 4, 8, 8)
        cv.text(sx + 11, y + 4, "Pile", MUTED)
    else:
        cv.text(sx, y + 4, "Inventory", MUTED)
    if copies > 1:
        cv.text(sx + 60, y + 4, f"x{copies}", CLOSE_HINT)
    bx = CX + ROW_W - 44
    if equipped:
        cv.pixels(bx - 14, y + 5, HEART, PINK)
        cv.text_r(bx + 40, y + 4, "Equipped", MUTED)
    else:
        cv.fill(bx + 4, y + 1, bx + 40, y + 14, 0xFF3A2B37 if locked else 0xFF7D4E5B if hovered else RAIL_SEL)
        cv.text_c(bx + 22, y + 4, "Equip", CLOSE_HINT if locked else WHITE)
    return y + 15


def show_more(cv, y, n):
    cv.fill(CX + 7, y, CX + 8, y + 12, EDGE)
    cv.text(CX + 26, y + 2, f"Show {n} more", GOLD)
    return y + 13


def footer(cv, text, color=CLOSE_HINT, second=None):
    cv.fill(CX, LIST_BOTTOM, CX + CW - 2, SY + SH - 1, SHELL)   # the list viewport clips here
    cv.outline(PX, SY, PW, SH, EDGE)
    cv.fill(CX, LIST_BOTTOM + 3, CX + CW, LIST_BOTTOM + 4, EDGE)
    cv.text(CX, LIST_BOTTOM + 7, text, color)
    if second:
        cv.text_r(CX + CW, LIST_BOTTOM + 7, second, CLOSE_HINT)


def tooltip(cv, x, y, lines):
    w = max(cv.width(t) for t, _ in lines) + 8
    h = len(lines) * 10 + 6
    cv.fill(x, y, x + w, y + h, 0xF0100010)
    cv.outline(x, y, w, h, 0xFF5000FF)
    cv.outline(x + 1, y + 1, w - 2, h - 2, 0xFF28007F)
    for i, (t, c) in enumerate(lines):
        cv.text(x + 4, y + 4 + i * 10, t, c)


def cursor(cv, x, y):
    arrow = ["#.....", "##....", "#.#...", "#..#..", "#...#.", "#..###", "#.#...", "##....", "#....."]
    cv.pixels(x, y, [r.replace(".", " ") for r in arrow], 0xFF000000)
    fill = ["......", "......", ".#....", ".##...", ".###..", ".##...", ".#....", "......", "......"]
    cv.pixels(x, y, fill, WHITE)


# =====================================================================================
# Mockups

def m1_equipped(a, out):
    cv = Canvas(a.font)
    shell(cv, a, "equipped")
    cv.text(CX, SY + 9, "Your companions", WHITE)
    count_box(cv, "2 / 3 equipped")
    card(cv, a, 0, "bluegill", "Finn", "31 cm  Rare", QUALITY["Rare"])
    card(cv, a, 1, "starfish", "Starfish", "18 cm  Common", QUALITY["Common"], hovered=True)
    card(cv, a, 2)
    formation(cv, "Bluegill + Starfish")
    toolbar(cv, summary="1,284 fish")
    y = LIST_Y
    y = species_row(cv, a, y, "bluegill", "Bluegill", 412, "unlocked", expanded=True)
    y = fish_row(cv, a, y, "Legendary", "46 cm", "Pile")
    y = fish_row(cv, a, y, "Rare", "31 cm", "Inventory", equipped=True)
    y = fish_row(cv, a, y, "Rare", "29 cm", "Pile", copies=3, hovered=True)
    y = show_more(cv, y, 37)
    y = species_row(cv, a, y, "starfish", "Starfish", 96, "unlocked")
    y = species_row(cv, a, y, "arctic_char", "Arctic Char", 7, "12", progress=0.4)
    scrollbar(cv, 0.0, 0.35)
    footer(cv, "Click Equip to add a fish to the first empty card.", CLOSE_HINT, "")
    cursor(cv, CX + ROW_W - 26, LIST_Y + 18 + 30 + 6)
    cv.save(out / "01_equipped.png")


def m2_replace(a, out):
    cv = Canvas(a.font)
    shell(cv, a, "equipped")
    cv.text(CX, SY + 9, "Your companions", WHITE)
    count_box(cv, "3 / 3 equipped")
    card(cv, a, 0, "bluegill", "Finn", "31 cm  Rare", QUALITY["Rare"], mode="replace")
    card(cv, a, 1, "bluegill", "Bluegill", "29 cm  Rare", QUALITY["Rare"], mode="replace", hovered=True)
    card(cv, a, 2, "starfish", "Starfish", "18 cm  Common", QUALITY["Common"], mode="replace")
    formation(cv, "Bluegill pair + Starfish")
    toolbar(cv, query="glass", summary="2 species")
    y = LIST_Y
    y = species_row(cv, a, y, "glass_squid", "Glass Squid", 23, "unlocked", expanded=True)
    y = fish_row(cv, a, y, "Epic", "22 cm", "Pile", hovered=True)
    cv.outline(CX + 13, y - 15, ROW_W - 13, 15, GOLD)
    y = fish_row(cv, a, y, "Uncommon", "17 cm", "Inventory", copies=2)
    y = species_row(cv, a, y, "glass_catfish", "Glass Catfish", 4, "20", progress=0.15)
    footer(cv, "All 3 cards are full: choose one to replace.", GOLD, "Esc  cancel")
    cursor(cv, card_x(1) + 60, CARD_Y + 30)
    cv.save(out / "02_replace.png")


def m3_rename_refuse(a, out):
    cv = Canvas(a.font)
    shell(cv, a, "equipped")
    cv.text(CX, SY + 9, "Your companions", WHITE)
    count_box(cv, "2 / 3 equipped")
    card(cv, a, 0, "bluegill", "Finn", "31 cm  Rare", QUALITY["Rare"], editing="Finnegan")
    card(cv, a, 1, "starfish", "Starfish", "18 cm  Common", QUALITY["Common"])
    card(cv, a, 2)
    formation(cv, "Bluegill + Starfish")
    toolbar(cv, summary="1,284 fish", selected="All")
    y = LIST_Y
    y = species_row(cv, a, y, "arctic_char", "Arctic Char", 7, "12", expanded=True, progress=0.4)
    y = fish_row(cv, a, y, "Uncommon", "52 cm", "Pile", flash=True, locked=True)
    y = fish_row(cv, a, y, "Common", "40 cm", "Inventory", copies=6, locked=True)
    y = species_row(cv, a, y, "electric_eel", "Electric Eel", 2, "quest")
    y = species_row(cv, a, y, "betta", "Betta", 31, "4", progress=0.88)
    footer(cv, "Catch 12 more Arctic Char to unlock it.", 0xFFFF6B6B, "See Unlocks")
    cv.save(out / "03_rename_and_refusal.png")


def m4_unlocks(a, out):
    cv = Canvas(a.font)
    shell(cv, a, "unlocks")
    cv.text(CX, SY + 9, "Unlocks", WHITE)
    count_box(cv, "3 of 62 unlocked")
    cv.text(CX, SY + 26, "Complete a species' Fishopedia entry to unlock it.", MUTED)
    cv.text(CX, SY + 36, "Closest first.", CLOSE_HINT)
    names = ["bluegill", "starfish", "glass_squid", "betta", "arctic_char", "glass_catfish", "discus",
             "clown_loach", "hellbender", "garden_eel", "electric_eel", "coelacanth", "angler_fish",
             "golden_trout", "bull_trout", "european_grayling", "fire_dartfish", "giraffe_cichlid",
             "greenstripe_barb", "blind_cave_tetra", "indian_glassy_fish", "japanese_spider_crab",
             "common_octopus", "american_paddlefish"]
    state = {0: 1.0, 1: 1.0, 2: 1.0, 3: 0.88, 4: 0.4, 5: 0.15, 6: 0.1, 7: 0.05}
    tile, gap, cols = 34, 5, 8
    for i, n in enumerate(names):
        col, row = i % cols, i // cols
        x, y = CX + col * (tile + gap) + 2, SY + 52 + row * (tile + 18)
        p = state.get(i, 0.0)
        cv.nine(a.panel_gold if p >= 1 else a.panel, x, y, tile, tile)
        icon = a.fish(n)
        if p >= 1:
            cv.sprite(icon, x + 1, y + 1)
            cv.pixels(x + tile - 9, y + 3, HEART, PINK)
        elif p > 0:
            cv.sprite(dimmed(icon, 0.7), x + 1, y + 1)
        else:
            cv.sprite(silhouette(icon), x + 1, y + 1)
        if 0 < p < 1:
            cv.fill(x, y + tile + 3, x + tile, y + tile + 5, TRACK)
            cv.fill(x, y + tile + 3, x + int(tile * p), y + tile + 5, PROGRESS)
        elif n == "electric_eel":
            cv.pixels(x + tile // 2 - 2, y + tile + 3, LOCK, MUTED)
    tooltip(cv, CX + 3 * (tile + gap) + 30, SY + 66, [("Betta", WHITE), ("Catch 4 more to unlock", PROGRESS),
                                                       ("as a companion.", PROGRESS), ("Click: open in Fishopedia", CLOSE_HINT)])
    cursor(cv, CX + 3 * (tile + gap) + 22, SY + 64)
    cv.save(out / "04_unlocks.png")


def m5_none_unlocked(a, out):
    cv = Canvas(a.font)
    shell(cv, a, "equipped")
    cv.text(CX, SY + 9, "Your companions", WHITE)
    count_box(cv, "0 / 3 equipped")
    for i in range(3):
        card(cv, a, i)
    cv.fill(CX, TOOL_Y - 4, CX + CW, TOOL_Y - 3, EDGE)
    cv.pixels(CX + CW // 2 - 14, TOOL_Y + 12, HEART, 0xFF5A4650, 4)
    cv.text_c(CX + CW // 2, TOOL_Y + 42, "No companions unlocked yet", WHITE)
    cv.text_c(CX + CW // 2, TOOL_Y + 54, "Complete a species' Fishopedia entry", MUTED)
    cv.text_c(CX + CW // 2, TOOL_Y + 64, "to bring that fish along with you.", MUTED)
    bx = CX + CW // 2 - 50
    cv.fill(bx, TOOL_Y + 78, bx + 100, TOOL_Y + 94, RAIL_SEL)
    cv.text_c(CX + CW // 2, TOOL_Y + 82, "See Unlocks", WHITE)
    cv.text_c(CX + CW // 2, TOOL_Y + 98, "Closest: Betta, 4 more catches", CLOSE_HINT)
    cv.save(out / "05_nothing_unlocked.png")


def inventory_with_tab(cv, a, ox, oy, locked=False, hovered=False):
    cv.sprite(a.inventory.crop((0, 0, 176, 166)), ox, oy)
    tx, ty = ox + 176 - 4 - 22, oy - 14                    # tab on the top edge, right side
    body = SHELL if not hovered else 0xFF2E2430
    cv.fill(tx, ty, tx + 22, ty + 17, body)
    cv.fill(tx, ty, tx + 22, ty + 1, EDGE)
    cv.fill(tx, ty, tx + 1, ty + 17, EDGE)
    cv.fill(tx + 21, ty, tx + 22, ty + 17, EDGE)
    cv.fill(tx + 1, ty + 14, tx + 21, ty + 17, 0xFFC6C6C6)  # tab merges into the panel's light top
    cv.pixels(tx + 8, ty + 4, HEART, 0xFF6E5E66 if locked else PINK)
    if locked:
        cv.pixels(tx + 14, ty + 8, LOCK, MUTED)
    return tx, ty


def m6_inventory(a, out):
    cv = Canvas(a.font)
    for y in range(H):
        cv.fill(0, y, W, y + 1, 0xFF2C3A28 if y > 150 else 0xFF5C86C0)
    cv.fill(0, 0, W, H, 0xA0101010)
    ox, oy = 40, 70
    tx, ty = inventory_with_tab(cv, a, ox, oy, hovered=True)
    cv.text(ox + 97, oy + 6, "Crafting", 0xFF404040, False)
    tooltip(cv, tx + 16, ty - 26, [("Companions  [B]", WHITE), ("2 / 3 equipped", MUTED)])
    cursor(cv, tx + 12, ty + 6)
    cv.text(ox, oy + 176, "Unlocked", WHITE)
    ox2 = 264
    tx2, ty2 = inventory_with_tab(cv, a, ox2, oy, locked=True, hovered=True)
    cv.text(ox2 + 97, oy + 6, "Crafting", 0xFF404040, False)
    tooltip(cv, tx2 - 96, ty2 - 46, [("Companions  [B]", WHITE), ("Complete a Fishopedia entry", MUTED),
                                     ("to unlock your first.", MUTED), ("Closest: Betta, 4 more", PROGRESS)])
    cv.text(ox2, oy + 176, "Nothing unlocked yet", WHITE)
    cv.save(out / "06_inventory_tab.png")


def creative_heart(cv, x, y, locked=False, hovered=False):
    """18x18, the size of the offhand slot it mirrors; plum chip so it never reads as an item slot."""
    cv.fill(x, y, x + 18, y + 18, 0xFF2E2430 if hovered else SHELL)
    cv.outline(x, y, 18, 18, GOLD if hovered else EDGE)
    cv.pixels(x + 6 - (2 if locked else 0), y + 6, HEART, 0xFF6E5E66 if locked else PINK)
    if locked:
        cv.pixels(x + 10, y + 10, LOCK, MUTED)


def creative_window(cv, a, ox, oy):
    # Tabs: five left-aligned columns plus two right-aligned (hotbar/search, op/inventory).
    def tab_x(col):
        return ox + col * 27 if col < 5 else ox + 195 - 27 * (7 - col) + 1
    for col in range(7):
        if not (col == 6):
            cv.sprite(a.tabs[f"tab_bottom_unselected_{col + 1}"], tab_x(col), oy + 136 - 4)
        cv.sprite(a.tabs[f"tab_top_unselected_{col + 1}"], tab_x(col), oy - 28)
    cv.sprite(a.creative.crop((0, 0, 195, 136)), ox, oy)
    cv.sprite(a.tabs["tab_bottom_selected_7"], tab_x(6), oy + 136 - 4)
    cv.pixels(tab_x(6) + 9, oy + 136 + 6, [".##.##.", "#######", "#######", "#######"], 0xFF8B6B3D)  # chest-ish glyph
    cv.text(ox + 8, oy - 38, "", WHITE)


def m7_creative(a, out):
    cv = Canvas(a.font)
    for y in range(H):
        cv.fill(0, y, W, y + 1, 0xFF2C3A28 if y > 150 else 0xFF5C86C0)
    cv.fill(0, 0, W, H, 0xA0101010)
    ox, oy = 26, 60
    creative_window(cv, a, ox, oy)
    bx, by = ox + 150, oy + 19
    creative_heart(cv, bx, by, hovered=True)
    cv.text(ox, oy + 182, "Inventory tab, unlocked (hover)", WHITE)
    ox2 = 262
    creative_window(cv, a, ox2, oy)
    creative_heart(cv, ox2 + 150, oy + 19, locked=True)
    cv.text(ox2, oy + 182, "Inventory tab, nothing unlocked", WHITE)
    tooltip(cv, bx + 14, by - 22, [("Companions  [B]", WHITE), ("2 / 3 equipped", MUTED)])
    cursor(cv, bx + 10, by + 8)
    cv.save(out / "07_creative_inventory.png")


def main():
    jar, out = Path(sys.argv[1]), Path(sys.argv[2])
    out.mkdir(parents=True, exist_ok=True)
    a = Assets(jar)
    for m in (m1_equipped, m2_replace, m3_rename_refuse, m4_unlocks, m5_none_unlocked, m6_inventory, m7_creative):
        m(a, out)
    print("wrote", sorted(p.name for p in out.glob("*.png")))


if __name__ == "__main__":
    main()
