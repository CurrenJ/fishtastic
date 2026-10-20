"""Pixel-faithful quest-book redesign mockups.

The mockups use the existing GUI workbench renderer and the mod's real panel, coin,
fish, and button textures. Layout is authored in Minecraft logical GUI pixels and
rendered at an integer scale so the proposal can be judged at the same size as the
live capture.

Run from the repository root:
    python -I docs/quest-log-redesign/mockups/render_mockups.py
"""
import importlib.util
import os
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
OUT = HERE
CURRENT = os.path.join(ROOT, "docs", "quest-log-redesign", "run-fabric", "screenshots",
                      "selftest-fabric-fixes-quest_log.png")
SOURCE = os.path.join(ROOT, "docs", "encyclopedia-redesign", "mockups", "render_mockups.py")
GUI_PANEL = os.path.join(ROOT, "common", "src", "main", "resources", "assets", "fishtastic",
                         "textures", "gui", "generic_item_panel.png")

spec = importlib.util.spec_from_file_location("pixel_renderer", SOURCE)
pixel = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pixel)
pixel.OUT = OUT
if os.path.exists(CURRENT):
    pixel.BACKDROP = CURRENT

Canvas = pixel.Canvas
FONT = pixel.FONT
panel = pixel.panel
button = pixel.button
inset = pixel.inset
WHITE, GREY, DGREY, BLACK = pixel.WHITE, pixel.GREY, pixel.DGREY, pixel.BLACK
GOLD, YELLOW, GREEN, RED, AQUA, PINK = pixel.GOLD, pixel.YELLOW, pixel.GREEN, pixel.RED, pixel.AQUA, pixel.PINK
coin_icon = pixel.coin_icon
claim_button = pixel.claim_button

BG = 0x17151B
INK = 0x201A24
MUTED = 0xC9B8BE
MAUVE = 0x6F3F48
MAUVE_DARK = 0x3D2931
BLUE = 0x8CCBFF
READY = 0x5DCC7A
READY_BG = 0x275B3C
PROGRESS = 0xB86E88
LINE = 0xA75A70
TOKEN_TOP, TOKEN_HEIGHT, FILTER_TOP = 6, 18, 28
assert TOKEN_TOP + TOKEN_HEIGHT + 4 <= FILTER_TOP


def save(c, name):
    c.save(name)
    return os.path.join(OUT, name)


def text(c, x, y, s, color=WHITE, box=None, label=""):
    c.text(x, y, s, color, box=box, label=label)


