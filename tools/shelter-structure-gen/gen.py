"""
Procedural generator for fishtastic's shelter cosmetics (docs/fish-shelters.md §12.9): structures
whose hollows, gates and open spaces are sized for medium and large fish as well as small ones.

Shelter interiors have to be exact to the cell: a hollow's interior is the box the engine confines a
fish to, a gate needs exactly one mouth front and one back, and several shelters in one structure
may not reach into each other's hulls. So these are generated, not hand-built and captured.

Two kinds of output, both a CosmeticStructure JSON in the datapack:

- **floor** structures stand in one tank's 3x3 grid. Built centred on the anchor voxel (0, 0, 0),
  x east, y up from the sand, z south (the front). One voxel is one block of `scale` world blocks;
  horizontal offsets are written in grid cells (x * scale / CELL_WIDTH), as the capture command does.
- **span** structures fill a box of tanks, as tools/span-structure-gen does: voxels counted from the
  box's interior corner on the sand.

    python tools/shelter-structure-gen/gen.py            # writes every JSON into the datapack
    python tools/shelter-structure-gen/gen.py --preview  # also renders matplotlib views to ./preview/
    python tools/shelter-structure-gen/gen.py amphora    # only the named designs

Fit at a glance (the engine's gates, FlockEngine §5.2): a fish of rendered length L uses a mouth if
0.4 L <= the mouth's narrower side, and (except through a gate) an interior if L <= 1.1 x its
longest straight horizontal run. Rendered lengths run 0.08 (goby) to 0.76 (sawfish) blocks.
"""
import json
import math
import os
import random
import sys

import footprint_audit

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_DIR = os.path.join(ROOT, "common/src/main/resources/data/fishtastic/fishtastic/cosmetic_structure")

CELL_WIDTH = (1 - 2 / 16) / 3     # CosmeticGridCell.CELL_WIDTH
FLOOR_HALF = (1 - 2 / 16) / 2     # sand from the tank centre to the glass, blocks
FLOOR_HEIGHT = 1 - 3 / 16         # sand to lid, blocks

FULL_CUBES = {
    "terracotta", "black_terracotta", "waxed_oxidized_copper", "waxed_weathered_copper",
    "waxed_oxidized_cut_copper", "basalt", "polished_basalt", "smooth_basalt", "magma_block",
    "calcite", "polished_deepslate", "mangrove_log", "muddy_mangrove_roots", "sandstone",
    "smooth_sandstone", "cut_sandstone", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
    "chiseled_stone_bricks", "bone_block", "dark_oak_planks", "stripped_dark_oak_log", "spruce_log",
    "gold_block", "ochre_froglight", "brain_coral_block", "tube_coral_block", "bubble_coral_block",
    "fire_coral_block", "horn_coral_block", "cherry_log", "smooth_stone", "packed_mud",
    "bricks", "polished_deepslate", "deepslate_tiles",
    "dead_brain_coral_block", "dead_tube_coral_block", "dead_horn_coral_block", "dead_fire_coral_block",
}


