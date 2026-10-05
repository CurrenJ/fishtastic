"""
The first reactive cosmetics (docs/fish-shelters.md §12.13): structures that do something when a fish
sets them off. Built with gen.py's Build; run through gen.py like every other floor design:

    python tools/shelter-structure-gen/gen.py giant_clam sunken_strongbox wayside_shrine sunken_gatehouse --preview

A reaction moves or lights named groups of parts. Grouped parts are drawn every frame instead of
being baked into the tank's mesh, so groups are kept to the parts that need to move or glow.
"""
import math
import random

from gen import Build, stair, value_noise
from sculpt import Ramp, bevel

SHELL_STAIRS = {n: n + "_stairs" for n in ("polished_diorite", "diorite", "polished_andesite", "andesite")}
SHELL_SLABS = {n: n + "_slab" for n in SHELL_STAIRS}

# Vanilla sounds, kept quiet: these are decorations, not events.
QUIET = 0.2


def sound(name, volume=QUIET, pitch=1.0):
    return {"id": "minecraft:" + name, "volume": volume, "pitch": pitch}


def fan(b, x, y, z, kind, group=None):
    b.set(x, y, z, kind + "_coral_fan", group=group, waterlogged=True)


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# Giant Clam — a Tridacna the size of a cart wheel, lying half sunk in the sand. Its two valves are
# heavy, chalky and low, folded into broad ribs that fan from the hinge at the back and stand out
# from the outline as scallops. Where they meet, the lips interlock in a deep zigzag, and in the
# notches the mantle peeks through in electric blue. Now and then a fish noses the lip and the clam
# yawns: the upper valve lifts on its hinge, a breath of bubbles escapes, and the mantle lights up
# in blue, turquoise and violet round a single pearl.
#    Reaction: nose the front lip -> the lid hinges up 48 degrees, mantle and pearl glow.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def giant_clam():
    scale = 0.07
    b = Build()
    rng = random.Random(41)
    noise = value_noise(41)
    A, B = 5.4, 3.6                                   # the shell's outline: half-length x, half-depth z
    HINGE = -(B + 0.9)                                # the ribs fan from here
    # One grey family, sunlit crest to shaded trough: the fog turns warmer stone mauve.
    SHELL = Ramp("polished_diorite", "diorite", "polished_andesite", "andesite")

    def fold(x, z):
        """+1 on a rib's crest, -1 in the trough between two."""
        return math.cos(math.atan2(x, z - HINGE) * 11)

    def inside(x, z, shrink=0.0):
        bulge = 0.4 * fold(x, z)                      # crests stand proud: a scalloped outline
        return (x / (A - shrink + bulge)) ** 2 + (z / (B - shrink + bulge)) ** 2 <= 1.0

    def shell(x, y, z):
        # Light follows the folds and the height: crests and the crown bleached, troughs and the
        # buried foot in shade, dithered so the ribs blend instead of striping.
        tone = 0.3 * (1 - fold(x, z)) / 2 + 0.7 * (1 - y / 5) + 0.1 * noise(x / 2, y / 2, z / 2)
        return SHELL.pick(tone, rng)[0]

    def lip(x, z):
        """Height the lid starts at on the rim: high on a crest, low in a trough. The zigzag."""
        t = fold(x, z)
        return 4 if t > 0.4 else (2 if t < -0.4 else 3)

    cells = [(x, z) for x in range(-5, 6) for z in range(-5, 6) if inside(x, z)]
    ring = {(x, z) for (x, z) in cells if not inside(x, z, 1.0)}

    # Lower valve: a broad bowl sunk in the sand, as wide at its foot as at its lip.
    for (x, z) in cells:
        if inside(x, z, 0.5):
            b.set(x, 0, z, shell(x, 0, z))
        b.set(x, 1, z, shell(x, 1, z))
    # The gape: the rim's teeth from both valves, and between them, all the way round, a zigzag of
    # mantle bulging out of the shell; inside, the mantle fills the bowl (dark until it opens).
    for (x, z) in ring:
        b.set(x, lip(x, z) - 1, z, "light_blue_concrete", group="mantle")
    for (x, z) in cells:
        for y in (2, 3):
            if (x, z) in ring:
                if y >= lip(x, z):
                    b.set(x, y, z, shell(x, y, z), group="lid")
                elif y < lip(x, z) - 1:
                    b.set(x, y, z, shell(x, y, z))
            elif y == 2:
                n = noise(x / 2.2, 0, z / 2.2)
                edge = not inside(x, z, 1.9)
                block = "cyan_concrete" if edge else ("purple_concrete" if n > 0.3 else "light_blue_concrete")
                b.set(x, y, z, block, group="mantle")
    b.set(0, 3, 0, "pearlescent_froglight", group="mantle", axis="y")
    # Upper valve: a low cap, ribbed like the lower, riding on the hinge at the back.
    for (x, z) in cells:
        if inside(x, z, 0.25):
            b.set(x, 4, z, shell(x, 4, z), group="lid")
        if inside(x, z, 1.6):
            b.set(x, 5, z, shell(x, 5, z), group="lid")

    def on_top(x, z):
        return max(y for y in range(0, 8) if (x, y, z) in b.v) + 1

    # Hitchhikers on the lid, which ride up with it.
    fan(b, -2, on_top(-2, -1), -1, "tube", group="lid")
    fan(b, 3, on_top(3, -1), -1, "horn", group="lid")
    b.set(1, on_top(1, -2), -2, "sea_pickle", group="lid", pickles=3, waterlogged=True)
    # A tuft of life where the shell meets the sand, behind and to the sides, never in front of the lip.
    for (x, z, block) in ((-5, -2, "fire_coral_fan"), (5, -2, "tube_coral"), (-4, -3, "horn_coral")):
        y = 1 if (x, 0, z) in b.v else 0
        b.setdefault(x, y, z, block, waterlogged=True)
    for (x, z) in ((-5, 3), (5, 3), (-2, -5), (3, -5), (0, -5)):
        if (x, 0, z) in b.v:
            b.setdefault(x, 1, z, "seagrass")
        else:
            b.setdefault(x, 0, z, "seagrass")

    # Round off the valves: exposed edges of the cap and the foot become stairs and slabs, so the
    # shell reads as a curved thing, not masonry. The gape stays crisp.
    solid = {p for p, (name, _) in b.v.items() if name in SHELL_STAIRS}
    keep = {p for p in solid if (p[0], p[2]) in ring and 1 <= p[1] <= 3}
    bevel(b, solid, SHELL_STAIRS, SHELL_SLABS, keep=keep, rng=rng)

    # The fish noses the front-most tooth of the lip; the hinge sits behind the back-most.
    front = max(z for (x, y, z) in b.v if x == 0 and y in (2, 3))
    back = min(z for (x, y, z) in b.v if y == 3)
    b.reactions.append({
        "nose": {"min": {"x": 0, "y": 2, "z": front}, "max": {"x": 0, "y": 3, "z": front}, "facing": "south"},
        "hold_seconds": 2.5,
        "open_ticks": 18, "hold_ticks": 60, "close_ticks": 22,
        "motions": [{"group": "lid", "pivot": [0, 3, back - 0.5], "axis": "x", "degrees": -48}],
        "glows": ["mantle"],
        "bursts": [{"at": [0, 3.5, 0.5], "count": 8}],
        "open_sound": sound("block.shulker_box.open", 0.18, 0.7),
        "close_sound": sound("block.shulker_box.close", 0.2, 0.75),
    })
    return b, "giant_clam", scale


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# Sunken Strongbox — a captain's strongbox, its dark oak gone black, bound in copper straps
# turned verdigris, its gold lock the one bright thing on the sand. Sand has drifted against one end;
# a trail of spilled coins leads away from it. A curious fish noses the lock, the lid creaks up, a
# gulp of trapped air escapes, and the gold heaped inside catches the light.
#    Reaction: nose the lock -> the lid hinges up 72 degrees, the treasure glows.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def sunken_strongbox():
    scale = 0.0625
    b = Build()
    rng = random.Random(9)
    X, Z = 4, 2                                       # half-length and half-depth of the box
    BAND = {-2, 2}                                    # the copper straps

    def wood(x):
        return "waxed_oxidized_cut_copper" if x in BAND else "dark_oak_planks"

    # Body: floor, walls and corner posts; hollow inside, the treasure heaped in the hollow.
    for x in range(-X, X + 1):
        for z in range(-Z, Z + 1):
            for y in range(0, 4):
                edge_x, edge_z = abs(x) == X, abs(z) == Z
                if edge_x and edge_z:
                    b.set(x, y, z, "stripped_dark_oak_log", axis="y")
                elif edge_x or edge_z or y == 0:
                    b.set(x, y, z, wood(x) if (edge_z or y == 0) else "dark_oak_planks")
    # The treasure: coins and bars, a few gems, a pearl. Its top layer glows when the lid lifts.
    heap = {}
    for x in range(-X + 1, X):
        for z in range(-Z + 1, Z):
            h = 3 + (1 if (abs(x) <= 2 and z <= 0 and rng.random() < 0.75) else 0)
            heap[(x, z)] = h
            for y in range(1, h):
                b.set(x, y, z, "gold_block")
    for (x, z), h in heap.items():
        r = rng.random()
        top = "raw_gold_block" if r < 0.35 else "gold_block"
        if (x, z) == (1, 0):
            top = "emerald_block"
        if (x, z) == (-2, -1):
            top = "pearlescent_froglight"
        b.set(x, h, z, top, group="gold", **({"axis": "y"} if top.endswith("froglight") else {}))
    b.set(-1, 4, 0, "amethyst_cluster", group="gold", facing="up", waterlogged=True)

    # Lid: a barrel top, a shell over the hollow so the heap fits under it, on a hinge at the back.
    for x in range(-X, X + 1):
        cap = abs(x) == X
        band = x in BAND
        stairs = "waxed_oxidized_cut_copper_stairs" if band else "dark_oak_stairs"
        full = "waxed_oxidized_cut_copper" if band else "dark_oak_planks"
        for z in range(-Z, Z + 1):
            if abs(z) == Z or cap:
                b.set(x, 4, z, "waxed_weathered_cut_copper" if (cap and abs(z) == Z) else full, group="lid")
        b.set(x, 5, -Z, stairs, group="lid", **stair("south"))
        b.set(x, 5, Z, stairs, group="lid", **stair("north"))
        for z in range(-Z + 1, Z):
            b.set(x, 5, z, full, group="lid")
        b.set(x, 6, -1, stairs, group="lid", **stair("south"))
        b.set(x, 6, 1, stairs, group="lid", **stair("north"))
        b.set(x, 6, 0, full, group="lid")
    # The lock: a gold plate on the body and its hasp on the lid, both standing proud of the front.
    b.set(0, 3, Z + 1, "gold_block")
    b.set(0, 4, Z + 1, "gold_block", group="lid")
    # Iron handles at the ends, a length of chain run off from one into the sand.
    b.set(X + 1, 2, 0, "iron_chain", axis="x")
    for x in range(X + 2, X + 3):
        b.set(x, 0, 0, "iron_chain", axis="x")
    b.set(-X - 1, 2, 0, "iron_chain", axis="x")
    # Sand drifted against the western end, and over the back corner.
    for z in range(-Z - 1, Z + 1):
        b.set(-X - 1, 0, z, "sand")
        if abs(z) < 2:
            b.set(-X - 1, 1, z, "sand")
    b.set(-X - 2, 0, 0, "sand")
    for x in range(-X, -1):
        b.set(x, 0, -Z - 1, "sand")
    # Spilled coins, trailing away from the front-right corner, never in front of the lock.
    for (x, z) in ((3, 3), (4, 4), (2, 4), (5, 3), (4, 5), (6, 4), (3, 5)):
        b.set(x, 0, z, "light_weighted_pressure_plate")
    b.set(5, 0, 5, "gold_block")
    # Life taking hold.
    fan(b, 3, 7, -1, "brain", group="lid")
    b.set(X, 0, -Z - 1, "tube_coral", waterlogged=True)
    for (x, z) in ((-6, -2), (-5, 3), (6, -1), (1, -4), (-2, 4)):
        b.setdefault(x, 0, z, "seagrass")

    b.reactions.append({
        "nose": {"min": {"x": 0, "y": 3, "z": Z + 1}, "max": {"x": 0, "y": 4, "z": Z + 1}, "facing": "south"},
        "hold_seconds": 2.0,
        "open_ticks": 16, "hold_ticks": 70, "close_ticks": 18,
        "motions": [{"group": "lid", "pivot": [0, 4, -Z - 0.5], "axis": "x", "degrees": -72}],
        "glows": ["gold"],
        "bursts": [{"at": [0, 4.5, 0], "count": 10}],
        "open_sound": sound("block.chest.open", 0.2, 0.65),
        "close_sound": sound("block.chest.close", 0.22, 0.7),
    })
    return b, "sunken_strongbox", scale


