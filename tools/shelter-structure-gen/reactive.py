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

# Vanilla sounds, kept quiet: these are decorations, not events.
QUIET = 0.2


def sound(name, volume=QUIET, pitch=1.0):
    return {"id": "minecraft:" + name, "volume": volume, "pitch": pitch}


def fan(b, x, y, z, kind, group=None):
    b.set(x, y, z, kind + "_coral_fan", group=group, waterlogged=True)


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# Giant Clam — a Tridacna the size of a cart wheel, lying closed in the sand, its two valves folded
# into deep scalloped ribs whose lips interlock in a zigzag. Now and then a fish noses the lip and
# the clam yawns: the upper valve lifts on its hinge, a breath of bubbles escapes, and the mantle
# inside lights up in electric blue, turquoise and violet round a single pearl.
#    Reaction: nose the front lip -> the lid hinges up 48 degrees, mantle and pearl glow.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def giant_clam():
    scale = 0.07
    b = Build()
    rng = random.Random(41)
    noise = value_noise(41)
    A, B = 5.6, 3.7                                   # the shell's outline: half-length x, half-depth z

    def inside(x, z, shrink=0.0):
        return (x / (A - shrink)) ** 2 + (z / (B - shrink)) ** 2 <= 1.0

    def shell(x, z):
        # Radial folds fanning from the hinge at the back: ridges calcite, furrows a darker grey,
        # so the scallops read from across the room even through the water's haze.
        ang = math.atan2(x, z + B + 0.8)
        return "calcite" if math.cos(ang * 14) > -0.1 else "dripstone_block"

    cells = [(x, z) for x in range(-5, 6) for z in range(-3, 4) if inside(x, z)]
    ring = {(x, z) for (x, z) in cells if not inside(x, z, 1.0)}

    # Lower valve: a bowl, narrower at its foot where it sinks into the sand.
    for (x, z) in cells:
        if inside(x, z, 1.4):
            b.set(x, 0, z, shell(x, z))
        if inside(x, z, 0.5):
            b.set(x, 1, z, shell(x, z))
    # The gape: the rim of the lower valve, and the mantle filling it (dark until the clam opens).
    for (x, z) in cells:
        if (x, z) in ring:
            b.set(x, 2, z, shell(x, z))
        else:
            n = noise(x / 2.2, 0, z / 2.2)
            edge = not inside(x, z, 1.9)
            block = "cyan_concrete" if edge else ("purple_concrete" if n > 0.3 else "light_blue_concrete")
            b.set(x, 2, z, block, group="mantle")
    b.set(0, 3, 0, "pearlescent_froglight", group="mantle", axis="y")
    # The zigzag lip: the rim's teeth alternate between the two valves.
    for (x, z) in ring:
        if (x + z) % 2 == 0:
            b.set(x, 3, z, shell(x, z))
        else:
            b.set(x, 3, z, shell(x, z), group="lid")
    # Upper valve: a dome, ribbed like the lower, riding on the hinge at the back.
    for (x, z) in cells:
        if inside(x, z, 0.3):
            b.set(x, 4, z, shell(x, z), group="lid")
        if inside(x, z, 1.3):
            b.set(x, 5, z, shell(x, z), group="lid")
        if inside(x, z, 2.6):
            b.set(x, 6, z, shell(x, z), group="lid")
    # Hitchhikers on the lid, which ride up with it.
    fan(b, -2, 7, 0, "tube", group="lid")
    fan(b, 2, 6, -2, "horn", group="lid")
    b.set(1, 7, 0, "sea_pickle", group="lid", pickles=2, waterlogged=True)
    # A tuft of life where the shell meets the sand, behind and to the sides, never in front of the lip.
    fan(b, -5, 1, -1, "fire")
    b.set(5, 0, -2, "tube_coral", waterlogged=True)
    for (x, z) in ((-5, 2), (5, 2), (-3, -4), (4, -4), (0, -4)):
        b.setdefault(x, 0, z, "seagrass")

    b.reactions.append({
        "nose": {"min": {"x": -1, "y": 2, "z": 3}, "max": {"x": 1, "y": 3, "z": 3}, "facing": "south"},
        "hold_seconds": 2.5,
        "open_ticks": 18, "hold_ticks": 60, "close_ticks": 22,
        "motions": [{"group": "lid", "pivot": [0, 3, -3.5], "axis": "x", "degrees": -48}],
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
# Wayside Shrine — a little Shinto hokora raised on vermilion legs, its sides screened with a timber
# lattice over two courses of mossy stone, its roof sheathed in copper gone sea-green and flaring
# out wide at the eaves, a cherry tree beside it and a stone lantern on the other side. Fish swim
# through beneath it, front to back. Before its open front hangs a bell on a red rope; when a fish
# noses the rope the bell sways, the candles on the altar kindle, the lanterns warm, and the mirror
# on the altar wall catches the light.
#    Gate: under the shrine, 0.4 tall. Reaction: nose the bell rope -> the bell sways, lights come up.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def wayside_shrine():
    scale = 0.05
    b = Build()
    rng = random.Random(17)
    H = 8                                             # the floor; fish pass beneath it
    # Legs at the corners, stone footings, and a lattice screen along both sides.
    for (x, z) in ((-3, -1), (3, -1), (-3, 1), (3, 1)):
        for y in range(0, H):
            b.set(x, y, z, "red_concrete")
    for x in (-3, 3):
        for y in (0, 1):
            b.set(x, y, 0, "mossy_stone_bricks")
        for y in range(2, H):
            b.set(x, y, 0, "dark_oak_fence", north=True, south=True, east=False, west=False, waterlogged=True)
    # Stone footings flare out under the legs, low on the sand where nobody swims.
    for x in (-4, -3, 3, 4):
        for z in (-2, 2):
            b.set(x, 0, z, "mossy_stone_brick_slab" if rng.random() < 0.6 else "stone_brick_slab", type="bottom", waterlogged=True)
    # The floor, its edge a vermilion beam.
    for x in range(-3, 4):
        for z in (-1, 0, 1):
            b.set(x, H, z, "red_concrete" if (abs(x) == 3 or z != 0) else "polished_blackstone")
    # The house: vermilion posts, dark timber at the back and sides, open to the front.
    for y in range(H + 1, H + 5):
        for (x, z) in ((-3, -1), (3, -1), (-3, 1), (3, 1)):
            b.set(x, y, z, "red_concrete")
        for x in range(-2, 3):
            b.set(x, y, -1, "dark_oak_planks")
        b.set(-3, y, 0, "dark_oak_planks")
        b.set(3, y, 0, "dark_oak_planks")
    for x in range(-2, 3):
        b.set(x, H + 4, 1, "red_concrete")                     # lintel over the doorway
    # The altar against the back wall: candles on it, the mirror set in the wall above.
    for x in range(-2, 3):
        b.set(x, H + 1, 0, "stripped_dark_oak_log", axis="x")
    for x in (-2, 2):
        b.set(x, H + 2, 0, "white_candle", group="lights", candles=3, lit=False, waterlogged=True)
    b.set(0, H + 2, 0, "white_candle", group="lights", candles=1, lit=False, waterlogged=True)
    b.set(0, H + 3, -1, "gold_block", group="lights")
    # The roof: a copper gable over everything, ridge along x, eaves flaring wide front and back
    # and turned up at the corners, a dark ridge with finials.
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
        b.set(x, R + 2, 0, "polished_blackstone_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=True)
    # Lanterns hung under the front eave: they warm when the shrine wakes.
    for x in (-5, 5):
        b.set(x, R - 1, 2, "lantern", group="lights", hanging=True, waterlogged=True)
    # The bell on a chain from the eave, its red rope hanging before the doorway.
    b.set(0, R - 1, 2, "iron_chain", group="bell", axis="y")
    b.set(0, R - 2, 2, "gold_block", group="bell")
    b.set(0, R - 3, 2, "red_wool", group="bell")
    b.set(0, R - 4, 2, "red_wool", group="bell")
    # A stone lantern to the west, lit with the rest.
    b.set(-6, 0, 0, "stone_bricks")
    b.set(-6, 1, 0, "stone_brick_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=True)
    b.set(-6, 2, 0, "stone_brick_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=True)
    b.set(-6, 3, 0, "lantern", group="lights", hanging=False, waterlogged=True)
    b.set(-6, 4, 0, "stone_brick_slab", type="bottom", waterlogged=True)
    # A cherry tree to the east, its crown spilling over the eave.
    for y in range(0, 12):
        b.set(7, y, 0, "cherry_log", axis="y")
    b.set(6, 11, 0, "cherry_log", axis="x")
    b.set(8, 10, 0, "cherry_log", axis="x")
    for x in range(3, 9):
        for y in range(12, 16):
            for z in range(-3, 3):
                d = ((x - 6.3) / 2.7) ** 2 + ((y - 13.5) / 2.0) ** 2 + ((z + 0.3) / 2.6) ** 2
                if d <= 1.0 and rng.random() < 0.9 and (x, y, z) not in b.v:
                    b.set(x, y, z, "cherry_leaves", persistent=True, waterlogged=True)
    for (x, y, z) in ((8, 11, -1), (8, 11, 1), (8, 10, 1), (7, 11, 1)):
        b.setdefault(x, y, z, "cherry_leaves", persistent=True, waterlogged=True)
    # Petals drifted on the sand, moss and seagrass at the feet.
    for (x, z) in ((5, 3), (-5, -2), (6, 2), (-4, 4), (4, -4), (2, 4)):
        b.set(x, 0, z, "pink_petals", flower_amount=rng.randint(2, 4), facing="east")
    for (x, z) in ((-7, 2), (-7, -3), (-5, 3), (5, -3)):
        b.setdefault(x, 0, z, "seagrass")

    # Under the shrine, front to back, closed above by its floor and at the sides by the lattice.
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
# Sunken Gatehouse — the gate of a drowned castle: an arched passage between two square towers of
# mossy stone, a dark deepslate surround to the arch, machicolations and crenellations up top,
# soul lanterns in the arrow slits, kelp trailing from the parapet, and the eastern tower's crown
# long since fallen in. The portcullis is down. Now and then it grinds up into the wall for a
# single fish, which swims through beneath it, and drops again behind it.
#    Gate: the passage, 0.4 tall, locked. Reaction: the portcullis rises for the fish it lets
#    through, up into the solid wall over the arch, where it is out of sight.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def sunken_gatehouse():
    scale = 0.05
    b = Build()
    rng = random.Random(23)
    P = 8                                             # passage height, voxels
    TOP = 12                                          # top of the walls in the swim band

    def stone(x, y, z):
        moss = y <= 2 or (y <= 6 and rng.random() < 0.4) or (z < 0 and rng.random() < 0.3)
        if moss:
            return "mossy_stone_bricks"
        return "cracked_stone_bricks" if rng.random() < 0.15 else "stone_bricks"

    # Walls and towers, three voxels deep, solid to the top of the band.
    broken = {}
    for x in range(-8, 9):
        top = TOP
        if x >= 5:
            top = TOP - rng.choice((0, 1, 2, 3))               # the eastern tower's ruined top
            broken[x] = top
        for z in (-1, 0, 1):
            for y in range(0, top + 1):
                b.set(x, y, z, stone(x, y, z))
    # Towers stand proud of the curtain: pilaster quoins at their corners, a battered plinth.
    for x in (-8, -5, 5, 8):
        for z in (-1, 1):
            for y in range(1, (broken.get(x, TOP)) + 1, 2):
                b.set(x, y, z, "chiseled_stone_bricks")
    for x in list(range(-8, -4)) + list(range(5, 9)):
        for z in (-2, 2):
            b.set(x, 0, z, "mossy_stone_brick_stairs" if rng.random() < 0.6 else "stone_brick_stairs",
                  **stair("south" if z < 0 else "north"))
    # The crown of the western tower and the gate: machicolations corbelled out front and back
    # (high up, over the band), a walkway, crenellations.
    for x in range(-8, 5):
        for z in (-2, 2):
            b.set(x, TOP + 1, z, "stone_brick_stairs", **stair("north" if z < 0 else "south", "top"))
        for z in (-1, 0, 1):
            b.set(x, TOP + 1, z, "stone_bricks")
        for z in range(-2, 3):
            b.set(x, TOP + 2, z, "stone_bricks" if rng.random() < 0.7 else "mossy_stone_bricks")
        for z in (-2, 2):
            if x % 2 == 0:
                b.set(x, TOP + 3, z, "stone_bricks")
        b.set(x, TOP + 3, 0, "stone_brick_slab", type="bottom", waterlogged=True)
    for x in (-8, -5):
        b.set(x, TOP + 3, -1, "stone_brick_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=True)
        b.set(x, TOP + 3, 1, "stone_brick_wall", up=True, north="none", south="none", east="none", west="none", waterlogged=True)
    # Over the passage the crown is solid: the portcullis rises up into it.
    for x in range(-3, 4):
        for y in range(TOP + 1, TOP + 4):
            b.set(x, y, 0, "stone_bricks")
    # The arch surround: dark deepslate jambs and voussoirs framing the passage, a keystone.
    for y in range(0, P + 1):
        for z in (-1, 1):
            b.set(-4, y, z, "polished_deepslate")
            b.set(4, y, z, "polished_deepslate")
    for x in range(-4, 5):
        for z in (-1, 1):
            b.set(x, P, z, "deepslate_tiles")
    for z in (-1, 1):
        b.set(0, P + 1, z, "chiseled_deepslate")
        b.set(-3, P, z, "deepslate_tile_stairs", **stair("east", "top"))
        b.set(3, P, z, "deepslate_tile_stairs", **stair("west", "top"))
    # Arrow slits in the towers, a soul lantern glimmering in each.
    for cx in (-7, 6):
        b.set(cx, 9, 1, "soul_lantern", hanging=False, waterlogged=True)
        b.set(cx, 10, 1, "polished_deepslate")
    # Kelp trailing from the parapet; seagrass and fallen stones on the sand.
    for (x, y, z) in ((-6, 11, 2), (-2, 12, 2), (1, 11, -2), (7, 8, -2)):
        b.setdefault(x, y, z, "kelp_plant")
    for (x, z) in ((-8, 3), (8, 3), (-6, 4), (6, -3), (-3, -4), (3, 4), (7, 4)):
        b.setdefault(x, 0, z, "seagrass")
    b.set(6, 0, 3, "mossy_cobblestone")
    b.set(7, 0, -3, "cobblestone")
    b.set(-7, 0, -3, "mossy_cobblestone")

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