class Build:
    def __init__(self, bounds=None):
        self.v = {}
        self.bounds = bounds          # (nx, ny, nz) for a span; None for a floor build
        self.shelters = []

    def inside(self, x, y, z):
        if y < 0:
            return False
        if self.bounds is None:
            return True
        nx, ny, nz = self.bounds
        return 0 <= x < nx and y < ny and 0 <= z < nz

    def set(self, x, y, z, name, **props):
        x, y, z = int(x), int(y), int(z)
        if self.inside(x, y, z):
            self.v[(x, y, z)] = (name, {k: str(v).lower() for k, v in props.items()})

    def setdefault(self, x, y, z, name, **props):
        if (int(x), int(y), int(z)) not in self.v:
            self.set(x, y, z, name, **props)

    def clear(self, x, y, z):
        self.v.pop((int(x), int(y), int(z)), None)

    def get(self, x, y, z):
        return self.v.get((x, y, z))

    def box(self, x0, y0, z0, x1, y1, z1, name, **props):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, name, **props)

    def clear_box(self, x0, y0, z0, x1, y1, z1):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.clear(x, y, z)

    def shelter(self, x0, y0, z0, x1, y1, z1, kind="hollow", capacity=None, min_length=None):
        """A box-shaped shelter interior. Interiors are boxes because the engine confines a fish
        to its interior's bounding box. Whatever was built there is cleared: the hollow wins."""
        cells = [(x, y, z) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1) for z in range(z0, z1 + 1)]
        for c in cells:
            self.clear(*c)
        self.shelters.append({"cells": cells, "kind": kind, "capacity": capacity, "min_length": min_length})

    def shift(self, dx):
        """Moves everything built, shelters too, dx voxels along x: to centre a design in a longer box."""
        self.v = {(x + dx, y, z): blk for (x, y, z), blk in self.v.items()}
        for sh in self.shelters:
            sh["cells"] = [(x + dx, y, z) for (x, y, z) in sh["cells"]]

    def full(self, x, y, z):
        if y < 0:
            return True  # the sand
        b = self.v.get((x, y, z))
        return b is not None and b[0] in FULL_CUBES

    def cull(self):
        """Drops full cubes no face of which can be seen: each is a separate block-model draw."""
        hidden = [p for p in self.v if self.v[p][0] in FULL_CUBES and all(
            self.full(p[0] + dx, p[1] + dy, p[2] + dz)
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
        for p in hidden:
            del self.v[p]
        return len(hidden)

    def shelter_json(self):
        out = []
        for s in self.shelters:
            for c in s["cells"]:
                assert c not in self.v, f"part left in a shelter interior at {c}"
            j = {}
            if s["kind"] != "hollow":
                j["kind"] = s["kind"]
            j["interior"] = [{"x": x, "y": y, "z": z} for x, y, z in sorted(s["cells"], key=lambda c: (c[1], c[2], c[0]))]
            if s["capacity"]:
                j["capacity"] = s["capacity"]
            if s["min_length"]:
                j["min_length"] = s["min_length"]
            out.append(j)
        return out

    def parts_json(self, xz):
        parts = []
        for (x, y, z), (block, props) in sorted(self.v.items(), key=lambda kv: (kv[0][1], kv[0][0], kv[0][2])):
            state = {"Name": "minecraft:" + block}
            if props:
                state["Properties"] = props
            parts.append({"state": state, "offsetX": round(x * xz, 7), "offsetY": y, "offsetZ": round(z * xz, 7)})
        return parts

    def export_floor(self, name, scale, item_icon=None):
        xz = scale / CELL_WIDTH
        for (x, y, z) in self.v:
            assert abs(x) * scale + scale / 2 <= FLOOR_HALF + 1e-6, f"{name}: x={x} reaches the glass"
            assert abs(z) * scale + scale / 2 <= FLOOR_HALF + 1e-6, f"{name}: z={z} reaches the glass"
            assert (y + 1) * scale <= FLOOR_HEIGHT + 1e-6, f"{name}: y={y} reaches the lid"
        footprint = [(0, 0)]
        for (x, _, z) in sorted(self.v):
            cell = (math.floor(x * xz + 0.5), math.floor(z * xz + 0.5))
            if cell not in footprint:
                footprint.append(cell)
        data = {"footprint_cells": [{"dx": dx, "dz": dz} for dx, dz in footprint],
                "parts": self.parts_json(xz), "scale": scale}
        return self._write(name, data, item_icon)

    def export_span(self, name, span, scale, item_icon=None):
        for c in self.v:
            assert self.inside(*c), f"{name}: {c} is outside the box"
        data = {"span": {"x": span[0], "y": span[1], "z": span[2]}, "scale": scale, "parts": self.parts_json(1)}
        return self._write(name, data, item_icon)

    def _write(self, name, data, item_icon):
        if item_icon:
            data["item_icon"] = item_icon
        shelters = self.shelter_json()
        if len(shelters) == 1:
            data["shelter"] = shelters[0]
        elif shelters:
            data["shelters"] = shelters
        data = footprint_audit.with_occupied(data)
        path = os.path.join(OUT_DIR, name + ".json")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, indent=1)
            f.write("\n")
        return path, len(data["parts"])


def grid_dims(span, scale):
    """Build-grid size for a span: wall-to-wall and sand-to-lid interior, in voxels."""
    return (int((span[0] - 2 / 16) / scale + 1e-6), int((span[1] - 3 / 16) / scale + 1e-6),
            int((span[2] - 2 / 16) / scale + 1e-6))


def smooth(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def weathered(rng, base="stone_bricks"):
    """A stone brick, now and then mossy or cracked."""
    r = rng.random()
    if r < 0.30:
        return "mossy_stone_bricks"
    if r < 0.42:
        return "cracked_stone_bricks"
    return base


FANS = ("tube_coral_fan", "brain_coral_fan", "bubble_coral_fan", "fire_coral_fan", "horn_coral_fan")


def value_noise(seed):
    """Smooth 3D value noise in [-1, 1]: random values on an integer lattice, blended with a
    smoothstep. Sample at x / feature_size for blobs about that many voxels across."""
    cache = {}
    def lattice(i, j, k):
        key = (i, j, k)
        if key not in cache:
            cache[key] = random.Random(hash((seed, i, j, k)) & 0xFFFFFFFF).random() * 2 - 1
        return cache[key]
    def sample(x, y=0.0, z=0.0):
        i, j, k = math.floor(x), math.floor(y), math.floor(z)
        fx, fy, fz = smooth(x - i), smooth(y - j), smooth(z - k)
        def lerp(a, b, t):
            return a + (b - a) * t
        c = [[[lattice(i + a, j + bb, k + c_) for c_ in (0, 1)] for bb in (0, 1)] for a in (0, 1)]
        return lerp(lerp(lerp(c[0][0][0], c[0][0][1], fz), lerp(c[0][1][0], c[0][1][1], fz), fy),
                    lerp(lerp(c[1][0][0], c[1][0][1], fz), lerp(c[1][1][0], c[1][1][1], fz), fy), fx)
    return sample


def stair(facing, half="bottom", shape="straight"):
    return {"facing": facing, "half": half, "shape": shape, "waterlogged": True}


def wall_post():
    """A stone wall block standing alone: a slender post, for pinnacles and finials."""
    return {"up": True, "north": "none", "south": "none", "east": "none", "west": "none", "waterlogged": True}


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 1. Amphora — a wine jar the sea drank dry. A Greek amphora lies on its side in the sand, black
#    glaze on its neck and foot, a band round its belly, handles curling from shoulder to neck. Its
#    neck is the only way in: a hide for medium fish, and a bichir's chosen home.
#    Hollow: the neck and belly as one 3x3 tube, 11 long. Mouth 0.18 (fish to 0.45), run 0.66.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def amphora():
    scale = 0.06
    b = Build()
    rng = random.Random(7)
    yc = 3                                    # the jar's axis, in cell centres: y + 0.5 = 3.5
    R = {-6: 1.6, -5: 2.6, -4: 3.3, -3: 3.6, -2: 3.75, -1: 3.7, 0: 3.5, 1: 3.0, 2: 2.6, 3: 2.3,
         4: 2.3, 5: 2.3, 6: 2.9}
    for x, r in R.items():
        for y in range(0, 8):
            for z in range(-4, 5):
                dy, dz = y - yc, z
                if dy * dy + dz * dz <= r * r:
                    black = x >= 3 or x == -6 or x == -2
                    b.set(x, y, z, "black_terracotta" if black else "terracotta")
    # Handles: from the shoulder out and round to the neck, a loop each side.
    for side in (-1, 1):
        for x in (1, 2, 3):
            b.set(x, yc, side * 4, "black_terracotta")
        b.set(4, yc, side * 3, "black_terracotta")
    # Barnacles and a little life on its upper flank.
    b.set(-2, 7, -1, "tube_coral_fan", waterlogged=True)
    b.set(0, 7, 1, "brain_coral_fan", waterlogged=True)
    b.set(-4, 6, 2, "sea_pickle", pickles=3, waterlogged=True)
    for (x, z) in ((-6, 3), (2, -4), (5, 3), (-5, -3)):
        b.setdefault(x, 0, z, "seagrass")
    b.shelter(-4, 2, -1, 6, 4, 1, capacity=2)
    return b, "amphora", scale


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 2. Drowned Bell — a great bronze temple bell, fallen from its tower and come to rest on its side,
#    green with verdigris, its lip half sunk in the sand and a length of chain trailing from its
#    crown. Fish fade into the dark of its throat.
#    Hollow: a 5x5 throat, 7 deep. Mouth 0.25, run 0.35 (fish to 0.38).
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def drowned_bell():
    scale = 0.05
    b = Build()
    yc = 4
    R = {-5: 2.3, -4: 3.5, -3: 3.9, -2: 4.0, -1: 4.1, 0: 4.2, 1: 4.4, 2: 4.7, 3: 5.0, 4: 5.3}
    for x, r in R.items():
        for y in range(0, 10):
            for z in range(-6, 7):
                dy, dz = y - yc, z
                if dy * dy + dz * dz <= r * r:
                    band = x in (-3, 4)
                    b.set(x, y, z, "waxed_weathered_copper" if band else "waxed_oxidized_copper")
    # The crown's loop, and the chain it hung by, fallen across the sand.
    b.set(-6, yc, 0, "iron_chain", axis="x")
    for z in range(0, 4):
        b.set(-6, 0, z, "iron_chain", axis="z")
    # Coral taking hold on its shoulder.
    b.set(0, 9, -1, "fire_coral_fan", waterlogged=True)
    b.set(1, 9, 1, "horn_coral_fan", waterlogged=True)
    b.set(-2, 9, 0, "sea_pickle", pickles=2, waterlogged=True)
    b.shelter(-2, 2, -2, 4, 6, 2, capacity=2)
    return b, "drowned_bell", scale


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 3. Mangrove Knees — a mangrove standing in the shallows on a cage of arching prop roots, its
#    crown of leaves above. Under the root mat is a nursery: fish loiter there in the dappled shade,
#    on show from every side.
#    Open: the space under the roots, 7 x 5, 4 tall. Mouths 0.26 tall, run 0.455 (fish to 0.5).
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def mangrove_knees():
    scale = 0.065
    b = Build()
    rng = random.Random(23)
    # The root mat over the nursery, ragged at its edge.
    for x in range(-5, 6):
        for z in range(-4, 5):
            edge = abs(x) == 5 or abs(z) == 4
            if edge and rng.random() < 0.45:
                continue
            b.set(x, 4, z, "mangrove_roots")
    # Prop roots: down from the mat's edge to the sand, splaying out at the foot.
    legs = [(-5, -4), (-5, 3), (5, -3), (5, 4), (-2, -4), (2, -4), (-1, 4), (3, 4)]
    for (x, z) in legs:
        b.set(x, 4, z, "mangrove_roots")
        for y in range(1, 4):
            b.set(x, y, z, "mangrove_log", axis="y")
        fx = x + (1 if x > 0 else -1 if x < 0 else 0)
        fz = z + (1 if z > 0 else -1)
        b.set(x, 0, z, "mangrove_log", axis="y")
        if abs(fx) <= 6 and abs(fz) <= 6:
            b.set(fx, 0, fz, "muddy_mangrove_roots")
    # Trunk and crown.
    for y in range(5, 9):
        for (x, z) in ((0, 0), (-1, 0), (0, -1), (-1, -1)):
            b.set(x, y, z, "mangrove_log", axis="y")
    b.set(1, 8, 0, "mangrove_log", axis="x")
    for x in range(-5, 5):
        for y in range(8, 12):
            for z in range(-5, 5):
                d = ((x + 0.5) / 4.6) ** 2 + ((y - 9.8) / 2.0) ** 2 + ((z + 0.5) / 4.4) ** 2
                if d <= 1.0 and rng.random() < 0.9:
                    b.setdefault(x, y, z, "mangrove_leaves", waterlogged=True)
    # Moss on the knees, seagrass round the edge.
    for (x, z) in ((-6, -5), (6, 5), (-6, 4), (6, -4)):
        b.setdefault(x, 0, z, "seagrass")
    b.setdefault(-6, 1, -5, "moss_carpet")
    b.shelter(-3, 0, -2, 3, 3, 2, kind="open", capacity=3)
    return b, "mangrove_knees", scale


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 4. Basalt Grotto — Fingal's Cave in miniature: a rounded hill of hexagonal basalt columns, each a
#    different height, stepping down as a causeway to the water at front and back. A dark sea cave
#    opens in its face between two great jamb columns under a lintel of columns lying on their
#    sides; far inside, a glint of magma. Drums fallen to the sand, pickles in the crevices.
#    Hollow: 8 deep, 4 wide, 4 tall. Mouth 0.26, run 0.52 (fish to 0.57: the eel just fits).
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def basalt_grotto():
    scale = 0.065
    b = Build()
    rng = random.Random(1772)
    noise = value_noise(1772)
    ix0, ix1, iy0, iy1, iz0, iz1 = -4, 3, 1, 4, -2, 1
    # Columns, not voxels: every cell of the hill belongs to the nearest centre of a hex lattice,
    # and each centre is one column of one height, so the hill steps like the Giant's Causeway.
    centres = []
    for j in range(-4, 5):
        for i in range(-5, 6):
            centres.append((i * 1.55 + (0.78 if j % 2 else 0.0) + rng.uniform(-0.2, 0.2),
                            j * 1.35 - 0.5 + rng.uniform(-0.2, 0.2)))
    def column_of(x, z):
        return min(range(len(centres)), key=lambda c: (centres[c][0] - x) ** 2 + (centres[c][1] - z) ** 2)
    # Height of each column: a ridge that climbs to the back left and slumps to the sides, with
    # steps down to the water beside the cave and an eroded, rounded outline.
    heights, stuff = {}, {}
    for c, (cx, cz) in enumerate(centres):
        # A dome peaking behind the cave's middle, falling away as a stepped cone.
        d = math.hypot((cx + 1.5) / 5.2, (cz + 0.5) / 3.9)
        ridge = 8.6 - 3.9 * d ** 2
        # Outer columns vary the most: some stand proud of the cave wall, some are stumps.
        jitter = (-2, -2, -1, 0, 1, 2, 3) if d > 0.7 else (-1, -1, 0, 0, 1, 1)
        h = round(ridge + 1.3 * noise(cx / 2.3, 0.0, cz / 2.3) + rng.choice(jitter))
        heights[c] = max(0, min(h, 9))
        r = rng.random()
        stuff[c] = "polished_basalt" if r < 0.3 else "smooth_basalt" if r < 0.38 else "basalt"
    for x in range(-5, 6):
        for z in range(-4, 4):
            c = column_of(x, z)
            h = heights[c]
            # The outline: an ellipse with a ragged rim, not the corners of a box.
            shell = ix0 - 1 <= x <= ix1 and iz0 - 1 <= z <= iz1 + 1 and not (
                x == ix0 - 1 and z in (iz0 - 1, iz1 + 1))     # the back corners close nothing
            rim = ((x + 0.5) / 6.4) ** 2 + ((z + 0.5) / 4.5) ** 2 + 0.25 * noise(x / 1.7, 3.0, z / 1.7)
            if rim > 1.0 and not shell:
                continue
            front = x >= 4
            if front and iz0 - 1 <= z <= iz1 + 1:
                continue                      # the mouth's jambs and lintel are built below
            if front:
                # Causeway steps down to the sand either side of the cave.
                h = min(h, 5 if x == 4 else 3) - (1 if abs(z + 0.5) > 3 else 0)
            if ix0 <= x <= ix1 and iz0 <= z <= iz1:
                h = max(h, iy1 + 2)           # the cave's roof
            elif ix0 - 1 <= x <= ix1 and iz0 - 1 <= z <= iz1 + 1:
                h = max(h, iy1 + 1)           # its walls: just tall enough to close it
            for y in range(0, max(h, 0)):
                b.set(x, y, z, stuff[c], axis="y")
    # The mouth: two great jamb columns and a lintel of columns lying on their sides, as the
    # columns over Fingal's Cave bend to the horizontal.
    for z in (iz0 - 1, iz1 + 1):
        for y in range(0, iy1 + 3):
            b.set(4, y, z, "polished_basalt", axis="y")
    for z in range(iz0, iz1 + 1):
        for y in (iy1 + 1, iy1 + 2):
            b.set(4, y, z, "basalt", axis="z")
    b.set(4, iy1 + 3, iz0, "basalt", axis="y")
    b.set(4, iy1 + 3, iz1 - 1, "polished_basalt", axis="y")
    # The cave's floor, and the magma glowing at its back.
    b.box(ix0, 0, iz0, ix1 + 1, 0, iz1, "smooth_basalt")
    b.box(ix0 - 1, 2, iz0 + 1, ix0 - 1, 3, iz1 - 1, "magma_block")
    # A broken column top or two, and a drum fallen to the sand beside the steps.
    for (x, z, axis) in ((-3, 3, "x"), (1, -4, "z")):
        top = max((y for (xx, y, zz) in b.v if xx == x and zz == z), default=None)
        if top is not None and top < 8:
            b.set(x, top + 1, z, "basalt", axis=axis)
    b.set(5, 0, -4, "basalt", axis="x")
    b.set(5, 0, 3, "polished_basalt", axis="z")
    # The causeway running down into the sea: stumps of columns at the front corners, and an
    # apron of low ones stepping off the back of the hill.
    for (x, z, h) in ((5, -3, 2), (5, 2, 1), (4, -4, 3), (4, 3, 2), (-6, -1, 3), (-6, 0, 2), (-6, 1, 1), (-6, -2, 1)):
        for y in range(h):
            b.setdefault(x, y, z, "basalt" if (x + z) % 3 else "polished_basalt", axis="y")
    # Life on the ledges: sea pickles in the crevices of the crown, moss on the low steps,
    # seagrass and a kelp strand round the foot.
    def top_of(x, z):
        return max((y for (xx, y, zz) in b.v if xx == x and zz == z), default=-1)
    for (x, z, n) in ((-4, -3, 3), (-1, 2, 2), (2, -3, 1), (-5, 0, 2)):
        t = top_of(x, z)
        if 0 <= t < 9:
            b.set(x, t + 1, z, "sea_pickle", pickles=n, waterlogged=True)
    for (x, z) in ((4, -4), (4, 3), (3, 3)):
        t = top_of(x, z)
        if 0 <= t < 6:
            b.setdefault(x, t + 1, z, "moss_carpet")
    for (x, z) in ((5, -3), (5, 2), (-5, 3), (2, 3), (-5, -4)):
        b.setdefault(x, 0, z, "seagrass")
    for y in range(0, 8):
        b.setdefault(-5, y, -4 if top_of(-5, -4) < 0 else 3, "kelp_plant" if y < 7 else "kelp")
    b.shelter(ix0, iy0, iz0, ix1, iy1, iz1, capacity=2)
    return b, "basalt_grotto", scale


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 5. Moon Gate — a round doorway in a Huizhou garden wall: white plaster stepping down in tiers
#    under dark tiled copings whose ridge ends kick up like horses' heads. One wing has slumped
#    to a ruin of bare brick. Behind it an old cherry tree twists up, throws a bough over the
#    coping and lets its blossom spill down the front, a lantern hanging from it, and blossom
#    fills the rim of the moon. Big enough for the biggest fish to sail through.
#    Gate: 7 wide, 9 tall, through the wall. Mouth 0.315: any fish (gates set no length limit).
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def moon_gate():
    scale = 0.045
    b = Build()
    rng = random.Random(1225)
    noise = value_noise(1225)
    cy, r = 5.5, 5.0
    ix0, ix1, iy0, iy1 = -3, 3, 1, 9
    def in_disc(x, y):
        return x * x + (y + 0.5 - cy) ** 2 <= r * r
    def in_ring(x, y):
        return x * x + (y + 0.5 - cy) ** 2 <= (r + 1.05) ** 2
    # Three tiers of wall: the gatehouse over the moon, then two steps down each side.
    def tier_top(x):
        return 12 if abs(x) <= 3 else 11 if abs(x) <= 5 else 9
    # The east wing has slumped: a ragged broken top, falling away to the end.
    def ruin_top(x, z):
        if x < 6:
            return None
        return (8 if x == 6 else 5) - rng.choice((0, 0, 1, 2)) + (z == 0)
    for x in range(-7, 8):
        top = tier_top(x)
        for z in (-1, 0, 1):
            cap = ruin_top(x, z)
            height = top if cap is None else min(top, cap)
            for y in range(0, height + 1):
                if in_disc(x, y) and y > 0:
                    continue
                if in_ring(x, y):
                    block = "polished_deepslate"
                elif y <= 1:
                    block = weathered(rng)               # a plinth of grey brick
                elif (cap is not None and y >= height - 2 - (x == 7)) or (
                        abs(z) == 1 and noise(x / 2.2, y / 2.2, z * 5.0) > 0.42):
                    block = "bricks"                     # the plaster flaked away
                else:
                    block = "calcite"
                b.set(x, y, z, block)
        if ruin_top(x, 0) is not None:
            continue
        # Coping: a ridge of tile with eaves either side.
        b.set(x, top + 1, 0, "deepslate_tiles")
        for z, facing in ((-1, "south"), (1, "north")):
            b.set(x, top + 1, z, "deepslate_tile_stairs", **stair(facing))
        for z, facing in ((-2, "south"), (2, "north")):
            b.set(x, top, z, "deepslate_tile_stairs", **stair(facing, "top"))
    # Horses' heads: where a tier steps down, its ridge ends in a tile kicked up and outward.
    for x, facing in ((-3, "west"), (3, "east"), (-5, "west"), (5, "east"), (-7, "west")):
        b.set(x, tier_top(x) + 2, 0, "deepslate_tile_stairs", **stair(facing))
    # Rubble of the slumped wing: bricks, a slipped tile, on the sand at its foot.
    for (x, y, z, block) in ((8, 0, 1, "bricks"), (8, 0, -1, "calcite"), (7, 0, 2, "bricks"),
                             (8, 1, 1, "deepslate_tile_slab"), (7, 0, -2, "deepslate_tile_stairs")):
        props = {"type": "bottom", "waterlogged": True} if block.endswith("slab") else \
            stair("north") if block.endswith("stairs") else {}
        b.set(x, y, z, block, **props)
    # Blossom filling the rim of the moon round the gateway.
    for x in range(-6, 7):
        for y in range(0, 12):
            if in_disc(x, y) and y > 0 and not (ix0 <= x <= ix1 and iy0 <= y <= iy1):
                for z in (-1, 0, 1):
                    b.set(x, y, z, "cherry_leaves", waterlogged=True)
    # The cherry: an old trunk twisting up behind the west wing, its roots gripping the sand.
    def bough(p0, p1, p2, steps=24):
        last = None
        for i in range(steps + 1):
            t = i / steps
            p = [(1 - t) ** 2 * a + 2 * (1 - t) * t * c + t * t * d for a, c, d in zip(p0, p1, p2)]
            v = tuple(round(q) for q in p)
            if v != last:
                axis = "y"
                if last is not None:
                    d = [abs(v[k] - last[k]) for k in range(3)]
                    axis = "xyz"[d.index(max(d))]
                b.set(*v, "cherry_log", axis=axis)
                last = v
        return last
    bough((-6, 0, -3), (-8.5, 6, -4.5), (-4, 11, -3))                # the trunk
    bough((-4, 11, -3), (-3, 13, -3), (-1, 14, -2))                   # leader, toward the gate
    bough((-1, 14, -2), (2, 15, -1), (4, 15, 1))                      # the bough over the coping
    bough((-6, 8, -4), (-8, 10, -5), (-8, 12, -5))                    # a limb reaching west
    bough((-4, 11, -3), (-5, 13, -4), (-4, 15, -4))                   # and one climbing
    for (x, z, axis) in ((-7, -3, "x"), (-5, -3, "x"), (-6, -2, "z"), (-6, -4, "z")):
        b.setdefault(x, 0, z, "cherry_log", axis=axis)
    # Crown: lumpy clouds of blossom round the bough ends, not one round ball.
    for (cx, cy_, cz, rr) in ((-7.5, 13.5, -5, 2.0), (-4, 15.5, -4.5, 2.1), (-0.5, 15.6, -2.5, 1.8),
                             (3.2, 15.4, 0.3, 1.5)):
        for x in range(math.floor(cx - rr - 1), math.ceil(cx + rr + 2)):
            for y in range(math.floor(cy_ - rr), min(17, math.ceil(cy_ + rr)) + 1):
                for z in range(math.floor(cz - rr - 1), math.ceil(cz + rr + 2)):
                    d = math.sqrt(((x - cx) / rr) ** 2 + ((y - cy_) / (rr * 0.75)) ** 2 + ((z - cz) / rr) ** 2)
                    if d + 0.35 * noise(x / 1.6, y / 1.6, z / 1.6) <= 1.0 and abs(x) <= 8 and abs(z) <= 8:
                        b.setdefault(x, y, z, "cherry_leaves", waterlogged=True)
    # Blossom weeping down the front of the wall, clear of the gateway.
    for (x, z, y0, y1) in ((4, 2, 12, 14), (5, 3, 10, 13), (3, 3, 12, 13), (-5, 3, 11, 13), (-6, 3, 10, 12)):
        for y in range(y0, y1 + 1):
            b.setdefault(x, y, z, "cherry_leaves", waterlogged=True)
    b.set(5, 9, 3, "lantern", hanging=True, waterlogged=True)
    # Petals drifted on the sand, and caught on the copings.
    for (x, z, n) in ((-5, 3, 4), (-2, 4, 2), (4, 3, 3), (6, -3, 2), (-3, -4, 4), (1, 5, 1), (-7, 2, 3), (3, -3, 2)):
        b.setdefault(x, 0, z, "pink_petals", facing=rng.choice(("north", "east", "south", "west")), flower_amount=n)
    for (x, z) in ((-2, 1), (2, -1), (-5, 1)):
        b.setdefault(x, tier_top(x) + 2, z, "pink_petals", facing="east", flower_amount=2)
    b.shelter(ix0, iy0, -1, ix1, iy1, 1, kind="gate", capacity=3)
    return b, "moon_gate", scale


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 6. Leviathan's Seat — a throne carved from a single great whale vertebra: the centrum its seat,
#    the spine rising behind as its back, the wings of bone its arms, a crown of tube coral on top.
#    Kept for the biggest fish in the tank, who holds court on it while the small fry pass by.
#    Open, capacity 1, min_length 0.40: the seat is 9 long (run 0.45) under 0.25 of headroom, so it
#    takes fish 0.40 to 0.50 long: pike, gar, idol, parrotfish, the trouts. Any bigger and a lone
#    tank has no water left round it.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def leviathans_seat():
    scale = 0.05
    b = Build()
    # The centrum: a thick drum of bone, the seat.
    for x in range(-6, 7):
        for z in range(-5, 5):
            if (x / 5.6) ** 2 + ((z + 0.5) / 4.6) ** 2 <= 1.0:
                for y in range(0, 3):
                    b.set(x, y, z, "bone_block", axis="y")
    # The spine rising behind: the throne's back, narrowing upward, the canal through it.
    widths = {3: 4, 4: 4, 5: 4, 6: 4, 7: 4, 8: 3, 9: 2}
    for y, w in widths.items():
        for x in range(-w, w + 1):
            for z in (-4, -3):
                b.set(x, y, z, "bone_block", axis="y")
    b.clear(0, 8, -3)
    b.clear(0, 8, -4)
    # The wings of bone: the arms, sweeping forward.
    for side in (-1, 1):
        for z in range(-3, 3):
            b.set(side * 5, 3, z, "bone_block", axis="z")
        b.set(side * 5, 3, 3, "bone_block", axis="z")
        b.set(side * 5, 2, 4, "bone_block", axis="z")
    # A crown of tube coral, and sea pickles glowing at its feet.
    # (Kept low: a lone tank needs a wall margin of water over it.)
    b.set(0, 10, -3, "tube_coral_block")
    b.set(-1, 10, -3, "tube_coral_fan", waterlogged=True)
    b.set(1, 10, -3, "tube_coral_fan", waterlogged=True)
    b.set(0, 11, -3, "tube_coral", waterlogged=True)
    b.set(0, 10, -4, "tube_coral", waterlogged=True)
    for (x, z, n) in ((-4, 4, 3), (4, 4, 2), (-5, -3, 1), (5, -2, 2)):
        b.setdefault(x, 3 if (x, 2, z) in b.v else 0, z, "sea_pickle", pickles=n, waterlogged=True)
    b.shelter(-4, 3, -2, 4, 7, 2, kind="open", capacity=1, min_length=0.40)
    return b, "leviathans_seat", scale


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 7. Sea Arch — a limestone headland with a great window worn through it, like the Azure Window:
#    a cliff on one side, a pillar on the other, a bridge of rock across the top, coral on its feet.
#    Big enough for the biggest fish in the tank to swim through.
#    Gate (span 2x2x2): a 6 wide, 9 tall window; the gate is its middle slice, so a fish up to 0.6
#    long has room to line up on it from either side of a two-deep tank (a fish lines up about a
#    body length out from a mouth, clear of the glass).
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def sea_arch():
    span, scale = (2, 2, 2), 0.1
    nx, ny, nz = grid_dims(span, scale)          # 18 x 18 x 18
    b = Build((nx, ny, nz))
    rng = random.Random(1906)
    zc = 8.5
    wx0, wx1, wy1, wz0, wz1 = 6, 11, 8, 8, 8     # the window: x 6..11, sand to y 8; the gate at z 8
    def top(x):
        if x <= 5:
            return 15 - 0.25 * x
        if x <= 11:
            return 12.6 - 0.15 * (x - 6)
        return 12.5 - 0.7 * max(0, x - 13)
    def half_width(x, y):
        if wx0 <= x <= wx1:
            return 2.6
        base = 5.5 if x <= 5 else 3.6
        return base - 0.12 * y
    for x in range(0, 17):
        t = top(x) + rng.uniform(-0.6, 0.6)
        for y in range(0, int(t) + 1):
            if wx0 <= x <= wx1 and y <= wy1:
                continue
            w = half_width(x, y) + rng.uniform(-0.4, 0.4)
            if x >= 15:
                w = 1.4
            for z in range(0, nz):
                if abs(z + 0.5 - zc) <= w:
                    upper = y >= int(t) - 0
                    b.set(x, y, z, "smooth_sandstone" if upper else "sandstone")
    # Weathered strata: a band of cut sandstone round the cliff.
    for (x, y, z), (name, _) in list(b.v.items()):
        if name == "sandstone" and y in (4, 10) and rng.random() < 0.7:
            b.set(x, y, z, "cut_sandstone")
    # Boulders off the pillar's foot.
    for (x, z) in ((16, 4), (17, 5), (16, 13), (13, 15)):
        b.set(x, 0, z, "sandstone")
    # Life on the rock: coral at its feet, fans on its ledges, kelp up the cliff, sea pickles.
    for (x, z, blk) in ((2, 2, "brain_coral_block"), (14, 3, "tube_coral_block"), (4, 15, "fire_coral_block"),
                        (13, 13, "bubble_coral_block")):
        b.set(x, 0, z, blk)
        b.setdefault(x, 1, z, rng.choice(FANS), waterlogged=True)
    for (x, y, z), (name, _) in list(b.v.items()):
        if name == "smooth_sandstone" and rng.random() < 0.08:
            b.setdefault(x, y + 1, z, rng.choice(FANS), waterlogged=True)
    for (x, z, h) in ((1, 12, 9), (0, 5, 7)):
        for y in range(0, h):
            if (x, y, z) not in b.v:
                b.set(x, y, z, "kelp_plant" if y < h - 1 else "kelp")
    for (x, z) in ((5, 14), (12, 2), (9, 14), (8, 3)):
        b.setdefault(x, 0, z, "sea_pickle", pickles=rng.randint(1, 4), waterlogged=True)
    culled = b.cull()
    b.shelter(wx0, 0, wz0, wx1, wy1, wz1, kind="gate", capacity=4)
    return b, "sea_arch", scale, span, culled


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 8. Sunken Ziggurat — a stepped temple of mossy stone, a stair climbing its face to a little
#    sanctum on the top. Through its base runs a tunnel between two feathered-serpent heads, gold
#    eyes glinting: a big fish slides into one maw and out of the other. Small fish rest in the
#    sanctum's shade up top.
#    Hollow (span 4x2x1): the tunnel, 4x4, 38 long. Mouth 0.25 (fish to 0.62), run 2.4. The temple
#    stands in the middle of the long tank so a big fish has room to line up on either maw.
#    Open: the sanctum, 6 long, 2 deep, 3 tall, under the roof. Mouths 0.125 (fish to 0.31).
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def sunken_ziggurat():
    span, scale = (4, 2, 1), 0.0625
    nx, ny, nz = grid_dims(span, scale)          # 62 x 29 x 14; built at x 1..41, then moved in by 8
    b = Build((nx, ny, nz))
    rng = random.Random(1194)
    tiers = [(0, 5, 7, 36, 1, 12), (6, 9, 10, 33, 2, 11), (10, 13, 13, 30, 3, 10), (14, 17, 16, 27, 4, 9)]
    for (y0, y1, x0, x1, z0, z1) in tiers:
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    b.set(x, y, z, weathered(rng))
        for x in range(x0, x1 + 1):                    # a chiselled course round each tier's top
            for z in (z0, z1):
                b.set(x, y1, z, "chiseled_stone_bricks" if x % 4 == 0 else weathered(rng))
    # The stair up the front, to the sanctum.
    for y in range(0, 18):
        z = 13 - y // 2
        for x in range(20, 24):
            for yy in range(0, y):
                b.setdefault(x, yy, z, weathered(rng))
            b.set(x, y, z, "stone_brick_stairs", facing="north", half="bottom", shape="straight")
    # The sanctum: colonnades front and back under a stepped roof, open at its ends.
    sx0, sx1, sy0, sy1, sz0, sz1 = 19, 24, 18, 20, 6, 7
    for x in (19, 21, 22, 24):
        for z in (5, 8):
            for y in range(sy0, sy1 + 1):
                b.set(x, y, z, "chiseled_stone_bricks" if y == sy0 else "stone_bricks")
    for x in (20, 23):
        for z in (5, 8):
            for y in range(sy0, sy1 + 1):
                b.set(x, y, z, "kelp" if y == sy1 else "kelp_plant")
    b.box(18, 21, 4, 25, 21, 9, "stone_bricks")
    b.box(19, 22, 5, 24, 22, 8, "mossy_stone_bricks")
    b.box(20, 23, 6, 23, 23, 7, "gold_block")
    # The tunnel through the base, and a serpent's head at each end.
    tx0, tx1, ty0, ty1, tz0, tz1 = 3, 40, 1, 4, 5, 8
    b.box(tx0, 0, tz0 - 1, tx1, 0, tz1 + 1, "stone_bricks")
    for (hx0, hx1, face) in ((3, 6, 2), (37, 40, 41)):
        for x in range(hx0, hx1 + 1):
            for y in range(0, 7):
                for z in range(tz0 - 2, tz1 + 3):
                    b.set(x, y, z, "chiseled_stone_bricks" if y == 6 else weathered(rng, "mossy_stone_bricks"))
        # The upper jaw juts over the mouth and the lower lip under it, clear of the opening itself.
        for z in range(tz0 - 1, tz1 + 2):
            b.set(face, ty1 + 1, z, "stone_brick_stairs", facing="west" if face < 20 else "east", half="top", shape="straight")
            b.set(face, 0, z, "stone_brick_slab", type="bottom")
        for z in (tz0 - 1, tz1 + 1):                    # gold eyes
            b.set(face, ty1 + 2, z, "gold_block")
    # Vines and moss on the ledges, kelp at the corners, sea pickles on the stair.
    for (y0, y1, x0, x1, z0, z1) in tiers[1:]:
        for x in range(x0, x1 + 1):
            if rng.random() < 0.35:
                b.setdefault(x, y0, z1 + 1, "moss_carpet")
    for (x, z, h) in ((6, 1, 14), (37, 12, 11), (8, 12, 8)):
        for y in range(0, h):
            if (x, y, z) not in b.v:
                b.set(x, y, z, "kelp_plant" if y < h - 1 else "kelp")
    for (x, z) in ((12, 13), (31, 13), (1, 3), (42, 10)):
        b.setdefault(x, 0, z, "sea_pickle", pickles=rng.randint(2, 4), waterlogged=True)
    culled = b.cull()
    b.shelter(tx0, ty0, tz0, tx1, ty1, tz1, capacity=4)
    b.shelter(sx0, sy0, sz0, sx1, sy1, sz1, kind="open", capacity=2)
    b.shift(8)
    return b, "sunken_ziggurat", scale, span, culled


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 9. Capsized Galleon — a warship turned turtle on the sand, keel to the sky, copper sheathing
#    green along it, the stern broken away to show the ribs, and its fallen mast beside it. In the
#    long dark of its hull something big waits, only its snout showing at the broken stern; far
#    in, a glint of gold.
#    Hollow (span 3x2x1): 22 long, 6 wide, 5 tall. Mouth 0.31 (fish to 0.78), run 1.375. The
#    broken stern stands 0.8 in from the end of the tank, room for a big fish to line up on it. Two
#    storeys, so the wreck lies on the bottom with open water over it: in one storey the hull
#    filled the box and the fish were squeezed into the ends.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def capsized_galleon():
    span, scale = (3, 2, 1), 0.0625
    nx, ny, nz = grid_dims(span, scale)          # 46 x 29 x 14
    b = Build((nx, ny, nz))
    rng = random.Random(1628)
    zc = 6.5
    def beam(x):
        if x >= 30:
            return 6.0 * math.sqrt(max(0.0, 1 - ((x - 30) / 9.5) ** 2))
        return 6.0
    for x in range(6, 40):
        B = beam(x)
        if B < 0.8:
            continue
        for z in range(0, nz):
            u = (z + 0.5 - zc) / B
            if abs(u) > 1:
                continue
            top = 9 - 3 * u * u - (1 if x >= 37 else 0)
            for y in range(0, int(round(top)) + 1):
                b.set(x, y, z, "dark_oak_planks")
            if abs(z + 0.5 - zc) <= 1 and x <= 37:       # the keel, sheathed in copper
                b.set(x, int(round(top)) + 1, z, "waxed_oxidized_cut_copper")
    # Wales: dark strakes along the side, and gun ports, upside down now, near the sand.
    for x in range(6, 37):
        for z in (0, 12):
            if (x, 5, z) in b.v:
                b.set(x, 5, z, "stripped_dark_oak_log", axis="x")
    for x in range(9, 27, 4):
        for z in (0, 12):
            if (x, 2, z) in b.v:
                b.set(x, 2, z, "black_terracotta")
    # The broken stern: ribs standing out past the planking, ragged.
    for x in (4, 5):
        for z in range(0, nz):
            u = (z + 0.5 - zc) / 6.0
            if abs(u) > 1:
                continue
            top = int(round(9 - 3 * u * u))
            shell = [(y, z) for y in range(0, top + 1) if y >= top - 1 or abs(u) > 0.62]
            for (y, zz) in shell:
                if 1 <= y <= 5 and 4 <= zz <= 9:
                    continue
                if x == 5 or (x == 4 and zz in (0, 1, 2, 11, 12, 13)):
                    b.set(x, y, zz, "stripped_dark_oak_log", axis="y")
    # Gold, deep inside at the bow end.
    b.set(28, 1, 6, "gold_block")
    b.set(28, 1, 7, "gold_block")
    b.set(28, 2, 7, "gold_block")
    # The fallen mast and its yard on the sand behind, kelp and life on the hull.
    for x in range(12, 31):
        b.set(x, 0, 13, "spruce_log", axis="x")
    for z in range(10, 14):
        b.set(17, 1, z, "spruce_log", axis="z")
    for (x, z, h) in ((10, 13, 10), (27, 13, 8), (36, 2, 11), (2, 1, 9)):
        for y in range(0, h):
            if (x, y, z) not in b.v:
                b.set(x, y, z, "kelp_plant" if y < h - 1 else "kelp")
    for (x, y, z), (name, _) in list(b.v.items()):
        if name == "waxed_oxidized_cut_copper" and rng.random() < 0.12:
            b.setdefault(x, y + 1, z, rng.choice(("tube_coral_fan", "brain_coral_fan")), waterlogged=True)
    for (x, z) in ((20, 13), (33, 12), (1, 9)):
        b.setdefault(x, 0, z, "sea_pickle", pickles=rng.randint(2, 4), waterlogged=True)
    culled = b.cull()
    b.shelter(6, 1, 4, 27, 5, 9, capacity=3)
    b.shift(6)
    return b, "capsized_galleon", scale, span, culled


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 11. Drowned Cathedral — a roofless gothic ruin: a west front with a pointed portal and a rose
#     window of stained glass, a nave whose walls still stand to their lancet windows, a crossing
#     tower with an open belfry under its spire, and three little chapels along the south side.
#     Three sizes, three behaviours: big fish sail the nave from the portal out through the ruined
#     east end, medium fish rest in the belfry on show, small fish hide in the chapels.
#     Gate (span 4x2x2): the nave, 5 wide, 9 tall, 25 long. Mouth 0.42: any fish. The church
#     stands in the middle of the long tank, so a big fish has room to line up on either door.
#     Open: the belfry, 4 x 5, 3 tall. Mouths 0.25, run 0.42 (fish to 0.46).
#     Hollow x3: the chapels, 3x3x3. Mouth 0.25, run 0.25 (fish to 0.27).
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def drowned_cathedral():
    span, scale = (4, 2, 2), 1 / 12
    nx, ny, nz = grid_dims(span, scale)          # 46 x 21 x 22; built at x 0..29, then moved in by 8
    b = Build((nx, ny, nz))
    rng = random.Random(1163)
    nave_x0, nave_x1, nave_y1, nave_z0, nave_z1 = 2, 26, 8, 6, 10
    wall_n, wall_s = 5, 11
    # Nave walls, ruined to a ragged top, lower toward the collapsed east end.
    for x in range(3, 27):
        for z in (wall_n, wall_s):
            top = 14 - (3 if x > 20 else 0) - (rng.random() < 0.3)
            for y in range(0, top + 1):
                b.set(x, y, z, weathered(rng))
            # Lancet windows high in the wall, some still glazed.
            if x % 4 == 1 and x < 23:
                for y in range(10, 13):
                    b.clear(x, y, z)
                b.clear(x, 13, z) if top > 13 else None
                if rng.random() < 0.6:
                    glass = rng.choice(("purple_stained_glass", "blue_stained_glass", "magenta_stained_glass"))
                    for y in range(10, 13):
                        b.set(x, y, z, glass)
    # Flying buttresses along the north side: a pier stepping in as it rises, an arch leaping
    # from its top to the wall, a pinnacle on the pier. The last, in the ruined east, has lost
    # its flyer and stands as a broken stump.
    def pinnacle(x, y, z):
        for yy in (y, y + 1):
            b.set(x, yy, z, "stone_brick_wall", **wall_post())
        b.set(x, y + 2, z, "pointed_dripstone", thickness="tip", vertical_direction="up", waterlogged=True)
    for x in range(5, 26, 5):
        broken = x == 25
        if x == 15:
            # Under the crossing tower: a plain pier, so nothing stands before the belfry's openings.
            for y in range(0, 9):
                for z in (1, 2, 3, 4):
                    if y <= 8 - 2 * (4 - z):
                        b.set(x, y, z, weathered(rng))
            continue
        for y in range(0, 7 if broken else 12):
            b.set(x, y, 2, weathered(rng))
            if y <= 8:
                b.set(x, y, 1, weathered(rng))
        if broken:
            b.set(x, 0, 0, weathered(rng, "mossy_stone_bricks"))
            continue
        for (z, y) in ((3, 10), (3, 11), (4, 11), (4, 12)):
            b.set(x, y, z, weathered(rng))
        b.set(x, 9, 3, "stone_brick_stairs", **stair("north", "top"))
        b.set(x, 10, 4, "stone_brick_stairs", **stair("north", "top"))
        pinnacle(x, 12, 2)
    # The west front: gable, rose window, pointed portal.
    for x in (2, 3):
        for z in range(3, 14):
            peak = 18 - abs(z - 8)
            for y in range(0, min(19, peak + 2)):
                b.set(x, y, z, weathered(rng))
    for (x, z) in ((2, 3), (2, 13)):                    # corner pinnacles
        for y in range(0, 17):
            b.set(x - 1, y, z, "chiseled_stone_bricks" if y % 5 == 4 else "stone_bricks")
    for x in (2, 3):
        for y in range(11, 18):
            for z in range(5, 12):
                d = math.hypot(y + 0.5 - 14.5, z + 0.5 - 8.5)
                if d <= 1.2:
                    b.set(x, y, z, "yellow_stained_glass")
                elif d <= 2.6:
                    b.set(x, y, z, "magenta_stained_glass" if (y + z) % 2 else "blue_stained_glass")
                elif d <= 3.3:
                    b.set(x, y, z, "chiseled_stone_bricks")
    for x in (2, 3):                                    # the portal's pointed head
        for z in (7, 8, 9):
            b.clear(x, nave_y1 + 1, z)
        b.clear(x, nave_y1 + 2, 8)
    for y in range(0, nave_y1 + 3):                     # archivolt round the portal
        for z in (nave_z0 - 1, nave_z1 + 1):
            if y <= nave_y1 + 1:
                b.set(1, y, z, "chiseled_stone_bricks")
    # The ruined east end: low broken walls round the breach, rubble off to the sides.
    for z in range(4, 13):
        if nave_z0 <= z <= nave_z1:
            continue
        for y in range(0, rng.randint(3, 7)):
            b.set(26, y, z, weathered(rng))
    for (x, z) in ((28, 3), (29, 4), (28, 12), (29, 13), (27, 14)):
        b.set(x, 0, z, weathered(rng, "mossy_stone_bricks"))
    # The crossing tower over the middle of the nave, its belfry open on every side.
    tx0, tx1, tz0, tz1 = 12, 17, 5, 11
    b.box(tx0, nave_y1 + 1, tz0, tx1, 12, tz1, "stone_bricks")
    for x in range(tx0, tx1 + 1):
        for y in range(nave_y1 + 1, 13):
            for z in range(tz0, tz1 + 1):
                b.set(x, y, z, weathered(rng))
    b.clear_box(tx0, 13, tz0, tx1, 15, tz1)               # the nave walls stop under the belfry
    for (x, z) in ((tx0, tz0), (tx0, tz1), (tx1, tz0), (tx1, tz1)):
        for y in range(13, 16):
            b.set(x, y, z, "chiseled_stone_bricks")
    b.box(tx0 - 1, 16, tz0 - 1, tx1 + 1, 16, tz1 + 1, "stone_bricks")
    for k, y in enumerate(range(17, 21)):
        b.box(tx0 + k, y, tz0 + k, tx1 - k, y, tz1 - k, "mossy_stone_bricks" if k % 2 else "stone_bricks")
    # The chapels along the south side, each under its own little gable.
    chapels = []
    for cx in (5, 10, 19):
        x0, x1, z0, z1 = cx, cx + 2, wall_s + 1, wall_s + 3
        b.box(x0 - 1, 0, z0, x0 - 1, 4, z1, "stone_bricks")
        b.box(x1 + 1, 0, z0, x1 + 1, 4, z1, "stone_bricks")
        b.box(x0, 0, z0, x1, 0, z1, "smooth_stone")
        b.box(x0 - 1, 4, z0, x1 + 1, 4, z1 + 1, "stone_bricks")
        b.box(x0, 5, z0, x1, 5, z1 + 1, "stone_brick_slab", type="bottom")
        b.set(x0 + 1, 5, z0, "chiseled_stone_bricks")
        b.set(x0 + 1, 6, z0, "lantern")
        chapels.append((x0, 1, z0, x1, 3, z1))
    # ── Adornment ──
    # Flying buttresses on the south side too, where the chapels leave room.
    for x in (24,):
        for y in range(0, 10):
            for z in (15, 16):
                b.set(x, y, z, weathered(rng))
        for (z, y) in ((14, 9), (14, 10), (13, 10), (13, 11), (12, 11), (12, 12)):
            b.set(x, y, z, weathered(rng))
        b.set(x, 8, 14, "stone_brick_stairs", **stair("south", "top"))
        b.set(x, 9, 13, "stone_brick_stairs", **stair("south", "top"))
        b.set(x, 10, 12, "stone_brick_stairs", **stair("south", "top"))
        pinnacle(x, 10, 16)
    # A string course along both nave walls under the windows, and gargoyles leaning out from
    # the wall heads.
    for x in range(4, 26):
        if (x, 9, wall_n - 1) not in b.v:
            b.set(x, 9, wall_n - 1, "stone_brick_stairs", **stair("south", "top"))
        if (x, 9, wall_s + 1) not in b.v:
            b.set(x, 9, wall_s + 1, "stone_brick_stairs", **stair("north", "top"))
    for x in (7, 9, 21, 23):
        top_n = max(y for (xx, y, zz) in b.v if xx == x and zz == wall_n)
        top_s = max(y for (xx, y, zz) in b.v if xx == x and zz == wall_s)
        b.setdefault(x, top_n - 1, wall_n - 1, "mossy_stone_brick_stairs", **stair("south", "top"))
        b.setdefault(x, top_s - 1, wall_s + 1, "mossy_stone_brick_stairs", **stair("north", "top"))
    # The portal's archivolts: two orders of pointed arch stepping out from the west front.
    for (x, z0, z1, y0) in ((1, nave_z0 - 1, nave_z1 + 1, nave_y1 + 1), (0, nave_z0 - 2, nave_z1 + 2, nave_y1 + 1)):
        for y in range(0, y0):
            for z in (z0, z1):
                b.set(x, y, z, "chiseled_stone_bricks" if y % 3 == 2 else "stone_bricks")
        zl, zr, y = z0, z1, y0
        while zl <= zr:
            b.set(x, y, zl, "stone_bricks")
            b.set(x, y, zr, "stone_bricks")
            if zl + 1 <= zr - 1:
                b.set(x, y, zl + 1, "stone_brick_stairs", **stair("north", "top"))
                b.set(x, y, zr - 1, "stone_brick_stairs", **stair("south", "top"))
                for z in range(zl + 2, zr - 1) if x == 1 else ():   # the inner order's tympanum
                    b.setdefault(x, y, z, "chiseled_stone_bricks")
            zl, zr, y = zl + 2, zr - 2, y + 1
        b.set(x, y, 8, "pointed_dripstone", thickness="tip", vertical_direction="up", waterlogged=True)
    # Crockets climbing the gable's edges, and a finial at its peak; spirelets on the corner pinnacles.
    for z in range(3, 14):
        top = max(y for (xx, y, zz) in b.v if xx == 2 and zz == z)
        if top < 18 and z != 8:
            b.setdefault(2, top + 1, z, "stone_brick_stairs", **stair("south" if z < 8 else "north"))
    b.set(2, 19, 8, "chiseled_stone_bricks")
    b.set(2, 20, 8, "pointed_dripstone", thickness="tip", vertical_direction="up", waterlogged=True)
    for z in (3, 13):
        pinnacle(1, 17, z)
    # The crossing tower: a pinnacle at each corner of its parapet, a finial on its spire.
    for (x, z) in ((tx0 - 1, tz0 - 1), (tx0 - 1, tz1 + 1), (tx1 + 1, tz0 - 1), (tx1 + 1, tz1 + 1)):
        pinnacle(x, 17, z)
    spire_top = max(y for (x, y, z) in b.v if tx0 <= x <= tx1 and tz0 <= z <= tz1)
    if spire_top < 20:
        b.set(15, spire_top + 1, 8, "pointed_dripstone", thickness="tip", vertical_direction="up", waterlogged=True)
    # Vault ribs: the transverse arches that once carried the roof, still leaping wall to wall over
    # the nave, lanterns hung from them on chains. Only in the west bays: the belfry's fish come
    # and go along the nave at the ribs' height. In the ruined east only a stub remains.
    for x in (5, 8):
        for (z, y) in ((6, 11), (6, 12), (7, 13), (8, 14), (9, 13), (10, 12), (10, 11)):
            b.set(x, y, z, "stone_bricks" if (z + x) % 3 else "mossy_stone_bricks")
        b.set(x, 12, 7, "stone_brick_stairs", **stair("north", "top"))
        b.set(x, 13, 8, "stone_brick_slab", type="top", waterlogged=True)
        b.set(x, 12, 9, "stone_brick_stairs", **stair("south", "top"))
        b.set(x, 12, 8, "iron_chain", axis="y", waterlogged=True)
        b.set(x, 11, 8, "lantern", hanging=True, waterlogged=True)
    for (z, y) in ((6, 10), (6, 11), (7, 12)):
        b.set(23, y, z, "cracked_stone_bricks")
    # Each chapel under a little pointed gable with a finial.
    for (x0, _, z0, x1, _, z1) in chapels:
        zf, xm = z1 + 1, x0 + 1
        b.box(x0 - 1, 5, zf, x1 + 1, 5, zf, "stone_bricks")
        b.set(x0, 6, zf, "stone_brick_stairs", **stair("east"))
        b.set(x1, 6, zf, "stone_brick_stairs", **stair("west"))
        b.set(xm, 6, zf, "chiseled_stone_bricks")
        b.set(xm, 7, zf, "pointed_dripstone", thickness="tip", vertical_direction="up", waterlogged=True)
    # Fallen masonry by the east end: a toppled pinnacle and a broken drum of a column, mossed over.
    for (x, z, block) in ((29, 2, "stone_bricks"), (30, 2, "chiseled_stone_bricks"), (31, 2, "mossy_stone_bricks"),
                          (29, 14, "mossy_stone_bricks"), (30, 15, "cracked_stone_bricks")):
        b.set(x, 0, z, block)
    b.set(32, 0, 2, "stone_brick_wall", up=True, north="none", south="none", east="none", west="tall", waterlogged=True)
    for (x, z) in ((29, 2), (31, 2), (29, 14)):
        b.setdefault(x, 1, z, "moss_carpet")
    # Kelp climbing the walls, seagrass in the nave, sea pickles at the door.
    for (x, z, h) in ((4, 4, 13), (25, 12, 9), (9, 4, 10), (16, 15, 6)):
        for y in range(0, h):
            if (x, y, z) not in b.v:
                b.set(x, y, z, "kelp_plant" if y < h - 1 else "kelp")
    for (x, z) in ((0, 7), (0, 10), (28, 15), (15, 17)):
        b.setdefault(x, 0, z, "sea_pickle", pickles=rng.randint(2, 4), waterlogged=True)
    culled = b.cull()
    b.shelter(nave_x0, 0, nave_z0, nave_x1, nave_y1, nave_z1, kind="gate", capacity=4)
    b.shelter(tx0 + 1, 13, tz0 + 1, tx1 - 1, 15, tz1 - 1, kind="open", capacity=2)
    for c in chapels:
        b.shelter(*c, capacity=1)
    b.shift(8)
    return b, "drowned_cathedral", scale, span, culled


# ─────────────────────────────────────────────────────────────────────────────────────────────────
# 12. Coral Warren — a living reef: a mound of dead coral rubble crowned by colonies grown into one
#     another, a heaving brain-coral massif, a blue tube-coral head, a red fire-coral knoll, yellow
#     table corals jutting out as shelves and staghorn branching from the crown. Three passages bore
#     through it at three heights; a shoal streams through all of them at once: small fish by the
#     low and high runs, behind lace curtains of coral fan, mediums by the wide middle one.
#     Gates (span 3x2x1): 3x3 (fish to 0.47 by height), 4x4 (to 0.62), 3x3, each 14 long. The reef
#     stands in the middle of the long tank, so a fish has room to line up on each passage.
# ─────────────────────────────────────────────────────────────────────────────────────────────────
def coral_warren():
    span, scale = (3, 2, 1), 0.0625
    nx, ny, nz = grid_dims(span, scale)          # 46 x 29 x 14; built at x 1..28, then moved in by 8
    b = Build((nx, ny, nz))
    rng = random.Random(1770)
    noise = value_noise(1770)
    x0, x1 = 8, 21
    passages = [(3, 5, 8, 10, True), (10, 13, 3, 6, False), (17, 19, 7, 9, True)]   # y0, y1, z0, z1, screened
    def in_corridor(x, y, z):
        """In front of a passage's mouth, out to the glass: kept clear, with a flat face round the mouth."""
        return not x0 <= x <= x1 and any(p[0] - 1 <= y <= p[1] + 1 and p[2] - 1 <= z <= p[3] + 1 for p in passages)
    # Colonies, each an ellipsoid of one coral: (centre, radii, block). The reef is their union,
    # roughened, and each voxel takes the colour of the colony it lies deepest in.
    colonies = [
        ((14.5, -1.0, 6.5), (7.6, 6.5, 6.4), "rubble"),                 # the dead-coral mound
        ((14.0, 9.0, 6.6), (6.6, 8.0, 6.0), "brain_coral_block"),       # the massif
        ((10.0, 18.5, 5.5), (5.0, 5.0, 4.8), "brain_coral_block"),
        ((19.0, 16.0, 8.0), (4.4, 5.6, 4.4), "tube_coral_block"),       # the tube head, west of the crown
        ((14.5, 22.5, 7.0), (4.2, 3.6, 4.0), "fire_coral_block"),       # the knoll on top
        ((7.5, 8.0, 9.5), (3.4, 4.0, 3.2), "bubble_coral_block"),       # a purple shoulder, low on the front
        ((21.0, 6.0, 3.5), (3.6, 4.6, 3.0), "tube_coral_block"),
        ((9.0, 13.5, 2.5), (3.0, 3.0, 2.6), "horn_coral_block"),
    ]
    def depth(x, y, z, c):
        (cx, cy, cz), (rx, ry, rz), _ = c
        return 1.0 - math.sqrt(((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 + ((z + 0.5 - cz) / rz) ** 2)
    dead = ("dead_brain_coral_block", "dead_tube_coral_block", "dead_horn_coral_block", "dead_fire_coral_block")
    def coral_at(x, y, z):
        best = max(colonies, key=lambda c: depth(x, y, z, c))
        block = best[2]
        if block == "rubble" or (y <= 2 and noise(x / 2.0, y, z / 2.0) < 0.2):
            return dead[int((noise(x / 1.3, y / 1.3 + 9, z / 1.3) + 1) * 2) % 4]
        if rng.random() < 0.05:              # a stray polyp of a neighbour's kind
            return rng.choice(("brain_coral_block", "tube_coral_block", "fire_coral_block", "bubble_coral_block"))
        return block
    for x in range(0, 30):
        for y in range(0, 26):
            for z in range(0, nz):
                field = max(depth(x, y, z, c) for c in colonies)
                field += 0.16 * noise(x / 2.4, y / 2.4, z / 2.4) + 0.07 * noise(x / 1.1, y / 1.1 + 40, z / 1.1)
                if field <= 0 or in_corridor(x, y, z):
                    continue
                if y < 5 and not 7 <= x <= 22:
                    continue                 # only overhangs reach the end tanks: their floors stay free
                b.set(x, y, z, coral_at(x, y, z))
    # Table corals: flat plates jutting out from the reef's sides, shelves for the fish to pass under.
    for (cx, py, cz, rr) in ((5.0, 15, 4.5, 3.4), (24.0, 15, 9.5, 3.0), (11.5, 22, 11.0, 2.4), (22.5, 21, 3.0, 2.2)):
        for x in range(math.floor(cx - rr), math.ceil(cx + rr) + 1):
            for z in range(max(0, math.floor(cz - rr)), min(nz - 1, math.ceil(cz + rr)) + 1):
                if math.hypot(x + 0.5 - cx, z + 0.5 - cz) + 0.5 * noise(x / 1.5, py, z / 1.5) <= rr \
                        and not in_corridor(x, py, z):
                    b.setdefault(x, py, z, "horn_coral_block")
    # The passages, bored through, their walls made good where the colonies thin out.
    for (py0, py1, pz0, pz1, screened) in passages:
        b.clear_box(x0, py0, pz0, x1, py1, pz1)
        for x in range(x0, x1 + 1):
            for y in range(py0 - 1, py1 + 2):
                for z in range(pz0 - 1, pz1 + 2):
                    if (x, y, z) not in b.v and not (py0 <= y <= py1 and pz0 <= z <= pz1):
                        b.set(x, y, z, coral_at(x, y, z))
        if screened:
            # A lace curtain of coral fans where the passage runs close under the front face.
            for x in range(x0 + 1, x1):
                for y in range(py0, py1 + 1):
                    z = pz1 + 1
                    if rng.random() < 0.5:
                        b.set(x, y, z, rng.choice(FANS), waterlogged=True)
                        for zz in range(z + 1, nz):
                            b.clear(x, y, zz)
    # Staghorn branching up from the crown: forking twigs of coral, a living tip on each.
    def twig(x, y, z, length, block):
        for _ in range(length):
            y += 1
            if y > 27:
                return
            if rng.random() < 0.45:
                dx, dz = rng.choice(((1, 0), (-1, 0), (0, 1), (0, -1)))
                x, z = x + dx, z + dz
            if not (0 <= z < nz) or in_corridor(x, y, z):
                return
            b.setdefault(x, y, z, block)
            if rng.random() < 0.22:
                twig(x, y, z, max(1, length // 2), block)
        if y + 1 <= 27 and not in_corridor(x, y + 1, z):
            b.setdefault(x, y + 1, z, block.replace("_block", ""), waterlogged=True)
    crown = sorted(((x, y, z) for (x, y, z) in b.v if y >= 18), key=lambda p: -p[1])
    for (x, y, z) in rng.sample(crown[:60], 9):
        if (x, y + 1, z) not in b.v:
            twig(x, y, z, rng.randint(2, 5), rng.choice(("fire_coral_block", "tube_coral_block", "bubble_coral_block")))
    # Fans and sea pickles on the reef's ledges, kelp swaying beside it.
    for (x, y, z), (name, _) in list(b.v.items()):
        if name.endswith("coral_block") and not name.startswith("dead") and (x, y + 1, z) not in b.v \
                and not in_corridor(x, y + 1, z) and y + 1 <= 27 and rng.random() < 0.09:
            b.set(x, y + 1, z, rng.choice(("tube_coral_fan", "brain_coral_fan", "fire_coral_fan", "horn_coral_fan",
                                           "bubble_coral", "brain_coral")), waterlogged=True)
        elif name.startswith("dead") and (x, y + 1, z) not in b.v and rng.random() < 0.12 and y + 1 <= 27:
            b.set(x, y + 1, z, "sea_pickle", pickles=rng.randint(1, 4), waterlogged=True)
    for (x, z, h) in ((3, 2, 16), (26, 12, 12), (5, 12, 7), (24, 1, 9)):
        for y in range(0, h):
            if (x, y, z) not in b.v and not in_corridor(x, y, z):
                b.set(x, y, z, "kelp_plant" if y < h - 1 else "kelp")
    for (x, z) in ((2, 9), (27, 3), (24, 13), (6, 1)):
        b.setdefault(x, 0, z, "sea_pickle", pickles=rng.randint(2, 4), waterlogged=True)
    culled = b.cull()
    for (py0, py1, pz0, pz1, _) in passages:
        b.shelter(x0, py0, pz0, x1, py1, pz1, kind="gate", capacity=3)
    b.shift(8)
    return b, "coral_warren", scale, span, culled


FLOOR = (amphora, drowned_bell, mangrove_knees, basalt_grotto, moon_gate, leviathans_seat)
SPANS = (sea_arch, sunken_ziggurat, capsized_galleon, drowned_cathedral, coral_warren)

COLORS = {
    "terracotta": "#a05a3c", "black_terracotta": "#2a1c18", "waxed_oxidized_copper": "#4fa892",
    "waxed_weathered_copper": "#6c9a6e", "iron_chain": "#555", "basalt": "#4a4a50", "polished_basalt": "#6a6a70",
    "smooth_basalt": "#3a3a3e", "magma_block": "#e06010", "calcite": "#e8e8e0", "polished_deepslate": "#3a3a40",
    "deepslate_tiles": "#30303a", "deepslate_tile_stairs": "#30303a", "cherry_leaves": "#f0a0c8",
    "cherry_log": "#4a2a30", "pink_petals": "#f0a0c8", "mangrove_log": "#6a3a2a", "mangrove_roots": "#5a3a2a",
    "muddy_mangrove_roots": "#4a3a2a", "mangrove_leaves": "#5a8a30", "sandstone": "#d8c890",
    "smooth_sandstone": "#e0d4a0", "cut_sandstone": "#d0bc80", "stone_bricks": "#8a8a8a",
    "mossy_stone_bricks": "#6a7a5a", "cracked_stone_bricks": "#7a7a7a", "chiseled_stone_bricks": "#9a9a9a",
    "stone_brick_stairs": "#8a8a8a", "stone_brick_slab": "#8a8a8a", "smooth_stone": "#9a9a9a",
    "bone_block": "#e8e0c8", "dark_oak_planks": "#4a3020", "stripped_dark_oak_log": "#5a4030",
    "waxed_oxidized_cut_copper": "#5ab89f", "spruce_log": "#4a3424", "gold_block": "#f4cc30",
    "kelp_plant": "#3a7a30", "kelp": "#3a7a30", "seagrass": "#4a9a40", "moss_carpet": "#5a8a30",
    "sea_pickle": "#8ab040", "lantern": "#ffb040", "pointed_dripstone": "#a08070",
    "brain_coral_block": "#d060a0", "tube_coral_block": "#3050d0", "bubble_coral_block": "#a020a0",
    "fire_coral_block": "#d03030", "horn_coral_block": "#d0c030", "tube_coral": "#3050d0",
    "purple_stained_glass": "#8030c0", "blue_stained_glass": "#3040c0", "magenta_stained_glass": "#c040c0",
    "yellow_stained_glass": "#e0d030", "bricks": "#9a4a3a", "deepslate_tile_slab": "#30303a",
    "dead_brain_coral_block": "#7a7470", "dead_tube_coral_block": "#827c78", "dead_horn_coral_block": "#8a847c",
    "dead_fire_coral_block": "#76706c", "stone_brick_wall": "#8a8a8a", "pointed_dripstone": "#a08070",
    "mossy_stone_brick_stairs": "#6a7a5a", "fire_coral": "#d03030", "bubble_coral": "#a020a0", "brain_coral": "#d060a0",
}


def preview(b, name):
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt
    import numpy as np
    xs = [p[0] for p in b.v]
    zs = [p[2] for p in b.v]
    ys = [p[1] for p in b.v]
    for s in b.shelters:
        xs += [c[0] for c in s["cells"]]
        ys += [c[1] for c in s["cells"]]
        zs += [c[2] for c in s["cells"]]
    mx, mz = min(xs), min(zs)
    sx, sy, sz = max(xs) - mx + 1, max(ys) + 1, max(zs) - mz + 1
    filled = np.zeros((sx, sz, sy), dtype=bool)
    colors = np.empty((sx, sz, sy), dtype=object)
    for (x, y, z), (block, _) in b.v.items():
        name_ = block if block in COLORS else ("coral_fan" if "fan" in block else block)
        filled[x - mx, sz - 1 - (z - mz), y] = True
        colors[x - mx, sz - 1 - (z - mz), y] = COLORS.get(block, "#c08040" if "fan" in block else "#ff00ff")
    shelter_colors = ("#00ffff60", "#ffff0060", "#ff00ff60", "#00ff0060", "#ff800060")
    for i, s in enumerate(b.shelters):
        for (x, y, z) in s["cells"]:
            filled[x - mx, sz - 1 - (z - mz), y] = True
            colors[x - mx, sz - 1 - (z - mz), y] = shelter_colors[i % len(shelter_colors)]
    fig = plt.figure(figsize=(16, 9))
    views = ((15, -90), (20, -50), (20, 40), (65, -120))
    for i, (elev, azim) in enumerate(views):
        ax = fig.add_subplot(2, 2, i + 1, projection="3d")
        ax.voxels(filled, facecolors=colors, edgecolor=(0, 0, 0, 0.12), linewidth=0.2)
        ax.set_box_aspect((sx, sz, sy))
        ax.view_init(elev=elev, azim=azim)
        ax.set_axis_off()
    plt.tight_layout()
    out = os.path.join(os.path.dirname(__file__), "preview", name + ".png")
    os.makedirs(os.path.dirname(out), exist_ok=True)
    plt.savefig(out, dpi=95, facecolor="#2a3a4a")
    plt.close(fig)
    return out


if __name__ == "__main__":
    names = [a for a in sys.argv[1:] if not a.startswith("--")]
    for design in FLOOR + SPANS:
        if names and design.__name__ not in names:
            continue
        result = design()
        if design in FLOOR:
            b, name, scale = result
            culled = b.cull()
            path, n = b.export_floor(name, scale)
            where = f"floor, scale {scale}"
        else:
            b, name, scale, span, culled = result
            path, n = b.export_span(name, span, scale)
            where = f"span {span}, scale {round(scale, 4)}"
        shelters = ", ".join(f"{s['kind']} {len(s['cells'])}" for s in b.shelters)
        print(f"{name}: {n} parts ({culled} hidden culled), {where}, shelters [{shelters}] -> {os.path.relpath(path, ROOT)}")
        if "--preview" in sys.argv:
            print("  preview:", preview(b, name))
