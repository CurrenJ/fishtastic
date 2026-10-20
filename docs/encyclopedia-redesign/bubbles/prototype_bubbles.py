"""
Motion prototype for the Fishopedia portrait's ambient bubbles - a stand-in for the in-game element
so the look (density, speed, variety, per-zone theme) can be judged before any mod code exists.

Everything is in logical GUI pixels and seconds, rendered at 4x with positions snapped to the
logical pixel grid exactly as the game would draw them. Bubble sizes, the dissolve-vs-pop split and
the wobble come from TankMicroBubbleParticle, so the portrait reads as the same water as a tank.

Run:  python -I docs/encyclopedia-redesign/bubbles/prototype_bubbles.py
Writes one looping GIF per theme plus a stills sheet, and prints per-theme density stats.
"""
import math
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "mockups"))
import render_mockups as R  # noqa: E402  (font + texture paths)

S = 4                        # output scale (1080p Auto)
PW, PH = 156, 66             # portrait plate, logical px (same box as the page mockups)
FPS = 20
SECONDS = 6
WARMUP = 5.0                 # pre-simulate so the plate is already alive when the page opens
SURFACE_Y = 3                # medium bubbles pop here instead of leaving the plate
FISH_BOX = (46, 1, 64, 64)   # where the specimen sprite sits

TEX = R.TEX
ASSETS = os.path.join(HERE, "..", "_assets")


def load(path, crop=None):
    im = Image.open(path).convert("RGBA")
    return im.crop(crop) if crop else im


SMALL = load(os.path.join(TEX, "particle", "small_bubble.png"))
MEDIUM = load(os.path.join(TEX, "particle", "medium_bubble.png"), (2, 2, 8, 8))   # 6x6 visible
POPS = [load(os.path.join(ASSETS, f"bubble_pop_{i}.png")) for i in range(5)]

# ------------------------------------------------------------------ themes

THEMES = {
    # name: plate gradient (top, bottom), rate multipliers, drift, specials, sample fish
    "ocean": dict(top=(20, 60, 78), bottom=(42, 96, 112), ramp=[(14, 40, 64), (20, 64, 88), (30, 92, 110), (52, 124, 130)], rate=1.0, rise=1.0, drift=0.0,
                  tiny_cols=[(235, 245, 255), (170, 215, 255)], fish="moorish_idol", label="Ocean"),
    "river": dict(top=(22, 66, 64), bottom=(44, 102, 90), ramp=[(14, 46, 50), (22, 70, 66), (36, 98, 82), (64, 126, 96)], rate=1.0, rise=1.0, drift=3.5,
                  tiny_cols=[(235, 250, 240), (180, 230, 215)], fish="bluegill", label="River: slow current"),
    "deep_ocean": dict(top=(6, 14, 30), bottom=(14, 34, 58), ramp=[(4, 8, 20), (8, 18, 38), (14, 30, 58), (24, 46, 80)], rate=0.4, rise=0.7, drift=0.0, snow=0.9,
                       tiny_cols=[(150, 190, 230)], fish="glass_squid", label="Deep Ocean: marine snow"),
    "cave": dict(top=(14, 22, 32), bottom=(24, 42, 54), ramp=[(10, 14, 24), (16, 24, 38), (24, 38, 54), (36, 56, 72)], rate=0.6, rise=0.85, drift=0.0,
                 tiny_cols=[(120, 170, 255), (90, 140, 230)], fish="blind_cavefish", label="Cave: dim, blue fizz"),
    "high_altitude": dict(top=(44, 98, 128), bottom=(84, 146, 166), ramp=[(34, 78, 116), (50, 108, 144), (74, 140, 168), (110, 174, 190)], rate=1.0, rise=1.1, drift=1.5, fizz=2.2,
                          tiny_cols=[(250, 252, 255)], fish="golden_trout", label="High Altitude: cold fizz"),
    "nether": dict(top=(70, 12, 6), bottom=(176, 64, 14), ramp=[(52, 8, 6), (100, 20, 8), (156, 48, 10), (206, 94, 20)], rate=0.0, rise=1.0, drift=0.0, embers=3.0,
                   tiny_cols=[], fish="molten_moorish_idol", label="Nether: embers in lava"),
}


# ------------------------------------------------------------------ dithered plate