def centered(c, x, y, w, s, color=WHITE, box=None, label=""):
    c.text_c(x + w // 2, y, s, color, box=box or w - 2, label=label)


def fit_text(c, x, y, max_w, s, color=WHITE, label=""):
    # Keep the bitmap font legible in the mockup while making long real strings safe.
    if FONT.width(s) - 1 <= max_w:
        text(c, x, y, s, color, box=max_w, label=label)
        return
    words, cur = s.split(" "), ""
    for word in words:
        candidate = word if not cur else cur + " " + word
        if FONT.width(candidate) - 1 <= max_w:
            cur = candidate
        else:
            if cur:
                text(c, x, y, cur, color, box=max_w, label=label)
            cur = word
            y += 9
    if cur:
        text(c, x, y, cur, color, box=max_w, label=label)


def coin(c, amount, x, y, slot=16):
    """Use the shared pile-of-coins ladder while keeping a stable icon slot."""
    tex, iw, ih = coin_icon(amount)
    c.tex(tex, x + (slot - iw) // 2, y + (slot - ih) // 2, iw, ih)


def claim_button_inset(c, right, y, amount, label, h=22, min_w=0, padding=5):
    """Place the standard claim button from the right edge so long labels cannot overflow."""
    w = max(min_w, 6 + FONT.width(label) + 3 + 16 + 5)
    claim_button(c, right - padding - w, y, amount, label, h=h, min_w=min_w)
    return w


def rail(c, ox, oy, active, ready_counts=None):
    # A labelled rail removes the ambiguity of the current seven icon-only tabs.
    c.nine(GUI_PANEL, ox, oy, 74, 232, border=4)
    c.rect(ox + 4, oy + 5, 66, 221, INK, 215)
    text(c, ox + 10, oy + 9, "CAPTAIN'S", 0xEBDAD1, box=57, label="rail title")
    text(c, ox + 10, oy + 18, "LOG", GOLD, box=57, label="rail title")
    items = [("Today", "sun"), ("Goals", "check"), ("Explore", "compass"),
             ("Collect", "map"), ("Trials", "star"), ("Market", "coin"), ("Cleanup", "wave")]
    y = oy + 34
    for label, icon in items:
        is_active = label == active
        row_x, row_y, row_w, row_h = ox + 7, y - 2, 60, 23
        if is_active:
            c.rect(row_x, row_y, row_w, row_h, MAUVE, 255)
            c.rect(row_x, row_y, 2, row_h, GOLD, 255)

        # Center the icon + label as one visual group. The previous fixed x positions
        # left short labels noticeably left of center and made the icon read as a
        # separate marker instead of part of the tab label.
        icon_w = icon_h = 5
        gap = 5
        label_w = FONT.width(label) - 1
        group_w = icon_w + gap + label_w
        group_x = row_x + (row_w - group_w) // 2
        icon_y = row_y + (row_h - icon_h) // 2
        text_y = row_y + (row_h - 8) // 2
        count = (ready_counts or {}).get(label, 0)
        if count:
            text(c, group_x, text_y, str(count), READY, box=5, label=f"ready count {label}")
        else:
            draw_rail_icon(c, icon, group_x, icon_y, MUTED)
        text(c, group_x + icon_w + gap, text_y, label,
             WHITE if is_active else MUTED, box=label_w, label=f"rail {label}")
        y += 26 if label == "Trials" else 25
        if label == "Trials":
            c.rect(ox + 11, y - 6, 52, 1, 0x6C4B54, 255)
            y += 3
    text(c, ox + 10, oy + 215, "Esc  close", DGREY, box=54, label="rail close")


def draw_rail_icon(c, name, x, y, color):
    # Small original pixel glyphs, deliberately quieter than item art.
    shapes = {
        "sun": [".###.", "#####", ".###.", "#####", ".###."],
        "check": ["....#", "...##", "..##.", "###..", "##..."],
        "compass": [".###.", "#####", "##.##", "#####", ".###."],
        "map": ["##.##", "#####", "##.##", "#####", "##.##"],
        "star": ["..#..", ".###.", "#####", ".###.", "..#.."],
        "coin": [".###.", "#####", "##.##", "#####", ".###."],
        "wave": ["#..#.", ".##..", "..#.#", ".##..", "#..#."],
    }
    rows = shapes[name]
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch == "#": c.rect(x + i, y + j, 1, 1, color)


def shell(active, title, subtitle="", token_label="42 tokens", ready_counts=None):
    c = Canvas(480, 270, 2, backdrop=True)
    ox, oy = 32, 19
    c.rect(ox, oy, 416, 232, BG, 235)
    rail(c, ox, oy, active, ready_counts)
    # main panel / header
    px, py, pw = ox + 78, oy, 338
    c.nine(GUI_PANEL, px, py, pw, 232, border=4)
    c.rect(px + 4, py + 4, pw - 8, 224, INK, 220)
    text(c, px + 12, py + 10, title, WHITE, box=205, label="page title")
    # The revised shell spends the subtitle space on actionable content.
    if subtitle:
        text(c, px + 12, py + 23, subtitle, MUTED, box=220, label="page context")
    # token chip is consistently placed in the same top-right location on every page
    # Keep a 4px clear band before the filters/reset row, including the coin artwork.
    c.rect(px + pw - 99, py + TOKEN_TOP, 87, TOKEN_HEIGHT, 0x3A2B22, 255)
    coin(c, 42, px + pw - 92, py + TOKEN_TOP + 1, 16)
    text(c, px + pw - 74, py + TOKEN_TOP + 5, token_label, GOLD, box=60, label="token balance")
    return c, px, py, pw


def filter_row(c, x, y, states=("All", "In progress", "Ready"), selected="All"):
    cursor = x
    for state in states:
        w = FONT.width(state) + 12
        c.rect(cursor, y, w, 15, MAUVE if state == selected else MAUVE_DARK, 255)
        if state == selected:
            c.rect(cursor, y + 14, w, 1, GOLD, 255)
        centered(c, cursor, y + 4, w, state, YELLOW if state == selected else MUTED, box=w - 3,
                 label=f"filter {state}")
        cursor += w + 3


def progress(c, x, y, w, fraction, color=PROGRESS):
    c.rect(x, y, w, 5, 0x2A2028, 255)
    if fraction > 0:
        c.rect(x, y, min(w, max(1, int(w * fraction))), 5, color, 255)
    c.rect(x, y + 5, w, 1, 0x59404A, 255)



if __name__ == "__main__":
    # One entry point for the complete, current proposal; no stale first-pass screens.
    import runpy
    runpy.run_path(os.path.join(HERE, "render_card_review.py"), run_name="__main__")