# Both gate pieces below are thin front to back (3 voxels) through the band roaming fish swim in,
# about 0.1 to 0.63 blocks above the sand: in a one-block-deep row of tanks a deeper piece leaves no
# lane in front of or behind it, and a fish crossing to its doorway from the side runs into it. Depth
# goes where it blocks nobody: a plinth on the sand, crowns and eaves up high. Their openings clear
# 0.4 above the sand: a lower lintel is closed down to the sand by the engine's gap closing.

# ─────────────────────────────────────────────────────────────────────────────────────────────────
# Wayside Shrine — a little Shinto hokora on a mossy stone podium that steps down to the sand, a
# culvert running through it front to back. The house sits on a dark timber sill: vermilion posts,
# white plaster sides, a dark altar room open to the front, and a copper roof gone sea-green that
# flares out wide over everything, crossed chigi at its gable ends. A gnarled cherry leans
# over it from one side, a stone lantern stands on the other. Fish swim through the culvert. Before
# the open front hangs a bell on a red rope; when a fish noses the rope the bell sways, the candles
# on the altar kindle, the lanterns warm, and the mirror on the altar wall catches the light.
#    Gate: the culvert, 0.4 tall. Reaction: nose the bell rope -> the bell sways, lights come up.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def wayside_shrine():
    scale = 0.05
    b = Build()
    rng = random.Random(17)
    noise = value_noise(17)
    H = 8                                             # the house floor; the culvert runs beneath it
    post = {"axis": "y"}

    def stone(x, y, z):
        # Moss where the podium meets the sand and on its steps, clean dressed stone higher up.
        wet = (3.5 - y) / 4 + 0.45 * noise(x / 2.5, y / 2.5, z / 2.5)
        if wet > 0.2:
            return "mossy_stone_bricks"
        return "cracked_stone_bricks" if wet < -0.55 else "stone_bricks"

    # The podium: piers either side of the culvert, stepping down outward to the sand. Three deep
    # through the swim band, so the lanes in front and behind stay open.
    for ax, top in ((3, 5), (4, 3), (5, 1)):
        for x in (-ax, ax):
            for z in (-1, 0, 1):
                for y in range(0, top + 1):
                    b.set(x, y, z, stone(x, y, z))
                # Each step capped with a stair rising toward the piers.
                if ax > 3:
                    stairs = "mossy_stone_brick_stairs" if rng.random() < 0.5 else "stone_brick_stairs"
                    b.set(x, top + 1, z, stairs, **stair("east" if x < 0 else "west"))
    # A footing course on the sand, wider than the podium; it blocks nobody.
    for x in list(range(-6, -2)) + list(range(3, 7)):
        for z in (-2, 2):
            b.set(x, 0, z, "mossy_stone_brick_slab" if rng.random() < 0.6 else "stone_brick_slab", type="bottom", waterlogged=True)
    for z in (-1, 0, 1):
        b.set(-6, 0, z, "mossy_stone_brick_slab", type="bottom", waterlogged=True)
        b.set(6, 0, z, "stone_brick_slab", type="bottom", waterlogged=True)
    # The culvert's lips: a dark keystone course where the passage meets the podium face.
    for z in (-1, 1):
        for x in (-3, 3):
            b.set(x, 5, z, "chiseled_stone_bricks")
    # The sill: dark timber cribbing on the piers, beams carrying the floor, their ends run out past
    # the podium.
    for x in (-3, 3):
        for y in (6, 7):
            for z in (-1, 0, 1):
                b.set(x, y, z, "stripped_dark_oak_log", axis="z")
    for x in range(-4, 5):
        for z in (-1, 1):
            b.set(x, H, z, "stripped_dark_oak_log", axis="x")
        b.set(x, H, 0, "dark_oak_planks")

    # The house: vermilion posts, white plaster sides between dark beams, a dark back wall.
    for y in range(H + 1, H + 5):
        for (x, z) in ((-3, -1), (3, -1), (-3, 1), (3, 1)):
            b.set(x, y, z, "red_concrete")
        for x in (-3, 3):
            if y in (H + 2, H + 3):
                b.set(x, y, 0, "white_concrete")
        for x in range(-2, 3):
            if y in (H + 2, H + 3):
                b.set(x, y, -1, "dark_oak_planks")
            else:
                b.set(x, y, -1, "stripped_dark_oak_log", axis="x")
    for x in (-3, 3):
        for y in (H + 1, H + 4):
            b.set(x, y, 0, "stripped_dark_oak_log", axis="z")
    for x in range(-2, 3):
        b.set(x, H + 4, 1, "red_concrete")                     # lintel over the doorway
    # A low vermilion rail across the front, broken in the middle where the rope hangs.
    for x in (-2, 2):
        b.set(x, H + 1, 1, "red_concrete")
    # The altar against the back wall: candles on it, the mirror set in the wall above.
    for x in range(-2, 3):
        b.set(x, H + 1, 0, "spruce_planks")
    for x in (-2, 2):
        b.set(x, H + 2, 0, "white_candle", group="lights", candles=3, lit=False, waterlogged=True)
    b.set(0, H + 2, 0, "white_candle", group="lights", candles=1, lit=False, waterlogged=True)
    b.set(0, H + 3, -1, "gold_block", group="lights")
    # The roof: a copper gable over everything, ridge along x, eaves flaring wide front and back
    # and turned up at the corners, a dark ridge, crossed chigi at the ends.
    R = H + 5
    for x in range(-6, 7):
        corner = abs(x) == 6
        for z in (-3, 3):
            if corner:
                b.set(x, R + 1, z, "waxed_oxidized_cut_copper_slab", type="bottom", waterlogged=True)
            else:
                b.set(x, R, z, "waxed_oxidized_cut_copper_slab", type="bottom", waterlogged=True)
        b.set(x, R, -2, "waxed_oxidized_cut_copper_stairs", **stair("south"))
        b.set(x, R, 2, "waxed_oxidized_cut_copper_stairs", **stair("north"))
        for z in (-1, 0, 1):
            b.set(x, R, z, "waxed_oxidized_cut_copper")
        b.set(x, R + 1, -1, "waxed_oxidized_cut_copper_stairs", **stair("south"))
        b.set(x, R + 1, 1, "waxed_oxidized_cut_copper_stairs", **stair("north"))
        b.set(x, R + 1, 0, "waxed_oxidized_cut_copper")
        b.set(x, R + 2, 0, "polished_blackstone_slab", type="bottom", waterlogged=True)
    for x in (-6, 6):
        b.set(x, R + 2, -1, "dark_oak_fence", north=False, south=True, east=False, west=False, waterlogged=True)
        b.set(x, R + 2, 1, "dark_oak_fence", north=True, south=False, east=False, west=False, waterlogged=True)
        b.set(x, R + 2, 0, "polished_blackstone_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=True)
    # Lanterns hung under the front eave: they warm when the shrine wakes.
    for x in (-5, 5):
        b.set(x, R - 1, 2, "lantern", group="lights", hanging=True, waterlogged=True)
    # The bell on a chain from the eave, its red rope hanging before the doorway.
    b.set(0, R - 1, 2, "iron_chain", group="bell", axis="y")
    b.set(0, R - 2, 2, "gold_block", group="bell")
    b.set(0, R - 3, 2, "red_wool", group="bell")
    b.set(0, R - 4, 2, "red_wool", group="bell")
    # A stone lantern to the west, on its own footing: post, firebox, a hat of slabs.
    b.set(-7, 0, 0, "stone_bricks")
    b.set(-7, 1, 0, "stone_brick_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=True)
    b.set(-7, 2, 0, "stone_brick_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=True)
    b.set(-7, 3, 0, "lantern", group="lights", hanging=False, waterlogged=True)
    b.set(-7, 4, 0, "stone_brick_slab", type="bottom", waterlogged=True)
    # A cherry to the east, its trunk leaning in toward the house, its crown spilling over the eave.
    for y in range(0, 7):
        b.set(7, y, 0, "cherry_log", axis="y")
    for (x, z) in ((8, 0), (7, -1), (7, 1)):
        b.set(x, 0, z, "cherry_log", axis="y")                 # root flare
    for y in range(6, 11):
        b.set(6, y, 0, "cherry_log", axis="y")
    b.set(7, 10, 0, "cherry_log", axis="x")
    b.set(8, 11, 0, "cherry_log", axis="x")
    b.set(5, 11, 0, "cherry_log", axis="x")
    for x in range(3, 9):
        for y in range(12, 16):
            for z in range(-3, 3):
                d = ((x - 6.0) / 2.9) ** 2 + ((y - 13.5) / 2.0) ** 2 + ((z + 0.3) / 2.6) ** 2
                if d <= 1.0 and rng.random() < 0.88 and (x, y, z) not in b.v:
                    b.set(x, y, z, "cherry_leaves", persistent=True, waterlogged=True)
    for (x, y, z) in ((8, 10, -1), (8, 10, 1), (8, 9, 1), (7, 10, 1)):
        b.setdefault(x, y, z, "cherry_leaves", persistent=True, waterlogged=True)
    # Petals drifted on the sand and the steps, moss and seagrass at the feet.
    for (x, y, z) in ((5, 0, 3), (-5, 0, -3), (6, 0, 3), (-4, 0, 4), (4, 0, -4), (2, 0, 4), (5, 3, 0), (4, 5, -1)):
        b.setdefault(x, y, z, "pink_petals", flower_amount=rng.randint(2, 4), facing="east")
    for (x, z) in ((-7, 2), (-7, -3), (-5, 3), (5, -3), (-8, 1)):
        b.setdefault(x, 0, z, "seagrass")

    # The culvert, front to back, closed above by the sill and at the sides by the piers.
    b.shelter(-2, 0, -1, 2, H - 1, 1, kind="gate")
    b.reactions.append({
        "nose": {"min": {"x": 0, "y": R - 4, "z": 2}, "max": {"x": 0, "y": R - 3, "z": 2}, "facing": "south"},
        "hold_seconds": 1.5,
        "open_ticks": 10, "hold_ticks": 100, "close_ticks": 40,
        "motions": [{"group": "bell", "pivot": [0, R, 2], "axis": "x", "degrees": 18, "sway_ticks": 24}],
        "glows": ["lights"],
        "open_sound": sound("block.amethyst_block.chime", 0.3, 1.2),
        "close_sound": sound("block.candle.extinguish", 0.15, 1.0),
    })
    return b, "wayside_shrine", scale


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# Sunken Gatehouse — the gate of a drowned castle. A square tower stands to the west, its crown
# corbelled out over the water and crenellated; to the east its twin has fallen to a stump, its
# stones spilled across the sand and claimed by coral. Between them, set back, the gate itself: a
# square passage framed in dark deepslate under a relieving arch with a chiseled keystone, and on
# top a timber hoist house roofed in green copper, where the portcullis winds up out of sight. Soul
# lanterns glimmer in the arrow slits, kelp climbs the walls, moss creeps up from the sand and down
# from the parapets. The portcullis is down. Now and then it grinds up into the hoist house for a
# single fish, which swims through beneath it, and drops again behind it.
#    Gate: the passage, 0.4 tall, locked. Reaction: the portcullis rises for the fish it lets
#    through, up into the gate block and its hoist house, where it is out of sight.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def sunken_gatehouse():
    scale = 0.05
    b = Build()
    rng = random.Random(23)
    noise = value_noise(23)
    P = 8                                             # passage height, voxels
    GATE_TOP = 12                                     # top of the gate block's walls
    TOWER_TOP = 12                                    # top of the west tower's shaft

    def stone(x, y, z, top):
        # Moss climbs from the sand and creeps down from the parapet; the middle stays clean, and
        # the cracks gather where the noise says the wall is tired, not by dice.
        n = noise(x / 3.0, y / 3.0, z / 2.0)
        wet = max((3.5 - y) / 4, (y - (top - 2.5)) / 3) + 0.5 * n
        if wet > 0.3:
            return "mossy_stone_bricks"
        if n < -0.45:
            return "cracked_stone_bricks"
        return "stone_bricks"

    def merlon(x, y, z):
        b.set(x, y, z, "mossy_stone_bricks" if rng.random() < 0.4 else "stone_bricks")

    # The gate block, set back between the towers: three deep, the passage through its middle.
    for x in range(-4, 5):
        for z in (-1, 0, 1):
            for y in range(0, GATE_TOP + 1):
                b.set(x, y, z, stone(x, y, z, GATE_TOP))
    # The west tower: its shaft three deep like the gate (a deeper one shuts the lanes behind and in
    # front of it), its crown spreading wider above the swim band.
    for x in range(-8, -4):
        for z in (-1, 0, 1):
            for y in range(0, TOWER_TOP + 1):
                b.set(x, y, z, stone(x, y, z, TOWER_TOP))
    # Its crown, corbelled out above the swim band: upside-down stairs, a parapet, merlons.
    C = TOWER_TOP + 1
    for x in range(-8, -4):
        for z in (-1, 0, 1):
            b.set(x, C, z, "stone_bricks")
        b.set(x, C, -2, "stone_brick_stairs", **stair("south", "top"))
        b.set(x, C, 2, "stone_brick_stairs", **stair("north", "top"))
    for z in (-1, 0, 1):
        b.set(-4, C, z, "stone_brick_stairs", **stair("west", "top"))
    b.set(-4, C, -2, "stone_brick_stairs", **stair("south", "top"))
    b.set(-4, C, 2, "stone_brick_stairs", **stair("north", "top"))
    ring = [(x, z) for x in range(-8, -3) for z in range(-2, 3) if x in (-8, -4) or abs(z) == 2]
    for (x, z) in ring:
        b.set(x, C + 1, z, "stone_bricks")
        if (x + z) % 2 == 0:
            merlon(x, C + 2, z)
    for x in range(-7, -4):
        for z in (-1, 0, 1):
            b.set(x, C + 1, z, "stone_brick_slab", type="bottom", waterlogged=True)   # the roof walk
    # The east tower, fallen: its shaft broken off in a ragged slope down toward the glass.
    for x in range(5, 9):
        for z in (-1, 0, 1):
            top = {5: 10, 6: 8, 7: 6, 8: 3}[x] + round(1.4 * noise(x / 1.5, 7, z / 1.5))
            for y in range(0, top + 1):
                b.set(x, y, z, stone(x, y, z, top + 6))
            # The broken top weathered round: a stair or slab where it meets the water.
            if rng.random() < 0.55:
                b.set(x, top + 1, z, "mossy_stone_brick_slab" if rng.random() < 0.6 else "stone_brick_slab",
                      type="bottom", waterlogged=True)
    # Tower quoins: chiseled corner stones every other course, so the shafts read as built.
    for (x, top) in ((-8, TOWER_TOP), (-5, TOWER_TOP)):
        for z in (-1, 1):
            for y in range(1, top + 1, 2):
                b.set(x, y, z, "chiseled_stone_bricks")
    for z in (-1, 1):
        for y in range(1, 9, 2):
            if (5, y, z) in b.v:
                b.set(5, y, z, "chiseled_stone_bricks")
    # A battered plinth round both towers, on the sand where nobody swims.
    for x in list(range(-8, -4)) + list(range(5, 9)):
        for z in (-2, 2):
            b.set(x, 0, z, "mossy_stone_brick_stairs" if rng.random() < 0.6 else "stone_brick_stairs",
                  **stair("south" if z < 0 else "north"))

    # The gate: a deepslate frame two wide round the passage, a lintel, and over it a relieving
    # arch of deepslate tiles round a chiseled keystone, its tympanum filled with dark prismarine.
    for z in (-1, 1):
        for y in range(0, P + 1):
            for x in (-4, 4):
                b.set(x, y, z, "polished_deepslate")
        for x in range(-4, 5):
            b.set(x, P, z, "polished_deepslate")
        for x in range(-4, 5):
            for y in range(P + 1, GATE_TOP):
                d = math.hypot(x, y - P + 0.5)
                if 2.6 <= d <= 3.9:
                    b.set(x, y, z, "deepslate_tiles")
                elif d < 2.6:
                    b.set(x, y, z, "dark_prismarine")
        b.set(0, P + 3, z, "chiseled_deepslate")
        # A dark course along the top of the gate block, under the walk.
        for x in range(-4, 5):
            b.set(x, GATE_TOP, z, "deepslate_tiles" if (x + GATE_TOP) % 2 else "polished_deepslate")
    # On top of the gate block: a walk with merlons at its corners, and the hoist house.
    for x in (-4, 4):
        for z in (-1, 1):
            merlon(x, GATE_TOP + 1, z)
    for x in range(-3, 4):
        for z in (-1, 0, 1):
            for y in (GATE_TOP + 1, GATE_TOP + 2):
                if abs(x) == 3 and abs(z) == 1:
                    b.set(x, y, z, "stripped_dark_oak_log", axis="y")
                elif abs(x) == 3 or abs(z) == 1:
                    b.set(x, y, z, "spruce_planks" if y == GATE_TOP + 1 else "dark_oak_planks")
        R = GATE_TOP + 3
        b.set(x, R, -1, "waxed_oxidized_cut_copper_stairs", **stair("south"))
        b.set(x, R, 1, "waxed_oxidized_cut_copper_stairs", **stair("north"))
        b.set(x, R, 0, "waxed_oxidized_cut_copper")
    R = GATE_TOP + 3
    for z in (-1, 0, 1):
        b.set(-4, R, z, "waxed_oxidized_cut_copper_slab", type="bottom", waterlogged=True)
        b.set(4, R, z, "waxed_oxidized_cut_copper_slab", type="bottom", waterlogged=True)
    # A dark window in the hoist house, front and back.
    for z in (-1, 1):
        b.set(-1, GATE_TOP + 2, z, "polished_blackstone")
        b.set(1, GATE_TOP + 2, z, "polished_blackstone")

    # Arrow slits: recessed a voxel into the tower faces, a soul lantern glimmering in the lower one.
    for (x, ys, depth) in ((-7, (5, 6), 1), (-6, (9, 10), 1), (6, (4, 5), 1)):
        for z in (-depth, depth):
            for y in ys:
                if (x, y, z) in b.v:
                    b.clear(x, y, z)
                    inner = (x, y, z - 1 if z > 0 else z + 1)
                    b.set(*inner, "polished_blackstone")
            if (x, ys[0], z) not in b.v and (x, ys[0] - 1, z) in b.v:
                b.set(x, ys[0], z, "soul_lantern", hanging=False, waterlogged=True)

    # Life: kelp climbing the tower, coral colonising the ruin, sea pickles in its broken top,
    # rubble and seagrass on the sand.
    for (x, z, h) in ((-8, 2, 7), (-6, -2, 9), (8, 2, 4)):
        for y in range(0, h):
            b.setdefault(x, y, z, "kelp_plant")
        b.setdefault(x, h, z, "kelp", age=20)

    def top_of(x, z):
        ys = [y for y in range(0, 16) if (x, y, z) in b.v]
        return max(ys) + 1 if ys else 0

    for (x, z, kind) in ((7, -1, "fire"), (6, 1, "horn"), (8, -2, "brain"), (5, -2, "tube")):
        fan(b, x, top_of(x, z), z, kind)
    for (x, z) in ((6, 0), (7, 2)):
        b.set(x, top_of(x, z), z, "sea_pickle", pickles=rng.randint(2, 4), waterlogged=True)
    # The rubble lies close against the ruin, so the gatehouse claims no more of the floor than it stands on.
    for (x, y, z, block) in ((6, 0, 3, "mossy_cobblestone"), (8, 0, 3, "brain_coral_block"),
                             (5, 0, -3, "mossy_cobblestone"), (7, 0, -3, "stone_bricks"), (8, 1, 3, "fire_coral")):
        b.setdefault(x, y, z, block, **({"waterlogged": True} if block == "fire_coral" else {}))
    b.set(7, 0, 3, "stone_brick_stairs", **stair("east"))
    b.set(4, 0, -3, "mossy_stone_brick_slab", type="bottom", waterlogged=True)
    for (x, z) in ((-8, 4), (-6, 4), (-3, -4), (3, 4), (-7, -4), (2, -5), (8, -4)):
        b.setdefault(x, 0, z, "seagrass")

    # The passage, front to back, and its portcullis halfway along, standing in it at rest.
    b.shelter(-3, 0, -1, 3, P - 1, 1, kind="gate")
    for x in range(-3, 4):
        for y in range(0, P):
            b.set(x, y, 0, "iron_bars", group="portcullis", east=True, west=True, north=False, south=False, waterlogged=True)
    b.doors.add("portcullis")
    b.reactions.append({
        "gate": 0,
        "open_ticks": 30, "hold_ticks": 16, "close_ticks": 10,
        "motions": [{"group": "portcullis", "offset": [0, P, 0]}],
        "bursts": [{"at": [0, 0.3, 0], "count": 6}],
        "open_sound": sound("block.iron_door.open", 0.22, 0.5),
        "close_sound": sound("block.iron_door.close", 0.25, 0.5),
    })
    return b, "sunken_gatehouse", scale


FLOOR = (giant_clam, sunken_strongbox, wayside_shrine, sunken_gatehouse)
