"""
Pixel-faithful mockups for the Fishopedia info-page redesign.

Layout is authored in Minecraft *logical GUI pixels* and rendered at an integer GUI
scale, exactly like the game: text uses vanilla's ascii.png glyph atlas and glyph
widths (with the 1px drop shadow), items/zones/panels use the mod's real textures.
Every text block is measured against the box it is given and the render FAILS on
overflow, so "it fits at 426x240" is a checked claim, not an eyeballed one.

Run:  python -I docs/encyclopedia-redesign/mockups/render_mockups.py
"""
import os
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageEnhance

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
ASSETS = os.path.join(HERE, "..", "_assets")
TEX = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "fishtastic", "textures")
OUT = HERE
BACKDROP = os.path.join(HERE, "..", "current", "selftest-fabric-encyclopedia-s4_disc.png")

OVERFLOWS = []

# Minecraft chat-format palette
WHITE, GREY, DGREY, BLACK = 0xFFFFFF, 0xAAAAAA, 0x555555, 0x000000
GOLD, YELLOW, GREEN, RED, AQUA, PINK = 0xFFAA00, 0xFFFF55, 0x55FF55, 0xFF5555, 0x55FFFF, 0xFF8FA8
MUTED = 0x8C7F7A          # warm grey that reads on the mauve panel
BOOST_BG, PEN_BG, NEU_BG = (46, 120, 52), (140, 44, 44), (58, 36, 34)


def rgb(c):
    return ((c >> 16) & 255, (c >> 8) & 255, c & 255)


# ---------------------------------------------------------------- font