BAYER4 = [[0, 8, 2, 10], [12, 4, 14, 6], [3, 11, 1, 9], [15, 7, 13, 5]]   # classic ordered dither
CHECKER = [[0, 2], [3, 1]]                                                # 2x2: chunkier, more "pixel art"
DITHER_ZONE = 0.6   # fraction of each band given to the dithered transition (rest is solid)


def plate_background(theme, pattern="bayer"):
    """The plate as a logical-size image: BANDS solid colours, ordered dither only where two meet.
    In game this is baked once per theme (it never changes), so its cost is one texture blit."""
    t = THEMES[theme]
    if pattern == "smooth":                             # the previous look, for comparison
        stops = [t["top"], t["bottom"]]
    else:
        stops = t["ramp"]                               # hand-picked, hue-shifted pixel-art ramp
    BANDS = len(stops)
    img = Image.new("RGBA", (PW, PH))
    px = img.load()
    for y in range(PH):
        pos = y / (PH - 1) * (BANDS - 1)
        band = min(int(pos), BANDS - 2)
        frac = pos - band
        # squeeze the blend into a zone centred on the band edge, so band middles stay solid
        w = DITHER_ZONE
        blend = min(1.0, max(0.0, (frac - (1 - w)) / w)) if frac > 1 - w else 0.0
        for x in range(PW):
            if pattern == "smooth":
                col = tuple(int(stops[band][c] + (stops[band + 1][c] - stops[band][c]) * frac) for c in range(3))
            else:
                m = BAYER4 if pattern == "bayer" else CHECKER
                n = len(m)
                threshold = (m[y % n][x % n] + 0.5) / (n * n)
                col = stops[band + 1] if blend > threshold else stops[band]
            px[x, y] = col + (255,)
    return img


class Bubble:
    __slots__ = ("kind", "x", "y", "vx", "vy", "age", "life", "amp", "freq", "phase", "alpha",
                 "size", "front", "col", "popping", "pop_age")

    def __init__(self, **kw):
        self.popping, self.pop_age, self.front, self.col = False, 0.0, False, (255, 255, 255)
        for k, v in kw.items():
            setattr(self, k, v)