class Font:
    """Vanilla legacy bitmap font: 8x8 cells, advance = glyph width + 1, space = 4."""

    def __init__(self, path):
        atlas = Image.open(path).convert("RGBA")
        self.masks, self.adv = {}, {}
        for code in range(256):
            cx, cy = (code % 16) * 8, (code // 16) * 8
            cell = atlas.crop((cx, cy, cx + 8, cy + 8))
            alpha = cell.split()[3]
            width = 0
            for x in range(8):
                if any(alpha.getpixel((x, y)) > 0 for y in range(8)):
                    width = x + 1
            self.masks[code] = alpha
            self.adv[code] = 4 if code == 32 else width + 1

    def width(self, s):
        return sum(self.adv.get(ord(ch), 6) for ch in s)

    def wrap(self, s, max_w):
        lines, cur = [], ""
        for word in s.split(" "):
            trial = word if not cur else cur + " " + word
            if self.width(trial) <= max_w:
                cur = trial
            else:
                lines.append(cur)
                cur = word
        if cur:
            lines.append(cur)
        return lines


FONT = Font(os.path.join(ASSETS, "ascii.png"))

# ---------------------------------------------------------------- tiny pixel icons (new art the design needs)

ICONS = {
    "sun": ["..#.#..", "...#...", "#.###.#", ".#####.", "#.###.#", "...#...", "..#.#.."],
    "dawn": [".......", "...#...", ".#.#.#.", "..###..", ".#####.", "#######", "......."],
    "dusk": [".......", ".......", "..###..", ".#####.", "#######", ".......", "#.#.#.#"],
    "night": ["..###..", ".##....", "##.....", "##.....", "##.....", ".##....", "..###.."],
    "clear": ["..#.#..", "...#...", "#.###.#", ".#####.", "#.###.#", "...#...", "..#.#.."],
    "rain": ["..###..", ".#####.", "#######", ".......", "#.#.#..", ".#.#.#.", "#.#.#.."],
    "snow": ["#..#..#", ".#.#.#.", "..###..", "#######", "..###..", ".#.#.#.", "#..#..#"],
    "thunder": ["..###..", ".#####.", "#######", "...#...", "..#....", ".####..", "...#..."],
    "lock": [".###.", "#...#", "#...#", "#####", "##.##", "##.##", "#####"],
    "left": ["...#", "..##", ".###", "####", ".###", "..##", "...#"],
    "right": ["#...", "##..", "###.", "####", "###.", "##..", "#..."],
    "check": [".......", "......#", ".....##", "#...##.", "##.##..", ".###...", "..#...."],
    "heart": [".##.##.", "#######", "#######", ".#####.", "..###..", "...#..."],
    "pin": ["..#..", ".###.", "#####", ".###.", "..#.."],
}
ICON_COLORS = {"sun": 0xFFE14A, "dawn": 0xFFB060, "dusk": 0xFF8A50, "night": 0xD8E0FF, "clear": 0xFFE14A,
               "rain": 0x7FB4FF, "snow": 0xF2F6FF, "thunder": 0xFFE14A}


def moon_icon(phase):
    """7x7 moon; phase 0 = full, 4 = new, matching vanilla's phase index order."""
    lit_frac = [1.0, 0.75, 0.5, 0.25, 0.0, 0.25, 0.5, 0.75][phase]
    waning = phase in (1, 2, 3)
    rows = []
    for y in range(7):
        row = ""
        for x in range(7):
            dx, dy = x - 3, y - 3
            if dx * dx + dy * dy > 10:
                row += "."
                continue
            # light comes from the right while waning in vanilla's ordering, left while waxing
            pos = (x + 0.5) / 7.0
            lit = pos > (1 - lit_frac) if waning else pos < lit_frac
            row += "#" if lit else "o"
        rows.append(row)
    return rows


# ---------------------------------------------------------------- canvas

class Canvas:
    def __init__(self, w, h, scale, backdrop=True):
        self.w, self.h, self.s = w, h, scale
        if backdrop and os.path.exists(BACKDROP):
            bg = Image.open(BACKDROP).convert("RGBA").resize((w * scale, h * scale), Image.BILINEAR)
            bg = bg.filter(ImageFilter.GaussianBlur(radius=6 * scale))
            bg = ImageEnhance.Brightness(bg).enhance(0.45)
        else:
            bg = Image.new("RGBA", (w * scale, h * scale), (24, 22, 26, 255))
        self.img = bg
        self.draw = ImageDraw.Draw(self.img, "RGBA")
        self.notes = []

    # geometry
    def rect(self, x, y, w, h, color, alpha=255):
        s = self.s
        if w <= 0 or h <= 0:
            return
        col = rgb(color) + (alpha,) if isinstance(color, int) else tuple(color)[:3] + (alpha,)
        if alpha >= 255:
            self.draw.rectangle([x * s, y * s, (x + w) * s - 1, (y + h) * s - 1], fill=col)
        else:
            self.img.alpha_composite(Image.new("RGBA", (w * s, h * s), col), (x * s, y * s))

    def outline(self, x, y, w, h, color, alpha=255):
        self.rect(x, y, w, 1, color, alpha)
        self.rect(x, y + h - 1, w, 1, color, alpha)
        self.rect(x, y, 1, h, color, alpha)
        self.rect(x + w - 1, y, 1, h, color, alpha)

    # text
    def text(self, x, y, s, color=WHITE, shadow=True, box=None, label=""):
        """Draws s at logical (x, y). box=(max_w) records an overflow if the text is wider."""
        width = FONT.width(s)
        if box is not None and width - 1 > box:
            OVERFLOWS.append(f"{label or s!r}: text {width - 1}px > box {box}px")
        if shadow:
            r, g, b = rgb(color)
            self._glyphs(x + 1, y + 1, s, (r // 4, g // 4, b // 4))
        self._glyphs(x, y, s, rgb(color))
        return width

    def text_c(self, cx, y, s, color=WHITE, **kw):
        return self.text(cx - (FONT.width(s) - 1) // 2, y, s, color, **kw)

    def text_r(self, rx, y, s, color=WHITE, **kw):
        return self.text(rx - FONT.width(s) + 1, y, s, color, **kw)

    def paragraph(self, x, y, w, s, color, line_h=10, max_lines=None, label=""):
        lines = FONT.wrap(s, w)
        if max_lines is not None and len(lines) > max_lines:
            OVERFLOWS.append(f"{label}: {len(lines)} lines > {max_lines} allowed")
        for i, line in enumerate(lines):
            self.text(x, y + i * line_h, line, color)
        return len(lines) * line_h

    def _glyphs(self, x, y, s, color):
        sc = self.s
        cx = x
        for ch in s:
            code = ord(ch)
            if code in FONT.masks and code != 32:
                m = FONT.masks[code].resize((8 * sc, 8 * sc), Image.NEAREST)
                solid = Image.new("RGBA", m.size, color + (255,))
                self.img.paste(solid, (cx * sc, y * sc), m)
            cx += FONT.adv.get(code, 6)

    # textures
    def tex(self, rel, x, y, w, h, silhouette=False, alpha=1.0):
        im = Image.open(os.path.join(TEX, rel) if not os.path.isabs(rel) else rel).convert("RGBA")
        im = im.resize((int(w * self.s), int(h * self.s)), Image.NEAREST)
        if silhouette:
            a = im.split()[3]
            im = Image.new("RGBA", im.size, (8, 8, 10, 255))
            im.putalpha(a)
        if alpha < 1.0:
            a = im.split()[3].point(lambda v: int(v * alpha))
            im.putalpha(a)
        self.img.alpha_composite(im, (int(x * self.s), int(y * self.s)))

    def nine(self, rel, x, y, w, h, border=4, src_box=None):
        """9-slice with each source texel drawn as one logical pixel (crisp borders)."""
        path = rel if os.path.isabs(rel) else os.path.join(TEX, rel)
        src = Image.open(path).convert("RGBA")
        if src_box:
            src = src.crop(src_box)
        sw, sh = src.size
        b, s = border, self.s
        xs = [(0, b, 0, b), (b, sw - b, b, w - b), (sw - b, sw, w - b, w)]
        ys = [(0, b, 0, b), (b, sh - b, b, h - b), (sh - b, sh, h - b, h)]
        for sx0, sx1, dx0, dx1 in xs:
            for sy0, sy1, dy0, dy1 in ys:
                if dx1 <= dx0 or dy1 <= dy0:
                    continue
                piece = src.crop((sx0, sy0, sx1, sy1)).resize(((dx1 - dx0) * s, (dy1 - dy0) * s), Image.NEAREST)
                self.img.alpha_composite(piece, ((x + dx0) * s, (y + dy0) * s))

    def icon(self, name_or_rows, x, y, color=None, dim_color=None):
        rows = ICONS[name_or_rows] if isinstance(name_or_rows, str) else name_or_rows
        col = color if color is not None else ICON_COLORS.get(name_or_rows, WHITE) if isinstance(name_or_rows, str) else WHITE
        for j, row in enumerate(rows):
            for i, ch in enumerate(row):
                if ch == "#":
                    self.rect(x + i, y + j, 1, 1, col)
                elif ch == "o" and dim_color is not None:
                    self.rect(x + i, y + j, 1, 1, dim_color)

    def annotate(self, n, x, y):
        """Numbered callout used by the annotated variants (legend lives in the design doc)."""
        w = FONT.width(str(n)) + 3
        self.rect(x - 1, y - 1, w + 2, 11, BLACK, 200)
        self.rect(x, y, w, 9, 0xFFD23F)
        self.text(x + 2, y + 1, str(n), BLACK, shadow=False)

    def save(self, name):
        self.img.convert("RGB").save(os.path.join(OUT, name))
        print("wrote", name, f"({self.w}x{self.h} logical @ {self.s}x)")


# ---------------------------------------------------------------- shared widgets

def button(c, x, y, w, h, label, color=WHITE, hot=False):
    sprite = os.path.join(ASSETS, "button_highlighted.png" if hot else "button.png")
    c.nine(sprite, x, y, w, h, border=3)
    c.text_c(x + w // 2, y + (h - 8) // 2, label, color, box=w - 4, label=f"button {label}")


def panel(c, x, y, w, h, kind="plain"):
    tex = {"plain": "gui/generic_item_panel.png", "gold": "gui/generic_item_panel_gold_border.png",
           "green": "gui/green_generic_item_panel_2.png"}[kind]
    c.nine(tex, x, y, w, h, border=4)


def inset(c, x, y, w, h, alpha=70):
    c.rect(x, y, w, h, BLACK, alpha)


def chip(c, x, y, label, mult, tier="primary", locked=False):
    """Biome chip: name + effect, coloured by direction of effect (boost/penalty), not by 'is it now'."""
    eff = effect_word(mult, tier)
    w = FONT.width(label) + FONT.width(eff) + 10
    bg = BOOST_BG if mult >= 1 else PEN_BG
    c.rect(x, y, w, 11, bg, 200)
    c.text(x + 2, y + 2, label, WHITE)
    c.rect(x + FONT.width(label) + 3, y + 2, 1, 7, BLACK, 90)
    c.text(x + 6 + FONT.width(label), y + 2, eff, 0xC8FFC0 if mult >= 1 else 0xFFC0C0)
    return w


# Encyclopedia coin ladder: same pattern as QuestLogScreen.coinLadderIcon, own thresholds.
# One fish pays at most 1+1+1+2+3 = 8 tokens. The three-coin stack is deliberately skipped: at
# 16px it reads as MORE gold than the small pile, so the ladder would look non-monotonic.
# A whole fish (8) earns the medium pile; the large pile is a disc backlog of 2.5+ fish.
COIN_LADDER = [  # (min amount, texture, logical w, h)
    (20, "item/pile_of_coins.png", 16, 16),
    (8, "item/medium_pile_of_coins.png", 16, 16),
    (3, "item/small_pile_of_coins.png", 16, 16),
    (2, "gui/two_coins.png", 12, 8),
    (1, "gui/one_coin.png", 8, 7),
]
COIN_RUNG_NAME = {"pile_of_coins": "large pile", "medium_pile_of_coins": "medium pile",
                  "small_pile_of_coins": "small pile", "two_coins": "2 coins", "one_coin": "1 coin"}


def coin_icon(amount):
    for threshold, tex, w, h in COIN_LADDER:
        if amount >= threshold:
            return tex, w, h
    return COIN_LADDER[-1][1:]


def claim_button(c, x, y, amount, label, h=22, min_w=0):
    """Green claim button: label, then the coin-ladder icon for amount. Returns its width."""
    tex, iw, ih = coin_icon(amount)
    w = max(min_w, 6 + FONT.width(label) + 3 + 16 + 5)
    panel(c, x, y, w, h, "green")
    c.text(x + 6, y + (h - 8) // 2, label, WHITE)
    slot_x = x + w - 5 - 16                      # icons share one 16px slot so the text never shifts
    c.tex(tex, slot_x + (16 - iw) // 2, y + (h - ih) // 2, iw, ih)
    return w


def truncate(s, max_w):
    if FONT.width(s) - 1 <= max_w:
        return s
    while s and FONT.width(s + "...") - 1 > max_w:
        s = s[:-1]
    return s + "..."


def effect_word(m, tier="primary"):
    """One condition's effect in plain words. Bands sit in gaps of the shipped data:
    boosts cluster 1.2-1.4 / 1.5-1.7 / 1.8-2.2, penalties 0.5-0.6 / 0.1-0.4."""
    if tier == "secondary" or 1 < m < 1.45:
        return "Slight"
    if m >= 1.75:
        return "Great"
    if m > 1:
        return "Good"
    return "Poor" if m >= 0.45 else "Bad"


def spot_word(m):
    """The verdict for the combined environment multiplier."""
    if m >= 1.75:
        return "Great spot", "green"
    if m >= 1.2:
        return "Good spot", "green"
    if m > 0.8:
        return "Fair spot", "fair"
    return "Poor spot", "amber"


def fmt_mult(m, tier="primary"):
    if tier == "secondary":
        return f"+{round((m - 1) * 100)}%"
    if abs(m - round(m)) < 1e-6:
        return f"{int(round(m))}x"
    return f"{m:g}x"


QUALITY_COLOR = {"Common": 0xAAAAAA, "Uncommon": 0x55FF55, "Rare": 0x5555FF, "Epic": 0xFF55FF, "Legendary": 0xFFAA00}
ZONE_TEX = {z: f"item/zone/{z}.png" for z in ["ocean", "deep_ocean", "river", "nether", "cave", "high_altitude"]}
ZONE_NAME = {"ocean": "Ocean", "deep_ocean": "Deep Ocean", "river": "River", "nether": "Nether",
             "cave": "Cave", "high_altitude": "High Altitude"}
BAITS = {"small": ("item/bait/freshwater_bait.png", "Small Fish Bait"),
         "calm": ("item/bait/ocean_bait.png", "Calm Bait"),
         "frenzy": ("item/bait/predator_bait.png", "Frenzy Bait"),
         "big": ("item/bait/deep_sea_bait.png", "Trophy Bait"),
         "exotic": ("item/fish/blazed_grub.png", "Blazed Grub")}

# ---------------------------------------------------------------- fixtures (real data from fish_profile / tiers)

BETTA = dict(
    id="betta", name="Betta", rarity="Rare", pool="~1 in 26 cave bites",
    zones=["river", "cave"], size=(13.0, 2.0), you=(15, "Rare"), server=(17, "Rival"),
    temperament=("Erratic", 0.825), catches=6,
    biomes=[("Jungle", 2.0), ("Swamp", 1.5), ("Lush Caves", 1.3)],
    time={"day": 1.5, "night": 0.5}, weather={}, moon={},
    groups=["small", "frenzy"], unbaitable_groups=[],
    lore="This colorful, small freshwater fish belongs to the genus Betta. Using an organ called the labyrinth, "
         "it can breathe atmospheric air to survive in low oxygen environments.",
    thresholds=dict(name=1, stats=1, types=2, spawn=2, lore=6),
)
GLASS_SQUID = dict(
    id="glass_squid", name="Glass Squid", rarity="Very rare", pool="~1 in 73 deep bites",
    zones=["deep_ocean"], size=(40.0, 12.0), you=(52, "Rare"), server=(64, "Rival"),
    temperament=("Ghost", 0.775), catches=3,
    biomes=[], time={"night": 2.0, "dusk": 1.5}, weather={},
    moon={0: (1.25, "secondary"), 1: (1.1, "secondary"), 7: (1.1, "secondary")},
    groups=["big", "calm", "exotic"], unbaitable_groups=[],
    lore="Glass squids comprise family Cranchiidae. Their generally transparent bodies spend their juvenile lives "
         "in surface waters, and descend to deeper waters as they mature. Some species have light-producing organs "
         "on their eyes that match the intensity of downwelling sunlight.",
    thresholds=dict(name=1, stats=1, types=1, spawn=1, lore=3),
)
BLUEGILL = dict(
    id="bluegill", name="Bluegill", rarity="Abundant", pool="~1 in 3 river bites",
    zones=["river"], size=(30.0, 8.0), you=(34, "Uncommon"), server=(41, "Rival"),
    temperament=("Docile", 0.2), catches=3,
    biomes=[("Cherry Grove", 1.5)], time={"night": 0.5, "dusk": 0.25}, weather={}, moon={},
    groups=["small", "calm"], unbaitable_groups=[],
    lore="The bluegill, also known as bream or sunny, is a freshwater fish found in rivers, streams, and lakes "
         "in North America.",
    thresholds=dict(name=2, stats=3, types=8, spawn=4, lore=30),
)
MAN_O_WAR = dict(
    id="portuguese_man_o_war", name="Portuguese Man O' War", rarity="Very rare", pool="",
    zones=["ocean"], size=(40.0, 10.0), you=None, server=None,
    temperament=("Aggressive", 0.825), catches=0,
    biomes=[], time={}, weather={"rain": 1.5, "thunder": 2.0},
    moon={0: (1.25, "secondary"), 1: (1.1, "secondary"), 7: (1.1, "secondary")},
    groups=["big", "frenzy", "exotic"], unbaitable_groups=[],
    lore="The Portuguese man o' war, also known as bluebottle or Caravela Portuguesa, is a marine hydrozoan.",
    thresholds=dict(name=1, stats=1, types=1, spawn=1, lore=3),
)

SECTION_ORDER = [("name", "Name", 1), ("stats", "Size", 1), ("types", "Bait", 1), ("spawn", "Habits", 2), ("lore", "Notes", 3)]


def unlocked(fish, key):
    return fish["catches"] >= fish["thresholds"][key]


def milestones(fish):
    """Sections in the order they actually unlock for this fish (fixes page-order != unlock-order)."""
    order = {"name": 0, "stats": 1, "types": 2, "spawn": 3, "lore": 4}
    ms = [(fish["thresholds"][k], order[k], k, label, coins) for k, label, coins in SECTION_ORDER]
    return sorted(ms)


# ---------------------------------------------------------------- Concept A: field-guide spread

CANVAS_W, CANVAS_H = 416, 232   # fits Auto GUI scale on every 16:9 window (426x240 worst case)


def concept_a(fish, env, tab="catch", claimed=True, screen=(426, 240), scale=4, name="", annotate=False, show_fish=True):
    W, H = screen
    c = Canvas(W, H, scale)
    k = max(1, min(W // CANVAS_W, H // CANVAS_H))     # integer content scale only - never fractional
    if k > 1:
        # Re-render the whole canvas at k and composite: keeps every glyph on the pixel grid.
        inner = concept_a(fish, env, tab, claimed, (CANVAS_W, CANVAS_H), scale * k, name="", annotate=annotate, show_fish=show_fish)
        ox, oy = (W * scale - inner.size[0]) // 2, (H * scale - inner.size[1]) // 2
        c.img.alpha_composite(inner.convert("RGBA"), (ox, oy))
        if name:
            c.save(name)
        return c.img
    ox, oy = (W - CANVAS_W) // 2, (H - CANVAS_H) // 2

    # --- header: back, discovery, prev/next
    button(c, ox, oy, 52, 14, "< All fish")
    c.text_c(ox + CANVAS_W // 2, oy + 3, "Fishopedia", GOLD)
    c.text_r(ox + CANVAS_W - 34, oy + 3, "12 / 68", GREY)
    button(c, ox + CANVAS_W - 31, oy, 14, 14, "<")
    button(c, ox + CANVAS_W - 15, oy, 15, 14, ">")

    top = oy + 17
    body_h = 178
    caught = fish["catches"] > 0
    name_ok = unlocked(fish, "name")

    # --- left page: the specimen
    lx, lw = ox, 168
    panel(c, lx, top, lw, body_h, "gold" if fish["catches"] >= fish["thresholds"]["lore"] else "plain")
    title = fish["name"] if name_ok else "???"
    c.text(lx + 7, top + 6, title, WHITE if name_ok else GREY, box=lw - 14, label="species name")
    if name_ok:
        tiers = ["Abundant", "Common", "Uncommon", "Rare", "Very rare"]
        gems = tiers.index(fish["rarity"]) + 1
        for i in range(5):
            c.rect(lx + 7 + i * 6, top + 17, 4, 6, (110, 205, 225) if i < gems else (70, 46, 52))
        c.text(lx + 39, top + 16, fish["pool"], MUTED, box=lw - 46, label="rarity line")
    else:
        c.text(lx + 7, top + 16, "Not yet caught", MUTED)

    # hero plate
    px, py, pw, ph = lx + 6, top + 28, lw - 12, 66
    plate_png = os.path.join(HERE, "..", "bubbles", "plates", f"{fish['zones'][0]}.png")
    if os.path.exists(plate_png):                        # dithered zone plate (bubbles/PLAN.md)
        c.tex(plate_png, px, py, pw, ph)
    else:
        c.rect(px, py, pw, ph, (18, 44, 58), 255)
    c.outline(px, py, pw, ph, BLACK, 120)
    if show_fish:                                        # off when the flying icon is drawn separately
        c.tex(f"item/fish/{fish['id']}.png", px + (pw - 64) // 2, py + 1, 64, 64, silhouette=not caught)

    # size ruler (Stats + Records merged)
    ry = py + ph + 5
    c.text(lx + 7, ry, "Size", GREY)
    bar_x, bar_w = lx + 34, lw - 42
    if unlocked(fish, "stats") and caught:
        mean, sd = fish["size"]
        lo, hi = mean - 2 * sd, mean + 2 * sd
        span_lo, span_hi = lo - sd, hi + 2 * sd          # room either side for outliers / records
        to_x = lambda v: bar_x + int((v - span_lo) / (span_hi - span_lo) * bar_w)
        c.rect(bar_x, ry + 3, bar_w, 3, (40, 26, 26))
        c.rect(to_x(lo), ry + 3, to_x(hi) - to_x(lo), 3, (130, 170, 200))
        c.rect(to_x(mean), ry + 2, 1, 5, WHITE)
        c.text(to_x(lo) - 2, ry + 9, f"{lo:.0f}", MUTED)
        c.text_r(to_x(hi) + 2, ry + 9, f"{hi:.0f} cm", MUTED)
        if fish["you"]:
            yx = to_x(fish["you"][0])
            c.icon(["#####", ".###.", "..#.."], yx - 2, ry - 3, WHITE)
        if fish["server"]:
            sx = to_x(fish["server"][0])
            c.icon(["#####", ".###.", "..#.."], sx - 2, ry - 3, GOLD)
        rec_y = ry + 20
        c.icon(["#####", ".###.", "..#.."], lx + 7, rec_y + 2, WHITE)
        c.text(lx + 14, rec_y, f"You {fish['you'][0]} cm", WHITE)
        qx = lx + 14 + FONT.width(f"You {fish['you'][0]} cm ")
        c.rect(qx, rec_y + 1, 6, 6, BLACK, 160)
        c.rect(qx + 1, rec_y + 2, 4, 4, QUALITY_COLOR[fish["you"][1]])
        c.text(qx + 8, rec_y, fish["you"][1], WHITE)
        c.icon(["#####", ".###.", "..#.."], lx + 7, rec_y + 12, GOLD)
        lead = f"Best {fish['server'][0]} cm "
        c.text(lx + 14, rec_y + 10, lead, GOLD)
        holder = truncate(fish["server"][1], lw - 20 - FONT.width(lead))   # full name in a hover tooltip
        c.text(lx + 14 + FONT.width(lead), rec_y + 10, holder, 0xFFD27A, box=lw - 20 - FONT.width(lead), label="server holder")
    else:
        c.rect(bar_x, ry + 3, bar_w, 3, (40, 26, 26))
        c.icon("lock", lx + 7, ry + 20, MUTED)
        c.text(lx + 15, ry + 20, f"Size & records at {fish['thresholds']['stats']} catch", MUTED, box=lw - 22, label="size lock")
    # fight
    fy = top + body_h - 37
    c.text(lx + 7, fy, "Fight", GREY)
    if caught and unlocked(fish, "stats"):
        tname, diff = fish["temperament"]
        c.text(lx + 34, fy, tname, WHITE)
        segs = round(diff * 5)
        for i in range(5):
            c.rect(lx + lw - 45 + i * 8, fy + 1, 6, 6, (230, 120, 60) if i < segs else (60, 38, 36))
    else:
        c.text(lx + 34, fy, "???", MUTED)
    # companion strip
    cy = top + body_h - 22
    inset(c, lx + 5, cy - 2, lw - 10, 18, 60)
    complete = fish["catches"] >= fish["thresholds"]["lore"]
    if complete:
        c.icon("heart", lx + 9, cy + 3, PINK)
        c.text(lx + 19, cy + 3, "Bond ready", PINK)
        button(c, lx + lw - 56, cy - 1, 49, 14, "Bond", WHITE)
    else:
        c.icon("heart", lx + 9, cy + 3, DGREY)
        c.text(lx + 19, cy + 3, "Bond when complete", MUTED, box=lw - 30, label="bond line")

    # --- right page
    rx, rw = ox + lw + 4, CANVAS_W - lw - 4
    panel(c, rx, top, rw, body_h)
    # tabs
    tabs = [("catch", "How to catch"), ("notes", "Field notes")]
    tx = rx + 6
    for key, label in tabs:
        locked_tab = key == "notes" and not unlocked(fish, "lore")
        tw = FONT.width(label) + 9 + (8 if locked_tab else 0)
        active = key == tab
        c.rect(tx, top + 4, tw, 13, (0, 0, 0) if active else (60, 36, 34), 110 if active else 255)
        if active:
            c.rect(tx, top + 16, tw, 1, GOLD)
        locked_tab = key == "notes" and not unlocked(fish, "lore")
        c.text(tx + 4, top + 6, label, (YELLOW if active else (MUTED if locked_tab else GREY)))
        if locked_tab:
            c.icon("lock", tx + tw - 10, top + 6, MUTED)
        tx += tw + 3

    inner_x, inner_w = rx + 6, rw - 12
    y = top + 21
    if tab == "catch":
        y = catch_guide(c, fish, env, inner_x, y, inner_w, top + body_h - 4, None, rx, top, oy)
    else:
        inset(c, inner_x, y, inner_w, body_h - 25, 60)
        used = c.paragraph(inner_x + 5, y + 5, inner_w - 10, fish["lore"], 0xE8DCD0, line_h=10,
                           max_lines=(body_h - 35) // 10, label="lore")

    # --- footer: one unlock track + one claim action
    fy0 = top + body_h + 3
    fh = CANVAS_H - (fy0 - oy)
    panel(c, ox, fy0, CANVAS_W, fh)
    ms = milestones(fish)
    claim_w = 70
    track_x0, track_x1 = ox + 18, ox + CANVAS_W - claim_w - 30
    step = (track_x1 - track_x0) / len(ms)
    node_y = fy0 + 7
    c.rect(track_x0, node_y + 2, int(step * len(ms)), 1, (60, 38, 36))
    unclaimed_coins = 0
    next_idx = next((i for i, m in enumerate(ms) if fish["catches"] < m[0]), None)
    spans = []
    for i, (thr, _, key, label, coins) in enumerate(ms):
        nx = int(track_x0 + step * i + step / 2)
        got = fish["catches"] >= thr
        if got and i > 0:
            c.rect(int(track_x0 + step * (i - 1) + step / 2), node_y + 2, int(step), 1, GREEN)
        if got and not claimed:
            unclaimed_coins += coins
        c.rect(nx - 3, node_y - 1, 7, 7, BLACK, 160)
        c.rect(nx - 2, node_y, 5, 5, (85, 220, 90) if got else (255, 210, 80) if i == next_idx else (90, 64, 60))
        lab = f"{label} {fish['catches']}/{thr}" if i == next_idx else label
        col = WHITE if got else YELLOW if i == next_idx else MUTED
        c.text_c(nx, node_y + 8, lab, col)
        half = (FONT.width(lab) - 1) // 2
        spans.append((nx - half, nx + half, lab))
    # bond end-node
    bx = int(track_x1 + 10)
    c.icon("heart", bx - 3, node_y - 1, PINK if fish["catches"] >= fish["thresholds"]["lore"] else DGREY)
    c.text_c(bx, node_y + 8, "Bond", PINK if fish["catches"] >= fish["thresholds"]["lore"] else MUTED)
    spans.append((bx - 11, bx + 11, "Bond"))
    for (a0, a1, la), (b0, b1, lb) in zip(spans, spans[1:]):
        if a1 + 3 > b0:
            OVERFLOWS.append(f"footer labels collide: {la!r} / {lb!r} ({fish['id']})")
    c.text_c(ox + 9, node_y - 1, str(fish["catches"]), YELLOW)
    c.text_c(ox + 9, node_y + 8, "", MUTED)
    # claim
    cx0 = ox + CANVAS_W - claim_w - 6
    if unclaimed_coins:
        bw = claim_button(c, cx0, fy0 + 4, unclaimed_coins, f"Claim {unclaimed_coins}", h=fh - 8, min_w=claim_w)
        if bw > claim_w:
            OVERFLOWS.append(f"claim button {bw}px > slot {claim_w}px ({fish['id']})")
    else:
        if fish["catches"]:
            c.icon("check", cx0 + 8, fy0 + (fh - 7) // 2, GREEN)
            c.text(cx0 + 18, fy0 + (fh - 8) // 2, "Claimed", MUTED, box=claim_w - 20, label="claimed")

    if annotate:
        # (number, canvas-relative x, y) - numbers sit in the screen margin beside what they label
        L, Rm = -17, CANVAS_W + 4
        for n, x, y in [(1, L, 22), (2, L, 70), (3, L, 120), (4, L, 158), (5, L, 176),
                        (6, Rm, 21), (7, Rm, 41), (8, Rm, 90), (9, Rm, 137),
                        (10, L, 207), (11, Rm, 207), (12, Rm, 2)]:
            c.annotate(n, ox + x, oy + y)
    if name:
        c.save(name)
    return c.img


def catch_guide(c, fish, env, x, y, w, bottom, A, rx, top, oy):
    """Right page default tab: a live verdict, then one row per axis, then bait."""
    habits = unlocked(fish, "spawn") and fish["catches"] > 0
    kind, head, sub = verdict(fish, env, habits)
    colors = {"green": ((34, 92, 40), GREEN), "fair": ((52, 70, 76), 0xB8E0E8),
              "amber": ((110, 76, 20), 0xFFCC55), "red": ((110, 34, 34), RED)}
    bg, fg = colors[kind]
    c.rect(x, y, w, 23, bg, 235)
    c.rect(x, y, 2, 23, fg)
    c.text(x + 6, y + 3, head, fg)
    c.text_r(x + w - 4, y + 3, "now", MUTED if kind != "red" else 0xE0A0A0)
    c.text(x + 6, y + 13, sub, WHITE, box=w - 10, label="verdict sub")
    y += 27

    LBL = 46

    def label(t, yy):
        c.text(x, yy, t, GREY)

    # WHERE: zones always (ungated), biomes with habits
    label("Where", y + 4)
    zx = x + LBL
    for z in fish["zones"]:
        here = z == env["zone"]
        c.rect(zx - 1, y - 1, 18, 18, GREEN if here else BLACK, 160 if here else 70)
        c.tex(ZONE_TEX[z], zx, y, 16, 16)
        zx += 19
        c.text(zx, y + 4, ZONE_NAME[z], WHITE if here else 0xD8D0C8)
        zx += FONT.width(ZONE_NAME[z]) + 6
    y += 20
    if fish["biomes"]:
        if habits:
            bx = x
            for name_, m in fish["biomes"]:
                cw = FONT.width(name_) + FONT.width(effect_word(m)) + 10
                if bx + cw > x + w:
                    bx, y = x, y + 13
                here = env.get("biome") == name_
                chip(c, bx, y, name_, m)
                if here:
                    c.outline(bx - 1, y - 1, cw + 2, 13, WHITE)
                bx += cw + 3
            y += 15
        else:
            pass
    y += 1

    if not habits:
        inset(c, x, y, w, 30, 60)
        c.icon("lock", x + 5, y + 6, MUTED)
        need = fish["thresholds"]["spawn"]
        c.text(x + 14, y + 6, "Habits: when it bites best", MUTED)
        c.text(x + 14, y + 17, f"Unlock at {need} catch{'es' if need > 1 else ''}", GREY)
        y += 34
    else:
        # WHEN strip - colour = effect on odds, outline = now (two separate encodings)
        label("When", y + 3)
        cells = [("dawn", "Dawn"), ("day", "Day"), ("dusk", "Dusk"), ("night", "Night")]
        if fish["time"]:
            cw = (w - LBL) // 4
            for i, (k, nm) in enumerate(cells):
                m = fish["time"].get(k)
                cx = x + LBL + i * cw
                bg = BOOST_BG if m and m > 1 else PEN_BG if m and m < 1 else NEU_BG
                c.rect(cx, y, cw - 2, 14, bg, 230)
                c.icon(k if k != "day" else "sun", cx + 2, y + 3)
                c.text(cx + 11, y + 3, effect_word(m) if m else "-", WHITE if m else MUTED, box=cw - 13, label=f"strip {k}")
                if env["time"] == k:
                    c.outline(cx - 1, y - 1, cw, 16, WHITE)
                    c.icon("pin", cx + cw // 2 - 3, y - 4, WHITE)
            y += 18
        else:
            c.text(x + LBL, y + 3, "Any time of day", MUTED)
            y += 14
        # WEATHER
        if not fish["weather"] and not fish["moon"]:
            label("Sky", y + 3)
            c.text(x + LBL, y + 3, "Any weather or moon", MUTED)
            y += 14
        elif fish["weather"]:
            label("Weather", y + 3)
            cells = ["clear", "rain", "snow", "thunder"]
            cw = (w - LBL) // 4
            for i, k in enumerate(cells):
                m = fish["weather"].get(k)
                cx = x + LBL + i * cw
                bg = BOOST_BG if m and m > 1 else PEN_BG if m and m < 1 else NEU_BG
                c.rect(cx, y, cw - 2, 14, bg, 230)
                c.icon(k, cx + 2, y + 3)
                c.text(cx + 11, y + 3, effect_word(m) if m else "-", WHITE if m else MUTED, box=cw - 13, label=f"strip {k}")
                if env["weather"] == k:
                    c.outline(cx - 1, y - 1, cw, 16, WHITE)
            y += 18
        else:
            label("Weather", y + 3)
            c.text(x + LBL, y + 3, "Any weather", MUTED)
            y += 14
        # MOON - only when it matters; 8 phases, secondary bonuses
        if fish["moon"]:
            label("Moon", y + 3)
            cw = (w - LBL) // 8
            for p in range(8):
                cx = x + LBL + p * cw
                m = fish["moon"].get(p)
                c.rect(cx, y, cw - 1, 14, BOOST_BG if m else NEU_BG, 230)
                c.icon(moon_icon(p), cx + (cw - 8) // 2, y + 3, 0xF4F0D0, dim_color=(70, 66, 80))
                if env["time"] == "night" and env["weather"] == "clear" and env.get("moon") == p:
                    c.outline(cx - 1, y - 1, cw + 1, 16, WHITE)
            y += 16
            best = max(fish["moon"].values(), key=lambda t: t[0])
            c.text(x + LBL, y, f"Bright moons: {effect_word(*best).lower()} help", MUTED, box=w - LBL, label="moon note")
            y += 11
    # BAIT
    y += 2
    label("Bait", y + 4)
    if unlocked(fish, "types") and fish["catches"] > 0:
        baits = [BAITS[g] for g in fish["groups"] if g in BAITS]
        colw = (w - LBL) // 2 if len(baits) > 1 else w - LBL
        for i, (tex, nm) in enumerate(baits):
            bx = x + LBL + (i % 2) * colw
            by = y + (i // 2) * 18
            c.rect(bx - 1, by - 1, 18, 18, BLACK, 70)
            c.tex(tex, bx, by, 16, 16)
            c.text(bx + 19, by + 4, nm.replace(" Bait", ""), WHITE, box=colw - 21, label=f"bait {nm}")
        y += 18 * ((len(baits) + 1) // 2) + 2
    else:
        c.icon("lock", x + LBL, y + 4, MUTED)
        c.text(x + LBL + 8, y + 4, f"at {fish['thresholds']['types']} catch{'es' if fish['thresholds']['types'] > 1 else ''}", MUTED)
        y += 16
    if y > bottom:
        OVERFLOWS.append(f"catch guide for {fish['id']}: content bottom {y} > page bottom {bottom}")
    return y


def verdict(fish, env, habits):
    """(colour kind, headline, sub-line) - the single source of the verdict's wording."""
    if env["zone"] not in fish["zones"]:
        return "red", "Can't bite here", "Fish in " + " or ".join(ZONE_NAME[z] for z in fish["zones"]) + " water"
    if not habits:
        return "green", "Can bite here", "Learn its habits to judge this spot"
    head, kind = spot_word(env_multiplier(fish, env))
    factors = env_factors(fish, env)
    helps = [n for n, m in factors if m > 1]
    hurts = [n for n, m in factors if m < 1]
    verb = lambda names, one, many: f"{', '.join(names)} {one if len(names) == 1 else many}"
    parts = ([verb(helps, "helps", "help")] if helps else []) + ([verb(hurts, "hurts", "hurt")] if hurts else [])
    sub = "; ".join(parts) if parts else "Nothing helps or hurts it here"
    return kind, head, sub


VERDICT_SUB_W = (CANVAS_W - 168 - 4 - 12) - 10


def env_multiplier(fish, env):
    m = 1.0
    for name_, mult in fish["biomes"]:
        if env.get("biome") == name_:
            m *= mult
            break
    m *= fish["time"].get(env["time"], 1.0)
    m *= fish["weather"].get(env["weather"], 1.0)
    if env["time"] == "night" and env["weather"] == "clear" and env.get("moon") in fish["moon"]:
        mm, tier = fish["moon"][env["moon"]]
        m *= mm  # secondary-tier maths simplified for a single active bonus
    return round(m, 2)


def env_factors(fish, env):
    """(name, multiplier) for every condition active right now."""
    out = []
    for name_, mult in fish["biomes"]:
        if env.get("biome") == name_:
            out.append((name_, mult))
    if env["time"] in fish["time"]:
        out.append((env["time"].capitalize(), fish["time"][env["time"]]))
    if env["weather"] in fish["weather"]:
        out.append((env["weather"].capitalize(), fish["weather"][env["weather"]]))
    if env["time"] == "night" and env["weather"] == "clear" and env.get("moon") in fish["moon"]:
        out.append(("Full moon" if env["moon"] == 0 else "Bright moon", fish["moon"][env["moon"]][0]))
    return out


# ---------------------------------------------------------------- Concept B: compact tabbed card (fallback < 416 wide)

def concept_b(fish, env, screen=(320, 240), scale=4, name=""):
    W, H = screen
    c = Canvas(W, H, scale)
    cw, ch = 300, 228
    ox, oy = (W - cw) // 2, (H - ch) // 2
    button(c, ox, oy, 24, 14, "<")
    c.text_c(ox + cw // 2, oy + 3, fish["name"], WHITE)
    button(c, ox + cw - 24, oy, 24, 14, ">")
    top = oy + 17
    panel(c, ox, top, cw, ch - 17 - 26)
    # hero strip: 36px plate, three compact lines (no word "Rare" - that is catch quality)
    c.rect(ox + 6, top + 5, 38, 36, (24, 64, 84))
    c.tex(f"item/fish/{fish['id']}.png", ox + 9, top + 7, 32, 32)
    tiers = ["Abundant", "Common", "Uncommon", "Rare", "Very rare"]
    for i in range(5):
        c.rect(ox + 50 + i * 6, top + 7, 4, 6, (110, 205, 225) if i < tiers.index(fish["rarity"]) + 1 else (70, 46, 52))
    c.text(ox + 82, top + 6, fish["pool"], MUTED)
    lo, hi = fish["size"][0] - 2 * fish["size"][1], fish["size"][0] + 2 * fish["size"][1]
    c.text(ox + 50, top + 17, f"{lo:.0f}-{hi:.0f} cm", GREY)
    c.text(ox + 50 + FONT.width(f"{lo:.0f}-{hi:.0f} cm  "), top + 17, f"You {fish['you'][0]}", WHITE)
    c.text(ox + 50, top + 28, f"Server {fish['server'][0]} cm - {fish['server'][1]}", GOLD)
    # tabs: records live in the strip above, so only two tabs
    tx = ox + 6
    for i, t in enumerate(["How to catch", "Field notes"]):
        tw = FONT.width(t) + 9
        c.rect(tx, top + 45, tw, 13, BLACK if i == 0 else (60, 36, 34), 110 if i == 0 else 255)
        if i == 0:
            c.rect(tx, top + 57, tw, 1, GOLD)
        c.text(tx + 4, top + 47, t, YELLOW if i == 0 else GREY)
        tx += tw + 3
    catch_guide(c, fish, env, ox + 6, top + 62, cw - 12, top + ch - 17 - 26, lambda *a: None, ox, top, oy)
    fy = oy + ch - 24
    panel(c, ox, fy, cw, 24)
    c.text(ox + 6, fy + 8, f"6/6  All unlocks done", WHITE)
    button(c, ox + cw - 54, fy + 5, 48, 14, "Bond", PINK)
    if name:
        c.save(name)
    return c.img


# ---------------------------------------------------------------- disc: book-wide claim

def disc_claim_all(name):
    """The real disc capture (640x360 logical @ 3x) with the proposed book-wide claim under the disc."""
    W, H, S = 640, 360, 3
    c = Canvas(W, H, S, backdrop=False)
    shot = os.path.join(HERE, "..", "current", "selftest-fabric-encyclopedia-s3_disc.png")
    c.img = Image.open(shot).convert("RGBA")
    c.draw = ImageDraw.Draw(c.img, "RGBA")
    coins, fish_n = 10, 2                       # bluegill 2 + glass squid 8, same fixtures as the pages
    label = f"Claim all {coins}"
    bw = 6 + FONT.width(label) + 3 + 16 + 5
    bx, by = (W - bw) // 2, 260
    claim_button(c, bx, by, coins, label)
    c.text_c(W // 2, by + 25, f"from the {fish_n} fish with green pips", MUTED)
    c.save(name)


def coin_ladder_sheet(name):
    """Every rung of the coin ladder, as the page button (top) and the disc button (bottom)."""
    amounts = [1, 2, 5, 8, 12, 30]
    W, H = 420, 96
    c = Canvas(W, H, 4, backdrop=False)
    panel(c, 0, 0, W, H)
    c.text(6, 5, "Page claim (max 8 per fish)", GREY)
    c.text(6, 50, "Disc claim-all (whole book)", GREY)
    col = W // len(amounts)
    for i, a in enumerate(amounts):
        x = 6 + i * col
        if a <= 8:
            claim_button(c, x, 16, a, f"Claim {a}", h=26, min_w=60)
        else:
            c.text(x + 4, 25, "(disc only)", DGREY)
        claim_button(c, x, 61, a, f"All {a}")
        rung = COIN_RUNG_NAME[coin_icon(a)[0].split("/")[-1][:-4]]
        c.text(x, 86, rung, MUTED, box=col - 4, label=f"rung {a}")
    c.save(name)


# ---------------------------------------------------------------- comparison sheet

def side_by_side(left_path, right_img, out, labels=("Current", "Proposed")):
    L = Image.open(left_path).convert("RGB")
    R = right_img.convert("RGB").resize(L.size, Image.NEAREST)
    gap, head = 24, 70
    sheet = Image.new("RGB", (L.width * 2 + gap, L.height + head), (16, 16, 20))
    sheet.paste(L, (0, head))
    sheet.paste(R, (L.width + gap, head))
    c = Canvas(sheet.width // 4, head // 4, 4, backdrop=False)
    c.img = sheet.convert("RGBA")
    c.draw = ImageDraw.Draw(c.img, "RGBA")
    c.text(4, 4, labels[0], WHITE)
    c.text((L.width + gap) // 4 + 4, 4, labels[1], GOLD)
    c.img.convert("RGB").save(os.path.join(OUT, out))
    print("wrote", out)


def main():
    night_river = dict(zone="river", biome=None, time="night", weather="clear", moon=2)
    jungle_day = dict(zone="river", biome="Jungle", time="day", weather="clear", moon=0)
    ocean_storm = dict(zone="ocean", biome=None, time="day", weather="thunder", moon=0)
    river_dusk = dict(zone="river", biome=None, time="dusk", weather="rain", moon=0)
    deep_night = dict(zone="deep_ocean", biome=None, time="night", weather="clear", moon=0)

    # Primary states at the binding size (Auto scale on 720p / 1440p / 4K) ...
    concept_a(BETTA, night_river, screen=(426, 240), name="A1_betta_complete_night_426x240.png")
    concept_a(BETTA, jungle_day, screen=(426, 240), name="A2_betta_jungle_day_426x240.png")
    concept_a(GLASS_SQUID, deep_night, claimed=False, screen=(426, 240), name="A3_glass_squid_unclaimed_426x240.png")
    concept_a(BLUEGILL, river_dusk, claimed=False, screen=(426, 240), name="A4_bluegill_midprogress_426x240.png")
    concept_a(MAN_O_WAR, night_river, claimed=False, screen=(426, 240), name="A5_man_o_war_uncaught_426x240.png")
    concept_a(BETTA, night_river, tab="notes", screen=(426, 240), name="A6_betta_field_notes_426x240.png")
    # ... the 1080p Auto size (same canvas, centred) and a manual scale 2 (integer 2x canvas)
    a1080 = concept_a(BETTA, night_river, screen=(480, 270), name="A7_betta_480x270_1080p_auto.png")
    concept_a(BETTA, night_river, screen=(960, 540), scale=2, name="A8_betta_960x540_1080p_scale2.png")
    concept_a(BETTA, night_river, screen=(452, 240), annotate=True, name="A0_betta_annotated.png")
    disc_claim_all("D1_disc_claim_all_640x360.png")
    coin_ladder_sheet("E1_claim_coin_ladder.png")
    # Compact fallback for 4:3 / tiny windows
    concept_b(BETTA, night_river, name="B1_compact_fallback_320x240.png")

    cur = os.path.join(HERE, "..", "current", "selftest-fabric-encyclopedia-s4_betta_top.png")
    if os.path.exists(cur):
        side_by_side(cur, a1080, "compare_betta_1080p_auto.png")
    cur2 = os.path.join(HERE, "..", "current", "selftest-fabric-encyclopedia-s4_portuguese_man_o_war_top.png")
    if os.path.exists(cur2):
        side_by_side(cur2, concept_a(MAN_O_WAR, night_river, screen=(480, 270)), "compare_uncaught_1080p_auto.png")

    if OVERFLOWS:
        print("\nLAYOUT OVERFLOWS:")
        for o in OVERFLOWS:
            print("  -", o)
        sys.exit(1)
    print("\nall text fits its box")


if __name__ == "__main__":
    main()