class Plate:
    """One portrait's ambient system. Deterministic for a seed, like the in-game element would be
    (seeded by species id so a self-test capture is reproducible)."""

    def __init__(self, theme, seed, muted=False, pattern="bayer"):
        self.paused = False                             # the portrait's toggle (global client pref)
        self.reduced_effects = False                    # existing reducedCelebrationEffects option
        self.last_click = -9.0
        self.t = THEMES[theme]
        self.bg = plate_background(theme, pattern).resize((PW * S, PH * S), Image.NEAREST)
        self.rng = random.Random(seed)
        self.bubbles = []
        self.muted = muted
        self.plume_timer = self.rng.uniform(1.5, 4.0)
        self.acc = {"tiny": 0.0, "small": 0.0, "medium": 0.0, "snow": 0.0, "ember": 0.0, "blob": 0.0}
        self.time = 0.0
        self.peak = 0

    # spawn rates per second (before theme multipliers)
    RATES = {"tiny": 2.4, "small": 1.2, "medium": 0.35}
    MAX_BUBBLES = 32                                    # hard cap for ambient spawns
    MAX_WITH_EXTRAS = 56                                # player-triggered bursts may go above it
    CLICK_COOLDOWN = 0.35

    def spawn(self, kind, x=None, y=None, extra=False):
        r, t = self.rng, self.t
        if len(self.bubbles) >= (self.MAX_WITH_EXTRAS if extra else self.MAX_BUBBLES):
            return None
        if x is None:
            x = r.uniform(3, PW - 4)
        if kind == "tiny":
            if y is None:
                y = r.uniform(PH * 0.45, PH - 2)        # fizz starts anywhere in the lower half
            b = Bubble(kind=kind, x=x, y=y, vx=t["drift"], vy=-r.uniform(10, 18) * t["rise"], age=0.0,
                       life=r.uniform(0.9, 2.2), amp=r.uniform(0.2, 0.6), freq=r.uniform(1.5, 3.0),
                       phase=r.uniform(0, 6.3), alpha=r.uniform(0.55, 0.9), size=1,
                       col=r.choice(t["tiny_cols"]))
        elif kind == "small":
            b = Bubble(kind=kind, x=x, y=PH - 1 if y is None else y, vx=t["drift"], vy=-r.uniform(7, 12) * t["rise"],
                       age=0.0, life=r.uniform(3.0, 6.0), amp=r.uniform(0.6, 1.3), freq=r.uniform(1.0, 1.8),
                       phase=r.uniform(0, 6.3), alpha=r.uniform(0.65, 0.95), size=2)
        elif kind == "medium":
            b = Bubble(kind=kind, x=x, y=PH + 2 if y is None else y, vx=t["drift"] * 0.6, vy=-r.uniform(5, 8) * t["rise"],
                       age=0.0, life=30.0, amp=r.uniform(1.0, 2.0), freq=r.uniform(0.5, 0.9),
                       phase=r.uniform(0, 6.3), alpha=0.9, size=r.choice([4, 5, 5, 6, 6, 7]))
            b.front = r.random() < 0.2                  # a few pass in front of the fish, for depth
        elif kind == "snow":                            # deep-ocean marine snow: drifts DOWN
            b = Bubble(kind=kind, x=x, y=-1, vx=r.uniform(-1.5, 1.5), vy=r.uniform(2, 4.5), age=0.0,
                       life=40.0, amp=r.uniform(0.5, 1.5), freq=r.uniform(0.2, 0.5), phase=r.uniform(0, 6.3),
                       alpha=r.uniform(0.35, 0.6), size=1, col=(205, 215, 225))
        elif kind == "ember":                           # nether: sparks rising through lava
            b = Bubble(kind=kind, x=x, y=PH - 1, vx=r.uniform(-2, 2), vy=-r.uniform(9, 17), age=0.0,
                       life=r.uniform(1.5, 3.5), amp=r.uniform(0.4, 1.2), freq=r.uniform(1.5, 3.5),
                       phase=r.uniform(0, 6.3), alpha=1.0, size=1)
        elif kind == "blob":                            # nether: slow molten globules
            b = Bubble(kind=kind, x=x, y=PH + 3, vx=0, vy=-r.uniform(3, 5), age=0.0, life=r.uniform(8, 14),
                       amp=r.uniform(0.5, 1.0), freq=r.uniform(0.3, 0.6), phase=r.uniform(0, 6.3),
                       alpha=1.0, size=r.choice([2, 3, 3]))
        self.bubbles.append(b)
        return b

    # ---------------------------------------------------------------- player-triggered extras

    def click(self, x, y):
        """Click on the portrait: a plume from the cursor (embers in lava). Ignored while paused."""
        if self.paused or self.time - self.last_click < self.CLICK_COOLDOWN:
            return
        self.last_click = self.time
        r = self.rng
        on_fish = FISH_BOX[0] <= x < FISH_BOX[0] + FISH_BOX[2]
        if self.t.get("embers"):
            for _ in range(18):
                b = self.spawn("ember", x=x + r.uniform(-2, 2), extra=True)
                if b:
                    b.y, b.vx, b.vy = y, r.uniform(-12, 12), -r.uniform(16, 32)
                    b.size = 2                          # starts as a hot 2px spark, cools to 1px
                    b.front = on_fish
            return
        for kind, n in (("small", 5), ("medium", 2), ("tiny", 6)):
            for i in range(n):
                b = self.spawn(kind, x=x + r.uniform(-3, 3), y=y + r.uniform(0, 3) + i * 2, extra=True)
                if b:
                    b.vy *= 1.3
                    b.front = on_fish                     # clicked on the fish: bubbles come off it

    def claim_burst(self):
        """Rewards claimed: one rush of bubbles up the whole plate (embers in lava)."""
        if self.paused or self.reduced_effects:
            return
        r = self.rng
        if self.t.get("embers"):
            for _ in range(28):
                b = self.spawn("ember", extra=True)
                if b:
                    b.vy *= 1.6
                    b.size = 2
                    b.front = r.random() < 0.3
            return
        for i in range(16):
            kind = "medium" if i % 4 == 0 else "small"
            b = self.spawn(kind, x=4 + i * (PW - 8) / 15 + r.uniform(-2, 2), y=PH + 1 + r.uniform(0, 6), extra=True)
            if b:
                b.vy *= 1.7
                b.front = r.random() < 0.3
        for _ in range(14):                             # fizz riding along with the rush
            b = self.spawn("tiny", y=PH - 1 - r.uniform(0, 8), extra=True)
            if b:
                b.vy *= 1.6

    def on_open(self):
        """Page just opened (after the warm start): schedule the welcome plume."""
        self.plume_timer = self.rng.uniform(0.8, 1.6)

    def step(self, dt):
        r, t = self.rng, self.t
        self.time += dt
        if self.paused:                                 # frozen: keep the last frame as a still
            return
        # steady emission
        for kind, rate in self.RATES.items():
            mult = t["rate"] * (t.get("fizz", 1.0) if kind == "tiny" else 1.0)
            self.acc[kind] += rate * mult * dt
        for kind, key in (("snow", "snow"), ("ember", "embers")):
            self.acc[kind] += t.get(key, 0.0) * dt
        if t.get("embers"):
            self.acc["blob"] += 0.25 * dt
        for kind in list(self.acc):
            while self.acc[kind] >= 1.0:
                self.acc[kind] -= 1.0
                self.spawn(kind)
        # plumes: the only "event" - a short column from one spot, spaced so it never feels busy
        if t["rate"] > 0:
            self.plume_timer -= dt
            if self.plume_timer <= 0:
                x = r.uniform(8, PW - 9)
                n = r.randint(4, 7)
                for i in range(n):
                    kind = "medium" if r.random() < 0.3 else "small"
                    self.spawn(kind, x=x + r.uniform(-1, 1), y=PH + 1 + i * 3.5)
                self.plume_timer = r.uniform(5.0, 8.0) / max(t["rate"], 0.3)
        # integrate
        alive = []
        for b in self.bubbles:
            if b.popping:
                b.pop_age += dt
                if b.pop_age < 0.25:
                    alive.append(b)
                continue
            b.age += dt
            b.x += b.vx * dt
            b.y += b.vy * dt
            if b.kind == "medium" and b.y <= SURFACE_Y:
                b.popping = True                        # vanilla bubble_pop frames, 0.25 s
                alive.append(b)
                continue
            if b.age >= b.life or b.y < -4 or b.y > PH + 6 or b.x < -4 or b.x > PW + 4:
                continue
            alive.append(b)
        self.bubbles = alive
        self.peak = max(self.peak, len(alive))

    # ---------------------------------------------------------------- drawing

    def render(self, fish_tex, silhouette=False, button="idle", cursor=None):
        img = self.bg.copy()
        mute = 0.5 if self.muted else 1.0
        for b in self.bubbles:
            if not b.front:
                self._draw(img, b, mute)
        fx, fy, fw, fh = FISH_BOX
        if fish_tex is None:                            # fish drawn elsewhere (e.g. mid-flight)
            for b in self.bubbles:
                if b.front:
                    self._draw(img, b, mute)
            return img
        fish = Image.open(fish_tex).convert("RGBA")
        if fish.height > fish.width:                    # animated strip: frames stacked vertically
            fish = fish.crop((0, 0, fish.width, fish.width))
        fish = fish.resize((fw * S, fh * S), Image.NEAREST)
        if silhouette:
            a = fish.split()[3]
            fish = Image.new("RGBA", fish.size, (8, 8, 10, 255))
            fish.putalpha(a)
        img.alpha_composite(fish, (fx * S, fy * S))
        for b in self.bubbles:
            if b.front:
                self._draw(img, b, mute)
        # frame
        edge = Image.new("RGBA", img.size, (0, 0, 0, 0))
        for (x0, y0, x1, y1) in [(0, 0, PW, 1), (0, PH - 1, PW, PH), (0, 0, 1, PH), (PW - 1, 0, PW, PH)]:
            edge.paste((0, 0, 0, 120), (x0 * S, y0 * S, x1 * S, y1 * S))
        img.alpha_composite(edge)
        if button:
            self._toggle_button(img, button)
        if cursor:
            self._cursor(img, *cursor)
        return img

    PAUSE_ICON = ["##.##", "##.##", "##.##", "##.##", "##.##"]
    PLAY_ICON = [".#...", ".##..", ".###.", ".##..", ".#..."]
    BUTTON = (PW - 11, 2, 9, 9)                         # top-right corner, clear of the fish art

    def _toggle_button(self, img, state):
        """Shows the action (pause while playing, play while paused), like a media control.
        Quiet at rest so it doesn't compete with the specimen; full contrast on hover."""
        bx, by, bw, bh = self.BUTTON
        hover = state == "hover"
        layer = Image.new("RGBA", img.size, (0, 0, 0, 0))
        layer.paste((0, 0, 0, 170 if hover else 90), (bx * S, by * S, (bx + bw) * S, (by + bh) * S))
        img.alpha_composite(layer)
        if hover:
            for (x0, y0, x1, y1) in [(bx, by, bx + bw, by + 1), (bx, by + bh - 1, bx + bw, by + bh),
                                      (bx, by, bx + 1, by + bh), (bx + bw - 1, by, bx + bw, by + bh)]:
                img.paste((255, 255, 255, 255), (x0 * S, y0 * S, x1 * S, y1 * S))
        icon = self.PLAY_ICON if self.paused else self.PAUSE_ICON
        col = (255, 255, 255) if hover else (210, 220, 230)
        a = 1.0 if hover else 0.6
        for j, row in enumerate(icon):
            for i, ch in enumerate(row):
                if ch == "#":
                    self._px(img, bx + 2 + i, by + 2 + j, col, a)

    CURSOR = ["#.....", "##....", "#.#...", "#..#..", "#...#.", "#..###", "#.#...", "##...."]

    def _cursor(self, img, x, y):
        for j, row in enumerate(self.CURSOR):
            for i, ch in enumerate(row):
                if ch == "#":
                    self._px(img, x + i, y + j, (255, 255, 255), 1.0)

    def _draw(self, img, b, mute):
        wob = b.amp * math.sin(2 * math.pi * b.freq * b.age + b.phase)
        x, y = round(b.x + wob), round(b.y)             # pixel-snapped, like the game draws it
        a = b.alpha * mute
        if b.popping:
            frame = POPS[min(4, int(b.pop_age / 0.05))].resize((8 * S, 8 * S), Image.NEAREST)
            self._blit(img, frame, x - 4, y - 4, a)
            return
        if b.kind == "tiny":
            fade = min(1.0, (b.life - b.age) / 0.4)
            self._px(img, x, y, b.col, a * fade)
        elif b.kind == "small":
            if b.life - b.age < 0.6:                    # dissolves: shrinks to 1px, then gone
                self._px(img, x, y, (230, 240, 255), a * 0.8)
            else:
                self._blit(img, SMALL.resize((2 * S, 2 * S), Image.NEAREST), x, y, a)
        elif b.kind == "medium":
            self._blit(img, MEDIUM.resize((b.size * S, b.size * S), Image.NEAREST),
                       x - b.size // 2, y - b.size // 2, a)
        elif b.kind == "snow":
            self._px(img, x, y, b.col, a)
        elif b.kind == "ember":
            f = b.age / b.life
            hot = b.size == 2 and f < 0.35
            col = (255, 250, 210) if hot else (255, 230, 120) if f < 0.3 else (255, 150, 40) if f < 0.7 else (200, 60, 20)
            flicker = 1.0 if hot else 0.6 + 0.4 * math.sin(b.age * 23 + b.phase)
            alpha = a * flicker * min(1.0, (b.life - b.age) / 0.5)
            for dx, dy in ([(0, 0), (1, 0), (0, 1), (1, 1)] if hot else [(0, 0)]):
                self._px(img, x + dx, y + dy, col, alpha)
        elif b.kind == "blob":
            s = max(1, round(b.size * (1 - b.age / b.life * 0.6)))
            for dx in range(s):
                for dy in range(s):
                    self._px(img, x + dx, y + dy, (255, 120, 30), 0.75)

    @staticmethod
    def _px(img, x, y, col, a):
        if 0 < x < PW - 1 and 0 < y < PH - 1 and a > 0.02:     # clipped to the plate interior
            img.alpha_composite(Image.new("RGBA", (S, S), col + (int(255 * min(1, a)),)), (x * S, y * S))

    @staticmethod
    def _blit(img, sprite, x, y, a):
        if a < 1.0:
            al = sprite.split()[3].point(lambda v: int(v * a))
            sprite = sprite.copy()
            sprite.putalpha(al)
        # clip to the plate interior (the in-game element uses a scissor rect)
        x0, y0 = max(x, 1), max(y, 1)
        x1 = min(x + sprite.width // S, PW - 1)
        y1 = min(y + sprite.height // S, PH - 1)
        if x1 <= x0 or y1 <= y0:
            return
        part = sprite.crop(((x0 - x) * S, (y0 - y) * S, (x1 - x) * S, (y1 - y) * S))
        img.alpha_composite(part, (x0 * S, y0 * S))


def run(theme, seed=1, muted=False, silhouette=False, name=None):
    plate = Plate(theme, seed, muted)
    dt = 1.0 / FPS
    for _ in range(int(WARMUP * FPS)):
        plate.step(dt)
    plate.on_open()
    plate.peak = 0
    fish_tex = os.path.join(TEX, "item", "fish", THEMES[theme]["fish"] + ".png")
    frames, counts = [], []
    for _ in range(SECONDS * FPS):
        plate.step(dt)
        counts.append(len(plate.bubbles))
        frames.append(plate.render(fish_tex, silhouette).convert("P", palette=Image.ADAPTIVE, colors=255))
    out = name or f"bubbles_{theme}.gif"
    frames[0].save(os.path.join(HERE, out), save_all=True, append_images=frames[1:],
                   duration=int(1000 / FPS), loop=0, disposal=2)
    avg = sum(counts) / len(counts)
    print(f"{out:34s} visible avg {avg:5.1f}  peak {plate.peak:3d}")
    return frames[len(frames) // 2], avg, plate.peak


def stills_sheet(stills):
    cols = 3
    rows = (len(stills) + cols - 1) // cols
    gap, head = 8, 12
    W = cols * (PW + gap) + gap
    H = rows * (PH + head + gap) + gap
    c = R.Canvas(W, H, S, backdrop=False)
    for i, (label, frame) in enumerate(stills):
        x = gap + (i % cols) * (PW + gap)
        y = gap + (i // cols) * (PH + head + gap)
        c.text(x, y, label, R.GOLD, box=PW, label=label)
        c.img.alpha_composite(frame.convert("RGBA"), (x * S, (y + head) * S))
    c.img.convert("RGB").save(os.path.join(HERE, "bubbles_themes_sheet.png"))
    print("wrote bubbles_themes_sheet.png")


def filmstrip(theme, name, seed=1, every=0.5, n=4):
    """n frames `every` seconds apart, stacked - for judging motion in a still image."""
    plate = Plate(theme, seed)
    dt = 1.0 / FPS
    for _ in range(int(WARMUP * FPS)):
        plate.step(dt)
    plate.on_open()
    for _ in range(int(1.6 * FPS)):                     # start the strip as the welcome plume rises
        plate.step(dt)
    fish_tex = os.path.join(TEX, "item", "fish", THEMES[theme]["fish"] + ".png")
    shots = []
    for i in range(n):
        shots.append(plate.render(fish_tex))
        for _ in range(int(every * FPS)):
            plate.step(dt)
    sheet = Image.new("RGB", (PW * S, (PH * S + 8) * n), (16, 16, 20))
    for i, sh in enumerate(shots):
        sheet.paste(sh.convert("RGB"), (0, i * (PH * S + 8)))
    sheet.save(os.path.join(HERE, name))
    print("wrote", name)


def dither_compare(name="plate_dither_styles.png"):
    """Smooth vs Bayer 4x4 vs 2x2 checker, same theme/seed/moment, plus a zoomed band edge."""
    rows = [("Smooth (previous)", "smooth"), ("Bayer 4x4 ordered dither", "bayer"), ("2x2 checker dither", "checker")]
    gap, head = 8, 12
    W = PW * 2 + gap * 3
    H = len(rows) * (PH + head + gap) + gap
    c = R.Canvas(W, H, S, backdrop=False)
    fish_tex = os.path.join(TEX, "item", "fish", THEMES["ocean"]["fish"] + ".png")
    for i, (label, pattern) in enumerate(rows):
        plate = Plate("ocean", 3, pattern=pattern)
        for _ in range(int(WARMUP * FPS)):
            plate.step(1.0 / FPS)
        y = gap + i * (PH + head + gap)
        c.text(gap, y, label, R.GOLD, box=PW, label=label)
        c.img.alpha_composite(plate.render(fish_tex), (gap * S, (y + head) * S))
        # zoom: the deep-ocean plate's left third at 2x, where band edges are easiest to read
        zoom = plate_background("ocean", pattern).crop((0, 0, PW // 3, PH // 3)).resize((PW * S, PH * S), Image.NEAREST)
        c.text(PW + 2 * gap, y, "Top-left corner, 3x zoom", R.GREY)
        c.img.alpha_composite(zoom, ((PW + 2 * gap) * S, (y + head) * S))
    c.img.convert("RGB").save(os.path.join(HERE, name))
    print("wrote", name)


def export_plates():
    """The candidate plate textures, one per theme at logical size (156x66): what the mod would ship."""
    out = os.path.join(HERE, "plates")
    os.makedirs(out, exist_ok=True)
    for theme in THEMES:
        plate_background(theme, "bayer").save(os.path.join(out, f"{theme}.png"))
    print("wrote plates/*.png")


def scripted_gif(theme, name, events, seconds=4.0, seed=5, pattern="bayer"):
    """events: list of (time, action, args). Draws a cursor for 0.3 s at each click."""
    plate = Plate(theme, seed, pattern=pattern)
    dt = 1.0 / FPS
    for _ in range(int(WARMUP * FPS)):
        plate.step(dt)
    plate.plume_timer = 99                              # keep ambient plumes out of the demo
    plate.peak = 0
    fish_tex = os.path.join(TEX, "item", "fish", THEMES[theme]["fish"] + ".png")
    frames, cursor_until, cursor_at = [], -1.0, None
    t = 0.0
    pending = sorted(events)
    while t < seconds:
        while pending and pending[0][0] <= t:
            _, action, args = pending.pop(0)
            if action == "click":
                plate.click(*args)
                cursor_at, cursor_until = args, t + 0.3
            elif action == "claim":
                plate.claim_burst()
        plate.step(dt)
        cur = cursor_at if t < cursor_until else None
        frames.append(plate.render(fish_tex, cursor=cur).convert("P", palette=Image.ADAPTIVE, colors=255))
        t += dt
    frames[0].save(os.path.join(HERE, name), save_all=True, append_images=frames[1:],
                   duration=int(1000 / FPS), loop=0, disposal=2)
    print(f"wrote {name}  peak alive {plate.peak}")
    return frames


def toggle_states(name="toggle_states.png"):
    """The corner toggle at rest, hovered (with its tooltip) and paused."""
    gap, head, tip = 8, 12, 24
    W = 3 * (PW + gap) + gap
    H = head + PH + tip + 2 * gap
    c = R.Canvas(W, H, S, backdrop=False)
    fish_tex = os.path.join(TEX, "item", "fish", THEMES["ocean"]["fish"] + ".png")
    for i, (label, state, paused, tooltip) in enumerate([
            ("Playing (at rest)", "idle", False, None),
            ("Hover", "hover", False, ("Pause animation", "Applies to every fish")),
            ("Paused (remembered)", "idle", True, None)]):
        plate = Plate("ocean", 3)
        for _ in range(int(WARMUP * FPS)):
            plate.step(1.0 / FPS)
        plate.paused = paused
        x = gap + i * (PW + gap)
        c.text(x, gap, label, R.GOLD)
        c.img.alpha_composite(plate.render(fish_tex, button=state), (x * S, (gap + head) * S))
        if tooltip:                                     # vanilla-style tooltip under the button
            line1, line2 = tooltip
            tw = max(R.FONT.width(line1), R.FONT.width(line2)) + 5
            tx = x + PW - tw
            ty = gap + head + 13
            c.rect(tx, ty, tw, 22, (16, 0, 16), 240)
            c.outline(tx, ty, tw, 22, (80, 0, 255), 200)
            c.text(tx + 3, ty + 2, line1, R.WHITE)
            c.text(tx + 3, ty + 12, line2, R.GREY)
    c.img.convert("RGB").save(os.path.join(HERE, name))
    print("wrote", name)


def main():
    export_plates()
    toggle_states()
    scripted_gif("ocean", "interact_click_plume.gif",
                 [(0.4, "click", (30, 44)), (1.4, "click", (78, 30)), (2.4, "click", (128, 50))])
    scripted_gif("nether", "interact_click_nether.gif", [(0.4, "click", (40, 46)), (1.6, "click", (80, 32))])
    scripted_gif("ocean", "interact_claim_burst.gif", [(0.6, "claim", ())], seconds=4.5)
    scripted_gif("nether", "interact_claim_burst_nether.gif", [(0.6, "claim", ())], seconds=4.0)
    dither_compare()
    stills = []
    for theme in THEMES:
        frame, _, _ = run(theme)
        stills.append((THEMES[theme]["label"], frame))
    frame, _, _ = run("ocean", seed=7, muted=True, silhouette=True, name="bubbles_uncaught_muted.gif")
    stills.append(("Uncaught: muted, silhouette", frame))
    stills_sheet(stills)
    filmstrip("ocean", "filmstrip_ocean.png")
    if R.OVERFLOWS:
        print("OVERFLOWS:", R.OVERFLOWS)
        sys.exit(1)


if __name__ == "__main__":
    main()
